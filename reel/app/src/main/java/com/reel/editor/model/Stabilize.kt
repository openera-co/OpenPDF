package com.reel.editor.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Video stabilization: finds how the picture moves between frames, keeps the smooth part of
 * that path (pans, walks) and cancels the shake with small moves, turns and a slight zoom.
 */
object Stabilize {
    /** A grey frame, values 0..1, row by row. */
    class Gray(val w: Int, val h: Int, val px: FloatArray) {
        operator fun get(x: Int, y: Int) = px[y * w + x]

        fun half(): Gray {
            val hw = w / 2; val hh = h / 2
            val o = FloatArray(hw * hh)
            for (y in 0 until hh) for (x in 0 until hw) {
                val i = y * 2 * w + x * 2
                o[y * hw + x] = (px[i] + px[i + 1] + px[i + w] + px[i + w + 1]) * 0.25f
            }
            return Gray(hw, hh, o)
        }
    }

    /** Movement of the picture from one frame to the next: pixels (of [w]x[h]) and radians, y down. */
    data class Motion(val dx: Float, val dy: Float, val angle: Float, val w: Int, val h: Int)

    /** How far to move each frame back, as fractions of the frame, plus a turn in radians. */
    data class Correction(val x: Float, val y: Float, val angle: Float)

    private fun sad(a: Gray, b: Gray, ax: Int, ay: Int, bx: Int, by: Int, half: Int, step: Int): Float {
        var s = 0f
        var y = -half
        while (y <= half) {
            var x = -half
            val ra = (ay + y) * a.w; val rb = (by + y) * b.w
            while (x <= half) { s += abs(a.px[ra + ax + x] - b.px[rb + bx + x]); x += step }
            y += step
        }
        return s
    }

    /** Whole-frame shift between [a] and [b], searched within ±[r] pixels. */
    private fun globalShift(a: Gray, b: Gray, r: Int): Pair<Int, Int> {
        val cx = a.w / 2; val cy = a.h / 2
        val half = min(cx, cy) - r - 1
        var best = Float.MAX_VALUE; var bx = 0; var by = 0
        for (dy in -r..r) for (dx in -r..r) {
            val s = sad(a, b, cx, cy, cx + dx, cy + dy, half, 2)
            if (s < best) { best = s; bx = dx; by = dy }
        }
        return bx to by
    }

    /** Estimates the motion from [a] to [b] (same size): a coarse whole-frame match, then patches. */
    fun motion(a: Gray, b: Gray): Motion {
        val coarse = globalShift(a.half(), b.half(), 10)
        val gx = coarse.first * 2; val gy = coarse.second * 2
        val half = 6; val r = 4
        val margin = half + r + max(abs(gx), abs(gy)) + 2
        val pts = ArrayList<FloatArray>() // x, y, vx, vy
        val stepX = max(6, (a.w - 2 * margin) / 10)
        val stepY = max(6, (a.h - 2 * margin) / 6)
        var py = margin
        while (py < a.h - margin) {
            var px = margin
            while (px < a.w - margin) {
                // Skip flat patches: they match anywhere.
                var mean = 0f; var n = 0
                for (y in -half..half) for (x in -half..half) { mean += a[px + x, py + y]; n++ }
                mean /= n
                var v = 0f
                for (y in -half..half) for (x in -half..half) { val d = a[px + x, py + y] - mean; v += d * d }
                if (v / n > 2e-4f) {
                    val scores = Array(2 * r + 1) { FloatArray(2 * r + 1) }
                    var best = Float.MAX_VALUE; var bx = 0; var by = 0
                    for (dy in -r..r) for (dx in -r..r) {
                        val s = sad(a, b, px, py, px + gx + dx, py + gy + dy, half, 1)
                        scores[dy + r][dx + r] = s
                        if (s < best) { best = s; bx = dx; by = dy }
                    }
                    // Sub-pixel: fit a parabola through the best score and its neighbours.
                    fun sub(m: Float, c: Float, p: Float): Float { val d = m - 2 * c + p; return if (d > 1e-6f) 0.5f * (m - p) / d else 0f }
                    val sx = if (bx in -r + 1 until r) sub(scores[by + r][bx + r - 1], best, scores[by + r][bx + r + 1]) else 0f
                    val sy = if (by in -r + 1 until r) sub(scores[by + r - 1][bx + r], best, scores[by + r + 1][bx + r]) else 0f
                    pts += floatArrayOf(px.toFloat(), py.toFloat(), gx + bx + sx, gy + by + sy)
                }
                px += stepX
            }
            py += stepY
        }
        if (pts.size < 3) return Motion(gx.toFloat(), gy.toFloat(), 0f, a.w, a.h)
        val tx = median(pts.map { it[2] }); val ty = median(pts.map { it[3] })
        // Rotation about the centre from what is left after the shift: v - t ≈ θ · (-y, x).
        val cx = a.w / 2f; val cy = a.h / 2f
        fun fit(keep: (FloatArray, Float) -> Boolean, prev: Float): Float {
            var num = 0f; var den = 0f
            for (p in pts) {
                val rx = p[0] - cx; val ry = p[1] - cy
                val ex = p[2] - tx; val ey = p[3] - ty
                if (!keep(p, prev)) continue
                num += -ry * ex + rx * ey
                den += rx * rx + ry * ry
            }
            return if (den > 0) (num / den).coerceIn(-0.2f, 0.2f) else 0f
        }
        // Loose first pass, then refit on the patches that agree with it (drops moving objects).
        val first = fit({ p, _ -> abs(p[2] - tx) < 5f && abs(p[3] - ty) < 5f }, 0f)
        val angle = fit({ p, th ->
            val rx = p[0] - cx; val ry = p[1] - cy
            abs(p[2] - tx + th * ry) < 1f && abs(p[3] - ty - th * rx) < 1f
        }, first)
        return Motion(tx, ty, angle, a.w, a.h)
    }

