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
}
