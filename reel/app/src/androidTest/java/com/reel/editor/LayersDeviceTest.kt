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
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.AdjustmentLayer
import com.reel.editor.model.Adjustments
import com.reel.editor.model.BlendMode
import com.reel.editor.model.Mask
import com.reel.editor.model.MaskShape
import com.reel.editor.model.OverlayClip
import com.reel.editor.model.Project
import com.reel.editor.model.ShapeLayer
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import com.reel.editor.model.Transform
import com.reel.editor.model.ValueKey
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Exports a project with every new kind of layer: a masked, blended video overlay with sound, a
 * shape, an adjustment layer that turns the video grey, volume keyframes and a long caption, then
 * checks the pixels of the result.
 */
@RunWith(AndroidJUnit4::class)
class LayersDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val main = Handler(Looper.getMainLooper())

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    @Test
    fun captionWrapsInsideTheFrame() {
        val long = "This is a very long caption line that should wrap cleanly inside the frame and never spill past the edges"
        val l = TextLayer(text = long, startUs = 0, durationUs = 1, isCaption = true, style = TextStyle(sizeSp = 64f))
        val bmp = LayerBitmaps.text(l, -1, -1, 1136, 640)!!
        Log.i(TAG, "caption bitmap ${bmp.width}x${bmp.height}")
        assertTrue("caption ${bmp.width}px wide in a 640px frame", bmp.width <= 640 * 0.9)
    }

    @Test fun exportsOverlayShapeAndAdjustmentLayer() = run(overlay = true, overlayAudio = true)
    @Test fun exportsSilentOverlay() = run(overlay = true, overlayAudio = false)
    @Test fun exportsWithoutOverlay() = run(overlay = false, overlayAudio = false)

    private fun run(overlay: Boolean, overlayAudio: Boolean) {
        val t0 = System.currentTimeMillis()
        val a = MediaProbe.probeClip(ctx, asset("a.mp4"))!!
        val b = MediaProbe.probeClip(ctx, asset("b.mp4"))!!
        val dur = a.sourceDurationUs
        val p = Project(
            clips = listOf(a.copy(volumeKeys = listOf(ValueKey(0, 0f), ValueKey(dur / 2, 1f)))),
            overlays = if (!overlay) emptyList() else listOf(
                OverlayClip(
                    uri = b.uri, kind = b.kind, sourceDurationUs = b.sourceDurationUs, sourceWidth = b.sourceWidth, sourceHeight = b.sourceHeight,
                    hasAudio = b.hasAudio && overlayAudio, startUs = 0, trimEndUs = minOf(b.sourceDurationUs, dur),
                    transform = Transform(x = 0.3f, y = 0.25f, scale = 0.4f), blend = BlendMode.SCREEN, mask = Mask(MaskShape.CIRCLE, feather = 0.05f),
                ),
            ),
            adjustments = listOf(AdjustmentLayer(startUs = 0, durationUs = dur, adjust = Adjustments(saturation = -1f))),
            shapes = listOf(ShapeLayer(shape = "rect", fill = 0xFFFF0000, startUs = 0, durationUs = dur, transform = Transform(x = 0.75f, y = 0.75f, scale = 0.15f))),
            texts = listOf(TextLayer(text = "A long caption that has to wrap inside the frame safely", startUs = 0, durationUs = dur, isCaption = true, animIn = null, animOut = null, transform = Transform(y = 0.99f))),
        )
        val built = CompositionFactory.build(ctx, p, 640, 30)!!
        val out = File(ctx.cacheDir, "layers.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        main.post {
            Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(15, TimeUnit.MINUTES))
        Log.i(TAG, "export overlay=$overlay audio=$overlayAudio took ${System.currentTimeMillis() - t0} ms, error=${error?.errorCodeName}")
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        val frame = r.getFrameAtTime(dur / 2, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        File(ctx.getExternalFilesDir(null), "layers_frame.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "layers export ${frame.width}x${frame.height} audio=$hasAudio")
        fun px(x: Float, y: Float) = frame.getPixel((x * (frame.width - 1)).toInt(), (y * (frame.height - 1)).toInt())
        // The shape is drawn above the adjustment layer: still red.
        val shape = px(0.75f, 0.75f)
        assertTrue("shape ${Integer.toHexString(shape)}", Color.red(shape) > 180 && Color.green(shape) < 90 && Color.blue(shape) < 90)
        // The video under the adjustment layer is grey everywhere away from the shape and caption.
        for ((x, y) in listOf(0.1f to 0.6f, 0.9f to 0.5f, 0.5f to 0.45f, 0.3f to 0.25f)) {
            val c = px(x, y)
            assertTrue("not grey at $x,$y: ${Integer.toHexString(c)}", abs(Color.red(c) - Color.green(c)) < 30 && abs(Color.green(c) - Color.blue(c)) < 30)
        }
        // The caption is kept off the bottom edge: the last rows hold no white text.
        var white = 0
        for (y in frame.height - 6 until frame.height) for (x in 0 until frame.width) {
            val c = frame.getPixel(x, y)
            if (Color.red(c) > 245 && Color.green(c) > 245 && Color.blue(c) > 245) white++
        }
        Log.i(TAG, "white pixels in bottom rows: $white")
        assertTrue("caption touches the bottom edge ($white px)", white < frame.width / 4)
        assertTrue("no audio track", hasAudio == "yes")
    }

    companion object { const val TAG = "LayersDeviceTest" }
}
