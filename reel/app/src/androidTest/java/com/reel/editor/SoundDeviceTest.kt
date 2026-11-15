package com.reel.editor

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
import com.reel.editor.media.SoundFx
import com.reel.editor.model.AudioItem
import com.reel.editor.model.AudioKind
import com.reel.editor.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Every built-in sound decodes on the phone, and placed sounds land in the export at their time. */
@RunWith(AndroidJUnit4::class)
class SoundDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    @Test
    fun everySoundDecodes() {
        for (s in SoundFx.all) {
            val f = SoundFx.file(ctx.filesDir, s)
            val pcm = AudioDecoder.decodeMono(ctx, Uri.fromFile(f).toString(), 0, Long.MAX_VALUE)
            assertTrue("${s.id} did not decode", pcm != null && pcm.samples.isNotEmpty())
            val ms = pcm!!.samples.size * 1000L / pcm.sampleRate
            assertTrue("${s.id} length $ms ms", kotlin.math.abs(ms - (s.seconds * 1000).toLong()) < 40)
        }
    }

    @Test
    fun placedSoundsAreInTheExport() {
        val v = File(ctx.cacheDir, "a.mp4")
        inst.context.assets.open("a.mp4").use { i -> v.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(v))!!.copy(muted = true)
        fun item(id: String, atUs: Long): AudioItem {
            val s = SoundFx.byId(id)!!
            val f = SoundFx.file(ctx.filesDir, s)
            val dur = (f.length() - 44) / 2 * 1_000_000L / SoundFx.RATE
            return AudioItem(uri = Uri.fromFile(f).toString(), title = s.name, kind = AudioKind.SOUND, sourceDurationUs = dur, startUs = atUs)
        }
        val p = Project(clips = listOf(clip), audio = listOf(item("kick", 500_000), item("ding", 1_500_000)))
        val out = export(p)
        val pcm = AudioDecoder.decodeMono(ctx, Uri.fromFile(out).toString(), 0, Long.MAX_VALUE)!!
        fun rms(fromMs: Int, toMs: Int): Double {
            val a = (fromMs * pcm.sampleRate / 1000).coerceIn(0, pcm.samples.size)
            val b = (toMs * pcm.sampleRate / 1000).coerceIn(a, pcm.samples.size)
            var e = 0.0
            for (i in a until b) e += pcm.samples[i] * pcm.samples[i]
            return sqrt(e / (b - a).coerceAtLeast(1))
        }
        val before = rms(100, 450)
        val kick = rms(500, 700)
        val gap = rms(1050, 1450)
        val ding = rms(1500, 1900)
        Log.i(TAG, "SOUNDS before=$before kick=$kick gap=$gap ding=$ding")
        assertEquals("silent before the first sound", 0.0, before, 0.01)
        assertTrue("kick is in the export", kick > 0.05)
        assertTrue("ding is in the export", ding > 0.05)
        assertTrue("kick has faded before the ding", gap < kick * 0.3)
    }

    /** Five quiet sounds hold five lanes; a sixth, loud one must still be heard. */
    @Test
    fun manyOverlappingSoundsAreAllMixed() {
        val v = File(ctx.cacheDir, "a.mp4")
        inst.context.assets.open("a.mp4").use { i -> v.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(v))!!.copy(muted = true)
        val wind = SoundFx.byId("wind")!!
        val wf = SoundFx.file(ctx.filesDir, wind)
        val wdur = (wf.length() - 44) / 2 * 1_000_000L / SoundFx.RATE
        val fillers = (0 until 5).map { i -> AudioItem(uri = Uri.fromFile(wf).toString(), title = "w$i", kind = AudioKind.SOUND, sourceDurationUs = wdur, startUs = i * 50_000L, volume = 0.001f) }
        val kick = SoundFx.byId("kick")!!
        val kf = SoundFx.file(ctx.filesDir, kick)
        val kdur = (kf.length() - 44) / 2 * 1_000_000L / SoundFx.RATE
        val p = Project(clips = listOf(clip), audio = fillers + AudioItem(uri = Uri.fromFile(kf).toString(), title = "kick", kind = AudioKind.SOUND, sourceDurationUs = kdur, startUs = 600_000))
        val out = export(p)
        val pcm = AudioDecoder.decodeMono(ctx, Uri.fromFile(out).toString(), 0, Long.MAX_VALUE)!!
        val a = 600 * pcm.sampleRate / 1000; val b = 800 * pcm.sampleRate / 1000
        var e = 0.0
        for (i in a until b) e += pcm.samples[i] * pcm.samples[i]
        val rms = sqrt(e / (b - a))
        Log.i(TAG, "SOUNDS sixth lane rms=$rms")
        assertTrue("the sixth overlapping sound is in the export ($rms)", rms > 0.05)
    }

    private fun export(p: Project): File {
        val built = CompositionFactory.build(ctx, p, 360, 30)!!
        val out = File(ctx.cacheDir, "sounds.mp4").apply { delete() }
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

    companion object {
        const val TAG = "ReelSoundTest"
    }
}
