package com.reel.editor.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.reel.editor.fx.ClipRenderEffect
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.RenderPlans
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Project
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimelineMath
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a project into a Media3 [Composition] used identically for preview and export. */
object CompositionFactory {
    /** Short side of the preview canvas; export uses the chosen resolution instead. */
    const val PREVIEW_HEIGHT = 720

    data class Built(val composition: Composition, val canvasWidth: Int, val canvasHeight: Int, val durationUs: Long)

    fun build(context: Context, project: Project, targetHeight: Int, exportFps: Int? = null, live: com.reel.editor.fx.LivePlans? = null, audioOnly: Boolean = false, premixInto: java.io.File? = null): Built? {
        if (project.clips.isEmpty()) return null
        val catalog = FxCatalog.get(context)
        val (w, h) = RenderPlans.canvasSize(project, targetHeight)
        val plans = RenderPlans.build(project, catalog, w, h)
        live?.set(plans)
        val placed = TimelineMath.place(project.clips)
        // Media3 mixes at the first input's sample rate: a 22 kHz voice clip would pull 44.1 kHz
        // music down to 22 kHz in the export. Every source is brought to one rate first.
        val exporting = exportFps != null || premixInto != null
        fun mixRate(chain: List<AudioProcessor>): List<AudioProcessor> =
            if (!exporting) chain else chain + androidx.media3.common.audio.SonicAudioProcessor().apply { setOutputSampleRateHz(AudioPremix.RATE) }

        val items = project.clips.mapIndexed { i, clip ->
            val plan = plans[i]
            val p = placed[i]
            val item = if (clip.kind == ClipKind.PHOTO) {
                MediaItem.Builder().setUri(clip.uri).setImageDurationMs(max(1L, clip.trimmedDurationUs / 1000)).build()
            } else {
                MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.trimStartUs)
                        .setEndPositionUs(clip.trimEndUs)
                        .build(),
                ).build()
            }
            val audio = ArrayList<AudioProcessor>()
            audio += voiceProcessors(clip.voice)
            val trackMuted = project.tracks.isMuted(com.reel.editor.model.TrackFlags.VIDEO)
            val volume = if (clip.muted || trackMuted) 0f else clip.volume
            val keyed = !clip.muted && !trackMuted && clip.volumeKeys.isNotEmpty()
            audio += VolumeFadeProcessor(if (keyed) 1f else volume, clip.fadeInUs, clip.fadeOutUs, p.durationUs, mediaToTimeline = { media ->
                SpeedMath.mediaToTimeline(clip.speed, clip.trimmedDurationUs, media)
            }, extraGain = if (keyed) { off -> com.reel.editor.model.interpolateValue(clip.volume, clip.volumeKeys, off) } else null)
            val b = EditedMediaItem.Builder(item)
                .setEffects(Effects(mixRate(audio), listOf(ClipRenderEffect(context, plan, live))))
            if (clip.kind == ClipKind.PHOTO) {
                b.setDurationUs(clip.trimmedDurationUs).setFrameRate(30)
            } else {
                // CompositionPlayer needs the original media duration up front; clipping applies on top.
                b.setDurationUs(clip.sourceDurationUs)
                val isDefaultSpeed = clip.speed.curve.isNullOrEmpty() && clip.speed.constant == 1f
                if (!isDefaultSpeed) {
                    b.setSpeed(SpeedParameters(SegmentSpeed(SpeedMath.segments(clip.speed, clip.trimmedDurationUs)), clip.keepPitch))
                }
            }
            b.build()
        }

        val sequences = ArrayList<EditedMediaItemSequence>()
        if (!audioOnly) {
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItems(items).build()
        } else {
            // Sound only: the clips' audio in sequence; silent clips and photos become gaps.
            val sb = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            for ((i, clip) in project.clips.withIndex()) {
                val dur = placed[i].durationUs
                if (clip.kind == ClipKind.PHOTO || !clip.hasAudio) { sb.addGap(dur); continue }
                val ei = items[i]
                sb.addItem(ei.buildUpon().setRemoveVideo(true).setEffects(Effects(ei.effects.audioProcessors, emptyList())).build())
            }
            sequences += sb.build()
        }

        val total = TimelineMath.duration(project)
        // Auto-duck: where people speak, from the waveform analysis already done for the timeline.
        val analyzer = (context.applicationContext as? com.reel.editor.ReelApp)?.audio
        val speech by lazy { com.reel.editor.model.Ducking.speechOnTimeline(project, { analyzer?.cached(it)?.peaks }, LEVELS_PER_SEC) }
        // Music, sounds and the sound of video overlays, each placed on the timeline.
        class Src(val uri: String, val startUs: Long, val trimStartUs: Long, val trimEndUs: Long, val sourceDurationUs: Long, val fx: List<AudioProcessor>)
        fun gainFn(volume: Float, keys: List<com.reel.editor.model.ValueKey>, duck: ((Long) -> Float)?): Pair<Float, ((Long) -> Float)?> = when {
            keys.isEmpty() -> volume to duck
            duck == null -> 1f to { off: Long -> com.reel.editor.model.interpolateValue(volume, keys, off) }
            else -> 1f to { off: Long -> com.reel.editor.model.interpolateValue(volume, keys, off) * duck(off) }
        }
        val srcs = ArrayList<Src>()
        // Sounds stop a moment before the timeline's end: a lane reaching the very end (or past it
        // by rounding) leaves the export's audio mixer waiting forever.
        val audioEnd = total - END_GUARD_US
        for (a in project.audio.filter { !it.muted && it.startUs < audioEnd && it.durationUs > 0 }) {
            val end = minOf(a.trimEndUs, a.trimStartUs + (audioEnd - a.startUs))
            val duck = if (a.duck && a.kind == com.reel.editor.model.AudioKind.MUSIC) { off: Long -> com.reel.editor.model.Ducking.gainAt(speech, a.startUs + off, a.duckDb) } else null
            val (vol, extra) = gainFn(a.volume, a.volumeKeys, duck)
            srcs += Src(a.uri, a.startUs, a.trimStartUs, end, a.sourceDurationUs, voiceProcessors(a.voice) + VolumeFadeProcessor(vol, a.fadeInUs, a.fadeOutUs, end - a.trimStartUs, extraGain = extra))
        }
        val overlaysHeard = !project.tracks.isMuted(com.reel.editor.model.TrackFlags.OVERLAYS) && !project.tracks.isHidden(com.reel.editor.model.TrackFlags.OVERLAYS)
        for (o in project.overlays.filter { overlaysHeard && it.kind == ClipKind.VIDEO && it.hasAudio && !it.muted && it.startUs < audioEnd && it.timelineDurationUs > 0 }) {
            val end = minOf(o.trimEndUs, o.trimStartUs + (audioEnd - o.startUs))
            val (vol, extra) = gainFn(o.volume, o.volumeKeys, null)
            srcs += Src(o.uri, o.startUs, o.trimStartUs, end, o.sourceDurationUs, listOf(VolumeFadeProcessor(vol, 0, 0, end - o.trimStartUs, extraGain = extra)))
        }
        // Export: with more than one source, mix them ourselves into one track (several audio
        // sequences at once can stall Media3's export).
        val premixed = if (premixInto != null && srcs.size > 1) {
            AudioPremix.render(context, srcs.map { AudioPremix.Source(it.uri, it.startUs, it.trimStartUs, it.trimEndUs, it.fx) }, audioEnd, premixInto)
        } else false
        if (premixed) {
            val frames = (premixInto!!.length() - 44) / 4
            val dur = frames * 1_000_000L / AudioPremix.RATE
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItem(EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(premixInto))).setRemoveVideo(true).setDurationUs(dur).build())
                .build()
        } else if (srcs.isNotEmpty()) {
            // Overlapping sources are split across sequences so each sequence stays gap-ordered.
            val lanes = ArrayList<MutableList<Src>>()
            for (a in srcs.sortedBy { it.startUs }) {
                val lane = lanes.firstOrNull { l -> l.last().let { it.startUs + (it.trimEndUs - it.trimStartUs) } <= a.startUs } ?: mutableListOf<Src>().also { lanes += it }
                lane += a
            }
            for (lane in lanes.take(MAX_AUDIO_LANES)) {
                val sb = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                var t = 0L
                for (a in lane) {
                    if (a.startUs > t) sb.addGap(a.startUs - t)
                    val mi = MediaItem.Builder().setUri(a.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionUs(a.trimStartUs).setEndPositionUs(a.trimEndUs).build(),
                    ).build()
                    sb.addItem(EditedMediaItem.Builder(mi).setRemoveVideo(true).setDurationUs(a.sourceDurationUs).setEffects(Effects(mixRate(a.fx), emptyList())).build())
                    t = a.startUs + (a.trimEndUs - a.trimStartUs)
                }
                sequences += sb.build()
            }
        }

        val cb = Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        // Frame dropping only when some clip runs faster than the export. Media3's dropper restarts
        // at every clip and lost the frames right after each start (a ~150 ms freeze at the start
        // and at every cut, PacingDeviceTest); at or below the export rate nothing needs dropping.
        if (exportFps != null && needsFrameDrop(context, project, exportFps)) {
            cb.setEffects(Effects(emptyList(), listOf(FrameDropEffect.createDefaultFrameDropEffect(exportFps.toFloat()))))
        }
        return Built(cb.build(), w, h, total)
    }

    /** Whether any video clip's frame rate is clearly above [fps] (unknown rates count as 30). */
    private fun needsFrameDrop(context: Context, project: Project, fps: Int): Boolean =
        project.clips.any { c ->
            c.kind == ClipKind.VIDEO && ((MediaProbe.frameRate(context, android.net.Uri.parse(c.uri)) ?: 30) * maxOf(c.speed.constant, c.speed.curve?.maxOrNull() ?: 0f)) > fps * 1.05f
        }

    /**
     * Sounds playing at the same moment, each on its own sequence. Sound effects stack up quickly,
     * so this leaves room for several on top of music and a voiceover.
     */
    const val MAX_AUDIO_LANES = 8

    /** How long before the timeline's end music, sounds and overlay audio stop. */
    const val END_GUARD_US = 50_000L

    /** Waveform levels per second, as produced by AudioAnalyzer. */
    const val LEVELS_PER_SEC = 40

    /** Pitch (Sonic) then the native voice chain; nothing when the voice is untouched. */
    private fun voiceProcessors(fx: com.reel.editor.model.VoiceFx): List<AudioProcessor> {
        if (fx.isIdentity) return emptyList()
        val out = ArrayList<AudioProcessor>()
        val pitch = com.reel.editor.model.VoiceEffects.pitchFactor(fx.effect)
        if (pitch != 1f) out += androidx.media3.common.audio.SonicAudioProcessor().apply { setPitch(pitch) }
        out += VoiceProcessor(fx)
        return out
    }

    /**
     * Parts of a project that need a new composition when they change. Everything else updates
     * live. [analyzed] lists sources whose levels are known, so auto-duck rebuilds when they arrive.
     */
    fun structure(project: Project, analyzed: Set<String> = emptySet()): Any = listOf(
        project.canvas.ratio,
        project.clips.map { listOf(it.id, it.uri, it.kind, it.trimStartUs, it.trimEndUs, it.speed, it.keepPitch, it.muted, it.volume, it.fadeInUs, it.fadeOutUs, it.voice, it.volumeKeys) },
        project.audio,
        project.tracks.muted, project.tracks.hidden.contains(com.reel.editor.model.TrackFlags.OVERLAYS),
        project.overlays.filter { it.kind == ClipKind.VIDEO && it.hasAudio }.map { listOf(it.id, it.uri, it.startUs, it.trimStartUs, it.trimEndUs, it.volume, it.muted, it.volumeKeys) },
        if (project.audio.any { it.duck }) analyzed else emptySet<String>(),
    )

    /** Bitrate that keeps quality consistent across resolutions and frame rates. */
    fun bitrate(width: Int, height: Int, fps: Int, factor: Float): Int {
        val bpp = 0.11f // bits per pixel per frame for H.264 "recommended"
        return (width.toLong() * height * fps * bpp * factor).toDouble().roundToInt().coerceIn(2_000_000, 120_000_000)
    }
}
