package com.reel.editor.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B0B0F)
    val Surface = Color(0xFF15151C)
    val Surface2 = Color(0xFF1E1E27)
    val Line = Color(0xFF2A2A35)
    val Track = Color(0xFF0F0F14)
    val Text = Color(0xFFF4F4F7)
    val Muted = Color(0xFF9A9AAB)
    val Soft = Color(0xFFC9C9D6)
    val Dim = Color(0xFF5A5A68)
    val Accent = Color(0xFF4C8DFF)
    val Key = Color(0xFFFFB547)
    val TextTrack = Color(0xFFFF7A59)
    val Effect = Color(0xFFA78BFA)
    val Music = Color(0xFF2DD4BF)
    val Voice = Color(0xFFFF4D5E)
    val Overlay = Color(0xFFF472B6)
    val Good = Color(0xFF3DDC84)
}

object Motion {
    val sheet = tween<Float>(320, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
    val quick = tween<Float>(150)
    val springy = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

@Composable
fun ReelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.Bg, background = C.Bg, surface = C.Surface,
            onSurface = C.Text, onBackground = C.Text, surfaceVariant = C.Surface2, outline = C.Line,
        ),
        content = content,
    )
}

val Label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = C.Soft)
val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Text)
val Title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
val Mono = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Text, fontFeatureSettings = "tnum")

/** Toolbar item: icon with a one-word label. */
@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, tint: Color = C.Text, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(64.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = label, tint = when { !enabled -> C.Dim; active -> C.Accent; else -> tint }, modifier = Modifier.size(22.dp))
        Text(label, style = Label.copy(color = if (!enabled) C.Dim else if (active) C.Accent else C.Soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = C.Text, size: Dp = 44.dp, iconSize: Dp = 22.dp, enabled: Boolean = true, background: Color = Color.Transparent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = if (enabled) tint else C.Dim, modifier = Modifier.size(iconSize)) }
}

@Composable
fun Chip(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) C.Text else C.Surface2)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, tint = if (selected) C.Bg else C.Soft, modifier = Modifier.size(16.dp))
        Text(text, style = Body.copy(fontSize = 13.sp, color = if (selected) C.Bg else C.Soft))
    }
}

/** Keyframe state for a slider: shows a diamond that adds or removes a key at the playhead. */
data class SliderKey(val atPlayhead: Boolean, val any: Boolean, val toggle: () -> Unit)

/**
 * Thin track with a small round thumb. The thumb follows the finger's movement (a stray tap never
 * jumps the value); double tap resets to [default]. Signed ranges fill from their middle.
 */
@Composable
fun ReelSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    origin: Float? = null,
    default: Float? = null,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val v by androidx.compose.runtime.rememberUpdatedState(value.coerceIn(range.start, range.endInclusive))
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val thumbScale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1.18f else 1f, label = "thumb")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier.height(36.dp).pointerInput(range, default) {
            var lastTap = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                val slop = viewConfiguration.touchSlop
                var dragging = false
                var start = v
                var startX = down.position.x
                val usable = (size.width - 20.dp.toPx()).coerceAtLeast(1f)
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val dx = ch.position.x - startX
                    if (!dragging && kotlin.math.abs(dx) > slop) { dragging = true; onStart(); startX = ch.position.x; start = v }
                    if (dragging) {
                        var nv = (start + (ch.position.x - startX) / usable * span).coerceIn(range.start, range.endInclusive)
                        if (default != null && kotlin.math.abs(nv - default) < span * 0.02f) {
                            if (nv != default && v != default) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            nv = default
                        }
                        onChange(nv)
                        ch.consume()
                    }
                }
                pressed = false
                if (dragging) onEnd()
                else {
                    val now = System.currentTimeMillis()
                    if (default != null && now - lastTap < 320) { onStart(); onChange(default); onEnd(); lastTap = 0 } else lastTap = now
                }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 10.dp.toPx()
            val y = size.height / 2
            val x0 = r
            val x1 = size.width - r
            fun xOf(t: Float) = x0 + (t - range.start) / span * (x1 - x0)
            val track = 4.dp.toPx()
            drawLine(C.Line, androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x1, y), strokeWidth = track, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val from = xOf((origin ?: range.start).coerceIn(range.start, range.endInclusive))
            val to = xOf(v)
            if (kotlin.math.abs(to - from) > 0.5f) drawLine(color, androidx.compose.ui.geometry.Offset(from, y), androidx.compose.ui.geometry.Offset(to, y), strokeWidth = track, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.35f), r * thumbScale + 1.5f, androidx.compose.ui.geometry.Offset(to, y + 1.5f))
            drawCircle(Color.White, r * thumbScale, androidx.compose.ui.geometry.Offset(to, y))
        }
    }
}

/** Slider row: icon, track, live value (tap to reset), optional keyframe diamond. */
@Composable
fun ValueSlider(
    icon: ImageVector,
    desc: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    color: Color = C.Text,
    default: Float? = null,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
    key: SliderKey? = null,
    onChange: (Float) -> Unit,
) {
    // Ranges that cross zero (brightness, tint...) fill from the middle.
    val origin = if (range.start < 0f && range.endInclusive > 0f) 0f else null
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, desc, tint = C.Soft, modifier = Modifier.size(18.dp))
        ReelSlider(value, range, color, Modifier.weight(1f).padding(horizontal = 10.dp), origin = origin, default = default, onStart = onStart, onEnd = onEnd, onChange = onChange)
        Text(
            format(value), style = Mono, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(48.dp).clickable(enabled = default != null) { onStart(); onChange(default ?: value); onEnd() },
        )
        if (key != null) {
            Box(Modifier.size(36.dp).clickable(onClick = key.toggle), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(14.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); lineTo(size.width / 2, size.height); lineTo(0f, size.height / 2); close()
                    }
                    if (key.atPlayhead) drawPath(path, C.Key) else drawPath(path, if (key.any) C.Key else C.Dim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun SheetHeader(title: String, icon: ImageVector? = null, iconTint: Color = C.Text, onClose: () -> Unit, onDone: (() -> Unit)? = null, center: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(androidx.compose.material.icons.Icons.Rounded.Close, "Close", size = 44.dp, iconSize = 20.dp, onClick = onClose)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (center != null) center() else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                Text(title, style = Title)
            }
        }
        if (onDone != null) IconBtn(androidx.compose.material.icons.Icons.Rounded.Check, "Done", tint = C.Accent, onClick = onDone) else Box(Modifier.size(44.dp))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(11.dp)).background(C.Bg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).background(if (i == selected) C.Line else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = Body.copy(fontSize = 13.sp, color = if (i == selected) C.Text else C.Muted)) }
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

fun formatTime(us: Long, tenths: Boolean = true): String {
    val totalMs = (us / 1000).coerceAtLeast(0)
    val m = totalMs / 60000
    val s = (totalMs / 1000) % 60
    val t = (totalMs / 100) % 10
    return if (tenths) "%02d:%02d.%d".format(m, s, t) else "%d:%02d".format(m, s)
}
