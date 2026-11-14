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
)

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
) {
    val trimmedDurationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0)
}

@Serializable
enum class Fit { FIT, FILL }

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
        val PRESETS = listOf(LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK)
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
) {
    val isIdentity: Boolean get() = this == Adjustments() || (copy(curves = Curves()) == Adjustments() && curves.isIdentity)
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
)

@Serializable
enum class BitrateLevel(val factor: Float) { LOWER(0.6f), RECOMMENDED(1f), HIGHER(1.6f) }
