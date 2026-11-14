package com.reel.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a copy of a video range that plays backwards, sound included. Frames are decoded a small
 * batch at a time from the end, so memory stays flat whatever the clip's length.
 */
class Reverser(private val context: Context) {
    @Volatile var cancelled = false

    /** Returns false when cancelled. Throws when the video can't be read or written. */
    fun reverse(uri: String, startUs: Long, endUs: Long, hasAudio: Boolean, out: File, progress: (Float) -> Unit): Boolean {
        val tmp = File(out.parentFile, out.name + ".part").apply { delete() }
        val r = MediaMetadataRetriever()
        r.setDataSource(context, Uri.parse(uri))
        val durUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        val frameCount = if (Build.VERSION.SDK_INT >= 28) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0 else 0
        val fps = if (frameCount > 0 && durUs > 0) (frameCount * 1e6 / durUs).coerceIn(10.0, 60.0) else 30.0
        val frameUs = (1e6 / fps).toLong()
        val first = (startUs * fps / 1e6).toInt()
        val last = ((endUs * fps / 1e6).toInt() - 1).coerceAtLeast(first)
        val total = last - first + 1

        // Sound first: decode, reverse, encode to AAC in memory (small next to video).
        val audio = if (hasAudio) encodeReversedAudio(uri, startUs, endUs) else null
        if (cancelled) { r.release(); return false }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val probe = frameAt(r, first, frameUs) ?: throw IllegalStateException("Can't read this video")
            val scale = minOf(1f, 1920f / maxOf(probe.width, probe.height))
            val w = ((probe.width * scale).toInt() / 2) * 2
            val h = ((probe.height * scale).toInt() / 2) * 2
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps * 0.2).toInt().coerceIn(2_000_000, 40_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder = enc
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
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
                                // Canvas-fed surfaces carry wall-clock times; replace them with even frame times.
                                info.presentationTimeUs = written * frameUs
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

            // Batches of frames from the end; each batch decodes from its keyframe once.
            val batch = 12
            var hi = last
            var done = 0
            while (hi >= first && !cancelled) {
                val lo = maxOf(first, hi - batch + 1)
                val frames = framesBetween(r, lo, hi, frameUs)
                for (b in frames.asReversed()) { draw(b); b.recycle(); done++ }
                progress(done.toFloat() / total)
                hi = lo - 1
            }
            if (cancelled) return false
            drain(true)
            mux.stop()
            muxer = null
            mux.release()
            if (!tmp.renameTo(out)) throw IllegalStateException("Couldn't save the reversed clip")
            return true
        } finally {
            r.release()
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            tmp.delete()
        }
    }

    private fun frameAt(r: MediaMetadataRetriever, index: Int, frameUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) runCatching { r.getFrameAtIndex(index) }.getOrNull() ?: r.getFrameAtTime(index * frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
        else r.getFrameAtTime(index * frameUs, MediaMetadataRetriever.OPTION_CLOSEST)

    private fun framesBetween(r: MediaMetadataRetriever, lo: Int, hi: Int, frameUs: Long): List<Bitmap> {
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { r.getFramesAtIndex(lo, hi - lo + 1) }.getOrNull()?.let { if (it.size == hi - lo + 1) return it }
        }
        return (lo..hi).mapNotNull { r.getFrameAtTime(it * frameUs, MediaMetadataRetriever.OPTION_CLOSEST) }
    }

    private class EncodedAudio(val format: MediaFormat, val samples: List<Pair<ByteBuffer, MediaCodec.BufferInfo>>)

    private fun encodeReversedAudio(uri: String, startUs: Long, endUs: Long): EncodedAudio? {
        val pcm = AudioDecoder.decodeMono(context, uri, startUs, endUs) { cancelled } ?: return null
        val s = pcm.samples
        if (s.isEmpty()) return null
        s.reverse()
        val rate = pcm.sampleRate
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        val out = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        try {
            while (!cancelled) {
                if (!inputDone) {
                    val i = enc.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = enc.getInputBuffer(i)!!.order(ByteOrder.nativeOrder())
                        buf.clear()
                        val n = minOf(buf.capacity() / 2, s.size - pos, 2048)
                        if (n <= 0) { enc.queueInputBuffer(i, 0, 0, pos * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else {
                            for (k in 0 until n) buf.putShort((s[pos + k].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                            enc.queueInputBuffer(i, 0, n * 2, pos * 1_000_000L / rate, 0)
                            pos += n
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat = enc.outputFormat
                else if (o >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val b = enc.getOutputBuffer(o)!!
                        b.position(info.offset).limit(info.offset + info.size)
                        val copy = ByteBuffer.allocateDirect(info.size).put(b).also { it.flip() }
                        out += copy to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                    }
                    enc.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { enc.stop() }
            enc.release()
        }
        return outFormat?.let { EncodedAudio(it, out) }
    }
}
