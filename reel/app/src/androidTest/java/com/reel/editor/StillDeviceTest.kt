package com.reel.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.StillRenderer
import com.reel.editor.fx.RenderPlans
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Fit
import com.reel.editor.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Save frame renders the project at full export size, at the playhead's frame. */
@RunWith(AndroidJUnit4::class)
class StillDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun diff(a: Bitmap, b: Bitmap): Double {
        val x = Bitmap.createScaledBitmap(a, 96, 54, true); val y = Bitmap.createScaledBitmap(b, 96, 54, true)
        var s = 0L
        for (j in 0 until 54) for (i in 0 until 96) {
            val p = x.getPixel(i, j); val q = y.getPixel(i, j)
            s += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q))
        }
        return s / (96.0 * 54 * 3)
    }

    @Test
    fun rendersTheRightFrameAtExportSize() = runBlocking {
        val f = File(ctx.cacheDir, "shaky.mp4")
        inst.context.assets.open("shaky.mp4").use { i -> f.outputStream().use { it.write(i.readBytes()) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!.copy(fit = Fit.FILL)
        // A 16:9 canvas so the clip fills it and frames can be compared directly.
        val p = Project(clips = listOf(clip)).let { it.copy(canvas = it.canvas.copy(ratio = com.reel.editor.model.AspectRatio.R16_9)) }
        val (w, h) = RenderPlans.canvasSize(p, 1080)
        val r = MediaMetadataRetriever().apply { setDataSource(f.absolutePath) }
        for (t in listOf(0L, 1_500_000L, 2_400_000L)) {
            val still = StillRenderer.render(ctx, p, t, 1080)
            assertNotNull("no still at $t", still)
            assertEquals(w, still!!.width); assertEquals(h, still.height)
            val src = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)!!
            val near = diff(still, src)
            // The shaky clip moves every frame, so a wrong frame is clearly further away.
            val other = diff(still, r.getFrameAtTime((t + 400_000) % 2_900_000, MediaMetadataRetriever.OPTION_CLOSEST)!!)
            Log.i(TAG, "STILL t=$t size=${still.width}x${still.height} diff=$near other=$other")
            assertTrue("frame at $t differs ($near)", near < 12)
            assertTrue("frame at $t is the right one ($near vs $other)", near < other)
        }
        r.release()
    }

    companion object {
        const val TAG = "ReelStillTest"
    }
}
