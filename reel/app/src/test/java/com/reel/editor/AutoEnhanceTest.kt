package com.reel.editor

import com.reel.editor.model.Adjustments
import com.reel.editor.model.AutoEnhance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AutoEnhanceTest {
    /** The grade shader's exposure, white balance and contrast steps (internal/grade.frag), on one pixel. */
    private fun grade(a: Adjustments, c: Int): Int {
        var r = (c shr 16 and 255) / 255f; var g = (c shr 8 and 255) / 255f; var b = (c and 255) / 255f
        val e = Math.pow(2.0, a.exposure * 2.0).toFloat()
        r *= e; g *= e; b *= e
        r *= 1 + 0.2f * a.temperature; b *= 1 - 0.2f * a.temperature
        g *= 1 - 0.15f * a.tint
        val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val hl = a.highlights * 0.35f * smooth(0.5f, 1f, l)
        r += hl; g += hl; b += hl
        r = (r - 0.5f) * (1 + a.contrast) + 0.5f; g = (g - 0.5f) * (1 + a.contrast) + 0.5f; b = (b - 0.5f) * (1 + a.contrast) + 0.5f
        fun q(v: Float) = (v.coerceIn(0f, 1f) * 255).toInt()
        return (0xFF shl 24) or (q(r) shl 16) or (q(g) shl 8) or q(b)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** A varied scene: textured greys and colours, then a camera's problem applied. */
    private fun scene(problem: (Float, Float, Float) -> Triple<Float, Float, Float>): IntArray {
        val rnd = Random(3)
        return IntArray(20_000) {
            val base = rnd.nextFloat()
            val r = (base + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            val g = (base + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            val b = (base + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            val (pr, pg, pb) = problem(r, g, b)
            (0xFF shl 24) or ((pr.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((pg.coerceIn(0f, 1f) * 255).toInt() shl 8) or (pb.coerceIn(0f, 1f) * 255).toInt()
        }
    }

    @Test
    fun brightensADarkClip() {
        val px = scene { r, g, b -> Triple(r * 0.35f, g * 0.35f, b * 0.35f) }
        val s = AutoEnhance.stats(px)
        val a = AutoEnhance.adjust(s)
        assertTrue("exposure ${a.exposure}", a.exposure > 0.2f)
        val after = AutoEnhance.stats(px.map { grade(a, it) }.toIntArray())
        assertTrue("mean ${s.meanLuma} -> ${after.meanLuma}", abs(after.meanLuma - 0.46f) < abs(s.meanLuma - 0.46f) * 0.5f)
    }

    @Test
    fun removesABlueCast() {
        val px = scene { r, g, b -> Triple(r * 0.78f, g * 0.92f, (b * 1.15f).coerceAtMost(1f)) }
        val s = AutoEnhance.stats(px)
        val a = AutoEnhance.adjust(s)
        assertTrue("warmer: ${a.temperature}", a.temperature > 0.1f)
        val after = AutoEnhance.stats(px.map { grade(a, it) }.toIntArray())
        assertTrue("cast ${s.b - s.r} -> ${after.b - after.r}", abs(after.b - after.r) < abs(s.b - s.r) * 0.7f)
    }

    @Test
    fun stretchesAFlatClip() {
        val px = scene { r, g, b -> Triple(0.3f + r * 0.4f, 0.3f + g * 0.4f, 0.3f + b * 0.4f) }
        val s = AutoEnhance.stats(px)
        val a = AutoEnhance.adjust(s)
        assertTrue("contrast ${a.contrast}", a.contrast > 0.15f)
        val after = AutoEnhance.stats(px.map { grade(a, it) }.toIntArray())
        assertTrue("range ${s.p98 - s.p02} -> ${after.p98 - after.p02}", after.p98 - after.p02 > (s.p98 - s.p02) * 1.2f)
    }

    @Test
    fun leavesAGoodClipAlmostAlone() {
        val px = scene { r, g, b -> Triple(r * 0.95f + 0.02f, g * 0.95f + 0.02f, b * 0.95f + 0.02f) }
        val a = AutoEnhance.adjust(AutoEnhance.stats(px))
        assertTrue("small changes: $a", abs(a.exposure) <= 0.05f && abs(a.temperature) <= 0.05f && abs(a.tint) <= 0.05f)
        // Other settings already on the clip are kept.
        val kept = AutoEnhance.adjust(AutoEnhance.stats(px), Adjustments(grain = 0.3f, sharpen = 0.2f))
        assertEquals(0.3f, kept.grain); assertEquals(0.2f, kept.sharpen)
    }
}
