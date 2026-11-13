package com.reel.editor

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.CompositionFactory
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.RenderDebug
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.FxRef
import com.reel.editor.model.Project
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TimedFx
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.TransitionRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the real engine on a device: builds a project from two bundled clips with a transition,
 * a look, an effect and a text layer, exports it, plays it through the preview player, and
 * records which time origin Media3 uses for item effects.
 */
@RunWith(AndroidJUnit4::class)
class EngineDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val main = Handler(Looper.getMainLooper())

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private fun project(): Project {
        val a = MediaProbe.probeClip(ctx, asset("a.mp4"))!!.let { it.copy(uri = Uri.fromFile(File(ctx.cacheDir, "a.mp4")).toString()) }
        val b = MediaProbe.probeClip(ctx, asset("b.mp4"))!!
        return Project(
            clips = listOf(a.copy(lookId = "tealorange"), b.copy(transitionIn = TransitionRef("glt_crosszoom", 500_000), chroma = b.chroma.copy(enabled = false))),
            texts = listOf(TextLayer(text = "Hello", startUs = 500_000, durationUs = 2_500_000)),
            effects = listOf(TimedFx(fx = FxRef("vhs"), startUs = 2_500_000, durationUs = 1_000_000)),
        )
    }

    @Test
    fun catalogLoads() {
        val c = FxCatalog.get(ctx)
        Log.i(TAG, "catalog: filters=${c.filters.size} effects=${c.effects.size} anims=${c.anims.size} transitions=${c.transitions.size} looks=${c.looks.size}")
        assertTrue(c.transitions.size > 200)
        assertTrue(c.looks.size > 50)
    }

    @Test
    fun exportsAndReportsTimeOrigin() {
        val p = project()
        val total = TimelineMath.duration(p)
        assertEquals(p.clips.sumOf { it.sourceDurationUs }, total)
        RenderDebug.reset()
        val built = CompositionFactory.build(ctx, p, 640, 30)!!
        val out = File(ctx.cacheDir, "out.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        main.post {
            Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(15, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        val second = p.clips[1].id
        Log.i(TAG, "EXPORT first pts: ${RenderDebug.firstPts}  last: ${RenderDebug.lastPts}  frames=${RenderDebug.frames.get()}")
        Log.i(TAG, "EXPORT origin for clip 2: firstPts=${RenderDebug.firstPts[second]} (composition start 2000000)")
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        Log.i(TAG, "EXPORT output ${w}x$h ${dur}ms size=${out.length()}")
        // Save a few frames for visual inspection.
        for (t in listOf(250L, 1000L, 2100L, 2250L, 3000L, 3800L)) {
            r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bmp ->
                File(ctx.getExternalFilesDir(null), "frame_$t.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        r.release()
        out.copyTo(File(ctx.getExternalFilesDir(null), "out.mp4"), overwrite = true)
        assertTrue("duration $dur", dur in 3800..4300)
        assertEquals(360, h)
        assertNotNull(RenderDebug.firstPts[second])
    }

    @Test
    fun previewPlaysWithEffects() {
        val p = project()
        RenderDebug.reset()
        val ended = CountDownLatch(1)
        var player: CompositionPlayer? = null
        var errorName: String? = null
        val st = SurfaceTexture(0).apply { setDefaultBufferSize(360, 640) }
        val surface = Surface(st)
        main.post {
            val built = CompositionFactory.build(ctx, p, CompositionFactory.PREVIEW_HEIGHT, live = com.reel.editor.fx.LivePlans())!!
            player = CompositionPlayer.Builder(ctx).build().also { pl ->
                pl.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended.countDown() }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { errorName = error.errorCodeName; ended.countDown() }
                })
                pl.setVideoSurface(surface, Size(360, 640))
                pl.setComposition(built.composition)
                pl.prepare()
                pl.play()
            }
        }
        val finished = ended.await(10, TimeUnit.MINUTES)
        main.post { player?.release() }
        Log.i(TAG, "PREVIEW first pts: ${RenderDebug.firstPts} last: ${RenderDebug.lastPts} frames=${RenderDebug.frames.get()} error=$errorName")
        assertTrue("preview did not finish", finished)
        assertEquals(null, errorName)
        assertTrue(RenderDebug.frames.get() > 10)
    }

    companion object {
        const val TAG = "ReelDeviceTest"
    }
}
