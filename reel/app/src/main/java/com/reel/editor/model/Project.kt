package com.reel.editor.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/** All times in the model are microseconds. Clip times on the source are "media" time;
 *  positions on the timeline are "timeline" time (after speed). */
@Serializable
data class Project(
    val id: String = newId(),
    val name: String = "Untitled",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val canvas: CanvasSettings = CanvasSettings(),
    val clips: List<Clip> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val stickers: List<StickerLayer> = emptyList(),
    val effects: List<TimedFx> = emptyList(),
    val filters: List<TimedFilter> = emptyList(),
    val audio: List<AudioItem> = emptyList(),
    val playheadUs: Long = 0,
    val shapes: List<ShapeLayer> = emptyList(),
    val adjustments: List<AdjustmentLayer> = emptyList(),
    val overlays: List<OverlayClip> = emptyList(),
    val tracks: TrackFlags = TrackFlags(),
)

/**
 * Switches on the timeline's left column. Track keys: "video", "effects", "text", "stickers",
 * "overlays". Hidden tracks are left out of the preview and the export; locked ones can't be moved.
 */
@Serializable
data class TrackFlags(
    val hidden: Set<String> = emptySet(),
    val muted: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
) {
    fun isHidden(k: String) = k in hidden
    fun isMuted(k: String) = k in muted
    fun isLocked(k: String) = k in locked
    fun toggle(kind: Kind, k: String): TrackFlags = when (kind) {
        Kind.HIDE -> copy(hidden = if (k in hidden) hidden - k else hidden + k)
        Kind.MUTE -> copy(muted = if (k in muted) muted - k else muted + k)
        Kind.LOCK -> copy(locked = if (k in locked) locked - k else locked + k)
    }
    enum class Kind { HIDE, MUTE, LOCK }

    companion object {
        const val VIDEO = "video"
        const val EFFECTS = "effects"
        const val TEXT = "text"
        const val STICKERS = "stickers"
        const val OVERLAYS = "overlays"
        const val AUDIO = "audio"
    }
}

@Serializable
data class CanvasSettings(
    val ratio: AspectRatio = AspectRatio.R9_16,
    val background: Background = Background(),
)

@Serializable
enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    ORIGINAL("Original", 0, 0),
    R9_16("9:16", 9, 16),
    R1_1("1:1", 1, 1),
    R4_5("4:5", 4, 5),
    R16_9("16:9", 16, 9),
    R3_4("3:4", 3, 4),
    R21_9("2.35:1", 47, 20);
}

@Serializable
data class Background(
    val mode: BackgroundMode = BackgroundMode.BLUR,
    val color: Long = 0xFF000000,
    val blur: Float = 0.6f,
)

@Serializable
enum class BackgroundMode { COLOR, BLUR }

@Serializable
enum class ClipKind { VIDEO, PHOTO }

@Serializable
data class Clip(
    val id: String = newId(),
    val uri: String,
    val kind: ClipKind,
    /** Source duration (for photos: the chosen display length at 1x). */
    val sourceDurationUs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int = 0,
    val hasAudio: Boolean = true,
    val trimStartUs: Long = 0,
    val trimEndUs: Long = sourceDurationUs,
    val speed: Speed = Speed(),
    val volume: Float = 1f,
    val fadeInUs: Long = 0,
    val fadeOutUs: Long = 0,
    val muted: Boolean = false,
    val keepPitch: Boolean = true,
    val fit: Fit = Fit.FIT,
    val transform: Transform = Transform(),
    val keyframes: List<Keyframe> = emptyList(),
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Visible part of the source frame, as fractions with a top-left origin. */
    val crop: CropRect = CropRect(),
    /** Set while the clip plays a reversed copy: what to go back to. */
    val reversedFrom: ReverseSource? = null,
    /** A blurred or pixelated region (usually a face), fixed or following tracked keyframes. */
    val privacy: Privacy? = null,
    val adjust: Adjustments = Adjustments(),
    val lookId: String? = null,
    val lookAmount: Float = 1f,
    val filter: FxRef? = null,
    val clipEffects: List<FxRef> = emptyList(),
    val animIn: AnimRef? = null,
    val animOut: AnimRef? = null,
    /** Transition into this clip from the previous one. Ignored on the first clip. */
    val transitionIn: TransitionRef? = null,
    val chroma: ChromaKey = ChromaKey(),
    val cutout: Cutout = Cutout(),
    val voice: VoiceFx = VoiceFx(),
    /** Volume keyframes, offsets from the clip start on the timeline. */
    val volumeKeys: List<ValueKey> = emptyList(),
    /** Look strength keyframes. */
    val lookKeys: List<ValueKey> = emptyList(),
    val mask: Mask = Mask(),
) {
    val trimmedDurationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0)
}

