package com.reel.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import com.reel.editor.nativebridge.NativeCore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Frame interpolation: writes a copy of a video range with new frames made between the real ones
 * (following the motion), at up to 120 fps. Slow motion stays fluid and 120 fps exports are real.
 */
class Smoother(private val context: Context) {
    @Volatile var cancelled = false

    data class Plan(val fps: Double, val factor: Int) { val outFps get() = fps * factor }

    companion object {
        /** How many output frames per source frame to reach about [target] fps; 1 means nothing to do. */
        fun plan(fps: Double, target: Double = 120.0): Plan = Plan(fps, Math.round(target / fps).toInt().coerceIn(1, 5))
    }

    /** Returns false when cancelled. Throws when the video can't be read or written. */
    fun smooth(uri: String, startUs: Long, endUs: Long, hasAudio: Boolean, out: File, progress: (Float) -> Unit): Boolean {
        val tmp = File(out.parentFile, out.name + ".part").apply { delete() }
        val r = MediaMetadataRetriever()
        r.setDataSource(context, Uri.parse(uri))
        val durUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        val frameCount = if (Build.VERSION.SDK_INT >= 28) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0 else 0
        val fps = if (frameCount > 0 && durUs > 0) (frameCount * 1e6 / durUs).coerceIn(10.0, 120.0) else 30.0
        val plan = plan(fps)
        if (plan.factor < 2) { r.release(); throw IllegalStateException("This clip is already smooth") }
        val srcFrameUs = 1e6 / fps
        val outFrameUs = 1e6 / plan.outFps
        val first = (startUs / srcFrameUs).toInt()
        val last = ((endUs / srcFrameUs).toInt() - 1).coerceAtLeast(first)
        val total = last - first + 1

        val audio = if (hasAudio) copyAudio(uri, startUs, endUs) else null
        if (cancelled) { r.release(); return false }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val probe = frameAt(r, first, srcFrameUs) ?: throw IllegalStateException("Can't read this video")
            // 1080p at 120 fps is what phones encode well; bigger sources are scaled down.
            val scale = minOf(1f, 1920f / maxOf(probe.width, probe.height))
            val w = ((probe.width * scale).toInt() / 2) * 2
            val h = ((probe.height * scale).toInt() / 2) * 2
            val outFps = plan.outFps
            val enc = createEncoder(w, h, outFps)
            encoder = enc
            val surface = enc.createInputSurface()
            enc.start()
            val mux = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            var videoTrack = -1
            var started = false
            var written = 0L
            val info = MediaCodec.BufferInfo()

            fun drain(endOfStream: Boolean) {
                if (endOfStream) enc.signalEndOfInputStream()
                while (true) {
                    val o = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                    when {
                        o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = mux.addTrack(enc.outputFormat)
                            val audioTrack = audio?.let { mux.addTrack(it.format) }
                            mux.start(); started = true
                            if (audio != null && audioTrack != null) for ((data, ai) in audio.samples) mux.writeSampleData(audioTrack, data, ai)
                        }
                        o >= 0 -> {
                            val buf = enc.getOutputBuffer(o)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && started) {
                                info.presentationTimeUs = (written * outFrameUs).toLong()
                                written++
                                buf.position(info.offset).limit(info.offset + info.size)
                                mux.writeSampleData(videoTrack, buf, info)
                            }
                            enc.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val dst = Rect(0, 0, w, h)
            fun draw(b: Bitmap) {
                val c = if (Build.VERSION.SDK_INT >= 23) surface.lockHardwareCanvas() else surface.lockCanvas(null)
                c.drawBitmap(b, null, dst, null)
                surface.unlockCanvasAndPost(c)
                drain(false)
            }

            val stride = w * 4
            val bufA = ByteBuffer.allocateDirect(stride * h).order(ByteOrder.nativeOrder())
            val bufB = ByteBuffer.allocateDirect(stride * h).order(ByteOrder.nativeOrder())
            val outs = Array(plan.factor - 1) { ByteBuffer.allocateDirect(stride * h).order(ByteOrder.nativeOrder()) }
            val times = FloatArray(plan.factor - 1) { (it + 1f) / plan.factor }
            val mid = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            fun fit(b: Bitmap): Bitmap {
                val argb = if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() }
                return if (argb.width == w && argb.height == h) argb else Bitmap.createScaledBitmap(argb, w, h, true).also { if (it !== argb) argb.recycle() }
            }

            var prev: Bitmap? = null
            var index = first
            val batch = 12
            while (index <= last && !cancelled) {
                val hi = minOf(last, index + batch - 1)
                for (raw in framesBetween(r, index, hi, srcFrameUs)) {
                    val cur = fit(raw)
                    val p = prev
                    if (p != null) {
                        draw(p)
                        bufA.clear(); p.copyPixelsToBuffer(bufA)
                        bufB.clear(); cur.copyPixelsToBuffer(bufB)
                        if (NativeCore.interpolate(bufA, bufB, w, h, stride, outs, times)) {
                            for (o in outs) { o.rewind(); mid.copyPixelsFromBuffer(o); draw(mid) }
                        } else repeat(outs.size) { draw(p) }
                        p.recycle()
                    }
                    prev = cur
                }
                index = hi + 1
                progress(((index - first).toFloat() / total).coerceAtMost(1f))
            }
            if (cancelled) return false
            // The last real frame holds for a whole source frame, so the length matches.
            prev?.let { p -> repeat(plan.factor) { draw(p) }; p.recycle() }
            drain(true)
            mux.stop()
            muxer = null
            mux.release()
            if (!tmp.renameTo(out)) throw IllegalStateException("Couldn't save the smooth clip")
            return true
        } finally {
            r.release()
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            tmp.delete()
        }
    }

