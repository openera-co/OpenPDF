package com.reel.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Piecewise-constant speed over a clip's trimmed media, timestamps relative to the trim start. */
class SegmentSpeed(private val segments: List<Pair<Long, Float>>) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float {
        var s = segments.first().second
        for ((start, speed) in segments) if (timeUs >= start) s = speed else break
        return s
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        for ((start, _) in segments) if (start > timeUs) return start
        return C.TIME_UNSET
    }
}

/**
 * Volume with linear fades. Operates on output (timeline) time: [durationUs] is the clip's length
 * on the timeline, and position is counted from the last flush using the stream offset when given.
 * Handles 16-bit and float PCM.
 */
class VolumeFadeProcessor(
    private val volume: Float,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val durationUs: Long,
    /** Maps a media offset reported by flush metadata to timeline offset. */
    private val mediaToTimeline: (Long) -> Long = { it },
) : BaseAudioProcessor() {
    private var framesSinceFlush = 0L
    private var startOffsetUs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive() && (volume != 1f || fadeInUs > 0 || fadeOutUs > 0)

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesSinceFlush = 0
        startOffsetUs = mediaToTimeline(max(0L, streamMetadata.positionOffsetUs))
    }

    private fun gainAt(timeUs: Long): Float {
        var g = volume
        if (fadeInUs > 0 && timeUs < fadeInUs) g *= timeUs.toFloat() / fadeInUs
        val left = durationUs - timeUs
        if (fadeOutUs > 0 && left < fadeOutUs) g *= max(0f, left.toFloat() / fadeOutUs)
        return g
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        val channels = fmt.channelCount
        val rate = fmt.sampleRate.toLong()
        val isFloat = fmt.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val frames = remaining / (bytesPerSample * channels)
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        for (f in 0 until frames) {
            val t = startOffsetUs + (framesSinceFlush + f) * 1_000_000L / rate
            val g = gainAt(t)
            for (c in 0 until channels) {
                if (isFloat) {
                    out.putFloat(src.float * g)
                } else {
                    val v = (src.short * g).toInt()
                    out.putShort(min(32767, max(-32768, v)).toShort())
                }
            }
        }
        framesSinceFlush += frames
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}
