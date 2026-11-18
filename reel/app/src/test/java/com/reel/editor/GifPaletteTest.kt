package com.reel.editor

import com.reel.editor.media.GifPalette
import com.reel.editor.media.GifWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class GifPaletteTest {
    private val w = 160
    private val h = 90

    /** A soft skin-tone and sky gradient: where a fixed colour cube bands worst. */
    private val img = IntArray(w * h) { i ->
        val x = i % w / (w - 1f); val y = i / w / (h - 1f)
        val r = (200 + 40 * x - 30 * y).toInt(); val g = (150 + 30 * x - 20 * y).toInt(); val b = (120 + 60 * y).toInt()
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun fixedColour(i: Int): Int { val r = i / 42; val g = (i / 6) % 7; val b = i % 6; return (r * 255 / 5 shl 16) or (g * 255 / 6 shl 8) or (b * 255 / 5) }

    /** Mean error per channel after blurring 4x4 (what the eye sees through the dither). */
    private fun error(idx: ByteArray, colour: (Int) -> Int): Double {
        var e = 0.0; var n = 0
        for (by in 0 until h / 4) for (bx in 0 until w / 4) {
            var r = 0; var g = 0; var b = 0; var r0 = 0; var g0 = 0; var b0 = 0
            for (y in 0 until 4) for (x in 0 until 4) {
                val k = (by * 4 + y) * w + bx * 4 + x
                val c = colour(idx[k].toInt() and 255); val o = img[k]
                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                r0 += (o shr 16) and 255; g0 += (o shr 8) and 255; b0 += o and 255
            }
            e += (abs(r - r0) + abs(g - g0) + abs(b - b0)) / 48.0; n++
        }
        return e / n
    }

    @Test
    fun paletteFromTheVideoBeatsTheFixedCube() {
        val pal = GifPalette.build(img)
        assertTrue("palette size ${pal.size}", pal.size in 16..256)
        val fixed = GifWriter(ByteArrayOutputStream(), w, h, 4).index(img)
        val adaptive = GifWriter(ByteArrayOutputStream(), w, h, 4, pal).index(img)
        val ef = error(fixed, ::fixedColour)
        val ea = error(adaptive) { pal[it] }
        // Raw (un-blurred) error too: banding shows as big jumps between neighbouring pixels.
        var rawA = 0.0
        for (k in img.indices) { val c = pal[adaptive[k].toInt() and 255]; rawA += abs(((c shr 8) and 255) - ((img[k] shr 8) and 255)) }
        println("GIF error fixed=$ef adaptive=$ea raw green=${rawA / img.size}")
        assertTrue("adaptive $ea vs fixed $ef", ea < ef * 0.5)
        // Per pixel the dither adds a little noise on purpose; it stays well under a fixed-cube step (51).
        assertTrue("raw error small", rawA / img.size < 10)
    }

    @Test
    fun fewColoursGiveAnExactPalette() {
        val two = IntArray(100) { if (it < 50) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val pal = GifPalette.build(two)
        assertTrue(pal.size == 2)
        val idx = GifWriter(ByteArrayOutputStream(), 10, 10, 4, pal).index(two)
        assertTrue(pal[idx[0].toInt()] == 0xFFFF0000.toInt() && pal[idx[99].toInt()] == 0xFF0000FF.toInt())
    }
}
