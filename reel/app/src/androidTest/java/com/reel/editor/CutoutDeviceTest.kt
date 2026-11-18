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
import com.reel.editor.model.Background
import com.reel.editor.model.BackgroundMode
import com.reel.editor.model.CanvasSettings
import com.reel.editor.model.Cutout
import com.reel.editor.model.CutoutMode
import com.reel.editor.model.Project
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exports a portrait with the background cut out and replaced by pure green, then checks pixels. */
@RunWith(AndroidJUnit4::class)
class CutoutDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun green(c: Int) = Color.green(c) > 170 && Color.red(c) < 90 && Color.blue(c) < 90

    @Test
    fun replacesBackgroundButKeepsThePerson() {
        val f = File(ctx.cacheDir, "portrait.jpg")
        inst.context.assets.open("portrait.jpg").use { i -> f.outputStream().use { i.copyTo(it) } }
        val clip = com.reel.editor.model.Clip(
            uri = Uri.fromFile(f).toString(), kind = com.reel.editor.model.ClipKind.PHOTO, sourceDurationUs = 1_000_000,
            sourceWidth = 820, sourceHeight = 1024, hasAudio = false,
            cutout = Cutout(enabled = true, mode = CutoutMode.COLOR, color = 0xFF00FF00, edge = 0.2f),
        )
        val project = Project(clips = listOf(clip), canvas = CanvasSettings(background = Background(mode = BackgroundMode.COLOR, color = 0xFF000000)))
        val built = CompositionFactory.build(ctx, project, 640, 30)!!
        val out = File(ctx.cacheDir, "cutout.mp4").apply { delete() }
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
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val frame: Bitmap = r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        File(ctx.cacheDir, "cutout_frame.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The photo (820x1024) fits the 9:16 canvas: full width, centred vertically.
        val w = frame.width
        val h = frame.height
        val ph = w * 1024f / 820f
        val top = (h - ph) / 2f
        fun at(x: Float, y: Float) = frame.getPixel((x * w).toInt().coerceIn(0, w - 1), (top + y * ph).toInt().coerceIn(0, h - 1))
        val face = at(0.43f, 0.22f)
        val suit = at(0.43f, 0.6f)
        val curtain = at(0.05f, 0.1f)
        val flagSide = at(0.95f, 0.5f)
        Log.i(TAG, "CUTOUT ${w}x$h face=${Integer.toHexString(face)} suit=${Integer.toHexString(suit)} curtain=${Integer.toHexString(curtain)} side=${Integer.toHexString(flagSide)}")
        assertTrue("background should be green", green(curtain) && green(flagSide))
        assertTrue("person must stay", !green(face) && !green(suit))
        assertTrue("face keeps its colour", Color.red(face) > 120)
    }

    companion object {
        const val TAG = "ReelCutoutTest"
    }
}
