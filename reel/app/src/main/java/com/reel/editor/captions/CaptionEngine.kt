package com.reel.editor.captions

import android.content.Context
import com.reel.editor.media.AudioDecoder
import com.reel.editor.model.AudioKind
import com.reel.editor.model.CaptionGrouping
import com.reel.editor.model.CaptionLine
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Project
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimedWord
import com.reel.editor.model.TimelineMath
import com.reel.editor.nativebridge.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import java.util.concurrent.atomic.AtomicLong

/**
 * Transcribes the speech in a project's clips and voiceovers on the device (whisper.cpp, model
 * bundled in the APK) and returns caption lines on the timeline.
 */
class CaptionEngine(private val context: Context) {
    data class Progress(val stage: Stage, val fraction: Float)
    enum class Stage { LISTENING, WRITING }
    data class Result(val lines: List<CaptionLine>, val language: String)

    /** One stretch of audio and how its times map onto the timeline. */
    private class Source(val uri: String, val mediaStartUs: Long, val mediaEndUs: Long, val toTimeline: (Long) -> Long)

    suspend fun run(project: Project, language: String, translate: Boolean, progress: (Progress) -> Unit): Result {
        val sources = sources(project)
        if (sources.isEmpty()) return Result(emptyList(), "")
        return withContext(Dispatchers.Default) {
            progress(Progress(Stage.LISTENING, 0f))
            val handle = NativeCore.speechOpenAsset(context.assets, MODEL_ASSET)
            if (handle == 0L) throw IllegalStateException("Captions are unavailable on this device.")
            active.set(handle)
            try {
                val words = ArrayList<TimedWord>()
                var detected = ""
                val total = sources.sumOf { it.mediaEndUs - it.mediaStartUs }.coerceAtLeast(1)
                var done = 0L
                for (s in sources) {
                    currentCoroutineContext().ensureActive()
                    progress(Progress(Stage.LISTENING, done.toFloat() / total))
                    val pcm = AudioDecoder.decodeMono(context, s.uri, s.mediaStartUs, s.mediaEndUs) { !isActive } ?: continue
                    if (pcm.samples.isEmpty()) continue
                    val pcm16 = NativeCore.resample(pcm.samples, pcm.sampleRate, 16_000)
                    val span = s.mediaEndUs - s.mediaStartUs
                    val base = done
                    // The model reports progress once per 30 s window, so a short clip would sit at
                    // 0% until it is done. In between, move the bar on an estimate from this phone's
                    // measured speed; the model's own reports take over when they come.
                    val reported = java.util.concurrent.atomic.AtomicInteger(0)
                    val expectedMs = (span / 1000.0 * speedFactor()).coerceAtLeast(1500.0)
                    val started = System.nanoTime()
                    val res = coroutineScope {
                        val ticker = launch {
                            while (isActive) {
                                delay(250)
                                val t = (System.nanoTime() - started) / 1e6
                                // Approaches 90% of this clip's share; never claims it is finished.
                                val est = (0.9 * (1 - exp(-t / expectedMs))).coerceAtMost(0.9)
                                val pct = maxOf(reported.get() / 100.0, est)
                                progress(Progress(Stage.LISTENING, (base + span * pct).toFloat() / total))
                            }
                        }
                        try {
                            NativeCore.speechTranscribe(handle, pcm16, language, translate, threads()) { pct ->
                                reported.set(pct)
                                progress(Progress(Stage.LISTENING, (base + span * pct / 100).toFloat() / total))
                            }
                        } finally {
                            ticker.cancel()
                        }
                    }
                    learnSpeed(span, System.nanoTime() - started)
                    currentCoroutineContext().ensureActive()
                    done += span
                    if (res == null) continue
                    @Suppress("UNCHECKED_CAST") val texts = res[0] as Array<String>
                    val starts = res[1] as LongArray
                    val ends = res[2] as LongArray
                    if (detected.isEmpty()) detected = res[4] as String
                    for (i in texts.indices) {
                        val a = s.toTimeline(s.mediaStartUs + starts[i] * 1000)
                        val b = s.toTimeline(s.mediaStartUs + ends[i] * 1000)
                        words += TimedWord(texts[i], a, maxOf(a + 1, b))
                    }
                }
                progress(Progress(Stage.WRITING, 1f))
                Result(CaptionGrouping.group(words), detected)
            } finally {
                active.set(0)
                NativeCore.speechClose(handle)
            }
        }
    }

    private val active = AtomicLong(0)

    /** Stops a running transcription quickly; also cancel the coroutine that called [run]. */
    fun cancel() {
        active.get().takeIf { it != 0L }?.let(NativeCore::speechCancel)
    }

    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private val prefs by lazy { context.getSharedPreferences("captions", Context.MODE_PRIVATE) }

    /** Milliseconds of work per second of audio on this phone (starts at a mid-range guess). */
    private fun speedFactor(): Double = prefs.getFloat("ms_per_audio_s", 600f).toDouble()

    private fun learnSpeed(spanUs: Long, tookNs: Long) {
        if (spanUs < 2_000_000) return
        val measured = (tookNs / 1e6) / (spanUs / 1e6)
        // Smooth over runs; the 30 s window makes very short clips look slower per second.
        val next = (speedFactor() * 0.6 + measured * 0.4).coerceIn(50.0, 20_000.0)
        prefs.edit().putFloat("ms_per_audio_s", next.toFloat()).apply()
    }

    /** Clip audio (unmuted video clips) and voiceovers, in timeline order. Music is skipped. */
    private fun sources(p: Project): List<Source> {
        val out = ArrayList<Source>()
        for (pc in TimelineMath.place(p.clips)) {
            val c = pc.clip
            if (c.kind != ClipKind.VIDEO || !c.hasAudio || c.muted || c.volume <= 0f) continue
            out += Source(c.uri, c.trimStartUs, c.trimEndUs) { media ->
                pc.startUs + SpeedMath.mediaToTimeline(c.speed, c.trimmedDurationUs, media - c.trimStartUs)
            }
        }
        for (a in p.audio) {
            if (a.kind != AudioKind.VOICEOVER || a.volume <= 0f) continue
            out += Source(a.uri, a.trimStartUs, a.trimEndUs) { media -> a.startUs + (media - a.trimStartUs) }
        }
        return out
    }

    companion object {
        /** Languages offered in the panel (whisper supports many more through "auto"). */
        val LANGUAGES = listOf(
            "auto" to "Auto", "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
            "it" to "Italiano", "pt" to "Português", "nl" to "Nederlands", "ru" to "Русский", "uk" to "Українська",
            "pl" to "Polski", "tr" to "Türkçe", "ar" to "العربية", "hi" to "हिन्दी", "id" to "Indonesia",
            "vi" to "Tiếng Việt", "th" to "ไทย", "ja" to "日本語", "ko" to "한국어", "zh" to "中文",
        )

        /** Bundled in the APK, stored uncompressed so it streams without a copy. */
        const val MODEL_ASSET = "models/speech.bin"
    }
}
