package com.reel.editor.fx

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Person segmentation with MediaPipe's selfie segmenter (Apache-2.0) on LiteRT. Input is an RGBA
 * frame read back from GL (rows bottom first); output is a mask in the same row order, so it can
 * be uploaded straight back as a texture that lines up with the frame.
 *
 * Interpreters are not thread-safe; one is kept per thread (preview and export run on their own
 * GL threads).
 */
class Segmenter private constructor(context: Context, landscape: Boolean) {
    val width: Int
    val height: Int
    private val interpreter: Interpreter
    private val input: ByteBuffer
    private val output: ByteBuffer
    private var prev: FloatArray? = null
    private var prevPts = Long.MIN_VALUE
    private var prevClip: String? = null

    init {
        val name = if (landscape) "models/selfie_segmenter_landscape.tflite" else "models/selfie_segmenter.tflite"
        val bytes = context.assets.open(name).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
        val shape = interpreter.getInputTensor(0).shape() // [1, h, w, 3]
        height = shape[1]
        width = shape[2]
        input = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder())
        output = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    }

    /**
     * [rgbaBottomUp] holds width*height RGBA pixels as read by glReadPixels. Returns mask bytes
     * (0..255, bottom-up rows). Consecutive frames are blended to keep edges from flickering.
     */
    fun run(rgbaBottomUp: ByteBuffer, clipId: String, pts: Long, out: ByteArray) {
        input.rewind()
        val fb = input.asFloatBuffer()
        // The model wants the picture upright: top row first.
        for (y in 0 until height) {
            val row = (height - 1 - y) * width * 4
            for (x in 0 until width) {
                val i = row + x * 4
                fb.put((rgbaBottomUp.get(i).toInt() and 255) / 255f)
                fb.put((rgbaBottomUp.get(i + 1).toInt() and 255) / 255f)
                fb.put((rgbaBottomUp.get(i + 2).toInt() and 255) / 255f)
            }
        }
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val mask = FloatArray(width * height)
        output.asFloatBuffer().get(mask)
        // Temporal smoothing only across neighbouring frames; a seek starts fresh.
        val p = prev
        val sequential = p != null && clipId == prevClip && pts > prevPts && pts - prevPts < 150_000
        if (sequential) for (i in mask.indices) mask[i] = mask[i] * 0.6f + p!![i] * 0.4f
        prev = mask
        prevPts = pts
        prevClip = clipId
        for (y in 0 until height) {
            val src = y * width
            val dst = (height - 1 - y) * width
            for (x in 0 until width) out[dst + x] = (mask[src + x].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        }
    }

    fun reset() {
        prev = null
        prevPts = Long.MIN_VALUE
        prevClip = null
    }

    companion object {
        private val perThread = ThreadLocal<HashMap<Boolean, Segmenter>>()

        /** The segmenter for this thread; landscape content uses the wide model. */
        fun forThread(context: Context, landscape: Boolean): Segmenter {
            val map = perThread.get() ?: HashMap<Boolean, Segmenter>().also { perThread.set(it) }
            return map.getOrPut(landscape) { Segmenter(context.applicationContext, landscape) }
        }
    }
}
