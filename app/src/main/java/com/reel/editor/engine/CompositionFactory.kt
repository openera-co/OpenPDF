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
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Project
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimelineMath
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a project into a Media3 [Composition] used identically for preview and export. */
object CompositionFactory {
    const val PREVIEW_HEIGHT = 1280

    data class Built(val composition: Composition, val canvasWidth: Int, val canvasHeight: Int, val durationUs: Long)

    fun build(context: Context, project: Project, targetHeight: Int, exportFps: Int? = null, live: com.reel.editor.fx.LivePlans? = null): Built? {
        if (project.clips.isEmpty()) return null
        val catalog = FxCatalog.get(context)
        val (w, h) = RenderPlans.canvasSize(project, targetHeight)
        val plans = RenderPlans.build(project, catalog, w, h)
        live?.set(plans)
        val placed = TimelineMath.place(project.clips)

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
            val volume = if (clip.muted) 0f else clip.volume
            audio += VolumeFadeProcessor(volume, clip.fadeInUs, clip.fadeOutUs, p.durationUs) { media ->
                SpeedMath.mediaToTimeline(clip.speed, clip.trimmedDurationUs, media)
            }
            val b = EditedMediaItem.Builder(item)
                .setEffects(Effects(audio, listOf(ClipRenderEffect(context, plan, live))))
            if (clip.kind == ClipKind.PHOTO) {
                b.setDurationUs(clip.trimmedDurationUs).setFrameRate(30)
            } else {
                val isDefaultSpeed = clip.speed.curve.isNullOrEmpty() && clip.speed.constant == 1f
                if (!isDefaultSpeed) {
                    b.setSpeed(SpeedParameters(SegmentSpeed(SpeedMath.segments(clip.speed, clip.trimmedDurationUs)), clip.keepPitch))
                }
            }
            b.build()
        }

        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItems(items).build()

        val total = TimelineMath.duration(project)
        val music = project.audio.filter { it.startUs < total && it.durationUs > 0 }.sortedBy { it.startUs }
        if (music.isNotEmpty()) {
            // Overlapping audio items are split across sequences so each sequence stays gap-ordered.
            val lanes = ArrayList<MutableList<com.reel.editor.model.AudioItem>>()
            for (a in music) {
                val lane = lanes.firstOrNull { it.last().endUs <= a.startUs } ?: mutableListOf<com.reel.editor.model.AudioItem>().also { lanes += it }
                lane += a
            }
            for (lane in lanes.take(4)) {
                val sb = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                var t = 0L
                for (a in lane) {
                    if (a.startUs > t) sb.addGap(a.startUs - t)
                    val end = minOf(a.trimEndUs, a.trimStartUs + (total - a.startUs))
                    val mi = MediaItem.Builder().setUri(a.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionUs(a.trimStartUs).setEndPositionUs(end).build(),
                    ).build()
                    val dur = end - a.trimStartUs
                    sb.addItem(
                        EditedMediaItem.Builder(mi).setRemoveVideo(true)
                            .setEffects(Effects(listOf(VolumeFadeProcessor(a.volume, a.fadeInUs, a.fadeOutUs, dur)), emptyList()))
                            .build(),
                    )
                    t = a.startUs + dur
                }
                sequences += sb.build()
            }
        }

        val cb = Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        if (exportFps != null) {
            cb.setEffects(Effects(emptyList(), listOf(FrameDropEffect.createDefaultFrameDropEffect(exportFps.toFloat()))))
        }
        return Built(cb.build(), w, h, total)
    }

    /** Parts of a project that need a new composition when they change. Everything else updates live. */
    fun structure(project: Project): Any = listOf(
        project.canvas.ratio,
        project.clips.map { listOf(it.id, it.uri, it.kind, it.trimStartUs, it.trimEndUs, it.speed, it.keepPitch, it.muted, it.volume, it.fadeInUs, it.fadeOutUs) },
        project.audio,
    )

    /** Bitrate that keeps quality consistent across resolutions and frame rates. */
    fun bitrate(width: Int, height: Int, fps: Int, factor: Float): Int {
        val bpp = 0.11f // bits per pixel per frame for H.264 "recommended"
        return (width.toLong() * height * fps * bpp * factor).toDouble().roundToInt().coerceIn(2_000_000, 120_000_000)
    }
}
