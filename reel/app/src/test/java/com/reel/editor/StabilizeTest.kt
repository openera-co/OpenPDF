package com.reel.editor

import com.reel.editor.model.HeadPath
import com.reel.editor.model.Stabilize
import com.reel.editor.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StabilizeTest {
    private val rnd = Random(7)
    // A textured scene: soft blobs of random brightness, sampled anywhere.
    private val blobs = List(140) { floatArrayOf(rnd.nextFloat() * 400 - 120, rnd.nextFloat() * 260 - 80, 4f + rnd.nextFloat() * 10, rnd.nextFloat()) }

    private fun scene(x: Float, y: Float): Float {
        var v = 0.3f
        for (b in blobs) {
            val dx = x - b[0]; val dy = y - b[1]
            v += b[3] * 0.6f * kotlin.math.exp(-(dx * dx + dy * dy) / (2 * b[2] * b[2]))
        }
        return v.coerceIn(0f, 1f)
    }

    /** The scene seen with the picture moved by (tx, ty) pixels and turned by [a] about the centre. */
    private fun frame(tx: Float, ty: Float, a: Float, w: Int = 160, h: Int = 90): Stabilize.Gray {
        val cx = w / 2f; val cy = h / 2f
        val ca = cos(-a); val sa = sin(-a)
        return Stabilize.Gray(w, h, FloatArray(w * h) { i ->
            val x = i % w - cx - tx; val y = i / w - cy - ty
            scene(x * ca - y * sa + cx, x * sa + y * ca + cy)
        })
    }

    @Test
    fun findsShifts() {
        val a = frame(0f, 0f, 0f)
        for ((tx, ty) in listOf(3f to -2f, -7.5f to 4.25f, 14f to 9f, 0.4f to -0.3f)) {
            val m = Stabilize.motion(a, frame(tx, ty, 0f))
            assertEquals("dx for $tx", tx, m.dx, 0.35f)
            assertEquals("dy for $ty", ty, m.dy, 0.35f)
            assertEquals(0f, m.angle, 0.004f)
        }
    }

    @Test
    fun findsTurns() {
        val a = frame(0f, 0f, 0f)
        for (ang in listOf(0.02f, -0.035f)) {
            val m = Stabilize.motion(a, frame(2f, -1f, ang))
            assertEquals("angle $ang", ang, m.angle, 0.008f)
            assertEquals(2f, m.dx, 0.6f)
        }
    }

    @Test
    fun cancelsShakeButKeepsThePan() {
        // A steady pan to the right with jitter on top.
        val n = 90
        val pos = List(n) { i -> i * 0.004f + 0.012f * sin(i * 1.7f) * (if (i % 3 == 0) 1f else -0.6f) }
        val motions = (1 until n).map { i -> Stabilize.Motion((pos[i] - pos[i - 1]) * 160f, 0f, 0f, 160, 90) }
        val c = Stabilize.corrections(motions, sigma = 6f)
        assertEquals(n, c.size)
        val shown = List(n) { pos[it] + c[it].x }
        // The pan stays: the shown picture still travels the full distance.
        assertEquals(pos.last() - pos.first(), shown.last() - shown.first(), 0.03f)
        // The shake goes: frame-to-frame steps are near the pan speed.
        var rawJerk = 0f; var outJerk = 0f
        for (i in 2 until n) {
            rawJerk += abs(pos[i] - 2 * pos[i - 1] + pos[i - 2])
            outJerk += abs(shown[i] - 2 * shown[i - 1] + shown[i - 2])
        }
        assertTrue("jerk $rawJerk -> $outJerk", outJerk < rawJerk * 0.1f)
    }

    @Test
    fun keyframesZoomEnoughAndNoMore() {
        val c = listOf(Stabilize.Correction(0.02f, -0.01f, 0f), Stabilize.Correction(-0.03f, 0.0f, 0f), Stabilize.Correction(0f, 0f, 0f))
        val base = Transform()
        val keys = Stabilize.keyframes(listOf(0, 100_000, 200_000), c, base, 16f / 9f, 16f / 9f, fill = true)
        val s = keys[0].transform.scale
        assertEquals(1f + 2 * 0.03f, s, 1e-4f)
        assertTrue(keys.all { it.transform.scale == s })
        // Moving right by 3% of the picture at that zoom.
        val (cw, _) = HeadPath.contentSize(16f / 9f, 16f / 9f, true)
        assertEquals(0.5f - 0.03f * cw * s, keys[1].transform.x, 1e-4f)
        // A huge shake is capped and only partly cancelled.
        val big = Stabilize.keyframes(listOf(0, 1), listOf(Stabilize.Correction(0.4f, 0f, 0f), Stabilize.Correction(0f, 0f, 0f)), base, 1f, 1f, fill = true, maxZoom = 1.2f)
        assertEquals(1.2f, big[0].transform.scale, 1e-4f)
        assertEquals(0.5f + 0.1f * 1.2f, big[0].transform.x, 1e-3f)
    }

    @Test
    fun endToEndOnShakyFrames() {
        val n = 40
        val shake = List(n) { i -> floatArrayOf(5f * sin(i * 2.1f), 4f * cos(i * 1.3f + 1f), 0.01f * sin(i * 1.7f)) }
        val frames = shake.map { frame(it[0], it[1], it[2]) }
        val motions = (1 until n).map { Stabilize.motion(frames[it - 1], frames[it]) }
        val c = Stabilize.corrections(motions, sigma = 6f)
        var before = 0f; var after = 0f
        for (i in 1 until n) {
            before += abs(shake[i][0] - shake[i - 1][0])
            after += abs((shake[i][0] / 160f + c[i].x) - (shake[i - 1][0] / 160f + c[i - 1].x)) * 160f
        }
        assertTrue("x wobble $before -> $after", after < before * 0.25f)
    }
}
