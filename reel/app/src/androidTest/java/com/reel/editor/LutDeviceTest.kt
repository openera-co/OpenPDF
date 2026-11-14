package com.reel.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.CompositionFactory
import com.reel.editor.fx.UserLuts
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Project
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** An imported .cube look goes through the real render path: here one that swaps red and blue. */
@RunWith(AndroidJUnit4::class)
class LutDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun export(p: Project, name: String): Bitmap {
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
        assertTrue(done.await(15, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        val r = MediaMetadataRetriever(); r.setDataSource(out.absolutePath)
        return r.getFrameAtTime(300_000, MediaMetadataRetriever.OPTION_CLOSEST)!!.also { r.release() }
    }

    @Test fun importedLookIsApplied() {
        UserLuts.init(ctx)
        // A 2x2x2 cube that swaps red and blue (red changes fastest).
        val cube = File(ctx.cacheDir, "Swap RB.cube")
        cube.writeText(buildString { append("TITLE \"swap\"\nLUT_3D_SIZE 2\n"); for (b in 0..1) for (g in 0..1) for (r in 0..1) append("$b $g $r\n") })
        val look = UserLuts.import(ctx, Uri.fromFile(cube), cube.name)
        val src = File(ctx.cacheDir, "a.mp4").also { f -> inst.context.assets.open("a.mp4").use { i -> f.outputStream().use { i.copyTo(it) } } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(src))!!.copy(trimEndUs = 600_000)
        val plain = export(Project(clips = listOf(clip)), "plain.mp4")
        val swapped = export(Project(clips = listOf(clip.copy(lookId = look.id))), "lut.mp4")
        // Pixel by pixel: the look's red should be the plain blue, and its blue the plain red.
        var swapErr = 0.0; var sameErr = 0.0; var n = 0
        for (y in 0 until plain.height step 4) for (x in 0 until plain.width step 4) {
            val p = plain.getPixel(x, y); val q = swapped.getPixel(x, y)
            swapErr += kotlin.math.abs(Color.red(q) - Color.blue(p)) + kotlin.math.abs(Color.blue(q) - Color.red(p))
            sameErr += kotlin.math.abs(Color.red(q) - Color.red(p)) + kotlin.math.abs(Color.blue(q) - Color.blue(p))
            n++
        }
        android.util.Log.i("LutDeviceTest", "mean error if swapped ${swapErr / n}, if unchanged ${sameErr / n}")
        UserLuts.delete(look.id)
        assertTrue("red/blue not swapped (${swapErr / n} vs ${sameErr / n})", swapErr / n < 12 && sameErr / n > 3 * (swapErr / n))
    }
}
