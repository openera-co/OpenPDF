package com.reel.editor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.reel.editor.model.Easing
import com.reel.editor.model.Keyframe
import com.reel.editor.model.Transform
import com.reel.editor.model.ease
import com.reel.editor.model.interpolate
import kotlin.math.abs
import kotlin.math.hypot

/** Which transform property the value graph shows. */
enum class GraphProp(val label: String, val get: (Transform) -> Float) {
    X("X", { it.x }), Y("Y", { it.y }), SCALE("Scale", { it.scale }), ROTATE("Rotate", { it.rotation }), OPACITY("Opacity", { it.opacity })
}

/**
 * The chosen property over the layer's whole length: the curve, its keyframes as diamonds and the
 * playhead. Tap or drag to move the playhead.
 */
@Composable
fun ValueGraph(base: Transform, keys: List<Keyframe>, startUs: Long, durationUs: Long, posUs: Long, prop: GraphProp, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val seek by rememberUpdatedState(onSeek)
    val dur = durationUs.coerceAtLeast(1)
    Canvas(
        modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(C.Track)
            .pointerInput(startUs, dur) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float) = startUs + (x / size.width).coerceIn(0f, 1f).times(dur).toLong()
                    seek(at(down.position.x))
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        seek(at(c.position.x))
                        c.consume()
                    }
                }
            },
    ) {
        val n = 90
        val vals = FloatArray(n + 1) { i -> prop.get(interpolate(base, keys, dur * i / n)) }
        var lo = vals.min()
        var hi = vals.max()
        if (hi - lo < 1e-4f) { lo -= 0.5f; hi += 0.5f }
        val pad = 10 * density
        fun y(v: Float) = size.height - pad - (v - lo) / (hi - lo) * (size.height - pad * 2)
        // Mid line for scale.
        drawLine(C.Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val path = Path()
        for (i in 0..n) {
            val px = size.width * i / n
            if (i == 0) path.moveTo(px, y(vals[i])) else path.lineTo(px, y(vals[i]))
        }
        drawPath(path, C.Key, style = Stroke(2.2f * density))
        for (k in keys) {
            if (k.timeUs !in 0..dur) continue
            val kx = size.width * k.timeUs / dur
            val ky = y(prop.get(k.transform))
            val r = 5 * density
            val on = abs(startUs + k.timeUs - posUs) < 40_000
            val d = Path().apply { moveTo(kx, ky - r); lineTo(kx + r, ky); lineTo(kx, ky + r); lineTo(kx - r, ky); close() }
            drawPath(d, if (on) C.Key else C.Text)
        }
        val px = size.width * ((posUs - startUs).toFloat() / dur).coerceIn(0f, 1f)
        drawLine(C.Text, Offset(px, 0f), Offset(px, size.height), 1.5f * density)
    }
}

/**
 * The easing of one segment: drag the two round handles to shape the curve. A ball below runs the
 * curve so the feel is visible, not just the shape.
 */
@Composable
fun EasingEditor(e: Easing, onStart: () -> Unit, onChange: (Easing) -> Unit, onEnd: () -> Unit) {
    val cur by rememberUpdatedState(e)
    val change by rememberUpdatedState(onChange)
    val bezier = e.name != "Bounce" && e.name != "Elastic"
    val loop = rememberInfiniteTransition(label = "ease")
    val t by loop.animateFloat(0f, 1.6f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(12.dp)).background(C.Track)
                .pointerInput(bezier) {
                    if (!bezier) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val pad = 14 * density
                        val w = size.width - pad * 2
                        val h = size.height - pad * 2
                        // y spans -0.5..1.5 so overshoot handles fit.
                        fun toPx(x: Float, y: Float) = Offset(pad + x * w, pad + h - (y + 0.5f) / 2f * h)
                        val h1 = toPx(cur.x1, cur.y1)
                        val h2 = toPx(cur.x2, cur.y2)
                        val which = if (hypot(down.position.x - h1.x, down.position.y - h1.y) < hypot(down.position.x - h2.x, down.position.y - h2.y)) 1 else 2
                        onStart()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            val x = ((c.position.x - pad) / w).coerceIn(0f, 1f)
                            val y = ((pad + h - c.position.y) / h * 2f - 0.5f).coerceIn(-0.5f, 1.5f)
                            val n = if (which == 1) cur.copy(x1 = x, y1 = y, name = "Custom") else cur.copy(x2 = x, y2 = y, name = "Custom")
                            change(n)
                            c.consume()
                        }
                        onEnd()
                    }
                },
        ) {
            val pad = 14 * density
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            fun toPx(x: Float, y: Float) = Offset(pad + x * w, pad + h - (y + 0.5f) / 2f * h)
            drawRect(C.Line, toPx(0f, 1f), Size(w, h / 2), style = Stroke(1f))
            val path = Path()
            for (i in 0..80) {
                val x = i / 80f
                val p = toPx(x, e.ease(x))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (bezier) {
                val a = toPx(0f, 0f); val b = toPx(1f, 1f)
                val h1 = toPx(e.x1, e.y1); val h2 = toPx(e.x2, e.y2)
                drawLine(C.Dim, a, h1, 1.5f * density)
                drawLine(C.Dim, b, h2, 1.5f * density)
                drawPath(path, C.Key, style = Stroke(2.5f * density))
                drawCircle(C.Text, 7 * density, h1)
                drawCircle(C.Text, 7 * density, h2)
                drawCircle(C.Key, 4 * density, h1)
                drawCircle(C.Key, 4 * density, h2)
            } else drawPath(path, C.Key, style = Stroke(2.5f * density))
        }
        // Ball preview: runs for one second, rests briefly.
        Canvas(Modifier.width(44.dp).height(120.dp).clip(RoundedCornerShape(12.dp)).background(C.Track)) {
            val p = e.ease(t.coerceAtMost(1f))
            val pad = 12 * density
            val y = size.height - pad - p * (size.height - pad * 2)
            drawRoundRect(C.Line, Offset(size.width / 2 - 1, pad), Size(2f, size.height - pad * 2), CornerRadius(1f))
            drawCircle(C.Key, 8 * density, Offset(size.width / 2, y))
        }
    }
}

@Composable
fun GraphPropChips(selected: GraphProp, onPick: (GraphProp) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GraphProp.entries.forEach { p -> Chip(p.label, p == selected) { onPick(p) } }
    }
}
