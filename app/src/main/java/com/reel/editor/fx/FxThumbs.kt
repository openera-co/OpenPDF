package com.reel.editor.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.LruCache
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Renders tile previews for every shader in the catalog with the same GLSL the editor uses,
 * on the user's own frame. Runs on a private GL thread with an offscreen context, so the
 * preview player is never disturbed. Motion previews are short frame loops.
 */
object FxThumbs {
    const val W = 120
    const val H = 80

    private const val LOOP_FRAMES = 14
    // About 6 s of candidates. The step is deliberately not a round number so periodic effects
    // (scan lines, beat flashes) don't alias into identical frames.
    private const val CANDIDATES = 84
    private const val CANDIDATE_STEP = 0.0717f
    private const val ANIM_STEPS = 10
    private const val HOLD = 4

    enum class Mode { STATIC, LOOP, ANIM_IN, ANIM_OUT }

    private val thread = HandlerThread("fx-thumbs").apply { start() }
    private val handler = Handler(thread.looper)

    private val cache = object : LruCache<String, List<Bitmap>>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, value: List<Bitmap>) = value.sumOf { it.byteCount }.coerceAtLeast(1)
    }
    private val failed = HashSet<String>()

    // GL state, touched only on the thumbs thread.
    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var surface: EGLSurface? = null
    private var fbTex = 0
    private var fb = 0
    private val programs = object : LinkedHashMap<String, Prog?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Prog?>): Boolean {
            if (size > 24) { eldest.value?.release(); return true }
            return false
        }
    }
    private val sourceTex = object : LinkedHashMap<String, Int>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>): Boolean {
            if (size > 6) { GlTex.delete(eldest.value); return true }
            return false
        }
    }
    private val readBuf: ByteBuffer = ByteBuffer.allocateDirect(W * H * 4).order(ByteOrder.nativeOrder())

    fun cached(key: String): List<Bitmap>? = cache.get(key)

    /** Preview frames for a filter, effect or clip animation applied to [src]. Null if the shader can't run. */
    suspend fun fx(catalog: FxCatalog, def: FxDef, mode: Mode, src: Bitmap, srcKey: String): List<Bitmap>? {
        val key = "fx:${def.id}:$mode:$srcKey"
        return run(key) {
            val prog = program("fx:${def.id}") { catalog.vertex to catalog.singleSource(def) } ?: return@run null
            val tex = source(srcKey, src)
            val seed = (def.id.hashCode() and 0xff) / 255f
            fun frame(time: Float, dur: Float, progress: Float, intensity: Float) = draw(prog) { p ->
                p.texture("uTex", tex, 0)
                p.set("uResolution", W.toFloat(), H.toFloat())
                p.set("uTime", time)
                p.set("uDuration", dur)
                p.set("uProgress", progress)
                p.set("uIntensity", intensity)
                p.set("uSeed", seed)
                for (param in def.params) p.set(param.name, *param.default.toFloatArray())
            }
            when (mode) {
                Mode.STATIC -> listOf(frame(1f, 2f, 0.5f, 1f))
                Mode.LOOP -> {
                    // Sample a longer stretch and keep the busiest part, so effects with rare events
                    // (flashes, drops) still show them in the tile.
                    val all = (0 until CANDIDATES).map { i -> val t = i * CANDIDATE_STEP; frame(t, CANDIDATES * CANDIDATE_STEP, t / (CANDIDATES * CANDIDATE_STEP), 1f) }
                    val motion = FloatArray(all.size) { i -> if (i == 0) 0f else change(all[i - 1], all[i]) }
                    var best = 0
                    var bestScore = -1f
                    for (start in 0..all.size - LOOP_FRAMES) {
                        var score = 0f
                        for (i in start + 1 until start + LOOP_FRAMES) score += motion[i]
                        if (score > bestScore) { bestScore = score; best = start }
                    }
                    val keep = all.subList(best, best + LOOP_FRAMES).toList()
                    all.forEach { if (it !in keep) it.recycle() }
                    keep
                }
                Mode.ANIM_IN, Mode.ANIM_OUT -> {
                    // Same easing as the renderer; the clip is fully visible at 1.
                    val steps = (0..ANIM_STEPS).map { i -> smooth(i / ANIM_STEPS.toFloat()) }
                    val vis = if (mode == Mode.ANIM_IN) steps else steps.reversed()
                    val frames = vis.mapIndexed { i, v -> frame(i * 0.05f, 0.5f, v, 1f) }
                    val still = frame(0f, 0.5f, 1f, 1f)
                    if (mode == Mode.ANIM_IN) frames + List(HOLD) { still } else List(HOLD) { still } + frames
                }
            }
        }
    }

    /** Preview frames for a transition from [from] to [to]. */
    suspend fun transition(catalog: FxCatalog, def: TransitionDef, from: Bitmap, to: Bitmap, srcKey: String): List<Bitmap>? {
        val key = "tr:${def.id}:$srcKey"
        return run(key) {
            val prog = program("tr:${def.id}") { catalog.vertex to catalog.transitionSource(def) } ?: return@run null
            val a = source("$srcKey:a", from)
            val b = source("$srcKey:b", to)
            fun frame(progress: Float) = draw(prog) { p ->
                p.texture("uFrom", a, 0)
                p.texture("uTo", b, 1)
                p.set("progress", progress)
                p.set("ratio", W.toFloat() / H)
                for (param in def.params) p.set(param.name, *param.default.toFloatArray())
            }
            val start = frame(0f)
            val end = frame(1f)
            List(HOLD) { start } + (1 until ANIM_STEPS).map { frame(it / ANIM_STEPS.toFloat()) } + List(HOLD) { end }
        }
    }

    /** Crops and scales a frame to the preview size. Falls back to a neutral sample picture. */
    fun prepare(frame: Bitmap?, hueShift: Boolean = false): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (frame == null || frame.width <= 0 || frame.height <= 0) {
            val paint = Paint()
            val (a, b) = if (hueShift) 0xFF2E6BD9.toInt() to 0xFF7A2BB8.toInt() else 0xFFE8955A.toInt() to 0xFF3A2C6B.toInt()
            paint.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), a, b, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
            paint.shader = null
            paint.color = 0x55FFFFFF
            c.drawCircle(W * 0.7f, H * 0.35f, H * 0.18f, paint)
            paint.color = 0x33000000
            c.drawRect(0f, H * 0.72f, W.toFloat(), H.toFloat(), paint)
            return out
        }
        val scale = maxOf(W.toFloat() / frame.width, H.toFloat() / frame.height)
        val sw = W / scale
        val sh = H / scale
        val l = (frame.width - sw) / 2f
        val t = (frame.height - sh) / 2f
        c.drawBitmap(frame, Rect(l.toInt(), t.toInt(), (l + sw).toInt(), (t + sh).toInt()), RectF(0f, 0f, W.toFloat(), H.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    // ------------------------------------------------------------------ GL thread

    private suspend fun run(key: String, block: () -> List<Bitmap>?): List<Bitmap>? {
        cache.get(key)?.let { return it }
        synchronized(failed) { if (key in failed) return null }
        return suspendCancellableCoroutine { cont ->
            val cancelled = AtomicBoolean(false)
            cont.invokeOnCancellation { cancelled.set(true) }
            handler.post {
                if (cancelled.get()) return@post
                val result = cache.get(key) ?: try {
                    ensureGl()
                    block()?.also { cache.put(key, it) }
                } catch (e: Throwable) {
                    logFx("thumb $key failed", e)
                    null
                }
                if (result == null) synchronized(failed) { failed += key }
                if (cont.isActive) cont.resume(result)
            }
        }
    }

    private fun ensureGl() {
        if (context != null) return
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        EGL14.eglInitialize(d, v, 0, v, 1)
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(d, attrs, 0, configs, 0, 1, n, 0)
        val cfg = configs[0] ?: error("no EGL config")
        val ctx = EGL14.eglCreateContext(d, cfg, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(d, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(d, surf, surf, ctx)) { "eglMakeCurrent failed" }
        display = d; context = ctx; surface = surf
        fbTex = GlTex.empty(W, H)
        fb = GlTex.framebuffer(fbTex)
    }

    private fun program(key: String, sources: () -> Pair<String, String>): Prog? {
        if (programs.containsKey(key)) return programs[key]
        val p = try {
            val (v, f) = sources()
            Prog(v, f)
        } catch (e: Exception) {
            logFx("thumb shader $key unavailable", e)
            null
        }
        programs[key] = p
        return p
    }

    /** Uploads a source picture once, flipped so texture coordinates follow GL convention like video frames. */
    private fun source(key: String, bmp: Bitmap): Int {
        sourceTex[key]?.let { return it }
        val tex = GlTex.fromBitmap(flip(bmp))
        sourceTex[key] = tex
        return tex
    }

    private fun draw(prog: Prog, setup: (Prog) -> Unit): Bitmap {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb)
        GLES20.glViewport(0, 0, W, H)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        prog.use()
        setup(prog)
        prog.draw()
        readBuf.position(0)
        GLES20.glReadPixels(0, 0, W, H, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readBuf)
        val raw = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        readBuf.position(0)
        raw.copyPixelsFromBuffer(readBuf)
        // Rows come back bottom first.
        return flip(raw).also { raw.recycle() }
    }

    private fun flip(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { preScale(1f, -1f) }, false)

    private val pxA = IntArray(W * H)
    private val pxB = IntArray(W * H)

    /** Mean per-channel difference between two previews, sampled sparsely. */
    private fun change(a: Bitmap, b: Bitmap): Float {
        a.getPixels(pxA, 0, W, 0, 0, W, H)
        b.getPixels(pxB, 0, W, 0, 0, W, H)
        var sum = 0L
        var i = 0
        while (i < pxA.size) {
            val p = pxA[i]; val q = pxB[i]
            sum += kotlin.math.abs(((p shr 16) and 255) - ((q shr 16) and 255)) + kotlin.math.abs(((p shr 8) and 255) - ((q shr 8) and 255)) + kotlin.math.abs((p and 255) - (q and 255))
            i += 3
        }
        return sum.toFloat() / (pxA.size / 3)
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }
}
