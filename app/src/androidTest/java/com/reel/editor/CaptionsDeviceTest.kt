package com.reel.editor

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.captions.CaptionEngine
import com.reel.editor.nativebridge.NativeCore
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Project
import com.reel.editor.model.Speed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * End to end captions on the device: decode a clip's audio, resample, transcribe with whisper.cpp,
 * and place words on the timeline through trim and speed, with the model bundled in the APK.
 * Transcription is slow on emulators without CPU acceleration, so it runs only with -e full 1.
 */
@RunWith(AndroidJUnit4::class)
class CaptionsDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    @Test
    fun bundledModelLoadsFromTheApk() {
        val t0 = System.currentTimeMillis()
        val h = NativeCore.speechOpenAsset(ctx.assets, CaptionEngine.MODEL_ASSET)
        Log.i(TAG, "MODEL load took ${System.currentTimeMillis() - t0} ms, handle=$h")
        assertTrue("model failed to load", h != 0L)
        NativeCore.speechClose(h)
        // Stored uncompressed so it streams in place.
        ctx.assets.openFd(CaptionEngine.MODEL_ASSET).use { assertTrue(it.length > 200_000_000) }
    }

    @Test
    fun transcribesSpeechOntoTheTimeline() = runBlocking {
        assumeTrue("full transcription runs with -e full 1", InstrumentationRegistry.getArguments().getString("full") == "1")
        val f = File(ctx.cacheDir, "speech.mp4")
        inst.context.assets.open("speech.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val probed = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!
        // Trim the first second and play at 2x: word times must follow both.
        val clip = probed.copy(trimStartUs = 1_000_000, speed = Speed(2f))
        val project = Project(clips = listOf(clip))
        val stages = mutableSetOf<CaptionEngine.Stage>()
        val t0 = System.currentTimeMillis()
        val res = CaptionEngine(ctx).run(project, "auto", false) { stages += it.stage }
        val secs = (System.currentTimeMillis() - t0) / 1000.0
        res.lines.forEach { l -> Log.i(TAG, "CAPTION ${l.startUs / 1000}-${l.endUs / 1000} ${l.text}  [${l.words.joinToString { "${it.text}@${it.startUs / 1000}" }}]") }
        Log.i(TAG, "CAPTIONS language=${res.language} lines=${res.lines.size} took ${secs}s stages=$stages")
        assertEquals("en", res.language)
        val words = res.lines.flatMap { it.words }
        val all = words.joinToString(" ") { it.text.lowercase() }
        assertTrue(all, "country" in all && "americans" in all)
        // "country" is spoken around 5.65 s in the source: (5.65 - 1.0) / 2 = 2.3 s on the timeline.
        val country = words.first { it.text.lowercase().startsWith("country") }
        assertTrue("country at ${country.startUs}", abs(country.startUs - 2_325_000) < 400_000)
        val clipEnd = (clip.trimmedDurationUs / 2)
        assertTrue(words.all { it.startUs >= 0 && it.endUs <= clipEnd + 300_000 })
        assertTrue(res.lines.zipWithNext().all { (a, b) -> a.endUs <= b.startUs })
        assertTrue(CaptionEngine.Stage.LISTENING in stages)
    }

    companion object {
        const val TAG = "ReelCaptionsTest"
    }
}