    /** H.264 at the frame rate asked; phones that can't take it at this size get a smaller ask. */
    private fun createEncoder(w: Int, h: Int, fps: Double): MediaCodec {
        var last: Exception? = null
        for (rate in listOf(fps, minOf(fps, 60.0))) {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (w.toDouble() * h * rate * 0.12).toInt().coerceIn(4_000_000, 60_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, rate.toInt())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                return enc
            } catch (e: Exception) {
                last = e
                enc.release()
            }
        }
        throw IllegalStateException("This phone can't encode the smooth clip", last)
    }

    private fun frameAt(r: MediaMetadataRetriever, index: Int, frameUs: Double): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) runCatching { r.getFrameAtIndex(index) }.getOrNull() ?: r.getFrameAtTime((index * frameUs).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
        else r.getFrameAtTime((index * frameUs).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)

    private fun framesBetween(r: MediaMetadataRetriever, lo: Int, hi: Int, frameUs: Double): List<Bitmap> {
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { r.getFramesAtIndex(lo, hi - lo + 1) }.getOrNull()?.let { if (it.size == hi - lo + 1) return it }
        }
        return (lo..hi).mapNotNull { r.getFrameAtTime((it * frameUs).toLong(), MediaMetadataRetriever.OPTION_CLOSEST) }
    }

    private class Audio(val format: MediaFormat, val samples: List<Pair<ByteBuffer, MediaCodec.BufferInfo>>)

    /** The compressed sound of the range as it is, times moved to start at zero. */
    private fun copyAudio(uri: String, startUs: Long, endUs: Long): Audio? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val cap = if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 64 * 1024
            val buf = ByteBuffer.allocate(cap)
            val out = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
            while (!cancelled) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val t = ex.sampleTime
                if (t >= endUs) break
                if (t >= startUs) {
                    buf.limit(n)
                    val copy = ByteBuffer.allocateDirect(n).put(buf).also { it.flip() }
                    val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    out += copy to MediaCodec.BufferInfo().apply { set(0, n, t - startUs, flags) }
                }
                ex.advance()
            }
            return if (out.isEmpty()) null else Audio(fmt, out)
        } catch (e: Exception) {
            return null
        } finally {
            ex.release()
        }
    }
}
