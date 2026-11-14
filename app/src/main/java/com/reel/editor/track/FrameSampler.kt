package com.reel.editor.track

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Decodes a video once, front to back, and hands out small RGB frames at the wanted times. Much
 * faster than asking MediaMetadataRetriever for each frame, which seeks and decodes from the
 * previous keyframe every time.
 */
class FrameSampler(private val context: Context, private val uri: String, private val maxSide: Int = 320) {
    /**
     * Calls [onFrame] with the frame nearest each of [timesUs] (media time, ascending). Returns
     * false if this device's decoder can't give readable frames, so the caller can fall back.
     */
    fun sample(timesUs: List<Long>, cancelled: () -> Boolean, onFrame: (Int, Bitmap) -> Unit): Boolean {
        if (timesUs.isEmpty()) return true
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return false
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val rot = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            ex.seekTo(timesUs.first(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val info = MediaCodec.BufferInfo()
            var next = 0
            var inputDone = false
            var prevPts = Long.MIN_VALUE
            while (next < timesUs.size && !cancelled()) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { c.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 5_000)
                if (o < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val pts = info.presentationTimeUs
                // Take this frame for every wanted time it is the closest frame to.
                var bmp: Bitmap? = null
                while (next < timesUs.size && (pts >= timesUs[next] || eos)) {
                    if (bmp == null) {
                        bmp = c.getOutputImage(o)?.use { toBitmap(it, rot) }
                        if (bmp == null) { c.releaseOutputBuffer(o, false); return false }
                    }
                    if (prevPts == Long.MIN_VALUE || pts >= timesUs[next] || eos) onFrame(next, bmp)
                    next++
                }
                prevPts = pts
                c.releaseOutputBuffer(o, false)
                if (eos) break
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    /** YUV420 image to a small ARGB bitmap (nearest-neighbour, upright). */
    private fun toBitmap(img: android.media.Image, rotation: Int): Bitmap {
        val w = img.width
        val h = img.height
        val step = maxOf(1, maxOf(w, h) / maxSide)
        val ow = w / step
        val oh = h / step
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        val px = IntArray(ow * oh)
        for (j in 0 until oh) {
            val sy = j * step
            for (i in 0 until ow) {
                val sx = i * step
                val Y = (yb.get(sy * y.rowStride + sx * y.pixelStride).toInt() and 255) - 16
                val ci = (sy / 2) * u.rowStride + (sx / 2) * u.pixelStride
                val cv = (sy / 2) * v.rowStride + (sx / 2) * v.pixelStride
                val U = (ub.get(ci).toInt() and 255) - 128
                val V = (vb.get(cv).toInt() and 255) - 128
                val yy = 1.164f * Y
                val r = (yy + 1.596f * V).toInt().coerceIn(0, 255)
                val g = (yy - 0.392f * U - 0.813f * V).toInt().coerceIn(0, 255)
                val b = (yy + 2.017f * U).toInt().coerceIn(0, 255)
                px[j * ow + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return bmp
        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, ow, oh, m, true)
    }
}
