package com.reel.editor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.CompositionFactory
import com.reel.editor.media.AudioDecoder
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Project
import com.reel.editor.model.VoiceFx
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Exports speech with the voice tools on and checks length, level and the pitch shift. */
@RunWith(AndroidJUnit4::class)
class VoiceDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun export(p: Project, name: String): File {
        val built = CompositionFactory.build(ctx, p, 360, 30)!!
        val out = File(ctx.cacheDir, name).apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        Handler(Looper.getMainLooper()).post {
            Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(20, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        return out
    }

    /** Zero crossings per second over the loud parts: rises with pitch. */
    private fun zcr(f: File): Pair<Double, Double> {
        val pcm = AudioDecoder.decodeMono(ctx, Uri.fromFile(f).toString(), 0, Long.MAX_VALUE)!!
        val s = pcm.samples
        var crossings = 0
        var counted = 0
        var sq = 0.0
        val block = pcm.sampleRate / 50
        var i = 0
        while (i + block < s.size) {
            var e = 0.0
            for (k in i until i + block) e += s[k] * s[k]
            if (e / block > 1e-4) {
                for (k in i + 1 until i + block) if ((s[k - 1] < 0) != (s[k] < 0)) crossings++
                counted += block
                sq += e
            }
            i += block
        }
        return crossings.toDouble() * pcm.sampleRate / counted.coerceAtLeast(1) to Math.sqrt(sq / counted.coerceAtLeast(1))
    }

    private fun durationMs(f: File): Long {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong().also { r.release() }
    }

    @Test
    fun voiceToolsChangeTheSoundAndKeepTheLength() {
        val f = File(ctx.cacheDir, "speech.mp4")
        inst.context.assets.open("speech.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!
        val plain = export(Project(clips = listOf(clip)), "plain.mp4")
        val voiced = export(Project(clips = listOf(clip.copy(voice = VoiceFx(enhance = 1f, denoise = 0.7f, normalize = true, effect = "helium")))), "voiced.mp4")
        val robot = export(Project(clips = listOf(clip.copy(voice = VoiceFx(effect = "robot")))), "robot.mp4")
        val (zPlain, lPlain) = zcr(plain)
        val (zVoiced, lVoiced) = zcr(voiced)
        val (_, lRobot) = zcr(robot)
        Log.i(TAG, "VOICE plain zcr=${"%.0f".format(zPlain)} level=$lPlain | voiced zcr=${"%.0f".format(zVoiced)} level=$lVoiced | robot level=$lRobot | durations ${durationMs(plain)} ${durationMs(voiced)} ${durationMs(robot)}")
        assertTrue("same length", abs(durationMs(voiced) - durationMs(plain)) < 120)
        assertTrue("pitch went up (zcr ${zVoiced / zPlain}x)", zVoiced > zPlain * 1.4)
        assertTrue("audible", lVoiced > 0.01 && lRobot > 0.01)
    }

    companion object {
        const val TAG = "ReelVoiceTest"
    }
}
