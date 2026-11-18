package com.reel.editor.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.reel.editor.model.Adjustments
import com.reel.editor.model.BackgroundMode
import com.reel.editor.model.Curves
import com.reel.editor.model.interpolate
import com.reel.editor.model.interpolateValue
import com.reel.editor.nativebridge.NativeCore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Media3 effect wrapping a clip's whole look: one GL program with internal passes. */
class ClipRenderEffect(private val context: Context, private val plan: RenderPlan, private val live: LivePlans?) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ClipRenderer(this.context.applicationContext, { live?.get(plan.clip.id) ?: plan }, FxCatalog.get(this.context), isLive = live != null)
}

/**
 * Plans the preview reads on every frame. Non-structural edits (color, effects, text, keyframes)
 * replace plans here and show up on the next frame without rebuilding the player.
 */
/** Press and hold on the preview: show the clips without looks, effects, animations or layers. */
object PreviewOriginal {
    @Volatile var on = false
}

class LivePlans {
    @Volatile private var plans: Map<String, RenderPlan> = emptyMap()
    fun get(clipId: String): RenderPlan? = plans[clipId]
    fun set(list: List<RenderPlan>) { plans = list.associateBy { it.clip.id } }
}

/**
 * Renders one clip:
 *   pre-canvas  (grade, clip look, clip filter, clip effects) at a working size no larger than needed
 *   canvas      (fit/fill, keyframed transform, chroma key, background) at the project canvas size
 *   post-canvas (timed looks/filters/effects, in/out animation, transition)
 *   tail tap    (copies the last frames for the next clip's transition)
 *   layers      (text and stickers, blended)
 * The last active pass draws straight into Media3's output framebuffer.
 */
