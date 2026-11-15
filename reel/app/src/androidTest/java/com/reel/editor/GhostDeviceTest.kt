package com.reel.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.CompositionPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.CompositionFactory
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.LivePlans
import com.reel.editor.fx.RenderPlans
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Project
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import com.reel.editor.model.TrackFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Hidden tracks stay on screen, faintly, while editing; playback and export leave them out. */
@RunWith(AndroidJUnit4::class)
class GhostDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun project(): Project {
        val f = File(ctx.cacheDir, "a.mp4")
        inst.context.assets.open("a.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!
        return Project(
            clips = listOf(clip),
            texts = listOf(TextLayer(text = "GHOST", startUs = 0, durationUs = 2_000_000, animIn = null, animOut = null, style = TextStyle(color = 0xFFFF00FF, sizeSp = 220f, shadow = false))),
            tracks = TrackFlags(hidden = setOf(TrackFlags.TEXT, TrackFlags.VIDEO)),
        )
    }

    @Test fun plansGhostWhileEditingAndDropForExport() {
        val p = project()
        val cat = FxCatalog.get(ctx)
        val export = RenderPlans.build(p, cat, 360, 640).single()
        assertTrue(export.hideVideo)
        assertTrue(export.layers.isEmpty())
        val edit = RenderPlans.build(p, cat, 360, 640, ghostHidden = true).single()
        assertFalse(edit.hideVideo)
        assertTrue(edit.ghostVideo)
        assertEquals(1, edit.layers.size)
        assertTrue(edit.layers.single().ghost)
        // Shown tracks are untouched.
        val shown = RenderPlans.build(p.copy(tracks = TrackFlags()), cat, 360, 640, ghostHidden = true).single()
        assertFalse(shown.ghostVideo)
        assertFalse(shown.layers.single().ghost)
    }

    @Test fun pausedPreviewDrawsHiddenTextFaintly() {
        val p = project().copy(tracks = TrackFlags(hidden = setOf(TrackFlags.TEXT)))
        val ghost = previewPinkness(p, ghost = true)
        val full = previewPinkness(p.copy(tracks = TrackFlags()), ghost = true)
        val gone = previewPinkness(p, ghost = false)
        Log.i("GhostDeviceTest", "pink: full=$full ghost=$ghost gone=$gone")
        assertTrue("hidden text vanished while editing ($ghost)", ghost > gone + 50)
        assertTrue("ghost as strong as shown text ($ghost vs $full)", ghost < full)
    }

    /** Counts pixels pulled toward magenta in a paused preview frame. */
    private fun previewPinkness(p: Project, ghost: Boolean): Int {
        val main = Handler(Looper.getMainLooper())
        val reader = android.media.ImageReader.newInstance(360, 640, android.graphics.PixelFormat.RGBA_8888, 3)
        val ready = CountDownLatch(1)
        var player: CompositionPlayer? = null
        main.post {
            val live = LivePlans()
            val built = CompositionFactory.build(ctx, p, CompositionFactory.PREVIEW_HEIGHT, live = live)!!
            val (w, h) = RenderPlans.canvasSize(p, CompositionFactory.PREVIEW_HEIGHT)
            live.set(RenderPlans.build(p, FxCatalog.get(ctx), w, h, ghostHidden = ghost))
            player = CompositionPlayer.Builder(ctx).build().also { pl ->
                pl.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                })
                pl.setVideoSurface(reader.surface, Size(360, 640))
                pl.setComposition(built.composition)
                pl.prepare()
            }
        }
        assertTrue(ready.await(3, TimeUnit.MINUTES))
        var count = -1
        val end = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < end && count < 0) {
            Thread.sleep(1500)
            val img = reader.acquireLatestImage() ?: continue
            val plane = img.planes[0]
            val buf = plane.buffer
            count = 0
            for (y in 0 until img.height step 4) for (x in 0 until img.width step 4) {
                val i = y * plane.rowStride + x * plane.pixelStride
                val r = buf.get(i).toInt() and 255
                val g = buf.get(i + 1).toInt() and 255
                val b = buf.get(i + 2).toInt() and 255
                if (r - g > 50 && b - g > 50) count++
            }
            img.close()
        }
        main.post { player?.release() }
        reader.close()
        return count
    }
}
