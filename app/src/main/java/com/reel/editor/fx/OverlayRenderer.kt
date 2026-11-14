package com.reel.editor.fx

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.reel.editor.model.ClipKind
import com.reel.editor.model.MaskShape
import com.reel.editor.model.OverlayClip
import com.reel.editor.model.interpolate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes an overlay video on the GL thread into an external texture, frame-accurately for any
 * requested time, and copies it into a regular 2D texture. Seeks backwards or far ahead re-seek the
 * extractor; playing forward just decodes on.
 */
class OverlayDecoder(context: Context, uri: String) {
    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private val oesTex: Int
    private val surfaceTexture: SurfaceTexture
    private val surface: Surface
    private val cbThread = HandlerThread("overlay-frames").apply { start() }
    private val lock = Object()
    private var frameReady = false
    private var inputDone = false
    private var outputDone = false
    private var lastPts = Long.MIN_VALUE
    private var started = false
    var width = 0
        private set
    var height = 0
        private set
    private val frameUs: Long

    init {
        extractor.setDataSource(context, Uri.parse(uri), null)
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        extractor.selectTrack(track)
        val fmt = extractor.getTrackFormat(track)
        val rot = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
        val w = fmt.getInteger(MediaFormat.KEY_WIDTH)
        val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
        width = if (rot % 180 != 0) h else w
        height = if (rot % 180 != 0) w else h
        val fps = if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) fmt.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 240) else 30
        frameUs = 1_000_000L / fps
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        oesTex = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        surfaceTexture = SurfaceTexture(oesTex)
        surfaceTexture.setOnFrameAvailableListener({ synchronized(lock) { frameReady = true; lock.notifyAll() } }, Handler(cbThread.looper))
        surface = Surface(surfaceTexture)
        val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        c.configure(fmt, surface, null, 0)
        c.start()
        codec = c
        started = true
    }

    private fun seek(us: Long) {
        val c = codec ?: return
        c.flush()
        extractor.seekTo(us, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        inputDone = false
        outputDone = false
        lastPts = Long.MIN_VALUE
    }

    /**
     * Puts the frame at media time [us] (or the nearest before the end) on the external texture.
     * Returns false when nothing could be decoded.
     */
    fun frameAt(us: Long): Boolean {
        val c = codec ?: return false
        val target = us.coerceAtLeast(0)
        if (lastPts != Long.MIN_VALUE && target >= lastPts - frameUs / 2 && target < lastPts + frameUs / 2) return true
        if (lastPts == Long.MIN_VALUE || target < lastPts || target > lastPts + 1_500_000) seek(target)
        val info = MediaCodec.BufferInfo()
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            if (!inputDone) {
                val i = c.dequeueInputBuffer(2_000)
                if (i >= 0) {
                    val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                    if (n < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            if (outputDone) return lastPts != Long.MIN_VALUE
            val o = c.dequeueOutputBuffer(info, 4_000)
            if (o < 0) continue
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val show = info.size > 0 && (info.presentationTimeUs >= target - frameUs / 2 || eos)
            c.releaseOutputBuffer(o, show)
            if (show) {
                awaitFrame()
                surfaceTexture.updateTexImage()
                lastPts = info.presentationTimeUs
                if (eos) outputDone = true
                return true
            }
            if (eos) { outputDone = true; return lastPts != Long.MIN_VALUE }
        }
        return lastPts != Long.MIN_VALUE
    }

    private fun awaitFrame() {
        synchronized(lock) {
            val until = System.currentTimeMillis() + 500
            while (!frameReady && System.currentTimeMillis() < until) lock.wait(50)
            frameReady = false
        }
    }

    private val matrix = FloatArray(16)

    /** Copies the current frame into [target] (a 2D texture of [w]x[h]) upright in GL orientation. */
    fun copyTo(fb: Int, w: Int, h: Int, prog: Int) {
        surfaceTexture.getTransformMatrix(matrix)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTexMatrix"), 1, false, matrix, 0)
        val pos = GLES20.glGetAttribLocation(prog, "aFramePosition")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 4, GLES20.GL_FLOAT, false, 0, QUAD)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { extractor.release() }
        surface.release()
        surfaceTexture.release()
        GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0)
        cbThread.quitSafely()
    }

    companion object {
        val QUAD: FloatBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f)); position(0)
        }

        const val COPY_VERTEX = """attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() { gl_Position = aFramePosition; vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5; }"""

        const val COPY_FRAGMENT = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexSamplingCoord;
