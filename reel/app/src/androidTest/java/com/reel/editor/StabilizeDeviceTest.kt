package com.reel.editor

import android.graphics.Bitmap
import android.graphics.Color
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
import com.reel.editor.fx.RenderPlans
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Fit
import com.reel.editor.model.HeadPath
import com.reel.editor.model.Project
import com.reel.editor.model.Stabilize
import com.reel.editor.track.Stabilizer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** A shaky clip, stabilized and exported, moves much less from frame to frame. */
@RunWith(AndroidJUnit4::class)
class StabilizeDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun gray(b: Bitmap): Stabilize.Gray {
        val w = 160; val h = 90
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        return Stabilize.Gray(w, h, FloatArray(w * h) { (0.299f * Color.red(px[it]) + 0.587f * Color.green(px[it]) + 0.114f * Color.blue(px[it])) / 255f })
    }

    /** Mean frame-to-frame movement (pixels at 160 wide) over the middle of a video. */
    private fun jitter(f: File): Float {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        // The middle of the clip, away from where the smoothing window is mirrored.
        val frames = (10 until 80 step 2).map { gray(r.getFrameAtTime(it * 33_333L, MediaMetadataRetriever.OPTION_CLOSEST)!!) }
        r.release()
        var sum = 0f
        for (i in 1 until frames.size) { val m = Stabilize.motion(frames[i - 1], frames[i]); sum += abs(m.dx) + abs(m.dy) }
        return sum / (frames.size - 1)
    }

    private fun export(p: Project, name: String): File {
        val built = CompositionFactory.build(ctx, p, 360, 30)!!
        val out = File(ctx.cacheDir, name).apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        Handler(Looper.getMainLooper()).post {
            Transformer.Builder(ctx).setMaxDelayBetweenMuxerSamplesMs(120_000).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(20, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        return out
    }

    @Test
    fun stabilizedExportShakesLess() {
        val f = File(ctx.cacheDir, "shaky.mp4")
        inst.context.assets.open("shaky.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!.copy(fit = Fit.FILL)
        val p0 = Project(clips = listOf(clip))
        val (times, motions) = Stabilizer(ctx).analyze(clip, clip.trimmedDurationUs)!!
        val corr = Stabilize.corrections(motions, 10f)
        val (cw, ch) = RenderPlans.canvasSize(p0, 1280)
        val (dw, dh) = RenderPlans.displaySize(clip)
        val keys = HeadPath.simplify(Stabilize.keyframes(times, corr, clip.transform, dw.toFloat() / dh, cw.toFloat() / ch, true, 1.14f), 0.0015f)
        val plain = export(p0, "shaky_plain.mp4")
        val steady = export(Project(clips = listOf(clip.copy(keyframes = keys))), "shaky_steady.mp4")
        val before = jitter(plain)
        val after = jitter(steady)
        Log.i(TAG, "STABILIZE keys=${keys.size} jitter before=$before after=$after")
        assertTrue("shake reduced ($before -> $after)", after < before * 0.4f)
    }

    companion object {
        const val TAG = "ReelStabilizeTest"
    }
}
