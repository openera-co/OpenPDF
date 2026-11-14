package com.reel.editor.fx

import com.reel.editor.model.Adjustments
import com.reel.editor.model.AnimRef
import com.reel.editor.model.Background
import com.reel.editor.model.BackgroundMode
import com.reel.editor.model.ChromaKey
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Fit
import com.reel.editor.model.FxRef
import com.reel.editor.model.Keyframe
import com.reel.editor.model.Placed
import com.reel.editor.model.Project
import com.reel.editor.model.StickerLayer
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.Transform

/** A shader with resolved parameter values, active over [startUs, endUs) on the timeline. */
data class ShaderUse(
    val def: FxDef,
    val intensity: Float,
    val params: Map<String, List<Float>>,
    val startUs: Long,
    val endUs: Long,
)

data class LookUse(val look: LookDef, val amount: Float, val startUs: Long, val endUs: Long)

data class TransitionUse(
    val def: TransitionDef,
    val durationUs: Long,
    val params: Map<String, List<Float>>,
    val fromClipId: String,
)

data class LayerUse(
    val id: String,
    val startUs: Long,
    val endUs: Long,
    val text: TextLayer? = null,
    val sticker: StickerLayer? = null,
    val shape: com.reel.editor.model.ShapeLayer? = null,
)

/** An adjustment layer resolved for rendering. */
data class AdjustUse(
    val layer: com.reel.editor.model.AdjustmentLayer,
    val look: LookDef?,
    val effects: List<ShaderUse>,
) {
    val startUs: Long get() = layer.startUs
    val endUs: Long get() = layer.startUs + layer.durationUs
}

/**
 * Everything one clip's renderer needs, resolved on the main thread so the GL thread never
 * touches mutable editor state.
 */
data class RenderPlan(
    val clip: Clip,
    val clipStartUs: Long,
    val clipDurationUs: Long,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val contentAspect: Float,
    val fit: Fit,
    val background: Background,
    val baseTransform: Transform,
    val keyframes: List<Keyframe>,
    val flipH: Boolean,
    val flipV: Boolean,
    /** The video track's eye is off: draw only the layers above it, on black. */
    val hideVideo: Boolean = false,
    val crop: com.reel.editor.model.CropRect = com.reel.editor.model.CropRect(),
    val mask: com.reel.editor.model.Mask = com.reel.editor.model.Mask(),
    val privacy: com.reel.editor.model.Privacy? = null,
    val chroma: ChromaKey,
    val cutout: com.reel.editor.model.Cutout,
    val adjust: Adjustments,
    val look: LookUse?,
    val clipFilter: ShaderUse?,
    val clipEffects: List<ShaderUse>,
    val timedLooks: List<LookUse>,
    val timedFilters: List<ShaderUse>,
    val timedEffects: List<ShaderUse>,
    val animIn: Pair<FxDef, AnimRef>?,
    val animOut: Pair<FxDef, AnimRef>?,
    val transition: TransitionUse?,
    val layers: List<LayerUse>,
    val adjustLayers: List<AdjustUse> = emptyList(),
    val overlays: List<com.reel.editor.model.OverlayClip> = emptyList(),
    /** Whether this clip's last frames should be kept for the next clip's transition. */
    val keepTail: Boolean,
    val timeOrigin: TimeOrigin,
) {
    val clipEndUs: Long get() = clipStartUs + clipDurationUs
}

/** How Media3 reports presentation times to item effects. Verified on device, see TimeOrigin docs. */
enum class TimeOrigin {
    /** Times count from the start of the whole composition. */
    COMPOSITION,
    /** Times count from the start of each media item. */
    ITEM,
}

object RenderPlans {
    @Volatile var timeOrigin: TimeOrigin = TimeOrigin.COMPOSITION

    fun canvasSize(project: Project, targetHeight: Int): Pair<Int, Int> {
        val ratio = project.canvas.ratio
        val (rw, rh) = if (ratio.w == 0) {
            val c = project.clips.firstOrNull()
            if (c == null) 9 to 16 else displaySize(c)
        } else ratio.w to ratio.h
        // The resolution names the short side (1080p 9:16 is 1080x1920). The other side is rounded
        // to a multiple of 8 for encoders, and the long side is capped at 4096.
        val short = targetHeight.coerceAtLeast(16)
        var w: Double
        var h: Double
        if (rw <= rh) { w = short.toDouble(); h = short.toDouble() * rh / rw } else { h = short.toDouble(); w = short.toDouble() * rw / rh }
        val long = maxOf(w, h)
        if (long > MAX_SIDE) { val k = MAX_SIDE / long; w *= k; h *= k }
        fun r8(v: Double) = (Math.round(v / 8.0) * 8).toInt().coerceAtLeast(16)
        return r8(w) to r8(h)
    }

    const val MAX_SIDE = 4096.0

    fun displaySize(c: Clip): Pair<Int, Int> =
        if (c.rotationDegrees % 180 != 0) c.sourceHeight to c.sourceWidth else c.sourceWidth to c.sourceHeight

    fun build(project: Project, catalog: FxCatalog, canvasW: Int, canvasH: Int): List<RenderPlan> {
        val placed = TimelineMath.place(project.clips)
        val shown = visible(project)
        return placed.map { p -> plan(shown, catalog, p, placed, canvasW, canvasH) }
    }

