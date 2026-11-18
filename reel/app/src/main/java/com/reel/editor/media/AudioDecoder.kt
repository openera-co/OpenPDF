package com.reel.editor.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.reel.editor.nativebridge.NativeCore
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes a time range of a file's first audio track to mono floats at the track's own rate. */
object AudioDecoder {
    class Pcm(val samples: FloatArray, val sampleRate: Int)

    /** Blocking. Returns null if the file has no decodable audio. [cancelled] is polled. */
    fun decodeMono(context: Context, uri: String, startUs: Long, endUs: Long, cancelled: () -> Boolean = { false }): Pcm? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val out = Builder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (cancelled()) return null
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0 || ex.sampleTime > endUs) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset).limit(info.offset + info.size)
                        val mono = if (float) floatToMono(buf, channels) else {
                            val direct = ByteBuffer.allocateDirect(info.size).order(ByteOrder.nativeOrder())
                            direct.put(buf)
                            direct.flip()
                            NativeCore.pcm16ToMono(direct, info.size, channels)
                        }
                        // Keep only samples inside [startUs, endUs).
                        val frameUs = 1_000_000.0 / rate
                        for ((k, v) in mono.withIndex()) {
                            val t = info.presentationTimeUs + (k * frameUs).toLong()
                            if (t >= startUs && t < endUs) out.add(v)
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            return Pcm(out.build(), rate)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    /** Interleaved stereo frames (mono is doubled, extra channels dropped). */
    class Stereo(val lr: FloatArray, val sampleRate: Int) { val frames get() = lr.size / 2 }

    /** Blocking. The range [startUs, endUs) as stereo floats at the track's own rate, or null. */
    fun decodeStereo(context: Context, uri: String, startUs: Long, endUs: Long, cancelled: () -> Boolean = { false }): Stereo? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val out = Builder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (cancelled()) return null
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0 || ex.sampleTime > endUs) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset).limit(info.offset + info.size)
                        val b = buf.slice().order(ByteOrder.nativeOrder())
                        val frames = info.size / (channels * if (float) 4 else 2)
                        val frameUs = 1_000_000.0 / rate
                        for (k in 0 until frames) {
                            val t = info.presentationTimeUs + (k * frameUs).toLong()
                            if (t < startUs || t >= endUs) continue
                            fun at(c: Int) = if (float) b.getFloat((k * channels + c) * 4) else b.getShort((k * channels + c) * 2) / 32768f
                            val l = at(0)
                            out.add(l); out.add(if (channels > 1) at(1) else l)
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            return Stereo(out.build(), rate)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    private fun floatToMono(buf: ByteBuffer, channels: Int): FloatArray {
        val fb = buf.slice().order(ByteOrder.nativeOrder()).asFloatBuffer()
        val frames = fb.remaining() / channels
        return FloatArray(frames) { i ->
            var acc = 0f
            for (c in 0 until channels) acc += fb.get(i * channels + c)
            acc / channels
        }
    }

    private class Builder {
        private var data = FloatArray(1 shl 16)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun build() = data.copyOf(size)
    }
}
