package com.reel.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.FxThumbs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Renders the tile preview of every filter, effect, clip animation and transition with the real
 * shaders, and checks each one exists, is not blank, and (for motion) actually changes.
 * Contact sheets are written to the app's external files dir for visual review.
 */
@RunWith(AndroidJUnit4::class)
class FxThumbsDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    private fun frame(name: String, atUs: Long): Bitmap {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val b = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        return FxThumbs.prepare(b)
    }

    private fun mean(b: Bitmap): Double {
        var s = 0L
        for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) {
            val c = b.getPixel(x, y); s += Color.red(c) + Color.green(c) + Color.blue(c)
        }
        return s / 3.0 / ((b.height / 4) * (b.width / 4))
    }

    private fun diff(a: Bitmap, b: Bitmap): Double {
        var s = 0L
        var n = 0
        for (y in 0 until a.height step 3) for (x in 0 until a.width step 3) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            s += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q)); n += 3
        }
        return s.toDouble() / n
    }

    private fun changedShare(a: Bitmap, b: Bitmap): Double {
        var n = 0
        var c = 0
        for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            val m = maxOf(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
            if (m > 40) c++
            n++
        }
        return c.toDouble() / n
    }

    private fun sheet(name: String, items: List<Pair<String, Bitmap>>) {
        val cols = 10
        val rows = (items.size + cols - 1) / cols
        val cw = FxThumbs.W + 4
        val ch = FxThumbs.H + 16
        val out = Bitmap.createBitmap(cols * cw, rows * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(20, 20, 24))
        val p = Paint().apply { color = Color.WHITE; textSize = 10f; isAntiAlias = true }
        items.forEachIndexed { i, (label, b) ->
            val x = (i % cols) * cw.toFloat(); val y = (i / cols) * ch.toFloat()
            c.drawBitmap(b, x + 2, y + 2, null)
            c.drawText(label.take(20), x + 3, y + FxThumbs.H + 13, p)
        }
        File(ctx.cacheDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun everyPreviewRenders() = runBlocking {
        val cat = FxCatalog.get(ctx)
        val a = frame("a.mp4", 1_000_000)
        val b = frame("b.mp4", 500_000)
        val problems = ArrayList<String>()
        val still = ArrayList<String>()

        fun check(kind: String, id: String, frames: List<Bitmap>?, motion: Boolean) {
            if (frames.isNullOrEmpty()) { problems += "$kind $id: no preview"; return }
            val m = frames.maxOf { mean(it) }
            if (m < 6) problems += "$kind $id: black (mean $m)"
            if (motion && frames.size > 1) {
                val d = frames.drop(1).maxOf { diff(frames[0], it) }
                if (d < 1.0) still += "$kind $id (max change $d)"
                // Tiny sparse elements (sparkles, drops) change few pixels; anything below this is frozen.
                if (d < 0.15) problems += "$kind $id: preview does not move"
            }
        }

        val filters = cat.filters.map { f ->
            f.name to FxThumbs.fx(cat, f, FxThumbs.Mode.STATIC, a, "t:a").also {
                check("filter", f.id, it, false)
                // A filter that leaves the picture as it was does nothing.
                it?.firstOrNull()?.let { fr -> val d = diff(fr, a); if (d < 1.0) problems += "filter ${f.id}: no visible change ($d)" }
            }
        }
        val effects = cat.effects.map { e ->
            e.name to FxThumbs.fx(cat, e, FxThumbs.Mode.LOOP, a, "t:a").also {
                check("effect", e.id, it, true)
                // Sparse effects (stars, dust) change few pixels, and grain changes all of them a little:
                // count pixels that clearly changed, or a mean change.
                it?.let { fr ->
                    val changed = fr.maxOf { x -> changedShare(x, a) }
                    val d = fr.maxOf { x -> diff(x, a) }
                    if (changed < 0.002 && d < 0.8) problems += "effect ${e.id}: no visible change ($changed, $d)"
                }
            }
        }
        val anims = cat.anims.map { an ->
            an.name to FxThumbs.fx(cat, an, FxThumbs.Mode.ANIM_IN, a, "t:a").also {
                check("anim", an.id, it, true)
                // When the animation is done the clip must look exactly like itself.
                it?.lastOrNull()?.let { fr -> val d = diff(fr, a); if (d > 4.0) problems += "anim ${an.id}: end frame is not the clip ($d)" }
            }
        }
        val trans = cat.transitions.map { t ->
            t.name to FxThumbs.transition(cat, t, a, b, "t:ab").also {
                check("transition", t.id, it, true)
                // A transition starts on the outgoing clip and ends on the incoming one.
                if (!it.isNullOrEmpty()) {
                    val ds = diff(it.first(), a); val de = diff(it.last(), b)
                    if (ds > 6.0) problems += "transition ${t.id}: does not start on clip A ($ds)"
                    if (de > 6.0) problems += "transition ${t.id}: does not end on clip B ($de)"
                }
            }
        }

        sheet("thumbs_filters", filters.mapNotNull { (n, f) -> f?.let { n to it[0] } })
        sheet("thumbs_effects", effects.mapNotNull { (n, f) -> f?.let { n to it[it.size / 2] } })
        sheet("thumbs_anims", anims.mapNotNull { (n, f) -> f?.let { n to it[5] } })
        sheet("thumbs_transitions", trans.mapNotNull { (n, f) -> f?.let { n to it[it.size / 2] } })

        Log.i(TAG, "THUMBS filters=${filters.size} effects=${effects.size} anims=${anims.size} transitions=${trans.size}")
        still.forEach { Log.i(TAG, "THUMBS STILL $it") }
        problems.forEach { Log.e(TAG, "THUMBS PROBLEM $it") }
        assertTrue("previews failed: $problems", problems.isEmpty())
    }

    companion object {
        const val TAG = "ReelThumbsTest"
    }
}
