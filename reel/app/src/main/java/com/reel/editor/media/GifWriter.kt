package com.reel.editor.media

import java.io.OutputStream

/**
 * Writes an animated GIF that loops forever. All frames share one palette, so colours never
 * flicker: either [palette] (up to 256 RGB colours, best made from the video with
 * [GifPalette.build]) or a fixed 252-colour cube. Ordered dithering hides the steps.
 */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int, private val delayCs: Int, palette: IntArray? = null) {
    private val pal: IntArray? = palette?.take(256)?.toIntArray()
    /** Nearest palette entry for each 5-bit-per-channel colour. */
    private val lookup: ByteArray? = pal?.let { GifPalette.lookupTable(it) }
    /** How far the dither moves a channel, in 0..255 units: about half the palette's spacing. */
    private val ditherAmp: Float = pal?.let { GifPalette.spacing(it) * 0.5f } ?: 0f

    init {
        out.write("GIF89a".toByteArray())
        le16(width); le16(height)
        out.write(0xF7) // global colour table, 8 bits, 256 entries
        out.write(0); out.write(0)
        for (i in 0 until 256) {
            if (pal != null) {
                val c = if (i < pal.size) pal[i] else 0
                out.write((c shr 16) and 255); out.write((c shr 8) and 255); out.write(c and 255)
            } else if (i < 252) {
                val r = i / 42; val g = (i / 6) % 7; val b = i % 6
                out.write(r * 255 / 5); out.write(g * 255 / 6); out.write(b * 255 / 5)
            } else { out.write(0); out.write(0); out.write(0) }
        }
        // Loop forever (NETSCAPE2.0).
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    /** Adds a frame of [argb] pixels, width x height. */
    fun frame(argb: IntArray) {
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); le16(delayCs); out.write(0); out.write(0)
        out.write(0x2C); le16(0); le16(0); le16(width); le16(height); out.write(0)
        lzw(index(argb))
    }

    fun finish() { out.write(0x3B); out.flush() }

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun index(argb: IntArray): ByteArray {
        val idx = ByteArray(width * height)
        val lut = lookup
        for (y in 0 until height) for (x in 0 until width) {
            val c = argb[y * width + x]
            val t = (bayer[(y and 3) * 4 + (x and 3)] - 7.5f) / 16f
            if (lut != null) {
                val d = t * ditherAmp
                val r = (((c shr 16) and 255) + d).toInt().coerceIn(0, 255) shr 3
                val g = (((c shr 8) and 255) + d).toInt().coerceIn(0, 255) shr 3
                val b = ((c and 255) + d).toInt().coerceIn(0, 255) shr 3
                idx[y * width + x] = lut[(r shl 10) or (g shl 5) or b]
            } else {
                val r = (((c shr 16) and 255) / 255f * 5f + t).toInt().coerceIn(0, 5)
                val g = (((c shr 8) and 255) / 255f * 6f + t).toInt().coerceIn(0, 6)
                val b = ((c and 255) / 255f * 5f + t).toInt().coerceIn(0, 5)
                idx[y * width + x] = (r * 42 + g * 6 + b).toByte()
            }
        }
        return idx
    }

    /** GIF LZW with 8-bit minimum code size, in 255-byte sub-blocks. */
    private fun lzw(data: ByteArray) {
        val minSize = 8
        val clear = 1 shl minSize
        val eoi = clear + 1
        out.write(minSize)
        val block = ByteArray(255)
        var blockLen = 0
        var bitBuf = 0
        var bitCount = 0
        var codeSize = minSize + 1
        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block[blockLen++] = (bitBuf and 0xFF).toByte()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
                if (blockLen == 255) { out.write(255); out.write(block, 0, 255); blockLen = 0 }
            }
        }
        val dict = HashMap<Int, Int>(8192)
        var next = eoi + 1
        emit(clear)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear)
                dict.clear(); next = eoi + 1; codeSize = minSize + 1
            }
            prefix = k
        }
        emit(prefix)
        emit(eoi)
        if (bitCount > 0) { block[blockLen++] = (bitBuf and 0xFF).toByte(); if (blockLen == 255) { out.write(255); out.write(block, 0, 255); blockLen = 0 } }
        if (blockLen > 0) { out.write(blockLen); out.write(block, 0, blockLen) }
        out.write(0)
    }
}