@Serializable
enum class Fit { FIT, FILL }

@Serializable
data class ReverseSource(val uri: String, val sourceDurationUs: Long, val trimStartUs: Long, val trimEndUs: Long, val hasAudio: Boolean)

@Serializable
enum class PrivacyMode { BLUR, PIXELATE, SOLID }

/**
 * An oval hidden on the canvas. Without [keys] it sits at [x], [y] with height [size] (canvas
 * fractions); with keys (from tracking, offsets from the clip start) each key's transform holds
 * the centre in x, y and the height in scale. [grow] enlarges the tracked oval.
 */
@Serializable
data class Privacy(
    val mode: PrivacyMode = PrivacyMode.BLUR,
    val strength: Float = 0.6f,
    val x: Float = 0.5f,
    val y: Float = 0.4f,
    val size: Float = 0.25f,
    val grow: Float = 1.3f,
    val keys: List<Keyframe> = emptyList(),
)

@Serializable
data class CropRect(val x: Float = 0f, val y: Float = 0f, val w: Float = 1f, val h: Float = 1f) {
    val isFull: Boolean get() = x <= 0f && y <= 0f && w >= 1f && h >= 1f

    companion object {
        /** The biggest centred crop of [ratio] (w/h) from a source of [srcAspect]. */
        fun forRatio(ratio: Float, srcAspect: Float): CropRect =
            if (ratio <= 0f) CropRect()
            else if (ratio < srcAspect) { val w = ratio / srcAspect; CropRect((1 - w) / 2, 0f, w, 1f) }
            else { val h = srcAspect / ratio; CropRect(0f, (1 - h) / 2, 1f, h) }
    }

    /** Zooms around the centre of this crop by [k] (>= 1), keeping it inside the frame. */
    fun zoomed(k: Float): CropRect {
        val nw = (w / k).coerceIn(0.05f, 1f)
        val nh = (h / k).coerceIn(0.05f, 1f)
        val cx = x + w / 2; val cy = y + h / 2
        return CropRect((cx - nw / 2).coerceIn(0f, 1f - nw), (cy - nh / 2).coerceIn(0f, 1f - nh), nw, nh)
    }

    /** Moves the crop to centre ([cx], [cy]), kept inside the frame. */
    fun centredAt(cx: Float, cy: Float) = copy(x = (cx - w / 2).coerceIn(0f, 1f - w), y = (cy - h / 2).coerceIn(0f, 1f - h))
}

@Serializable
data class Transform(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)

@Serializable
data class Keyframe(
    /** Offset from the clip's start on the timeline. */
    val timeUs: Long,
    val transform: Transform,
    val easing: Easing = Easing.EASE_IN_OUT,
)

@Serializable
data class Easing(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val name: String = "Custom") {
    companion object {
        val LINEAR = Easing(0f, 0f, 1f, 1f, "Linear")
        val EASE_IN = Easing(0.42f, 0f, 1f, 1f, "Ease in")
        val EASE_OUT = Easing(0f, 0f, 0.58f, 1f, "Ease out")
        val EASE_IN_OUT = Easing(0.42f, 0f, 0.58f, 1f, "Ease in out")
        val BACK = Easing(0.34f, 1.56f, 0.64f, 1f, "Overshoot")
        /** Not bezier curves: [ease] special-cases these names. */
        val BOUNCE = Easing(0.3f, 0f, 0.7f, 1f, "Bounce")
        val ELASTIC = Easing(0.3f, 0f, 0.7f, 1f, "Elastic")
        val PRESETS = listOf(LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK, BOUNCE, ELASTIC)
    }
}

