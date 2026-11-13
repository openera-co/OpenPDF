package com.reel.editor.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Minimum clip length after a trim or split, in timeline time. */
const val MIN_CLIP_US = 100_000L

/** Number of constant-speed segments a speed curve is rendered into for playback and export. */
const val SPEED_SEGMENTS = 48

object SpeedMath {
    /** Speed at a normalized position [0,1] of the clip's media range. */
    fun speedAt(speed: Speed, pos: Double): Double {
        val c = speed.curve
        if (c.isNullOrEmpty()) return speed.constant.toDouble().coerceIn(0.05, 100.0)
        if (c.size == 1) return c[0].toDouble().coerceIn(0.05, 100.0)
        val x = pos.coerceIn(0.0, 1.0) * (c.size - 1)
        val i = min(x.toInt(), c.size - 2)
        val f = x - i
        return (c[i] + (c[i + 1] - c[i]) * f).coerceIn(0.05, 100.0)
    }

    /** Piecewise-constant segments (media start offset, speed) used by Media3's SpeedProvider. */
    fun segments(speed: Speed, mediaDurationUs: Long): List<Pair<Long, Float>> {
        if (speed.curve.isNullOrEmpty()) return listOf(0L to speed.constant.coerceIn(0.05f, 100f))
        return (0 until SPEED_SEGMENTS).map { s ->
            val start = mediaDurationUs * s / SPEED_SEGMENTS
            val mid = (s + 0.5) / SPEED_SEGMENTS
            start to speedAt(speed, mid).toFloat()
        }
    }

    /** Timeline duration of a media range played with the clip's speed segments. */
    fun timelineDurationUs(speed: Speed, mediaDurationUs: Long): Long {
        val segs = segments(speed, mediaDurationUs)
        var total = 0.0
        for (i in segs.indices) {
            val start = segs[i].first
            val end = if (i + 1 < segs.size) segs[i + 1].first else mediaDurationUs
            total += (end - start) / segs[i].second.toDouble()
        }
        return total.roundToLong()
    }

    /** Media offset (from trim start) to timeline offset (from clip start). */
    fun mediaToTimeline(speed: Speed, mediaDurationUs: Long, mediaOffsetUs: Long): Long {
        val segs = segments(speed, mediaDurationUs)
        var acc = 0.0
        for (i in segs.indices) {
            val start = segs[i].first
            val end = if (i + 1 < segs.size) segs[i + 1].first else mediaDurationUs
            if (mediaOffsetUs <= end) return (acc + (mediaOffsetUs - start).coerceAtLeast(0) / segs[i].second).roundToLong()
            acc += (end - start) / segs[i].second.toDouble()
        }
        return acc.roundToLong()
    }

    /** Timeline offset (from clip start) to media offset (from trim start). */
    fun timelineToMedia(speed: Speed, mediaDurationUs: Long, timelineOffsetUs: Long): Long {
        val segs = segments(speed, mediaDurationUs)
        var acc = 0.0
        for (i in segs.indices) {
            val start = segs[i].first
            val end = if (i + 1 < segs.size) segs[i + 1].first else mediaDurationUs
            val len = (end - start) / segs[i].second.toDouble()
            if (timelineOffsetUs <= acc + len) return (start + (timelineOffsetUs - acc) * segs[i].second).roundToLong().coerceIn(0, mediaDurationUs)
            acc += len
        }
        return mediaDurationUs
    }
}

val Clip.timelineDurationUs: Long
    get() = if (kind == ClipKind.PHOTO) trimmedDurationUs else SpeedMath.timelineDurationUs(speed, trimmedDurationUs)

/** A clip resolved onto the timeline. */
data class Placed(val clip: Clip, val index: Int, val startUs: Long, val durationUs: Long) {
    val endUs: Long get() = startUs + durationUs
}

object TimelineMath {
    fun place(clips: List<Clip>): List<Placed> {
        var t = 0L
        return clips.mapIndexed { i, c ->
            val d = c.timelineDurationUs
            Placed(c, i, t, d).also { t += d }
        }
    }