    /** The project as the eye switches on the timeline leave it: hidden tracks are dropped. */
    fun visible(project: Project): Project {
        val t = project.tracks
        if (t.hidden.isEmpty()) return project
        return project.copy(
            texts = if (t.isHidden(com.reel.editor.model.TrackFlags.TEXT)) emptyList() else project.texts,
            stickers = if (t.isHidden(com.reel.editor.model.TrackFlags.STICKERS)) emptyList() else project.stickers,
            shapes = if (t.isHidden(com.reel.editor.model.TrackFlags.STICKERS)) emptyList() else project.shapes,
            effects = if (t.isHidden(com.reel.editor.model.TrackFlags.EFFECTS)) emptyList() else project.effects,
            filters = if (t.isHidden(com.reel.editor.model.TrackFlags.EFFECTS)) emptyList() else project.filters,
            adjustments = if (t.isHidden(com.reel.editor.model.TrackFlags.EFFECTS)) emptyList() else project.adjustments,
            overlays = if (t.isHidden(com.reel.editor.model.TrackFlags.OVERLAYS)) emptyList() else project.overlays,
        )
    }

    private fun overlaps(s: Long, e: Long, a: Long, b: Long) = s < b && e > a

    private fun shader(catalog: FxCatalog, ref: FxRef?, start: Long, end: Long): ShaderUse? {
        ref ?: return null
        val def = catalog.fx(ref.id) ?: return null
        return ShaderUse(def, ref.intensity, ref.params, start, end)
    }

    private fun plan(project: Project, catalog: FxCatalog, p: Placed, all: List<Placed>, w: Int, h: Int): RenderPlan {
        val c = p.clip
        val (dw, dh) = displaySize(c)
        val next = all.getOrNull(p.index + 1)
        val prev = all.getOrNull(p.index - 1)
        val transition = if (prev != null) c.transitionIn?.let { t ->
            catalog.transition(t.id)?.let { TransitionUse(it, t.durationUs.coerceAtMost(p.durationUs / 2), t.params, prev.clip.id) }
        } else null
        val timedLooks = project.filters.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) && it.lookId != null }
            .mapNotNull { f -> catalog.look(f.lookId!!)?.let { LookUse(it, f.amount, f.startUs, f.startUs + f.durationUs) } }
        val timedFilters = project.filters.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) && it.shaderFx != null }
            .mapNotNull { f -> shader(catalog, f.shaderFx, f.startUs, f.startUs + f.durationUs)?.copy(intensity = f.amount * (f.shaderFx?.intensity ?: 1f)) }
        val timedEffects = project.effects.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) }
            .mapNotNull { shader(catalog, it.fx, it.startUs, it.startUs + it.durationUs) }
        val layers = (project.texts.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) }
            .map { LayerUse(it.id, it.startUs, it.startUs + it.durationUs, text = it) } +
            project.stickers.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) }
                .map { LayerUse(it.id, it.startUs, it.startUs + it.durationUs, sticker = it) } +
            project.shapes.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) }
                .map { LayerUse(it.id, it.startUs, it.startUs + it.durationUs, shape = it) })
            // Captions sit above every other layer.
            .sortedBy { it.text?.isCaption == true }
        val adjustLayers = project.adjustments.filter { overlaps(it.startUs, it.startUs + it.durationUs, p.startUs, p.endUs) }
            .map { a -> AdjustUse(a, a.lookId?.let(catalog::look), a.effects.mapNotNull { shader(catalog, it, a.startUs, a.startUs + a.durationUs) }) }
        val overlays = project.overlays.filter { overlaps(it.startUs, it.endUs, p.startUs, p.endUs) }
        return RenderPlan(
            clip = c,
            clipStartUs = p.startUs,
            clipDurationUs = p.durationUs,
            canvasWidth = w,
            canvasHeight = h,
            contentAspect = dw * c.crop.w / (dh.coerceAtLeast(1) * c.crop.h),
            fit = c.fit,
            background = if (c.kind == ClipKind.PHOTO && project.canvas.background.mode == BackgroundMode.BLUR) project.canvas.background else project.canvas.background,
            baseTransform = c.transform,
            keyframes = c.keyframes,
            flipH = c.flipH,
            hideVideo = project.tracks.isHidden(com.reel.editor.model.TrackFlags.VIDEO),
            crop = c.crop,
            mask = c.mask,
            privacy = c.privacy,
            flipV = c.flipV,
            chroma = c.chroma,
            cutout = c.cutout,
            adjust = c.adjust,
            look = c.lookId?.let { id -> catalog.look(id)?.let { LookUse(it, c.lookAmount, p.startUs, p.endUs) } },
            clipFilter = shader(catalog, c.filter, p.startUs, p.endUs),
            clipEffects = c.clipEffects.mapNotNull { shader(catalog, it, p.startUs, p.endUs) },
            timedLooks = timedLooks,
            timedFilters = timedFilters,
            timedEffects = timedEffects,
            animIn = c.animIn?.let { a -> catalog.fx(a.id)?.let { it to a } },
            animOut = c.animOut?.let { a -> catalog.fx(a.id)?.let { it to a } },
            transition = transition,
            layers = layers,
            adjustLayers = adjustLayers,
            overlays = overlays,
            keepTail = next?.clip?.transitionIn != null,
            timeOrigin = timeOrigin,
        )
    }
}
