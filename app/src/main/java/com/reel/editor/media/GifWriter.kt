package com.reel.editor.media

import java.io.OutputStream

/**
 * Writes an animated GIF that loops forever. Colours go through one fixed 252-colour palette
 * (6 red x 7 green x 6 blue levels) with ordered dithering: fast, steady across frames (no
 * palette flicker), and small enough for phones.
 */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int, private val delayCs: Int) {
    init {
        out.write("GIF89a".toByteArray())
        le16(width); le16(height)
        out.write(0xF7) // global colour table, 8 bits, 256 entries
        out.write(0); out.write(0)
        for (i in 0 until 256) {
            if (i < 252) {
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
        for (y in 0 until height) for (x in 0 until width) {
            val c = argb[y * width + x]
            val t = (bayer[(y and 3) * 4 + (x and 3)] - 7.5f) / 16f
            val r = (((c shr 16) and 255) / 255f * 5f + t).toInt().coerceIn(0, 5)
            val g = (((c shr 8) and 255) / 255f * 6f + t).toInt().coerceIn(0, 6)
            val b = ((c and 255) / 255f * 5f + t).toInt().coerceIn(0, 5)
            idx[y * width + x] = (r * 42 + g * 6 + b).toByte()
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