    private fun median(v: List<Float>): Float { val s = v.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    /**
     * The shake to cancel at each frame. The camera path is the running sum of [motions] (one per
     * frame after the first); a Gaussian of [sigma] frames keeps its smooth part.
     */
    fun corrections(motions: List<Motion>, sigma: Float = 8f): List<Correction> {
        val n = motions.size + 1
        val px = FloatArray(n); val py = FloatArray(n); val pa = FloatArray(n)
        for (i in motions.indices) {
            val m = motions[i]
            px[i + 1] = px[i] + m.dx / m.w
            py[i + 1] = py[i] + m.dy / m.h
            pa[i + 1] = pa[i] + m.angle
        }
        val r = (sigma * 3).toInt().coerceAtLeast(1)
        val wts = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        fun smooth(p: FloatArray) = FloatArray(n) { i ->
            // Near the ends the window is mirrored around the end point, which keeps a pan's speed.
            var s = 0f; var w = 0f
            for (k in -r..r) {
                var j = i + k
                val v = when {
                    j < 0 -> 2 * p[0] - p[min(n - 1, -j)]
                    j >= n -> 2 * p[n - 1] - p[max(0, 2 * (n - 1) - j)]
                    else -> p[j]
                }
                s += v * wts[k + r]; w += wts[k + r]
            }
            s / w
        }
        val sx = smooth(px); val sy = smooth(py); val sa = smooth(pa)
        return List(n) { Correction(sx[it] - px[it], sy[it] - py[it], sa[it] - pa[it]) }
    }

    /**
     * Clip keyframes that apply [corr] (one per time in [timesUs]) on top of [base]. The picture
     * is zoomed just enough to hide the moved edges, up to [maxZoom]; bigger shakes are only
     * partly cancelled.
     */
    fun keyframes(timesUs: List<Long>, corr: List<Correction>, base: Transform, contentAspect: Float, canvasAspect: Float, fill: Boolean, maxZoom: Float = 1.2f): List<Keyframe> {
        require(timesUs.size == corr.size)
        val (cw, ch) = HeadPath.contentSize(contentAspect, canvasAspect, fill)
        // Margin each correction needs on each side: the move, plus what a turn swings into view.
        var need = 0f
        for (c in corr) {
            val rot = abs(sin(c.angle)) * 0.5f * (1 + max(contentAspect, 1f / contentAspect))
            need = max(need, max(abs(c.x), abs(c.y)) + rot)
        }
        val wanted = 1f + 2f * need
        val zoom = min(wanted, maxZoom)
        val k = if (wanted > zoom) (zoom - 1f) / (wanted - 1f) else 1f
        val scale = base.scale * zoom
        return timesUs.indices.map { i ->
            val c = corr[i]
            val a = c.angle * k
            // Move in source-frame fractions, onto the canvas at the zoomed size, turned with the clip.
            val dx = c.x * k * cw * scale * canvasAspect
            val dy = c.y * k * ch * scale
            val br = Math.toRadians(base.rotation.toDouble())
            val rx = (dx * cos(br) - dy * sin(br)).toFloat()
            val ry = (dx * sin(br) + dy * cos(br)).toFloat()
            Keyframe(timesUs[i], base.copy(x = base.x + rx / canvasAspect, y = base.y + ry, scale = scale, rotation = base.rotation + Math.toDegrees(a.toDouble()).toFloat()), Easing.LINEAR)
        }
    }
}