@Serializable
data class Speed(
    val constant: Float = 1f,
    /** Optional curve: speed multipliers sampled at evenly spaced points over the clip. */
    val curve: List<Float>? = null,
    val curveName: String? = null,
)

@Serializable
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val hue: Float = 0f,
    val vignette: Float = 0f,
    val sharpen: Float = 0f,
    val grain: Float = 0f,
    val curves: Curves = Curves(),
    /** Per-colour changes (HSL), one entry per [Hsl.BANDS] colour. */
    val hsl: Hsl = Hsl(),
) {
    val isIdentity: Boolean get() = this == Adjustments() || (copy(curves = Curves()) == Adjustments() && curves.isIdentity)

    /** Every slider multiplied by [k] (curves stay as they are). */
    fun scaled(k: Float): Adjustments = if (k >= 0.999f) this else copy(
        brightness = brightness * k, contrast = contrast * k, saturation = saturation * k, vibrance = vibrance * k,
        exposure = exposure * k, temperature = temperature * k, tint = tint * k, highlights = highlights * k,
        shadows = shadows * k, fade = fade * k, hue = hue * k, vignette = vignette * k, sharpen = sharpen * k, grain = grain * k,
        hsl = Hsl(hsl.hue.map { it * k }, hsl.sat.map { it * k }, hsl.lum.map { it * k }),
    )
}

/** Hue shift, saturation and lightness per colour range, each -1..1. */
@Serializable
data class Hsl(
    val hue: List<Float> = List(8) { 0f },
    val sat: List<Float> = List(8) { 0f },
    val lum: List<Float> = List(8) { 0f },
) {
    val isIdentity: Boolean get() = hue.all { it == 0f } && sat.all { it == 0f } && lum.all { it == 0f }

    fun with(band: Int, h: Float? = null, s: Float? = null, l: Float? = null) = Hsl(
        hue.mapIndexed { i, v -> if (i == band && h != null) h else v },
        sat.mapIndexed { i, v -> if (i == band && s != null) s else v },
        lum.mapIndexed { i, v -> if (i == band && l != null) l else v },
    )

    companion object {
        /** Band names and their centre hues in degrees. */
        val BANDS = listOf("Red" to 0f, "Orange" to 30f, "Yellow" to 60f, "Green" to 120f, "Aqua" to 180f, "Blue" to 225f, "Purple" to 270f, "Magenta" to 315f)
    }
}

/** Tone curves as control points (x,y in 0..1) per channel. */
@Serializable
data class Curves(
    val master: List<Pt> = IDENTITY,
    val red: List<Pt> = IDENTITY,
    val green: List<Pt> = IDENTITY,
    val blue: List<Pt> = IDENTITY,
) {
    val isIdentity: Boolean get() = listOf(master, red, green, blue).all { it == IDENTITY }

    companion object {
        val IDENTITY = listOf(Pt(0f, 0f), Pt(1f, 1f))
    }
}

@Serializable
data class Pt(val x: Float, val y: Float)

@Serializable
data class FxRef(
    val id: String,
    val intensity: Float = 1f,
    val params: Map<String, List<Float>> = emptyMap(),
)

@Serializable
data class AnimRef(val id: String, val durationUs: Long = 500_000)

@Serializable
data class TransitionRef(val id: String, val durationUs: Long = 500_000, val params: Map<String, List<Float>> = emptyMap())

@Serializable
data class ChromaKey(
    val enabled: Boolean = false,
    val color: Long = 0xFF2BD46A,
    val similarity: Float = 0.6f,
    val smoothness: Float = 0.2f,
    val spill: Float = 0.3f,
)

/** A vector shape drawn above the video. [aspect] is width / height of its box. */
@Serializable
data class ShapeLayer(
    val id: String = newId(),
    val shape: String = "roundrect",
    val fill: Long? = 0xFF4C8DFF,
    val stroke: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 0f,
    /** Corner radius for rectangles, as a fraction of the shorter side (0..0.5). */
    val corner: Float = 0.18f,
    val aspect: Float = 1f,
    val startUs: Long,
    val durationUs: Long,
    val transform: Transform = Transform(scale = 0.4f),
    val keyframes: List<Keyframe> = emptyList(),
    val animIn: TextAnim? = null,
    val animOut: TextAnim? = null,
)

