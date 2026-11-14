package com.reel.editor.nativebridge

import android.graphics.Bitmap
import java.nio.ByteBuffer

/** C++ core: procedural look LUTs, audio peaks and beat detection, histograms. */
object NativeCore {
    init {
        System.loadLibrary("reelcore")
    }

    external fun lookCount(): Int

    /** [id, name, category] or null for a bad index. */
    external fun lookInfo(index: Int): Array<String>?

    /** ARGB ints, index ((r * size + g) * size + b). */
    external fun renderLut(index: Int, size: Int): IntArray?

    external fun peaks(samples: FloatArray, buckets: Int): FloatArray

    /** [bpm, confidence, beat times in seconds...]. */
    external fun beats(samples: FloatArray, sampleRate: Int): DoubleArray

    external fun pcm16ToMono(buffer: ByteBuffer, byteCount: Int, channels: Int): FloatArray

    /** 4 x 256 bins: red, green, blue, luma. Bitmap must be ARGB_8888. */
    external fun histogram(bitmap: Bitmap): IntArray?

    external fun rmsDb(samples: FloatArray): Double

    // ------------------------------------------------------------------ speech to text

    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    /** Opens the speech model stored (uncompressed) in the APK assets; 0 if it can't be loaded. */
    external fun speechOpenAsset(assets: android.content.res.AssetManager, name: String): Long

    external fun speechClose(handle: Long)

    /** Safe from any thread; the running transcription returns null soon after. */
    external fun speechCancel(handle: Long)

    /** 16 kHz mono. Returns [Array<String> words, LongArray startMs, LongArray endMs, FloatArray prob, String language] or null. */
    external fun speechTranscribe(handle: Long, samples16k: FloatArray, language: String, translate: Boolean, threads: Int, listener: ProgressListener?): Array<Any>?

    external fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray

    // ------------------------------------------------------------------ voice processing

    /** [effect] is a native effect id (see VoiceEffects). Free with [voiceFree]. */
    external fun voiceCreate(rate: Int, channels: Int, enhance: Float, denoise: Float, normalize: Boolean, effect: Int): Long

    external fun voiceFree(handle: Long)

    external fun voiceReset(handle: Long)

    /** Processes interleaved floats in a direct buffer in place; returns frames written. */
    external fun voiceProcess(handle: Long, io: java.nio.ByteBuffer, frames: Int): Int

    /** Writes frames held back by the noise reducer; returns frames. */
    external fun voiceDrain(handle: Long, out: java.nio.ByteBuffer, maxFrames: Int): Int

    external fun voiceLatency(handle: Long): Int
}