    fun duration(project: Project): Long {
        val clipsEnd = place(project.clips).lastOrNull()?.endUs ?: 0L
        return clipsEnd
    }

    /** Index of the clip under a timeline position; the last clip owns the very end. */
    fun clipAt(placed: List<Placed>, timeUs: Long): Placed? {
        if (placed.isEmpty()) return null
        return placed.firstOrNull { timeUs >= it.startUs && timeUs < it.endUs } ?: placed.last().takeIf { timeUs >= it.endUs }
    }

    /** Splits the clip under [timeUs]. Returns null when the cut would leave a piece shorter than [MIN_CLIP_US]. */
    fun split(project: Project, timeUs: Long): Project? {
        val placed = place(project.clips)
        val p = clipAt(placed, timeUs) ?: return null
        val local = timeUs - p.startUs
        if (local < MIN_CLIP_US || p.durationUs - local < MIN_CLIP_US) return null
        val c = p.clip
        val mediaCut = if (c.kind == ClipKind.PHOTO) c.trimStartUs + local else c.trimStartUs + SpeedMath.timelineToMedia(c.speed, c.trimmedDurationUs, local)
        val firstMediaLen = mediaCut - c.trimStartUs
        val secondMediaLen = c.trimEndUs - mediaCut
        val (curveA, curveB) = splitCurve(c.speed, firstMediaLen.toDouble() / c.trimmedDurationUs)
        val a = c.copy(
            trimEndUs = mediaCut,
            fadeOutUs = 0,
            animOut = null,
            speed = c.speed.copy(curve = curveA),
            keyframes = c.keyframes.filter { it.timeUs <= local },
        )
        val b = c.copy(
            id = newId(),
            trimStartUs = mediaCut,
            fadeInUs = 0,
            animIn = null,
            transitionIn = null,
            speed = c.speed.copy(curve = curveB),
            keyframes = c.keyframes.filter { it.timeUs >= local }.map { it.copy(timeUs = it.timeUs - local) },
        )
        if (firstMediaLen <= 0 || secondMediaLen <= 0) return null
        val clips = project.clips.toMutableList()
        clips[p.index] = a
        clips.add(p.index + 1, b)
        return project.copy(clips = clips)
    }

    private fun splitCurve(speed: Speed, frac: Double): Pair<List<Float>?, List<Float>?> {
        val c = speed.curve ?: return null to null
        val n = c.size
        val a = (0 until n).map { i -> SpeedMath.speedAt(speed, frac * i / (n - 1)).toFloat() }
        val b = (0 until n).map { i -> SpeedMath.speedAt(speed, frac + (1 - frac) * i / (n - 1)).toFloat() }
        return a to b
    }

    /**
     * Trims a clip edge by a timeline delta (positive moves the edge right).
     * Clamped to the source range and to [MIN_CLIP_US].
     */
    fun trim(project: Project, clipId: String, leftEdge: Boolean, deltaUs: Long): Project {
        val idx = project.clips.indexOfFirst { it.id == clipId }
        if (idx < 0) return project
        val c = project.clips[idx]
        val scale = if (c.kind == ClipKind.PHOTO) 1.0 else c.trimmedDurationUs.toDouble() / max(1L, c.timelineDurationUs)
        val mediaDelta = (deltaUs * scale).roundToLong()
        val minMedia = (MIN_CLIP_US * scale).roundToLong()
        val updated = if (leftEdge) {
            val maxStart = c.trimEndUs - minMedia
            val start = (c.trimStartUs + mediaDelta).coerceIn(if (c.kind == ClipKind.PHOTO) 0 else 0, maxStart)
            c.copy(trimStartUs = start)
        } else {
            val minEnd = c.trimStartUs + minMedia
            val maxEnd = if (c.kind == ClipKind.PHOTO) Long.MAX_VALUE / 4 else c.sourceDurationUs
            c.copy(trimEndUs = (c.trimEndUs + mediaDelta).coerceIn(minEnd, maxEnd))
        }
        val fixed = updated.copy(
            fadeInUs = min(updated.fadeInUs, updated.timelineDurationUs / 2),
            fadeOutUs = min(updated.fadeOutUs, updated.timelineDurationUs / 2),
            transitionIn = updated.transitionIn?.let { it.copy(durationUs = min(it.durationUs, updated.timelineDurationUs / 2)) },
        )
        val clips = project.clips.toMutableList()
        clips[idx] = if (fixed.kind == ClipKind.PHOTO) fixed.copy(sourceDurationUs = max(fixed.sourceDurationUs, fixed.trimEndUs)) else fixed
        return project.copy(clips = clips)
    }