/** Applies adjustments, a look and effects to everything below it while it is on the timeline. */
@Serializable
data class AdjustmentLayer(
    val id: String = newId(),
    val startUs: Long,
    val durationUs: Long,
    val adjust: Adjustments = Adjustments(),
    val lookId: String? = null,
    val lookAmount: Float = 1f,
    val effects: List<FxRef> = emptyList(),
    /** Overall strength, 0..1; keyframable through [amountKeys]. */
    val amount: Float = 1f,
    val amountKeys: List<ValueKey> = emptyList(),
)

@Serializable
enum class BlendMode(val label: String) {
    NORMAL("Normal"), SCREEN("Screen"), MULTIPLY("Multiply"), OVERLAY("Overlay"), LIGHTEN("Lighten"),
    DARKEN("Darken"), ADD("Add"), SOFT_LIGHT("Soft light"), DIFFERENCE("Difference"),
}

@Serializable
enum class MaskShape { NONE, CIRCLE, RECT, LINEAR, HEART, STAR }

/** A mask in the layer's own space: centre, size (fractions of the layer), rotation, feather. */
@Serializable
data class Mask(
    val shape: MaskShape = MaskShape.NONE,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val w: Float = 0.7f,
    val h: Float = 0.7f,
    val rotation: Float = 0f,
    val feather: Float = 0.15f,
    val invert: Boolean = false,
)

/** A video or photo placed over the main video (picture in picture). */
@Serializable
data class OverlayClip(
    val id: String = newId(),
    val uri: String,
    val kind: ClipKind,
    val sourceDurationUs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int = 0,
    val hasAudio: Boolean = false,
    val startUs: Long,
    val trimStartUs: Long = 0,
    val trimEndUs: Long = sourceDurationUs,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val transform: Transform = Transform(scale = 0.5f),
    val keyframes: List<Keyframe> = emptyList(),
    val blend: BlendMode = BlendMode.NORMAL,
    val mask: Mask = Mask(),
    val chroma: ChromaKey = ChromaKey(),
    val adjust: Adjustments = Adjustments(),
    val lookId: String? = null,
    val animIn: TextAnim? = null,
    val animOut: TextAnim? = null,
    val volumeKeys: List<ValueKey> = emptyList(),
) {
    val timelineDurationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0)
    val endUs: Long get() = startUs + timelineDurationUs
}

/** A keyframed number (volume, strength...) at an offset from the item's start. */
@Serializable
data class ValueKey(val timeUs: Long, val value: Float, val easing: Easing = Easing.EASE_IN_OUT)

/** Voice tools for a clip's sound or an audio item. */
@Serializable
data class VoiceFx(
    /** Vocal enhancer amount, 0..1. */
    val enhance: Float = 0f,
    /** Noise reduction amount, 0..1. */
    val denoise: Float = 0f,
    /** Keep the speaking level steady. */
    val normalize: Boolean = false,
    /** Voice effect id from [VoiceEffects], or null. */
    val effect: String? = null,
) {
    val isIdentity: Boolean get() = enhance <= 0.001f && denoise <= 0.001f && !normalize && effect == null
}

/** Removes the background behind people and puts something else there. */
@Serializable
data class Cutout(
    val enabled: Boolean = false,
    val mode: CutoutMode = CutoutMode.BLUR,
    val color: Long = 0xFF14141C,
    val blur: Float = 0.6f,
    /** Content URI of a picture, for [CutoutMode.IMAGE]. */
    val imageUri: String? = null,
    /** Edge softness, 0 hard to 1 very soft. */
    val edge: Float = 0.35f,
)

@Serializable
enum class CutoutMode { COLOR, BLUR, IMAGE }

/** An effect bar on the effect track, independent of clip boundaries. */
@Serializable
data class TimedFx(
    val id: String = newId(),
    val fx: FxRef,
    val startUs: Long,
    val durationUs: Long,
)

