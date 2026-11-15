package com.reel.editor.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One-tap auto enhance: reads a few frames and sets exposure, contrast, white balance and
 * colour so the picture is well exposed, uses the full range and has no colour cast. The values
 * follow the grade shader (internal/grade.frag), so the result can be predicted exactly.
 */
object AutoEnhance {
    /** What the frames look like (sRGB values 0..1). */
    data class Stats(val meanLuma: Float, val p02: Float, val p98: Float, val r: Float, val g: Float, val b: Float, val saturation: Float)

    fun stats(pixels: IntArray): Stats {
        if (pixels.isEmpty()) return Stats(0.5f, 0f, 1f, 0.5f, 0.5f, 0.5f, 0.3f)
        val hist = IntArray(256)
        var sl = 0.0; var sr = 0.0; var sg = 0.0; var sb = 0.0; var ss = 0.0; var nMid = 0
        for (c in pixels) {
            val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            hist[(l * 255).toInt().coerceIn(0, 255)]++
            sl += l
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            ss += if (mx > 0f) (mx - mn) / mx else 0f
            // White balance from the middle tones: deep shadows and clipped highlights mislead.
            if (l in 0.12f..0.92f) { sr += r; sg += g; sb += b; nMid++ }
        }
        fun pct(q: Float): Float {
            val target = (pixels.size * q).toLong()
            var acc = 0L
            for (i in 0 until 256) { acc += hist[i]; if (acc >= target) return i / 255f }
            return 1f
        }
        val n = pixels.size.toDouble()
        val m = nMid.coerceAtLeast(1).toDouble()
        return Stats((sl / n).toFloat(), pct(0.02f), pct(0.98f), (sr / m).toFloat(), (sg / m).toFloat(), (sb / m).toFloat(), (ss / n).toFloat())
    }

    /** Adjustments that correct [s], merged into [base] (other settings are kept). */
    fun adjust(s: Stats, base: Adjustments = Adjustments()): Adjustments {
        // Exposure and contrast together: the shader's contrast pivots at mid grey, so the
        // exposure is chosen to land the mean on target after the contrast has been applied.
        val target = 0.46f
        val m = s.meanLuma.coerceAtLeast(0.02f)
        var gain = (target / m).coerceIn(0.7f, 2.4f)
        var contrast = 0f
        repeat(3) {
            // Spread the darkest and brightest 2% towards 0.05 and 0.95.
            val range = ((s.p98 - s.p02) * gain).coerceAtLeast(0.05f)
            contrast = (0.9f / range - 1f).coerceIn(-0.1f, 0.3f)
            gain = (((target - 0.5f) / (1f + contrast) + 0.5f) / m).coerceIn(0.7f, 2.4f)
        }
        val exposure = ((ln(gain.toDouble()) / ln(2.0)) / 2.0).toFloat().coerceIn(-0.3f, 0.63f)
        gain = Math.pow(2.0, exposure * 2.0).toFloat()
        // White balance (grey world, half strength so real light keeps its mood).
        val temperature = if (s.r + s.b > 0.02f) (0.5f * (s.b - s.r) / (0.2f * (s.r + s.b))).coerceIn(-0.5f, 0.5f) else 0f
        val tint = if (s.g > 0.02f) (0.5f * (1f - (s.r + s.b) / (2f * s.g)) / 0.15f).coerceIn(-0.4f, 0.4f) else 0f
        // Dull pictures get a little colour; colourful ones are left alone.
        val vibrance = ((0.3f - s.saturation) * 1.2f).coerceIn(0f, 0.35f)
        // Pull bright skies back when the exposure lift would clip them.
        val highlights = if (s.p98 * gain > 0.97f) -0.25f else 0f
        fun tidy(v: Float) = if (abs(v) < 0.02f) 0f else (v * 100).toInt() / 100f
        return base.copy(
            exposure = tidy(exposure), contrast = tidy(contrast), temperature = tidy(temperature), tint = tidy(tint),
            vibrance = tidy(vibrance), highlights = tidy(highlights),
        )
    }
}