class ClipRenderer(
    private val context: Context,
    private val planSource: () -> RenderPlan,
    private val catalog: FxCatalog,
    private val isLive: Boolean = false,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    /** Snapshot for the frame being drawn; canvas size is fixed for the program's lifetime. */
    private var plan: RenderPlan = planSource()

    private var inW = 0
    private var inH = 0
    private var workW = 0
    private var workH = 0
    private val workTex = IntArray(2)
    private val workFb = IntArray(2)
    private val canvasTex = IntArray(2)
    private val canvasFb = IntArray(2)
    private var curveTex = 0
    private var lutTexCache = HashMap<String, Int>()
    private var curvesKey: Curves? = null
    private val overlays by lazy { OverlayRenderer(context, catalog) }

    // Cutout: a small copy of the frame for the segmenter, and the mask it produces.
    private var segW = 0
    private var segH = 0
    private var segTex = 0
    private var segFb = 0
    private var maskTex = 0
    private var segRead: java.nio.ByteBuffer? = null
    private var maskBytes = ByteArray(0)
    private var maskBuf: java.nio.ByteBuffer? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inW = inputWidth
        inH = inputHeight
        // Work no larger than what covers the canvas; 4K sources get filtered at canvas resolution.
        val cover = max(plan.canvasWidth.toFloat() / inputWidth, plan.canvasHeight.toFloat() / inputHeight)
        val k = min(1f, cover * 1.05f)
        workW = (inputWidth * k).roundToInt().coerceAtLeast(2)
        workH = (inputHeight * k).roundToInt().coerceAtLeast(2)
        return Size(plan.canvasWidth, plan.canvasHeight)
    }

    private fun localTime(presentationTimeUs: Long): Long = when (plan.timeOrigin) {
        TimeOrigin.COMPOSITION -> presentationTimeUs - plan.clipStartUs
        TimeOrigin.ITEM -> presentationTimeUs
    }.coerceIn(0, plan.clipDurationUs)

    private fun ensureTargets() {
        if (workTex[0] == 0) for (i in 0..1) { workTex[i] = GlTex.empty(workW, workH); workFb[i] = GlTex.framebuffer(workTex[i]) }
        if (canvasTex[0] == 0) for (i in 0..1) { canvasTex[i] = GlTex.empty(plan.canvasWidth, plan.canvasHeight); canvasFb[i] = GlTex.framebuffer(canvasTex[i]) }
    }

    private fun prog(key: String, src: () -> String) = GlCache.program(key, catalog.vertex, src)

    /** A pass reads a texture and writes to a target; null target means "Media3 output". */
    private class Pass(val run: (srcTex: Int, w: Int, h: Int) -> Unit, val canvasStage: Boolean)

    @Throws(VideoFrameProcessingException::class)
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            draw(inputTexId, presentationTimeUs)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun draw(inputTexId: Int, pts: Long) {
        RenderDebug.record(plan.clip.id, pts)
        val fresh = planSource()
        if (fresh.canvasWidth == plan.canvasWidth && fresh.canvasHeight == plan.canvasHeight) plan = fresh
        val local = localTime(pts)
        val global = plan.clipStartUs + local
        val outFb = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, it, 0) }[0]
        val viewport = IntArray(4).also { GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, it, 0) }
        ensureTargets()
        if (plan.cutout.enabled) updateMask(inputTexId, pts)

        // Only the live preview reads this; export always renders everything.
        val original = isLive && PreviewOriginal.on
        val pre = ArrayList<Pass>()
        if (!original) buildPrePasses(pre, local, global)
        // The clip itself: canvas, its in/out animation and the transition into it.
        val clipPost = ArrayList<Pass>()
        clipPost += Pass({ src, _, _ -> canvasPass(src, local) }, true)
        if (!original) buildClipPasses(clipPost, local)
        // Everything above the clip: overlays, then timed looks, filters, adjustment layers, effects.
        val globalPost = ArrayList<Pass>()
        if (!original) buildGlobalPasses(globalPost, local, global)

        // Pre-canvas: ping-pong at work size. The first pass downsamples from the input.
        var src = inputTexId
        var ping = 0
        for (p in pre) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, workFb[ping])
            GLES20.glViewport(0, 0, workW, workH)
            p.run(src, workW, workH)
            src = workTex[ping]
            ping = 1 - ping
        }

        // Post-canvas: canvas-size ping-pong.
        var cping = 0
        fun runAll(passes: List<Pass>) {
            for (p in passes) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, canvasFb[cping])
                GLES20.glViewport(0, 0, plan.canvasWidth, plan.canvasHeight)
                currentTarget = canvasFb[cping]
                p.run(src, plan.canvasWidth, plan.canvasHeight)
                src = canvasTex[cping]
                cping = 1 - cping
            }
        }
        runAll(clipPost)
        // Keep the clip's last frames (without overlays or global effects) for the next transition.
        if (wantsTap(local)) {
            val target = GlCache.frameTarget(plan.clip.id, plan.canvasWidth, plan.canvasHeight)
            val fb = GlTex.framebuffer(target.id)
            GLES20.glViewport(0, 0, plan.canvasWidth, plan.canvasHeight)
            passthrough(src)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(fb), 0)
        }
        runAll(globalPost)

        // Final: frame, then text, stickers and shapes, into the output.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFb)
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        passthrough(src)
        val layers = if (original) emptyList() else activeLayers(global)
        if (layers.isNotEmpty()) drawLayers(layers, global)
    }

    /** Framebuffer the running post pass writes to (overlays bind it again after decoding). */
    private var currentTarget = 0

    private fun wantsTap(local: Long) = plan.keepTail && plan.clipDurationUs - local <= 200_000

    private fun passthrough(src: Int) {
        val p = prog("internal:passthrough") { catalog.internalSource("passthrough") } ?: return
        p.use()
        p.texture("uTex", src, 0)
        p.draw()
    }

    // ------------------------------------------------------------------ pre-canvas

    private fun buildPrePasses(out: MutableList<Pass>, local: Long, global: Long) {
        val needGrade = !plan.adjust.isIdentity || plan.look != null
        val look = plan.look?.let { it.copy(amount = interpolateValue(it.amount, plan.clip.lookKeys, local)) }
        if (needGrade) out += Pass({ src, w, h -> gradePass(src, w, h, local, plan.adjust, look) }, false)
        plan.clipFilter?.let { f -> out += Pass({ src, w, h -> shaderPass(f, src, w, h, local / 1e6f, plan.clipDurationUs / 1e6f) }, false) }
        for (e in plan.clipEffects) out += Pass({ src, w, h -> shaderPass(e, src, w, h, local / 1e6f, plan.clipDurationUs / 1e6f) }, false)
    }

    private fun gradePass(src: Int, w: Int, h: Int, local: Long, a: Adjustments, look: LookUse?) {
        val p = prog("internal:grade") { catalog.internalSource("grade") } ?: return passthrough(src)
        p.use()
        p.texture("uTex", src, 0)
        p.set("uResolution", w.toFloat(), h.toFloat())
        p.set("uTime", local / 1e6f)
        p.set("uBrightness", a.brightness); p.set("uContrast", a.contrast); p.set("uSaturation", a.saturation)
        p.set("uVibrance", a.vibrance); p.set("uExposure", a.exposure); p.set("uTemperature", a.temperature)
        p.set("uTint", a.tint); p.set("uHighlights", a.highlights); p.set("uShadows", a.shadows)
        p.set("uFade", a.fade); p.set("uHue", a.hue); p.set("uVignette", a.vignette)
        p.set("uSharpen", a.sharpen); p.set("uGrain", a.grain)
        p.set("uHslOn", if (a.hsl.isIdentity) 0f else 1f)
        for (i in 0 until 8) p.set("uHsl$i", a.hsl.hue[i], a.hsl.sat[i], a.hsl.lum[i])
        if (!a.curves.isIdentity) {
            if (curvesKey != a.curves || curveTex == 0) {
                GlTex.delete(curveTex)
                curveTex = GlTex.fromBitmap(CurveLut.bitmap(a.curves))
                curvesKey = a.curves
            }
            p.texture("uCurves", curveTex, 2)
            p.set("uCurvesOn", 1f)
        } else {
            p.set("uCurvesOn", 0f)
            p.texture("uCurves", lutTex(null) , 2)
        }
        if (look != null && look.amount > 0f) {
            p.texture("uLut", lutTex(look.look), 1)
            p.set("uLutSize", LUT_SIZE.toFloat())
            p.set("uLutAmount", look.amount)
        } else {
            p.texture("uLut", lutTex(null), 1)
            p.set("uLutSize", LUT_SIZE.toFloat())
            p.set("uLutAmount", 0f)
        }
        p.draw()
    }

    /** Look LUT texture; with null returns a 2x2 dummy so samplers are always bound. */
    private fun lutTex(look: LookDef?): Int {
        val key = look?.id ?: "dummy"
        lutTexCache[key]?.let { return it }
        val tex = GlCache.texture("lut:$key") {
            if (look == null) Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            else (if (look.index < 0) UserLuts.pixels(look.id, LUT_SIZE) else NativeCore.renderLut(look.index, LUT_SIZE))?.let { px ->
                Bitmap.createBitmap(px, LUT_SIZE, LUT_SIZE * LUT_SIZE, Bitmap.Config.ARGB_8888)
            }
        }?.id ?: 0
        lutTexCache[key] = tex
        return tex
    }

    private fun shaderPass(use: ShaderUse, src: Int, w: Int, h: Int, timeSec: Float, durSec: Float) {
        val p = prog("fx:${use.def.id}") { catalog.singleSource(use.def) } ?: return passthrough(src)
        p.use()
        p.texture("uTex", src, 0)
        p.set("uResolution", w.toFloat(), h.toFloat())
        p.set("uTime", timeSec)
        p.set("uDuration", durSec)
        p.set("uProgress", if (durSec > 0f) (timeSec / durSec).coerceIn(0f, 1f) else 0f)
        p.set("uIntensity", use.intensity)
        p.set("uSeed", (use.def.id.hashCode() and 0xff) / 255f)
        for (param in use.def.params) {
            val v = use.params[param.name] ?: param.default
            p.set(param.name, *v.toFloatArray())
        }
        p.draw()
    }

    // ------------------------------------------------------------------ canvas

    private fun canvasPass(src: Int, local: Long) {
        val p = prog("internal:canvas") { catalog.internalSource("canvas") } ?: return passthrough(src)
        val t = interpolate(plan.baseTransform, plan.keyframes, local)
        p.use()
        p.texture("uTex", src, 0)
        p.set("uResolution", plan.canvasWidth.toFloat(), plan.canvasHeight.toFloat())
        p.set("uCanvasAspect", plan.canvasWidth.toFloat() / plan.canvasHeight)
        p.set("uContentAspect", plan.contentAspect)
        p.set("uFill", if (plan.fit == com.reel.editor.model.Fit.FILL) 1f else 0f)
        // Model y grows downward (screen); GL grows upward.
        p.set("uCenter", t.x, 1f - t.y)
        p.set("uScale", t.scale)
        p.set("uRotation", Math.toRadians(t.rotation.toDouble()).toFloat())
        p.set("uFlip", if (plan.flipH) -1f else 1f, if (plan.flipV) -1f else 1f)
        val cr = plan.crop
        p.set("uCrop", cr.x, 1f - cr.y - cr.h, cr.w, cr.h)
        val m = plan.mask
        p.set("uCmShape", if (m.shape == com.reel.editor.model.MaskShape.NONE) 0f else m.shape.ordinal.toFloat())
        p.set("uCmCenter", m.x, 1f - m.y)
        p.set("uCmSize", m.w, m.h)
        p.set("uCmRot", Math.toRadians(m.rotation.toDouble()).toFloat())
        p.set("uCmFeather", m.feather)
        p.set("uCmInvert", if (m.invert) 1f else 0f)
        p.set("uOpacity", if (plan.hideVideo) 0f else if (plan.ghostVideo) t.opacity * RenderPlans.GHOST else t.opacity)
        val bg = plan.background
        p.set("uBgMode", if (bg.mode == BackgroundMode.BLUR && !plan.hideVideo) 1f else 0f)
        if (plan.hideVideo) p.set("uBgColor", 0f, 0f, 0f)
        else p.set("uBgColor", ((bg.color shr 16) and 0xff) / 255f, ((bg.color shr 8) and 0xff) / 255f, (bg.color and 0xff) / 255f)
        p.set("uBlur", bg.blur)
        val k = plan.chroma
        p.set("uKeyOn", if (k.enabled) 1f else 0f)
        p.set("uKeyColor", ((k.color shr 16) and 0xff) / 255f, ((k.color shr 8) and 0xff) / 255f, (k.color and 0xff) / 255f)
        p.set("uKeySimilarity", k.similarity)
        p.set("uKeySmoothness", k.smoothness)
        p.set("uKeySpill", k.spill)
        val cut = plan.cutout
        if (cut.enabled && maskTex != 0) {
            p.texture("uMask", maskTex, 1)
            p.set("uMaskOn", 1f)
            p.set("uMaskEdge", cut.edge)
            val img = if (cut.mode == com.reel.editor.model.CutoutMode.IMAGE) cut.imageUri?.let { uri ->
                GlCache.texture("cutbg:$uri:${plan.canvasHeight}") { LayerBitmaps.sticker(context, uri, plan.canvasHeight)?.let { flipForGl(it) } }
            } else null
            val mode = when {
                cut.mode == com.reel.editor.model.CutoutMode.COLOR -> 0f
                cut.mode == com.reel.editor.model.CutoutMode.BLUR -> 1f
                img != null -> 2f
                else -> 0f
            }
            p.set("uCutMode", mode)
            p.set("uCutColor", ((cut.color shr 16) and 0xff) / 255f, ((cut.color shr 8) and 0xff) / 255f, (cut.color and 0xff) / 255f)
            p.set("uCutBlur", cut.blur)
            p.texture("uCutImage", img?.id ?: maskTex, 2)
            p.set("uCutImageAspect", img?.let { it.width.toFloat() / it.height } ?: 1f)
        } else {
            p.set("uMaskOn", 0f)
            // Keep the unused samplers on valid textures.
            p.texture("uMask", src, 1)
            p.texture("uCutImage", src, 2)
        }
        p.draw()
    }

    /** Runs person segmentation on a small copy of the input frame and uploads the mask. */
    private fun updateMask(inputTexId: Int, pts: Long) {
        val landscape = plan.contentAspect > 1.3f
        val seg = try {
            Segmenter.forThread(context, landscape)
        } catch (e: Exception) {
            logFx("segmenter unavailable", e)
            return
        }
        if (seg.width != segW || seg.height != segH || segTex == 0) {
            releaseMask()
            segW = seg.width
            segH = seg.height
            segTex = GlTex.empty(segW, segH)
            segFb = GlTex.framebuffer(segTex)
            maskTex = lumaTexture(segW, segH)
            segRead = java.nio.ByteBuffer.allocateDirect(segW * segH * 4).order(java.nio.ByteOrder.nativeOrder())
            maskBytes = ByteArray(segW * segH)
            maskBuf = java.nio.ByteBuffer.allocateDirect(segW * segH)
        }
        val read = segRead ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, segFb)
        GLES20.glViewport(0, 0, segW, segH)
        passthrough(inputTexId)
        read.rewind()
        GLES20.glReadPixels(0, 0, segW, segH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, read)
        read.rewind()
        seg.run(read, plan.clip.id, pts, maskBytes)
        val mb = maskBuf ?: return
        mb.rewind()
        mb.put(maskBytes)
        mb.rewind()
        val prev = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, segW, segH, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mb)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prev)
    }

    private fun lumaTexture(w: Int, h: Int): Int {
        val ids = IntArray(1)
        val prev = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, it, 0) }[0]
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prev)
        return ids[0]
    }

    private fun releaseMask() {
        if (segFb != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(segFb), 0)
        GlTex.delete(segTex)
        GlTex.delete(maskTex)
        segFb = 0; segTex = 0; maskTex = 0
    }

    // ------------------------------------------------------------------ post-canvas

    private fun buildClipPasses(out: MutableList<Pass>, local: Long) {
        // The hidden region moves with the picture, so it comes before animations and transitions.
        plan.privacy?.let { pr -> out += Pass({ src, _, _ -> privacyPass(pr, src, local) }, true) }
        plan.animIn?.let { (def, ref) ->
            val d = min(ref.durationUs, plan.clipDurationUs / 2)
            if (local < d && d > 0) out += Pass({ src, w, h -> animPass(def, src, w, h, smooth(local.toFloat() / d), local) }, true)
        }
        plan.animOut?.let { (def, ref) ->
            val d = min(ref.durationUs, plan.clipDurationUs / 2)
            val left = plan.clipDurationUs - local
            if (left < d && d > 0) out += Pass({ src, w, h -> animPass(def, src, w, h, smooth(left.toFloat() / d), local) }, true)
        }
        plan.transition?.let { tr ->
            if (local < tr.durationUs) out += Pass({ src, _, _ -> transitionPass(tr, src, local.toFloat() / tr.durationUs) }, true)
        }
    }

    private fun privacyPass(pr: com.reel.editor.model.Privacy, src: Int, local: Long) {
        val p = prog("internal:privacy") { catalog.internalSource("privacy") } ?: return passthrough(src)
        val (cx, cy, h) = if (pr.keys.isEmpty()) Triple(pr.x, pr.y, pr.size)
            else interpolate(com.reel.editor.model.Transform(pr.x, pr.y, pr.size), pr.keys, local).let { Triple(it.x, it.y, it.scale * pr.grow) }
        p.use()
        p.texture("uTex", src, 0)
        p.set("uCanvasAspect", plan.canvasWidth.toFloat() / plan.canvasHeight)
        p.set("uCenter", cx, 1f - cy)
        // Faces are a little taller than wide.
        p.set("uSize", h * 0.8f, h)
        p.set("uMode", pr.mode.ordinal.toFloat())
        p.set("uStrength", pr.strength)
        p.draw()
    }

    private fun buildGlobalPasses(out: MutableList<Pass>, local: Long, global: Long) {
        for (o in plan.overlays) if (global >= o.startUs && global < o.endUs) {
            out += Pass({ src, _, _ ->
                val target = currentTarget
                if (!overlays.draw(o, global, src, plan.canvasWidth, plan.canvasHeight, target, if (o.id in plan.ghostOverlays) RenderPlans.GHOST else 1f)) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target)
                    GLES20.glViewport(0, 0, plan.canvasWidth, plan.canvasHeight)
                    passthrough(src)
                }
            }, true)
        }
        for (l in plan.timedLooks) if (global in l.startUs until l.endUs) {
            out += Pass({ src, w, h -> gradePass(src, w, h, local, Adjustments(), l) }, true)
        }
        for (f in plan.timedFilters) if (global in f.startUs until f.endUs) {
            out += Pass({ src, w, h -> shaderPass(f, src, w, h, (global - f.startUs) / 1e6f, (f.endUs - f.startUs) / 1e6f) }, true)
        }
        for (a in plan.adjustLayers) if (global >= a.startUs && global < a.endUs) {
            val al = global - a.startUs
            val k = interpolateValue(a.layer.amount, a.layer.amountKeys, al).coerceIn(0f, 1f)
            if (k <= 0.001f) continue
            val look = a.look?.let { LookUse(it, a.layer.lookAmount * k, a.startUs, a.endUs) }
            val adj = a.layer.adjust.scaled(k)
            if (!adj.isIdentity || look != null) out += Pass({ src, w, h -> gradePass(src, w, h, al, adj, look) }, true)
            for (e in a.effects) out += Pass({ src, w, h -> shaderPass(e.copy(intensity = e.intensity * k), src, w, h, al / 1e6f, (a.endUs - a.startUs) / 1e6f) }, true)
        }
        for (e in plan.timedEffects) if (global in e.startUs until e.endUs) {
            out += Pass({ src, w, h -> shaderPass(e, src, w, h, (global - e.startUs) / 1e6f, (e.endUs - e.startUs) / 1e6f) }, true)
        }
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    private fun animPass(def: FxDef, src: Int, w: Int, h: Int, visibility: Float, local: Long) {
        val p = prog("fx:${def.id}") { catalog.singleSource(def) } ?: return passthrough(src)
        p.use()
        p.texture("uTex", src, 0)
        p.set("uResolution", w.toFloat(), h.toFloat())
        p.set("uTime", local / 1e6f)
        p.set("uProgress", visibility)
        p.set("uIntensity", 1f)
        p.draw()
    }

    private fun transitionPass(tr: TransitionUse, src: Int, progress: Float) {
        val from = GlCache.frame(tr.fromClipId)
        if (from == null) {
            // Scrubbed straight into the transition: no tail frame captured yet, show the clip.
            passthrough(src)
            return
        }
        val p = prog("tr:${tr.def.id}") { catalog.transitionSource(tr.def) } ?: return passthrough(src)
        p.use()
        p.texture("uFrom", from.id, 0)
        p.texture("uTo", src, 1)
        p.set("progress", progress.coerceIn(0f, 1f))
        p.set("ratio", plan.canvasWidth.toFloat() / plan.canvasHeight)
        for (param in tr.def.params) {
            val v = tr.params[param.name] ?: param.default
            p.set(param.name, *v.toFloatArray())
        }
        p.draw()
    }

    // ------------------------------------------------------------------ layers

    private fun activeLayers(global: Long) = plan.layers.filter { global >= it.startUs && global < it.endUs }

    private fun drawLayers(layers: List<LayerUse>, global: Long) {
        val p = prog("internal:overlay") { catalog.internalSource("overlay") } ?: return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val aspect = plan.canvasWidth.toFloat() / plan.canvasHeight
        for (layer in layers) {
            val local = global - layer.startUs
            val dur = layer.endUs - layer.startUs
            val text = layer.text
            val sticker = layer.sticker
            val shape = layer.shape
            val (base, keys, motion) = when {
                text != null -> Triple(text.transform, text.keyframes, TextMotion.at(text.animIn, text.animOut, text.loop, local, dur))
                sticker != null -> Triple(sticker.transform, sticker.keyframes, TextMotion.at(sticker.animIn, sticker.animOut, null, local, dur))
                shape != null -> Triple(shape.transform, shape.keyframes, TextMotion.at(shape.animIn, shape.animOut, null, local, dur))
                else -> continue
            }
            if (motion.opacity <= 0.001f) continue
            val tex = layerTexture(layer, motion.reveal, local, motion.letters, motion.letterMode.takeIf { motion.letters < 1f }) ?: continue
            val t = interpolate(base, keys, local)
            // Text keeps its rendered size (scale 1 = authored size); stickers scale relative to canvas height.
            val px = if (text != null) tex.height.toFloat() / plan.canvasHeight else 1f
            val heightUnits = if (text != null) px * t.scale * motion.scale else t.scale * motion.scale
            val widthUnits = heightUnits * tex.width / tex.height
            var cx = t.x + motion.dx
            var cy = t.y + motion.dy
            if (text?.isCaption == true) {
                // Captions always stay inside the frame's safe area, whatever their length or position.
                val hx = widthUnits / aspect / 2f
                val hy = heightUnits / 2f
                cx = if (hx >= 0.46f) 0.5f else cx.coerceIn(0.04f + hx, 0.96f - hx)
                cy = if (hy >= 0.44f) 0.5f else cy.coerceIn(0.06f + hy, 0.94f - hy)
            }
            p.use()
            p.texture("uLayer", tex.id, 0)
            p.set("uCanvasAspect", aspect)
            p.set("uCenter", cx, 1f - cy)
            p.set("uSize", widthUnits * motion.sx, heightUnits * motion.sy)
            p.set("uRotation", Math.toRadians((t.rotation + motion.rotationDeg).toDouble()).toFloat())
            p.set("uOpacity", (t.opacity * motion.opacity * (if (layer.ghost) RenderPlans.GHOST else 1f)).coerceIn(0f, 1f))
            p.draw()
        }
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun layerTexture(layer: LayerUse, reveal: Float, local: Long, letters: Float = 1f, letterMode: String? = null): GlCache.Tex? {
        val text = layer.text
        if (text != null) {
            val chars = if (reveal >= 1f) -1 else (text.text.length * reveal).toInt()
            val word = if (text.words.isEmpty()) -1 else text.words.indexOfFirst { local >= it.startUs && local < it.endUs }
            // Letter-by-letter animations redraw the text in 40 steps.
            val lq = if (letterMode == null) 40 else (letters.coerceIn(0f, 1f) * 40).toInt()
            val key = "text:${text.hashCode()}:$chars:$word:$lq:$letterMode:${plan.canvasWidth}x${plan.canvasHeight}"
            return GlCache.texture(key) { LayerBitmaps.text(text, chars, word, plan.canvasHeight, plan.canvasWidth, lq / 40f, letterMode)?.let { flipForGl(it) } }
        }
        layer.shape?.let { sh ->
            return GlCache.texture("shape:${sh.copy(transform = com.reel.editor.model.Transform(), keyframes = emptyList(), startUs = 0, durationUs = 0).hashCode()}:${plan.canvasHeight}") {
                LayerBitmaps.shapeLayer(sh, plan.canvasHeight)
            }
        }
        val s = layer.sticker ?: return null
        return GlCache.texture("sticker:${s.source}:${plan.canvasHeight}") {
            LayerBitmaps.sticker(context, s.source, plan.canvasHeight)?.let { flipForGl(it) }
        }
    }

    /** Overlay shader flips v itself; keep bitmaps as decoded. */
    private fun flipForGl(b: Bitmap): Bitmap = b

    override fun release() {
        super.release()
        releaseMask()
        if (plan.overlays.isNotEmpty()) overlays.release()
        for (i in 0..1) {
            if (workFb[i] != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(workFb[i]), 0)
            if (canvasFb[i] != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(canvasFb[i]), 0)
            GlTex.delete(workTex[i])
            GlTex.delete(canvasTex[i])
        }
        GlTex.delete(curveTex)
    }

    companion object {
        const val LUT_SIZE = 33
    }
}