@Serializable
data class TimedFilter(
    val id: String = newId(),
    val lookId: String? = null,
    val shaderFx: FxRef? = null,
    val amount: Float = 1f,
    val startUs: Long,
    val durationUs: Long,
)

@Serializable
data class TextStyle(
    val font: String = "sans-bold",
    val sizeSp: Float = 64f,
    val color: Long = 0xFFFFFFFF,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 0f,
    val shadow: Boolean = true,
    val labelColor: Long? = null,
    val align: TextAlignment = TextAlignment.CENTER,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** Second colour: the fill fades from [color] at the top to this at the bottom. */
    val gradient: Long? = null,
    /** Bends the text along an arc: positive curves up like a smile, negative like a rainbow. -1..1. */
    val curve: Float = 0f,
)

@Serializable
enum class TextAlignment { START, CENTER, END }

@Serializable
data class TextLayer(
    val id: String = newId(),
    val text: String,
    val startUs: Long,
    val durationUs: Long,
    val style: TextStyle = TextStyle(),
    val transform: Transform = Transform(y = 0.72f),
    val keyframes: List<Keyframe> = emptyList(),
    val animIn: TextAnim? = TextAnim("pop", 400_000),
    val animOut: TextAnim? = TextAnim("fade", 300_000),
    val loop: TextAnim? = null,
    val isCaption: Boolean = false,
    /** Word timings for karaoke highlight (captions), offsets from startUs. */
    val words: List<WordTiming> = emptyList(),
    /** Captions follow the speech they came from: the source clip and media range (absolute source time). */
    val anchorClipId: String? = null,
    val anchorUri: String? = null,
    val anchorMediaStartUs: Long = 0,
    val anchorMediaEndUs: Long = 0,
)

@Serializable
data class WordTiming(val text: String, val startUs: Long, val endUs: Long)

@Serializable
data class TextAnim(val id: String, val durationUs: Long)

@Serializable
data class StickerLayer(
    val id: String = newId(),
    /** Either a content URI of a user image or "shape:<name>" for a built-in vector sticker. */
    val source: String,
    val startUs: Long,
    val durationUs: Long,
    val transform: Transform = Transform(scale = 0.35f),
    val keyframes: List<Keyframe> = emptyList(),
    val animIn: TextAnim? = TextAnim("pop", 300_000),
    val animOut: TextAnim? = null,
)

@Serializable
data class AudioItem(
    val id: String = newId(),
    val uri: String,
    val title: String,
    val kind: AudioKind = AudioKind.MUSIC,
    val sourceDurationUs: Long,
    val startUs: Long,
    val trimStartUs: Long = 0,
    val trimEndUs: Long = sourceDurationUs,
    val volume: Float = 1f,
    val fadeInUs: Long = 0,
    val fadeOutUs: Long = 0,
    /** Beats found by analysis, in source time. Shown only when [autoBeats] is on. */
    val beatsUs: List<Long> = emptyList(),
    val autoBeats: Boolean = false,
    /** Show every Nth detected beat: 1 every beat, 2 every other, 4 once per bar. */
    val beatEvery: Int = 1,
    /** Markers placed by hand, in source time, so they move with the music. */
    val markersUs: List<Long> = emptyList(),
    val voice: VoiceFx = VoiceFx(),
    val volumeKeys: List<ValueKey> = emptyList(),
    val muted: Boolean = false,
    /** Lower this item while someone speaks (clip audio and voiceovers). */
    val duck: Boolean = false,
    val duckDb: Float = -14f,
) {
    val durationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0)
    val endUs: Long get() = startUs + durationUs
}

@Serializable
enum class AudioKind { MUSIC, VOICEOVER, SOUND }

@Serializable
data class ExportSettings(
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: BitrateLevel = BitrateLevel.RECOMMENDED,
    val hevc: Boolean = false,
    val format: ExportFormat = ExportFormat.VIDEO,
)

@Serializable
enum class ExportFormat { VIDEO, GIF, AUDIO }

@Serializable
enum class BitrateLevel(val factor: Float) { LOWER(0.6f), RECOMMENDED(1f), HIGHER(1.6f) }