    fun move(project: Project, from: Int, to: Int): Project {
        if (from !in project.clips.indices || to !in project.clips.indices || from == to) return project
        val clips = project.clips.toMutableList()
        val c = clips.removeAt(from)
        clips.add(to, c)
        // The first clip cannot carry an incoming transition.
        if (clips.isNotEmpty() && clips[0].transitionIn != null) clips[0] = clips[0].copy(transitionIn = null)
        return project.copy(clips = clips)
    }

    /** Snap candidate nearest to [timeUs] within [thresholdUs], else [timeUs]. */
    fun snap(timeUs: Long, candidates: Collection<Long>, thresholdUs: Long): Long {
        var best = timeUs
        var bestD = thresholdUs + 1
        for (c in candidates) {
            val d = abs(c - timeUs)
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }

    fun snapPoints(project: Project, beats: Boolean = true): List<Long> {
        val pts = mutableListOf(0L)
        place(project.clips).forEach { pts += it.startUs; pts += it.endUs }
        project.texts.forEach { pts += it.startUs; pts += it.startUs + it.durationUs }
        project.effects.forEach { pts += it.startUs; pts += it.startUs + it.durationUs }
        if (beats) project.audio.forEach { a -> a.beatsUs.forEach { b -> val t = a.startUs + b - a.trimStartUs; if (t >= a.startUs && t <= a.endUs) pts += t } }
        return pts
    }
}

/** Cubic-bezier easing (CSS semantics) solved with Newton iterations. */
fun Easing.ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    if (this.x1 == this.y1 && this.x2 == this.y2) return x
    fun bx(u: Float) = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u
    fun by(u: Float) = 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    fun dbx(u: Float) = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2)
    var u = x
    repeat(8) {
        val d = dbx(u)
        if (abs(d) < 1e-6f) return@repeat
        u -= (bx(u) - x) / d
        u = u.coerceIn(0f, 1f)
    }
    // Bisection fallback for flat derivative regions.
    var lo = 0f
    var hi = 1f
    if (abs(bx(u) - x) > 1e-4f) {
        repeat(30) {
            val mid = (lo + hi) / 2
            if (bx(mid) < x) lo = mid else hi = mid
        }
        u = (lo + hi) / 2
    }
    return by(u)
}

/** Interpolates keyframed transform at [localUs] (offset from the layer/clip start). */
fun interpolate(base: Transform, keyframes: List<Keyframe>, localUs: Long): Transform {
    if (keyframes.isEmpty()) return base
    val ks = keyframes.sortedBy { it.timeUs }
    if (localUs <= ks.first().timeUs) return ks.first().transform
    if (localUs >= ks.last().timeUs) return ks.last().transform
    val i = ks.indexOfLast { it.timeUs <= localUs }
    val a = ks[i]
    val b = ks[i + 1]
    val span = (b.timeUs - a.timeUs).coerceAtLeast(1)
    val e = b.easing.ease((localUs - a.timeUs).toFloat() / span)
    fun l(x: Float, y: Float) = x + (y - x) * e
    return Transform(
        x = l(a.transform.x, b.transform.x),
        y = l(a.transform.y, b.transform.y),
        scale = l(a.transform.scale, b.transform.scale),
        rotation = l(a.transform.rotation, b.transform.rotation),
        opacity = l(a.transform.opacity, b.transform.opacity),
    )
}
