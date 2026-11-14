package com.reel.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.reel.editor.model.VoiceEffects
import com.reel.editor.model.VoiceFx
import com.reel.editor.nativebridge.NativeCore
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the native voice chain (enhancer, noise reduction, leveler, character effect) inside
 * Media3's audio pipeline, so preview and export sound the same. Pitch effects are done by a
 * separate Sonic processor placed before this one.
 */
class VoiceProcessor(private val fx: VoiceFx) : BaseAudioProcessor() {
    private var handle = 0L
    private var work: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive() && !fx.isIdentity && nativeEffect() >= 0

    private fun nativeEffect(): Int = if (fx.effect == null) 0 else VoiceEffects.get(fx.effect)?.nativeId ?: 0

    private fun ensureChain() {
        if (handle != 0L) return
        val f = inputAudioFormat
        handle = NativeCore.voiceCreate(f.sampleRate, f.channelCount, fx.enhance, fx.denoise, fx.normalize, nativeEffect())
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        if (handle != 0L) NativeCore.voiceReset(handle) else ensureChain()
    }

    private fun workBuffer(bytes: Int): ByteBuffer {
        if (work.capacity() < bytes) work = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        work.clear()
        return work
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        ensureChain()
        val f = inputAudioFormat
        val isFloat = f.encoding == C.ENCODING_PCM_FLOAT
        val ch = f.channelCount
        val samples = remaining / (if (isFloat) 4 else 2)
        val frames = samples / ch
        val w = workBuffer(samples * 4)
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        val fw = w.asFloatBuffer()
        if (isFloat) {
            val fs = src.asFloatBuffer()
            for (i in 0 until samples) fw.put(fs.get(i))
        } else {
            for (i in 0 until samples) fw.put(src.getShort(src.position() + i * 2) / 32768f)
        }
        inputBuffer.position(inputBuffer.limit())
        val written = NativeCore.voiceProcess(handle, w, frames)
        emit(w, written, ch, isFloat)
    }

    override fun onQueueEndOfStream() {
        if (handle == 0L) return
        val f = inputAudioFormat
        val lat = NativeCore.voiceLatency(handle)
        if (lat <= 0) return
        val w = workBuffer((lat + 4096) * f.channelCount * 4)
        val n = NativeCore.voiceDrain(handle, w, lat + 4096)
        if (n > 0) emit(w, n, f.channelCount, f.encoding == C.ENCODING_PCM_FLOAT)
    }

    private fun emit(w: ByteBuffer, frames: Int, ch: Int, isFloat: Boolean) {
        val samples = frames * ch
        val out = replaceOutputBuffer(samples * (if (isFloat) 4 else 2)).order(ByteOrder.nativeOrder())
        val fw = w.asFloatBuffer()
        if (isFloat) {
            for (i in 0 until samples) out.putFloat(fw.get(i))
        } else {
            for (i in 0 until samples) out.putShort((fw.get(i) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    override fun onReset() {
        if (handle != 0L) NativeCore.voiceFree(handle)
        handle = 0L
    }
}