/** A palette made from the colours a video actually uses (median cut). */
object GifPalette {
    /** Up to [size] colours that cover [pixels] (ARGB) well. */
    fun build(pixels: IntArray, size: Int = 256): IntArray {
        // Count colours at 5 bits per channel; boxes are cut on those buckets, weighted by count.
        val hist = IntArray(32768)
        for (c in pixels) hist[(((c shr 19) and 31) shl 10) or (((c shr 11) and 31) shl 5) or ((c shr 3) and 31)]++
        val cols = hist.indices.filter { hist[it] > 0 }.toIntArray()
        if (cols.isEmpty()) return intArrayOf(0)
        class Box(val items: IntArray) {
            val lo = IntArray(3) { 31 }; val hi = IntArray(3)
            var weight = 0L
            init {
                for (k in items) {
                    val ch = intArrayOf(k shr 10, (k shr 5) and 31, k and 31)
                    for (j in 0..2) { lo[j] = minOf(lo[j], ch[j]); hi[j] = maxOf(hi[j], ch[j]) }
                    weight += hist[k]
                }
            }
            fun longest() = (0..2).maxBy { hi[it] - lo[it] }
            fun range() = hi[longest()] - lo[longest()]
        }
        val boxes = ArrayList<Box>().apply { add(Box(cols)) }
        while (boxes.size < size) {
            // Split the box that is both busy and wide; single-colour boxes can't split.
            val b = boxes.filter { it.items.size > 1 }.maxByOrNull { it.weight.toDouble() * (it.range() + 1) } ?: break
            val ax = b.longest()
            fun chan(k: Int) = when (ax) { 0 -> k shr 10; 1 -> (k shr 5) and 31; else -> k and 31 }
            val sorted = b.items.sortedBy { chan(it) }
            var acc = 0L; var cut = 1
            for (i in sorted.indices) { acc += hist[sorted[i]]; if (acc * 2 >= b.weight) { cut = (i + 1).coerceIn(1, sorted.size - 1); break } }
            boxes.remove(b)
            boxes += Box(sorted.subList(0, cut).toIntArray())
            boxes += Box(sorted.subList(cut, sorted.size).toIntArray())
        }
        return boxes.map { b ->
            var r = 0L; var g = 0L; var bl = 0L
            for (k in b.items) { val n = hist[k]; r += (k shr 10) * n; g += ((k shr 5) and 31) * n; bl += (k and 31) * n }
            val w = b.weight.coerceAtLeast(1)
            fun up(v: Long) = ((v * 255 / 31 + w / 2) / w).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (up(r) shl 16) or (up(g) shl 8) or up(bl)
        }.toIntArray()
    }

    /** Index of the nearest colour in [pal] for every 5-bit-per-channel colour. */
    fun lookupTable(pal: IntArray): ByteArray {
        val t = ByteArray(32768)
        val pr = IntArray(pal.size) { (pal[it] shr 16) and 255 }
        val pg = IntArray(pal.size) { (pal[it] shr 8) and 255 }
        val pb = IntArray(pal.size) { pal[it] and 255 }
        for (k in 0 until 32768) {
            val r = ((k shr 10) * 255 + 15) / 31; val g = (((k shr 5) and 31) * 255 + 15) / 31; val b = ((k and 31) * 255 + 15) / 31
            var best = 0; var bd = Int.MAX_VALUE
            for (i in pal.indices) {
                val dr = r - pr[i]; val dg = g - pg[i]; val db = b - pb[i]
                // Green counts most to the eye, blue least.
                val d = 3 * dr * dr + 4 * dg * dg + 2 * db * db
                if (d < bd) { bd = d; best = i }
            }
            t[k] = best.toByte()
        }
        return t
    }

    /** Typical distance between neighbouring palette colours, 0..255 units. */
    fun spacing(pal: IntArray): Float = (256f / Math.cbrt(pal.size.toDouble()).toFloat()).coerceIn(4f, 64f)
}
