package com.reel.editor.ui

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
fun IconBtn(icon: ImageVector, desc: String, tint: Color = C.Text, size: Dp = 44.dp, iconSize: Dp = 22.dp, enabled: Boolean = true, background: Color = Color.Transparent, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = if (enabled) tint else C.Dim, modifier = Modifier.size(iconSize)) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) C.Text else C.Surface2)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Body.copy(fontSize = 13.sp, color = if (selected) C.Bg else C.Soft)) }
}

/** Slider row: icon, track, live value. Double tap the value to reset. */
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
    onChange: (Float) -> Unit,
) {
    var dragging = remember { booleanArrayOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, desc, tint = C.Soft, modifier = Modifier.size(18.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                if (!dragging[0]) { dragging[0] = true; onStart() }
                val snapped = if (default != null && kotlin.math.abs(it - default) < (range.endInclusive - range.start) * 0.02f) default else it
                onChange(snapped)
            },
            onValueChangeFinished = { dragging[0] = false; onEnd() },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = color, inactiveTrackColor = C.Line),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            format(value), style = Mono,
            modifier = Modifier.width(52.dp).clickable(enabled = default != null) { onStart(); onChange(default ?: value); onEnd() },
        )
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
