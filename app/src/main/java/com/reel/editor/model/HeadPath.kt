package com.reel.editor.model

import kotlin.math.cos
import kotlin.math.sin

/** A head found in a source frame: centre and size as fractions of the frame (y down). */
data class HeadPoint(val timeUs: Long, val x: Float, val y: Float, val size: Float, val found: Boolean)

/** Pure math for head tracking: smoothing and mapping frame positions onto the canvas. */
object HeadPath {
    /**
     * Fills frames where no head was found from their neighbours (holding at the ends) and
     * smooths with a centred moving average so the result doesn't jitter.
     */
    fun smooth(points: List<HeadPoint>, radius: Int = 2): List<HeadPoint> {
        if (points.none { it.found }) return emptyList()
        val filled = points.toMutableList()
        val known = points.indices.filter { points[it].found }
        for (i in points.indices) if (!points[i].found) {
            val before = known.lastOrNull { it < i }
            val after = known.firstOrNull { it > i }
            val p = when {
                before == null -> points[after!!]
                after == null -> points[before]
                else -> {
                    val f = (i - before).toFloat() / (after - before)
                    val a = points[before]; val b = points[after]
                    HeadPoint(points[i].timeUs, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.size + (b.size - a.size) * f, true)
                }
            }
            filled[i] = p.copy(timeUs = points[i].timeUs, found = true)
        }
        return filled.indices.map { i ->
            val lo = (i - radius).coerceAtLeast(0)
            val hi = (i + radius).coerceAtMost(filled.lastIndex)
            val n = hi - lo + 1
            var x = 0f; var y = 0f; var s = 0f
            for (j in lo..hi) { x += filled[j].x; y += filled[j].y; s += filled[j].size }
            HeadPoint(filled[i].timeUs, x / n, y / n, s / n, true)
        }
    }

    /** Size of the clip's picture on the canvas at scale 1, as fractions of canvas width and height. */
    fun contentSize(contentAspect: Float, canvasAspect: Float, fill: Boolean): Pair<Float, Float> {
        val wider = contentAspect > canvasAspect
        return if (wider != fill) 1f to canvasAspect / contentAspect else contentAspect / canvasAspect to 1f
    }

    /** Where a point of the source frame lands on the canvas for a clip placed with [t]. */
    fun toCanvas(fx: Float, fy: Float, t: Transform, contentAspect: Float, canvasAspect: Float, fill: Boolean): Pair<Float, Float> {
        val (cw, ch) = contentSize(contentAspect, canvasAspect, fill)
        // Offsets in canvas-height units so rotation keeps shapes.
        val dx = (fx - 0.5f) * cw * t.scale * canvasAspect
        val dy = (fy - 0.5f) * ch * t.scale
        val a = Math.toRadians(t.rotation.toDouble())
        val rx = (dx * cos(a) - dy * sin(a)).toFloat()
        val ry = (dx * sin(a) + dy * cos(a)).toFloat()
        return (t.x + rx / canvasAspect) to (t.y + ry)
    }

    /**
     * Clip transforms that keep the head at [targetX] across the frame, never showing past the
     * picture's edges. Scale is raised to at least what fills the canvas.
     */
    fun reframe(points: List<HeadPoint>, base: Transform, contentAspect: Float, canvasAspect: Float, fill: Boolean, targetX: Float = 0.5f): List<Keyframe> {
        val (cw, ch) = contentSize(contentAspect, canvasAspect, fill)
        val minScale = maxOf(1f / cw, 1f / ch)
        val scale = maxOf(base.scale, minScale)
        val half = cw * scale / 2f
        return points.map { p ->
            val x = (targetX - (p.x - 0.5f) * cw * scale).coerceIn(1f - half, half)
            Keyframe(p.timeUs, base.copy(x = x, scale = scale, rotation = 0f), Easing.LINEAR)
        }
    }

    /**
     * Drops keyframes that a straight line between their neighbours already reproduces within
     * [tolerance] (canvas fractions; scale is compared relatively), keeping the first and last.
     */
    fun simplify(keys: List<Keyframe>, tolerance: Float = 0.006f): List<Keyframe> {
        if (keys.size <= 2) return keys
        val keep = BooleanArray(keys.size)
        keep[0] = true; keep[keys.lastIndex] = true
        fun err(a: Keyframe, b: Keyframe, m: Keyframe): Float {
            val f = (m.timeUs - a.timeUs).toFloat() / (b.timeUs - a.timeUs).coerceAtLeast(1)
            fun lerp(x: Float, y: Float) = x + (y - x) * f
            val ex = kotlin.math.abs(lerp(a.transform.x, b.transform.x) - m.transform.x)
            val ey = kotlin.math.abs(lerp(a.transform.y, b.transform.y) - m.transform.y)
            val es = kotlin.math.abs(lerp(a.transform.scale, b.transform.scale) - m.transform.scale) / m.transform.scale.coerceAtLeast(0.01f)
            return maxOf(ex, ey, es * 0.5f)
        }
        fun rec(i: Int, j: Int) {
            if (j - i < 2) return
            var worst = -1f; var at = -1
            for (k in i + 1 until j) { val e = err(keys[i], keys[j], keys[k]); if (e > worst) { worst = e; at = k } }
            if (worst > tolerance) { keep[at] = true; rec(i, at); rec(at, j) }
        }
        rec(0, keys.lastIndex)
        return keys.filterIndexed { i, _ -> keep[i] }
    }
}