uniform samplerExternalOES uTex;
uniform mat4 uTexMatrix;
void main() { gl_FragColor = texture2D(uTex, (uTexMatrix * vec4(vTexSamplingCoord, 0.0, 1.0)).xy); }"""
    }
}

/**
 * Draws overlays (picture in picture) onto the canvas: transform and keyframes, blend modes,
 * shape masks with feather, chroma key, and in/out fades. Decoders are cached per GL context.
 */
class OverlayRenderer(private val context: Context, private val catalog: FxCatalog) {
    private val decoders = HashMap<String, OverlayDecoder>()
    private val frames = HashMap<String, IntArray>() // id -> [tex, fb, w, h]
    private var copyProg = 0

    private fun copyProgram(): Int {
        if (copyProg != 0) return copyProg
        fun sh(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, OverlayDecoder.COPY_VERTEX))
        GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, OverlayDecoder.COPY_FRAGMENT))
        GLES20.glLinkProgram(p)
        copyProg = p
        return p
    }

    /** The overlay's frame for [mediaUs] as a 2D texture id plus its aspect and v-flip, or null. */
    private fun frameTexture(o: OverlayClip, mediaUs: Long): Triple<Int, Float, Boolean>? {
        if (o.kind == ClipKind.PHOTO) {
            val tex = GlCache.texture("ovphoto:${o.uri}") { LayerBitmaps.sticker(context, o.uri, 1280) } ?: return null
            return Triple(tex.id, tex.width.toFloat() / tex.height, true)
        }
        val dec = decoders.getOrPut(o.id + o.uri) {
            try { OverlayDecoder(context, o.uri) } catch (e: Exception) { logFx("overlay decoder ${o.uri}", e); return null }
        }
        if (!dec.frameAt(mediaUs)) return null
        // Copy into a 2D texture no larger than 1280 on the long side.
        val k = min(1f, 1280f / max(dec.width, dec.height).coerceAtLeast(1))
        val w = (dec.width * k).toInt().coerceAtLeast(2)
        val h = (dec.height * k).toInt().coerceAtLeast(2)
        val f = frames.getOrPut(o.id) {
            val t = GlTex.empty(w, h)
            intArrayOf(t, GlTex.framebuffer(t), w, h)
        }
        if (f[2] != w || f[3] != h) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(f[1]), 0); GlTex.delete(f[0])
            f[0] = GlTex.empty(w, h); f[1] = GlTex.framebuffer(f[0]); f[2] = w; f[3] = h
        }
        dec.copyTo(f[1], w, h, copyProgram())
        return Triple(f[0], w.toFloat() / h, false)
    }

    /**
     * Blends [o] at timeline time [global] over the canvas texture [dst] into the currently bound
     * framebuffer. Returns false if the overlay was not drawn (caller keeps [dst]).
     */
    fun draw(o: OverlayClip, global: Long, dst: Int, canvasW: Int, canvasH: Int, outFb: Int): Boolean {
        val local = global - o.startUs
        if (local < 0 || local >= o.timelineDurationUs) return false
        val motion = TextMotion.at(o.animIn, o.animOut, null, local, o.timelineDurationUs)
        if (motion.opacity <= 0.001f) return false
        val src = frameTexture(o, o.trimStartUs + local) ?: return false
        val p = GlCache.program("internal:overlay_blend", catalog.vertex) { catalog.internalSource("overlay_blend") } ?: return false
        val t = interpolate(o.transform, o.keyframes, local)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFb)
        GLES20.glViewport(0, 0, canvasW, canvasH)
        p.use()
        p.texture("uDst", dst, 0)
        p.texture("uSrc", src.first, 1)
        p.set("uCanvasAspect", canvasW.toFloat() / canvasH)
        p.set("uSrcAspect", src.second)
        p.set("uFlipV", if (src.third) 1f else 0f)
        p.set("uCenter", t.x + motion.dx, 1f - (t.y + motion.dy))
        p.set("uScale", t.scale * motion.scale * motion.sx, t.scale * motion.scale * motion.sy)
        p.set("uRotation", Math.toRadians((t.rotation + motion.rotationDeg).toDouble()).toFloat())
        p.set("uOpacity", (t.opacity * motion.opacity).coerceIn(0f, 1f))
        p.set("uBlend", o.blend.ordinal.toFloat())
        val m = o.mask
        p.set("uMaskShape", if (m.shape == MaskShape.NONE) 0f else m.shape.ordinal.toFloat())
        p.set("uMaskCenter", m.x, 1f - m.y)
        p.set("uMaskSize", m.w, m.h)
        p.set("uMaskRot", Math.toRadians(m.rotation.toDouble()).toFloat())
        p.set("uMaskFeather", m.feather)
        p.set("uMaskInvert", if (m.invert) 1f else 0f)
        val k = o.chroma
        p.set("uKeyOn", if (k.enabled) 1f else 0f)
        p.set("uKeyColor", ((k.color shr 16) and 0xff) / 255f, ((k.color shr 8) and 0xff) / 255f, (k.color and 0xff) / 255f)
        p.set("uKeySimilarity", k.similarity)
        p.set("uKeySmoothness", k.smoothness)
        p.set("uKeySpill", k.spill)
        p.draw()
        return true
    }

    fun release() {
        decoders.values.forEach { runCatching { it.release() } }
        decoders.clear()
        frames.values.forEach { GLES20.glDeleteFramebuffers(1, intArrayOf(it[1]), 0); GlTex.delete(it[0]) }
        frames.clear()
        if (copyProg != 0) GLES20.glDeleteProgram(copyProg)
        copyProg = 0
    }
}
