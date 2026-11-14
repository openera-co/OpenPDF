package com.reel.editor

import android.content.Context
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
import com.reel.editor.model.CropRect
import com.reel.editor.model.HeadPath
import com.reel.editor.model.Project
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import com.reel.editor.model.TrackFlags
import com.reel.editor.track.HeadTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real footage (a public-domain NASA interview): head tracking, speech, crop and hidden tracks. */
@RunWith(AndroidJUnit4::class)
class TrackingDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    @Test fun findsTheHeadInRealFootage() {
        val clip = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!
        val t0 = System.currentTimeMillis()
        val pts = HeadTracker(ctx).track(clip, 0, 5_000_000, 250_000)
        val ms = System.currentTimeMillis() - t0
        val found = pts.count { it.found }
        Log.i(TAG, "tracked ${pts.size} frames in $ms ms, found $found: " + pts.joinToString { "%.2f,%.2f".format(it.x, it.y) })
        assertTrue("found only $found of ${pts.size}", found >= pts.size * 0.8)
        val s = HeadPath.smooth(pts)
        // The astronaut sits in the middle of the frame, head in the upper half.
        val mx = s.map { it.x }.average()
        val my = s.map { it.y }.average()
        assertTrue("x $mx", mx in 0.38..0.62)
        assertTrue("y $my", my in 0.15..0.6)
    }

    @Test fun readsTextAloud() = runBlocking {
        val out = File(ctx.cacheDir, "tts.wav").apply { delete() }
        val r = runCatching { com.reel.editor.media.Speech.synthesize(ctx, "Hello from Reel", out) }
        Log.i(TAG, "tts: ${r.exceptionOrNull()?.message ?: "${out.length()} bytes"}")
        assumeTrue("no text-to-speech voice on this device", r.isSuccess)
        assertTrue(out.length() > 5_000)
    }

    @Test fun exportsCropWithHiddenTextAndMutedVideo() {
        val clip = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!
        val aspect = clip.sourceWidth.toFloat() / clip.sourceHeight
        val p = Project(
            clips = listOf(clip.copy(trimEndUs = 2_000_000, crop = CropRect.forRatio(9f / 16f, aspect))),
            texts = listOf(TextLayer(text = "HIDDEN", startUs = 0, durationUs = 2_000_000, animIn = null, animOut = null, style = TextStyle(color = 0xFFFF00FF, sizeSp = 200f, shadow = false))),
            tracks = TrackFlags(hidden = setOf(TrackFlags.TEXT), muted = setOf(TrackFlags.VIDEO)),
        )
        val built = CompositionFactory.build(ctx, p, 360, 30)!!
        val out = File(ctx.cacheDir, "crop.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        Handler(Looper.getMainLooper()).post {
            Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(15, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val f = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        File(ctx.getExternalFilesDir(null), "crop_frame.png").outputStream().use { f.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        var magenta = 0
        for (y in 0 until f.height step 2) for (x in 0 until f.width step 2) {
            val c = f.getPixel(x, y)
            if (Color.red(c) > 200 && Color.green(c) < 60 && Color.blue(c) > 200) magenta++
        }
        Log.i(TAG, "crop export ${f.width}x${f.height}, magenta=$magenta")
        assertTrue("hidden text was drawn ($magenta px)", magenta < 20)
        // A 9:16 crop of a 16:9 clip fills the 9:16 frame: no black bars at the sides.
        val edge = f.getPixel(2, f.height / 2)
        assertTrue("black edge ${Integer.toHexString(edge)}", Color.red(edge) + Color.green(edge) + Color.blue(edge) > 30)
    }

    companion object { const val TAG = "TrackingDeviceTest" }
}
