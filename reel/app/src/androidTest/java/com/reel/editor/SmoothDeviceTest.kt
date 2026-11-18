package com.reel.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.media.Smoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Frame interpolation on a 30 fps clip whose 120 fps original is known: the new in-between
 * frames must match the real ones far better than repeating the last frame does.
 */
@RunWith(AndroidJUnit4::class)
class SmoothDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun asset(name: String) = File(ctx.cacheDir, name).also { f -> inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } } }

    private fun diff(a: Bitmap, b: Bitmap): Double {
        val x = Bitmap.createScaledBitmap(a, 320, 180, true); val y = Bitmap.createScaledBitmap(b, 320, 180, true)
        var s = 0L; var n = 0
        for (j in 15 until 165) for (i in 15 until 305) {
            val p = x.getPixel(i, j); val q = y.getPixel(i, j)
            s += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q)); n += 3
        }
        return s.toDouble() / n
    }

    @Test
    fun makesRealInBetweenFrames() {
        val src = asset("shaky.mp4")
        val truth = asset("shaky120.mp4")
        val out = File(ctx.cacheDir, "smooth_out.mp4").apply { delete() }
        val t0 = System.currentTimeMillis()
        assertTrue(Smoother(ctx).smooth(Uri.fromFile(src).toString(), 0, 3_000_000, false, out) {})
        val took = System.currentTimeMillis() - t0

        val ro = MediaMetadataRetriever().apply { setDataSource(out.absolutePath) }
        val rs = MediaMetadataRetriever().apply { setDataSource(src.absolutePath) }
        val rt = MediaMetadataRetriever().apply { setDataSource(truth.absolutePath) }
        val frames = ro.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
        val ms = ro.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        Log.i(TAG, "SMOOTH frames=$frames duration=$ms ms took=$took ms")
        assertEquals("4 frames per source frame", 360, frames)
        assertTrue("same length ($ms ms)", abs(ms - 3000) < 60)

        var interp = 0.0; var hold = 0.0; var n = 0
        for (i in listOf(10, 23, 37, 51, 66, 80)) {
            val a = rs.getFrameAtIndex(i)!!
            for (k in 1..3) {
                val real = rt.getFrameAtIndex(i * 4 + k)!!
                interp += diff(ro.getFrameAtIndex(i * 4 + k)!!, real)
                hold += diff(a, real)
                n++
            }
            // Every fourth frame is a real one.
            assertTrue("frame ${i * 4} is the source frame", diff(ro.getFrameAtIndex(i * 4)!!, a) < 3.0)
        }
        ro.release(); rs.release(); rt.release()
        Log.i(TAG, "SMOOTH in-between error ${interp / n} vs repeating the frame ${hold / n}")
        assertTrue("in-between frames follow the motion (${interp / n} vs ${hold / n})", interp / n < hold / n * 0.65)
    }

    @Test
    fun planReachesAbout120() {
        assertEquals(4, Smoother.plan(30.0).factor)
        assertEquals(5, Smoother.plan(24.0).factor)
        assertEquals(2, Smoother.plan(60.0).factor)
        assertEquals(1, Smoother.plan(120.0).factor)
    }

    companion object {
        const val TAG = "ReelSmoothTest"
    }
}