/** First and last presentation time each clip renderer saw; used by device tests and diagnostics. */
object RenderDebug {
    val firstPts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val lastPts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val frames = java.util.concurrent.atomic.AtomicInteger()

    fun record(clipId: String, pts: Long) {
        firstPts.putIfAbsent(clipId, pts)
        lastPts[clipId] = pts
        frames.incrementAndGet()
    }

    fun reset() { firstPts.clear(); lastPts.clear(); frames.set(0) }
}

/** Tone curves rendered to a 256x2 texture: row 0 holds R, G, B curves, row 1 the master curve. */
object CurveLut {
    fun bitmap(c: Curves): Bitmap {
        val r = sample(c.red)
        val g = sample(c.green)
        val b = sample(c.blue)
        val m = sample(c.master)
        // Opaque pixels only: alpha must stay 255 so premultiplication never alters the data.
        val px = IntArray(512) { i ->
            if (i < 256) (0xFF shl 24) or (r[i] shl 16) or (g[i] shl 8) or b[i]
            else { val v = m[i - 256]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        }
        return Bitmap.createBitmap(px, 256, 2, Bitmap.Config.ARGB_8888)
    }

    /** Monotone cubic (Fritsch-Carlson) through the control points, sampled at 256 levels. */
    fun sample(points: List<com.reel.editor.model.Pt>): IntArray {
        val pts = points.sortedBy { it.x }.ifEmpty { Curves.IDENTITY }
        val n = pts.size
        val out = IntArray(256)
        if (n == 1) { out.fill((pts[0].y * 255).roundToInt().coerceIn(0, 255)); return out }
        val dx = FloatArray(n - 1) { (pts[it + 1].x - pts[it].x).coerceAtLeast(1e-4f) }
        val slope = FloatArray(n - 1) { (pts[it + 1].y - pts[it].y) / dx[it] }
        val m = FloatArray(n)
        m[0] = slope[0]; m[n - 1] = slope[n - 2]
        for (i in 1 until n - 1) m[i] = if (slope[i - 1] * slope[i] <= 0) 0f else (slope[i - 1] + slope[i]) / 2
        for (i in 0 until n - 1) {
            if (slope[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / slope[i]
            val b = m[i + 1] / slope[i]
            val s = a * a + b * b
            if (s > 9) { val t = 3 / kotlin.math.sqrt(s); m[i] = t * a * slope[i]; m[i + 1] = t * b * slope[i] }
        }
        for (v in 0 until 256) {
            val x = v / 255f
            val y = when {
                x <= pts[0].x -> pts[0].y
                x >= pts[n - 1].x -> pts[n - 1].y
                else -> {
                    var i = 0
                    while (i < n - 2 && x > pts[i + 1].x) i++
                    val h = dx[i]
                    val t = (x - pts[i].x) / h
                    val t2 = t * t
                    val t3 = t2 * t
                    (2 * t3 - 3 * t2 + 1) * pts[i].y + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * pts[i + 1].y + (t3 - t2) * h * m[i + 1]
                }
            }
            out[v] = (y * 255).roundToInt().coerceIn(0, 255)
        }
        return out
    }
}

@Suppress("unused")
private fun Bitmap.flippedVertically(): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { preScale(1f, -1f) }, false)
