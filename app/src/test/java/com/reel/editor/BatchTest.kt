package com.reel.editor

import com.reel.editor.fx.UserLuts
import com.reel.editor.model.CropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTest {
    private fun identityCube(n: Int): String = buildString {
        append("TITLE \"id\"\n# comment\nLUT_3D_SIZE $n\n")
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) append("${r / (n - 1f)} ${g / (n - 1f)} ${b / (n - 1f)}\n")
    }

    @Test fun identityCubeGivesIdentityLut() {
        val px = UserLuts.toPixels(UserLuts.parse(identityCube(5)), 9)
        for (r in 0 until 9) for (g in 0 until 9) for (b in 0 until 9) {
            val c = px[(r * 9 + g) * 9 + b]
            fun near(v: Int, i: Int) = kotlin.math.abs(v - i * 255 / 8) <= 1
            assertTrue(near((c shr 16) and 255, r) && near((c shr 8) and 255, g) && near(c and 255, b))
        }
    }

    @Test fun cubeChannelOrderIsRedFastest() {
        // A cube that swaps red and blue.
        val n = 2
        val text = buildString { append("LUT_3D_SIZE 2\n"); for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) append("$b $g $r\n") }
        val px = UserLuts.toPixels(UserLuts.parse(text), 2)
        val pureRed = px[(1 * 2 + 0) * 2 + 0]
        assertEquals(0, (pureRed shr 16) and 255)
        assertEquals(255, pureRed and 255)
    }

    @Test(expected = IllegalArgumentException::class) fun rejects1dLuts() { UserLuts.parse("LUT_1D_SIZE 4\n0 0 0\n") }

    @Test(expected = IllegalArgumentException::class) fun rejectsShortCubes() { UserLuts.parse("LUT_3D_SIZE 4\n0 0 0\n1 1 1\n") }

    @Test fun cropForRatioIsCentredAndFits() {
        val c = CropRect.forRatio(9f / 16f, 16f / 9f)
        assertEquals(1f, c.h, 1e-5f)
        assertEquals(0.5f, c.x + c.w / 2, 1e-5f)
        assertEquals(9f / 16f, c.w * 16f / 9f / c.h, 1e-4f)
        val z = c.zoomed(2f)
        assertEquals(c.w / 2, z.w, 1e-5f)
        assertTrue(z.x >= 0f && z.x + z.w <= 1f && z.y >= 0f && z.y + z.h <= 1f)
        val moved = z.centredAt(1f, 0f)
        assertEquals(1f, moved.x + moved.w, 1e-5f); assertEquals(0f, moved.y, 1e-5f)
    }
}
