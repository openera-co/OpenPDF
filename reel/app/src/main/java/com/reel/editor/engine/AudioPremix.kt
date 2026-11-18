package com.reel.editor.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import com.reel.editor.media.AudioDecoder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Mixes music, sounds and overlay audio into one stereo WAV for export. Media3 can stall when
 * several audio sequences play at once, so the export gets a single, already mixed track.
 */
object AudioPremix {
    const val RATE = 44_100

    class Source(val uri: String, val startUs: Long, val trimStartUs: Long, val trimEndUs: Long, val fx: List<AudioProcessor>)

    /** Writes the mix of [sources] from 0 to [endUs] into [out]. False if nothing could be decoded. */
    fun render(context: Context, sources: List<Source>, endUs: Long, out: File): Boolean {
        val frames = (endUs * RATE / 1_000_000L).toInt().coerceAtLeast(1)
        val mix = FloatArray(frames * 2)
        var any = false
        for (s in sources) {
            val pcm = AudioDecoder.decodeStereo(context, s.uri, s.trimStartUs, s.trimEndUs) ?: continue
            val (lr, rate) = process(pcm, s.fx)
            val at = (s.startUs * RATE / 1_000_000L).toInt()
            val n = lr.size / 2
            val step = rate.toDouble() / RATE
            var i = 0
            while (true) {
                val src = i * step
                val k = src.toInt()
                if (k >= n) break
                val f = (src - k).toFloat()
                val dst = at + i
                if (dst >= frames) break
                if (dst >= 0) {
                    val k1 = minOf(k + 1, n - 1)
                    mix[dst * 2] += lr[k * 2] * (1 - f) + lr[k1 * 2] * f
                    mix[dst * 2 + 1] += lr[k * 2 + 1] * (1 - f) + lr[k1 * 2 + 1] * f
                }
                i++
            }
            any = true
        }
        if (!any) return false
        writeWav(out, mix)
        return true
    }

    /** Runs the item's processors (volume, fades, voice) over the whole decoded range. */
    private fun process(pcm: AudioDecoder.Stereo, fx: List<AudioProcessor>): Pair<FloatArray, Int> {
        if (fx.isEmpty()) return pcm.lr to pcm.sampleRate
        val pipe = AudioProcessingPipeline(ImmutableList.copyOf(fx))
        val outFmt = pipe.configure(AudioProcessor.AudioFormat(pcm.sampleRate, 2, C.ENCODING_PCM_16BIT))
        pipe.flush(AudioProcessor.StreamMetadata(0))
        if (!pipe.isOperational) return pcm.lr to pcm.sampleRate
        val bytesPerSample = if (outFmt.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val collected = java.io.ByteArrayOutputStream()
        fun drain() {
            while (true) {
                val o = pipe.output
                if (!o.hasRemaining()) break
                val b = ByteArray(o.remaining()); o.get(b); collected.write(b)
            }
        }
        val chunk = 4096
        var f = 0
        val total = pcm.frames
        while (f < total) {
            val m = minOf(chunk, total - f)
            val buf = ByteBuffer.allocateDirect(m * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until m * 2) buf.putShort((pcm.lr[f * 2 + k].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            buf.flip()
            var guard = 0
            while (buf.hasRemaining()) {
                pipe.queueInput(buf)
                drain()
                if (++guard > 10_000) break
            }
            f += m
        }
        pipe.queueEndOfStream()
        var guard = 0
        while (!pipe.isEnded && guard++ < 10_000) drain()
        drain()
        val bytes = ByteBuffer.wrap(collected.toByteArray()).order(ByteOrder.nativeOrder())
        val ch = outFmt.channelCount
        val outFrames = bytes.remaining() / (bytesPerSample * ch)
        val lr = FloatArray(outFrames * 2)
        for (k in 0 until outFrames) {
            fun at(c: Int) = if (bytesPerSample == 4) bytes.getFloat((k * ch + c) * 4) else bytes.getShort((k * ch + c) * 2) / 32768f
            val l = at(0)
            lr[k * 2] = l
            lr[k * 2 + 1] = if (ch > 1) at(1) else l
        }
        return lr to outFmt.sampleRate
    }

    /** 16-bit stereo; peaks over full scale are rounded off instead of clipped hard. */
    private fun writeWav(f: File, lr: FloatArray) {
        val data = ByteArray(lr.size * 2)
        for (i in lr.indices) {
            var v = lr[i]
            if (abs(v) > 0.9f) v = (if (v > 0) 1 else -1) * (0.9f + 0.1f * tanh((abs(v) - 0.9f) / 0.1f))
            val s = (v * 32767f).toInt().coerceIn(-32768, 32767)
            data[i * 2] = (s and 0xff).toByte(); data[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
        }
        RandomAccessFile(f, "rw").use { out ->
            out.setLength(0)
            fun i32(v: Int) = out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()))
            fun i16(v: Int) = out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte()))
            out.write("RIFF".toByteArray()); i32(36 + data.size); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); i32(16); i16(1); i16(2); i32(RATE); i32(RATE * 4); i16(4); i16(16)
            out.write("data".toByteArray()); i32(data.size); out.write(data)
        }
    }
}
