package com.reel.editor

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.media.AudioAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The full beat path on a device: AAC decode, downmix and decimation, then the native tracker,
 * on a 124 BPM drum loop with loud hats between the kicks. Beats must land on the kicks.
 */
@RunWith(AndroidJUnit4::class)
class BeatDeviceTest {
    @Test fun beatsLandOnTheKicks() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val f = File(ctx.cacheDir, "drums124.m4a")
        inst.context.assets.open("drums124.m4a").use { i -> f.outputStream().use { i.copyTo(it) } }
        val a = AudioAnalyzer(ctx).analyze(Uri.fromFile(f).toString())!!
        val period = 60.0 / 124
        val truth = (0 until 40).map { ((0.5 + it * period) * 1e6).toLong() }
        val errors = a.beatsUs.map { b -> truth.map { t -> b - t }.minBy { abs(it) } }
        val onKick = errors.count { abs(it) <= 20_000 }
        val median = errors.sorted().getOrElse(errors.size / 2) { 0 }
        Log.i("BeatDeviceTest", "bpm=${a.bpm} beats=${a.beatsUs.size} onKick=$onKick median=${median / 1000.0} ms")
        assertTrue("tempo ${a.bpm}", abs(a.bpm - 124) < 2)
        assertTrue("only ${a.beatsUs.size} beats", a.beatsUs.size >= 36)
        assertTrue("$onKick of ${a.beatsUs.size} beats on the kick", onKick >= a.beatsUs.size * 95 / 100)
        assertTrue("beats ${median / 1000.0} ms off", abs(median) <= 12_000)
    }
}
