package com.reel.editor.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.reel.editor.nativebridge.NativeCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes audio to mono floats at a reduced rate for the native analysis (waveform peaks, beats).
 * Results are cached per source URI.
 */
class AudioAnalyzer(private val context: Context) {
    data class Analysis(val durationUs: Long, val peaks: FloatArray, val bpm: Double, val beatsUs: List<Long>)

    private val cache = ConcurrentHashMap<String, Analysis>()

    fun cached(uri: String): Analysis? = cache[uri]

    /** Blocking; call from a background thread. */
    fun analyze(uri: String, peaksPerSecond: Int = 40, maxSeconds: Int = 600): Analysis? {
        cache[uri]?.let { return it }
        val decoded = decodeMono(uri, maxSeconds) ?: return null
        val (samples, rate) = decoded
        if (samples.isEmpty()) return null
        val seconds = samples.size.toDouble() / rate
        val peaks = NativeCore.peaks(samples, (seconds * peaksPerSecond).toInt().coerceAtLeast(1))
        val beats = NativeCore.beats(samples, rate)
        val bpm = beats.getOrElse(0) { 0.0 }
        // Every track with a pulse gets its beats; the user can switch them off.
        val beatList = if (beats.size > 2) beats.drop(2).map { (it * 1e6).toLong() } else emptyList()
        return Analysis((seconds * 1e6).toLong(), peaks, bpm, beatList).also { cache[uri] = it }
    }

    private fun decodeMono(uri: String, maxSeconds: Int): Pair<FloatArray, Int>? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Decimate to ~11 kHz: plenty for peaks and onset detection, 4x less work.
            val out = FloatArrayBuilder()
            var decim = 1
            var phase = 0
            var acc = 0f
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxUs = maxSeconds * 1_000_000L
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > maxUs) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    o >= 0 -> {
                        decim = (rate / 11025).coerceAtLeast(1)
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(o)!!
                            buf.position(info.offset).limit(info.offset + info.size)
                            val direct = ByteBuffer.allocateDirect(info.size).order(ByteOrder.nativeOrder())
                            direct.put(buf)
                            direct.flip()
                            val mono = NativeCore.pcm16ToMono(direct, info.size, channels)
                            for (v in mono) {
                                acc += v
                                if (++phase == decim) { out.add(acc / decim); acc = 0f; phase = 0 }
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return out.build() to (rate / decim)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(1 shl 16)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun build() = data.copyOf(size)
    }
}
