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

class Batch16Test {
    /** Decodes a GIF with Python's imaging library: frame count, size, and two pixel colours. */
    private fun pyDecode(bytes: ByteArray, x1: Int, x2: Int, y: Int): String? {
        val ok = runCatching { ProcessBuilder("python3", "-c", "import PIL").start().waitFor() == 0 }.getOrDefault(false)
        if (!ok) return null
        val f = java.io.File.createTempFile("test", ".gif").apply { writeBytes(bytes) }
        val code = "from PIL import Image\nim=Image.open('${f.absolutePath}');n=im.n_frames;im=im.convert('RGB');print(n,im.size[0],im.size[1],*im.getpixel(($x1,$y)),*im.getpixel(($x2,$y)))"
        val p = ProcessBuilder("python3", "-c", code).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().decodeToString().trim()
        org.junit.Assert.assertEquals(out, 0, p.waitFor())
        return out
    }

    @org.junit.Test fun gifDecodesWithTheRightSizeAndColours() {
        val w = 40; val h = 30
        val bos = java.io.ByteArrayOutputStream()
        val g = com.reel.editor.media.GifWriter(bos, w, h, 8)
        repeat(3) { g.frame(IntArray(w * h) { i -> if (i % w < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }) }
        g.finish()
        val out = pyDecode(bos.toByteArray(), 2, w - 3, 10)
        org.junit.Assume.assumeTrue("python imaging not available", out != null)
        val v = out!!.split(" ").map { it.toInt() }
        org.junit.Assert.assertEquals(listOf(3, w, h), v.take(3))
        org.junit.Assert.assertTrue("left ${v.subList(3, 6)}", v[3] > 200 && v[5] < 60)
        org.junit.Assert.assertTrue("right ${v.subList(6, 9)}", v[8] > 200 && v[6] < 60)
    }

    @org.junit.Test fun gifHandlesLongFramesPastTheDictionaryLimit() {
        val w = 320; val h = 240
        val rnd = java.util.Random(1)
        val px = IntArray(w * h) { 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF) }
        // A known pixel to check after the dictionary has been reset many times.
        px[200 * w + 300] = 0xFF00FF00.toInt(); px[200 * w + 10] = 0xFFFFFFFF.toInt()
        val bos = java.io.ByteArrayOutputStream()
        com.reel.editor.media.GifWriter(bos, w, h, 10).apply { frame(px); finish() }
        val out = pyDecode(bos.toByteArray(), 300, 10, 200)
        org.junit.Assume.assumeTrue("python imaging not available", out != null)
        val v = out!!.split(" ").map { it.toInt() }
        org.junit.Assert.assertEquals(listOf(1, w, h), v.take(3))
        // Dithered, so close rather than exact.
        org.junit.Assert.assertTrue("green ${v.subList(3, 6)}", v[4] > 180 && v[3] < 90 && v[5] < 90)
        org.junit.Assert.assertTrue("white ${v.subList(6, 9)}", v[6] > 180 && v[7] > 180 && v[8] > 180)
    }

    @org.junit.Test fun historyNamesChanges() {
        val a = com.reel.editor.model.Project()
        val b = a.copy(texts = listOf(com.reel.editor.model.TextLayer(text = "x", startUs = 0, durationUs = 1)))
        org.junit.Assert.assertEquals("Text added", com.reel.editor.edit.describe(a, b))
        org.junit.Assert.assertEquals("Text deleted", com.reel.editor.edit.describe(b, a))
        org.junit.Assert.assertEquals("Renamed", com.reel.editor.edit.describe(a, a.copy(name = "z")))
    }

    @org.junit.Test fun historyJumpsBackSeveralSteps() {
        val h = com.reel.editor.edit.History()
        var p = com.reel.editor.model.Project(name = "0")
        for (i in 1..4) { h.record(p); p = p.copy(name = "$i") }
        org.junit.Assert.assertEquals(4, h.steps(p).size)
        val back = h.undoSteps(3, p)!!
        org.junit.Assert.assertEquals("1", back.name)
        org.junit.Assert.assertTrue(h.canRedo)
    }

    @org.junit.Test fun projectUrisRoundTrip() {
        val clip = com.reel.editor.model.Clip(uri = "content://a/1", kind = com.reel.editor.model.ClipKind.VIDEO, sourceDurationUs = 1, sourceWidth = 1, sourceHeight = 1)
        val p = com.reel.editor.model.Project(
            clips = listOf(clip),
            audio = listOf(com.reel.editor.model.AudioItem(uri = "file:///m.mp3", title = "m", sourceDurationUs = 1, startUs = 0)),
            stickers = listOf(com.reel.editor.model.StickerLayer(source = "shape:star", startUs = 0, durationUs = 1)),
        )
        org.junit.Assert.assertEquals(setOf("content://a/1", "file:///m.mp3"), com.reel.editor.model.ProjectUris.all(p))
        val packed = com.reel.editor.model.ProjectUris.map(p) { "media/" + it.hashCode() }
        org.junit.Assert.assertTrue(packed.clips[0].uri.startsWith("media/"))
        org.junit.Assert.assertEquals("shape:star", packed.stickers[0].source)
    }
}
