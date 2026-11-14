package com.reel.editor

import android.content.Context
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
import com.reel.editor.media.MediaProbe
import com.reel.editor.media.Reverser
import com.reel.editor.model.Privacy
import com.reel.editor.model.PrivacyMode
import com.reel.editor.model.Project
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BatchDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private fun diff(a: Bitmap, b: Bitmap): Double {
        val sa = Bitmap.createScaledBitmap(a, 64, 36, true); val sb = Bitmap.createScaledBitmap(b, 64, 36, true)
        var d = 0.0
        for (y in 0 until 36) for (x in 0 until 64) { val p = sa.getPixel(x, y); val q = sb.getPixel(x, y); d += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q)) }
        return d / (64 * 36 * 3)
    }

    @Test fun reversesRealFootage() {
        val src = asset("a.mp4")
        val clip = MediaProbe.probeClip(ctx, src)!!
        val out = File(ctx.cacheDir, "rev.mp4").apply { delete() }
        val t0 = System.currentTimeMillis()
        assertTrue(Reverser(ctx).reverse(src.toString(), 0, clip.sourceDurationUs, clip.hasAudio, out) {})
        Log.i(TAG, "reversed ${clip.sourceDurationUs / 1000} ms in ${System.currentTimeMillis() - t0} ms, ${out.length()} bytes")
        val rev = MediaProbe.probeClip(ctx, Uri.fromFile(out))!!
        assertTrue("duration ${rev.sourceDurationUs} vs ${clip.sourceDurationUs}", abs(rev.sourceDurationUs - clip.sourceDurationUs) < 200_000)
        val r1 = MediaMetadataRetriever().apply { setDataSource(ctx, src) }
        val r2 = MediaMetadataRetriever().apply { setDataSource(out.absolutePath) }
        val lastOrig = r1.getFrameAtTime(clip.sourceDurationUs - 60_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        val firstOrig = r1.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)!!
        val firstRev = r2.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)!!
        val dSame = diff(firstRev, lastOrig)
        val dOther = diff(firstRev, firstOrig)
        Log.i(TAG, "first reversed vs last original $dSame, vs first original $dOther, audio=${rev.hasAudio}")
        assertTrue("reversed start doesn't match original end", dSame < dOther)
        assertTrue(rev.hasAudio == clip.hasAudio)
    }

    @Test fun exportsFaceBlur() {
        val clip = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!.let { it.copy(trimEndUs = 1_000_000) }
        fun export(p: Project, name: String): Bitmap {
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
            return r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!.also { r.release() }
        }
        val plain = export(Project(clips = listOf(clip)), "plain.mp4")
        val blurred = export(Project(clips = listOf(clip.copy(privacy = Privacy(mode = PrivacyMode.SOLID, x = 0.5f, y = 0.5f, size = 0.2f)))), "blur.mp4")
        File(ctx.getExternalFilesDir(null), "blur_frame.png").outputStream().use { blurred.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The block is black in the middle; the corners are untouched.
        val mid = blurred.getPixel(blurred.width / 2, blurred.height / 2)
        Log.i(TAG, "middle ${Integer.toHexString(mid)}, corner diff ${abs(Color.red(plain.getPixel(4, 4)) - Color.red(blurred.getPixel(4, 4)))}")
        assertTrue(Color.red(mid) + Color.green(mid) + Color.blue(mid) < 40)
        assertTrue(abs(Color.red(plain.getPixel(4, 4)) - Color.red(blurred.getPixel(4, 4))) < 20)
    }

    companion object { const val TAG = "BatchDeviceTest" }
}
