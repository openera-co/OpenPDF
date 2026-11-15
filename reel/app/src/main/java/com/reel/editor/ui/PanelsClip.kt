package com.reel.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tonality
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.setKey
import com.reel.editor.model.keyAt
import com.reel.editor.fx.FxCatalog
import com.reel.editor.model.Adjustments
import com.reel.editor.model.AnimRef
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Curves
import com.reel.editor.model.Pt
import com.reel.editor.model.Speed
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.timelineDurationUs
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------- speed

private val SPEED_PRESETS = listOf(
    "Montage" to listOf(1f, 2.5f, 0.6f, 2.5f, 1f),
    "Hero" to listOf(2f, 2f, 0.3f, 0.3f, 2.5f),
    "Bullet" to listOf(2f, 0.25f, 0.25f, 0.25f, 2f),
    "Jump cut" to listOf(0.5f, 0.5f, 4f, 0.5f, 0.5f),
    "Flash in" to listOf(4f, 2f, 1f, 1f, 1f),
    "Flash out" to listOf(1f, 1f, 1f, 2f, 4f),
)

private fun speedToPos(s: Float) = (log10(s / 0.1f) / 3f).coerceIn(0f, 1f)
private fun posToSpeed(p: Float) = 0.1f * 10f.pow(p * 3f)

@Composable
fun SpeedPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(if (clip.speed.curve != null) 1 else 0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Speed", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Normal", "Curve"), tab, { tab = it }) })
        if (clip.kind == ClipKind.PHOTO) {
            // Photos have no speed; set how long they show instead.
            ValueSlider(Icons.Rounded.AccessTime, "Length", clip.trimmedDurationUs / 1e6f, 0.5f..30f, { "%.1fs".format(it) }, default = 3f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.updateClip(clip.id, true) { c -> val end = c.trimStartUs + (v * 1e6).toLong(); c.copy(trimEndUs = end, sourceDurationUs = maxOf(c.sourceDurationUs, end)) }
            }
            return@Column
        }
        val newLen = clip.timelineDurationUs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessTime, null, tint = C.Muted, modifier = Modifier.size(14.dp))
            Text("  %.1fs  →  ".format(clip.trimmedDurationUs / 1e6), style = Mono.copy(color = C.Muted))
            Text("%.1fs".format(newLen / 1e6), style = Mono)
        }
        if (tab == 0) {
            val s = clip.speed.constant
            Text("%.1fx".format(s), style = Title.copy(fontSize = 36.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            ReelSlider(
                speedToPos(s), 0f..1f, C.Text, Modifier.padding(horizontal = 22.dp), default = speedToPos(1f),
                onStart = vm::beginGesture, onEnd = vm::endGesture,
            ) { v ->
                var sp = posToSpeed(v)
                for (snap in listOf(0.5f, 1f, 2f)) if (abs(speedToPos(sp) - speedToPos(snap)) < 0.02f) sp = snap
                vm.updateClip(clip.id, gesture = true) { it.copy(speed = Speed(constant = (sp * 10).roundToInt() / 10f)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0.1x", "0.5x", "1x", "2x", "10x", "100x").forEach { Text(it, style = Label.copy(color = C.Muted)) }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", clip.speed.curve == null) { vm.updateClip(clip.id) { it.copy(speed = Speed()) } }
                SPEED_PRESETS.forEach { (name, pts) ->
                    Chip(name, clip.speed.curveName == name) { vm.updateClip(clip.id) { it.copy(speed = Speed(curve = pts, curveName = name)) } }
                }
            }
            CurveEditor(
                points = clip.speed.curve ?: listOf(1f, 1f, 1f, 1f, 1f),
                onStart = { vm.beginGesture() },
                onChange = { pts -> vm.updateClip(clip.id, gesture = true) { it.copy(speed = Speed(curve = pts, curveName = "Custom")) } },
                onEnd = { vm.endGesture() },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, null, tint = C.Soft, modifier = Modifier.size(18.dp))
            Text("Keep pitch", style = Body, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Switch(clip.keepPitch, { v -> vm.updateClip(clip.id) { it.copy(keepPitch = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SlowMotionVideo, null, tint = C.Soft, modifier = Modifier.size(18.dp))
            Text("Smooth", style = Body, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Switch(clip.smoothFrom != null, { vm.smoothClip() }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Speed curve: points evenly spaced in time, value on a log scale from 0.1x to 10x. */
@Composable
private fun CurveEditor(points: List<Float>, onStart: () -> Unit, onChange: (List<Float>) -> Unit, onEnd: () -> Unit) {
    val pts by androidx.compose.runtime.rememberUpdatedState(points)
    var active by remember { mutableIntStateOf(-1) }
    fun yOf(v: Float, h: Float) = h * (1f - ((log10(v) + 1f) / 2f).coerceIn(0f, 1f))
    fun vOf(y: Float, h: Float) = 10f.pow((1f - (y / h).coerceIn(0f, 1f)) * 2f - 1f)
    Canvas(
        Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(C.Track)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val n = pts.size
                        val idx = (0 until n).minBy { abs(o.x - size.width * it / (n - 1f)) }
                        active = idx
                        onStart()
                    },
                    onDragEnd = { active = -1; onEnd() },
                    onDragCancel = { active = -1; onEnd() },
                ) { change, _ ->
                    val i = active
                    if (i >= 0) {
                        val v = (vOf(change.position.y, size.height.toFloat()) * 100).roundToInt() / 100f
                        onChange(pts.toMutableList().also { it[i] = v.coerceIn(0.1f, 10f) })
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        listOf(10f, 1f, 0.1f).forEach { v -> drawLine(if (v == 1f) C.Dim else C.Line, Offset(0f, yOf(v, h)), Offset(w, yOf(v, h)), 1.5f) }
        val path = Path()
        val n = pts.size
        for (i in 0 until 100) {
            val f = i / 99f
            val v = SpeedMath.speedAt(Speed(curve = pts), f.toDouble()).toFloat()
            val x = f * w
            val y = yOf(v, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, C.Key, style = Stroke(3.dp.toPx()))
        for (i in 0 until n) {
            val c = Offset(w * i / (n - 1f), yOf(pts[i], h))
            drawCircle(if (i == active) C.Key else C.Track, if (i == active) 10.dp.toPx() else 7.dp.toPx(), c)
            drawCircle(C.Key, 7.dp.toPx(), c, style = Stroke(2.5f.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------- volume

@Composable
fun VolumePanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetHeader("Volume", Icons.Rounded.VolumeUp, onClose = onClose, onDone = onClose)
        val maxFade = (clip.timelineDurationUs / 2 / 1e6f).coerceAtLeast(0.1f)
        val pos by vm.preview.positionUs.collectAsStateCompat()
        val start = TimelineMath.place(vm.project.value.clips).firstOrNull { it.clip.id == clip.id }?.startUs ?: 0L
        val local = pos - start
        val inside = local in 0..clip.timelineDurationUs
        val vol = com.reel.editor.model.interpolateValue(clip.volume, clip.volumeKeys, local)
        ValueSlider(
            Icons.Rounded.VolumeUp, "Volume", vol * 100, 0f..200f, { "${it.roundToInt()}%" }, C.Music, default = 100f,
            key = SliderKey(inside && clip.volumeKeys.keyAt(local) != null, clip.volumeKeys.isNotEmpty()) {
                if (!inside) return@SliderKey
                vm.updateClip(clip.id) { c ->
                    val ex = c.volumeKeys.keyAt(local)
                    if (ex != null) c.copy(volumeKeys = c.volumeKeys - ex) else c.copy(volumeKeys = c.volumeKeys.setKey(local, com.reel.editor.model.interpolateValue(c.volume, c.volumeKeys, local)))
                }
            },
            onStart = vm::beginGesture, onEnd = vm::endGesture,
        ) { v ->
            vm.updateClip(clip.id, true) { c -> if (c.volumeKeys.isEmpty() || !inside) c.copy(volume = v / 100f, muted = false) else c.copy(volumeKeys = c.volumeKeys.setKey(local, v / 100f), muted = false) }
        }
        ValueSlider(Icons.Rounded.Gradient, "Fade in", clip.fadeInUs / 1e6f, 0f..maxFade, { "%.1fs".format(it) }, C.Music, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.updateClip(clip.id, true) { it.copy(fadeInUs = (v * 1e6).toLong()) }
        }
        ValueSlider(Icons.Rounded.Gradient, "Fade out", clip.fadeOutUs / 1e6f, 0f..maxFade, { "%.1fs".format(it) }, C.Music, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.updateClip(clip.id, true) { it.copy(fadeOutUs = (v * 1e6).toLong()) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolumeOff, null, tint = C.Soft, modifier = Modifier.size(18.dp))
            Text("Mute clip", style = Body, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Switch(clip.muted, { m -> vm.updateClip(clip.id) { it.copy(muted = m) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- clip animation

@Composable
fun AnimationPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    val catalog = FxCatalog.get(androidx.compose.ui.platform.LocalContext.current)
    var tab by remember { mutableIntStateOf(0) }
    val current = if (tab == 0) clip.animIn else clip.animOut
    val tick = rememberThumbTick()
    val src = rememberClipSource(vm, clip, clip.timelineDurationUs / 2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Animation", onClose = onClose, onDone = onClose, center = { Segmented(listOf("In", "Out"), tab, { tab = it }) })
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 212.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FxTile("None", current == null, Icons.Rounded.Block) { vm.updateClip(clip.id) { if (tab == 0) it.copy(animIn = null) else it.copy(animOut = null) } } }
            items(catalog.anims, key = { it.id }) { a ->
                ShaderFxTile(catalog, a, if (tab == 0) com.reel.editor.fx.FxThumbs.Mode.ANIM_IN else com.reel.editor.fx.FxThumbs.Mode.ANIM_OUT, src, tick, current?.id == a.id, fxBrush(a), animIcon(a.id)) {
                    val d = current?.durationUs ?: 500_000
                    vm.updateClip(clip.id) { if (tab == 0) it.copy(animIn = AnimRef(a.id, d)) else it.copy(animOut = AnimRef(a.id, d)) }
                    // Preview it: jump to where the animation plays.
                    val start = com.reel.editor.model.TimelineMath.place(vm.project.value.clips).firstOrNull { it.clip.id == clip.id }
                    if (start != null) { vm.preview.seekTo(if (tab == 0) start.startUs else (start.endUs - d - 100_000).coerceAtLeast(start.startUs)); vm.preview.play() }
                }
            }
        }
        if (current != null) {
            val max = (clip.timelineDurationUs / 2 / 1e6f).coerceAtLeast(0.2f)
            ValueSlider(Icons.Rounded.AccessTime, "Duration", current.durationUs / 1e6f, 0.1f..max.coerceAtMost(3f), { "%.1fs".format(it) }, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.updateClip(clip.id, true) { if (tab == 0) it.copy(animIn = it.animIn?.copy(durationUs = (v * 1e6).toLong())) else it.copy(animOut = it.animOut?.copy(durationUs = (v * 1e6).toLong())) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun animIcon(id: String): ImageVector = when {
    "zoom" in id -> Icons.Rounded.Details
    "spin" in id -> Icons.Rounded.RotateRight
    "blur" in id -> Icons.Rounded.BlurOn
    "flash" in id -> Icons.Rounded.WbSunny
    "glitch" in id -> Icons.Rounded.Grain
    else -> Icons.Rounded.Opacity
}

@Composable
fun FxTile(name: String, selected: Boolean, icon: ImageVector?, brush: Brush? = null, onClick: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                .background(brush ?: Brush.linearGradient(listOf(C.Surface2, C.Surface2)))
                .then(if (selected) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { if (icon != null) Icon(icon, null, tint = if (selected) C.Accent else C.Text, modifier = Modifier.size(22.dp)) }
        Text(name, style = Label.copy(color = if (selected) C.Text else C.Muted), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------- adjust

private data class AdjDef(val key: String, val label: String, val icon: ImageVector, val get: (Adjustments) -> Float, val set: (Adjustments, Float) -> Adjustments, val signed: Boolean = true)

private val ADJ = listOf(
    AdjDef("brightness", "Bright", Icons.Rounded.LightMode, { it.brightness }, { a, v -> a.copy(brightness = v) }),
    AdjDef("contrast", "Contrast", Icons.Rounded.Contrast, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    AdjDef("saturation", "Saturate", Icons.Rounded.InvertColors, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    AdjDef("exposure", "Exposure", Icons.Rounded.Exposure, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    AdjDef("temperature", "Temp", Icons.Rounded.Thermostat, { it.temperature }, { a, v -> a.copy(temperature = v) }),
    AdjDef("tint", "Tint", Icons.Rounded.Palette, { it.tint }, { a, v -> a.copy(tint = v) }),
    AdjDef("highlights", "Highlights", Icons.Rounded.WbSunny, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    AdjDef("shadows", "Shadows", Icons.Rounded.Tonality, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    AdjDef("vibrance", "Vibrance", Icons.Rounded.Tune, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
    AdjDef("hue", "Hue", Icons.Rounded.Palette, { it.hue }, { a, v -> a.copy(hue = v) }),
    AdjDef("fade", "Fade", Icons.Rounded.Opacity, { it.fade }, { a, v -> a.copy(fade = v) }, signed = false),
    AdjDef("vignette", "Vignette", Icons.Rounded.Vignette, { it.vignette }, { a, v -> a.copy(vignette = v) }, signed = false),
    AdjDef("sharpen", "Sharpen", Icons.Rounded.Details, { it.sharpen }, { a, v -> a.copy(sharpen = v) }, signed = false),
    AdjDef("grain", "Grain", Icons.Rounded.Grain, { it.grain }, { a, v -> a.copy(grain = v) }, signed = false),
)

@Composable
fun AdjustPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    // Scrolls when HSL's extra sliders don't fit; the curves editor keeps its own drags.
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Adjust", Icons.Rounded.Tune, onClose = onClose, onDone = onClose)
        AdjustBody(vm, clip.adjust) { g, f -> vm.updateClip(clip.id, g) { it.copy(adjust = f(it.adjust)) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(Icons.Rounded.Refresh, "Reset") { vm.updateClip(clip.id) { it.copy(adjust = Adjustments()) } }
            PillButton(Icons.Rounded.ContentCopy, "Apply to all") { vm.edit { p -> p.copy(clips = p.clips.map { it.copy(adjust = clip.adjust) }) }; vm.toast("Applied to all clips") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Round parameter buttons with value badges, then the slider (or curves) for the chosen one. */
@Composable
fun AdjustBody(vm: EditorViewModel, adjust: Adjustments, set: (gesture: Boolean, (Adjustments) -> Adjustments) -> Unit) {
    var current by remember { mutableStateOf("brightness") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ADJ.forEach { d ->
                val v = d.get(adjust)
                ParamButton(d.label, d.icon, current == d.key, if (v != 0f) "${(v * 100).roundToInt()}" else null) { current = d.key }
            }
            ParamButton("Curves", Icons.Rounded.ShowChart, current == "curves", if (!adjust.curves.isIdentity) "•" else null) { current = "curves" }
            ParamButton("HSL", Icons.Rounded.ColorLens, current == "hsl", if (!adjust.hsl.isIdentity) "•" else null) { current = "hsl" }
        }
        if (current == "hsl") {
            var band by remember { mutableStateOf(0) }
            val dots = listOf(0xFFE5484D, 0xFFF76B15, 0xFFFFD60A, 0xFF30A46C, 0xFF12A594, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                dots.forEachIndexed { i, col ->
                    val changed = adjust.hsl.hue[i] != 0f || adjust.hsl.sat[i] != 0f || adjust.hsl.lum[i] != 0f
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                            .border(if (band == i) 3.dp else if (changed) 1.5.dp else 0.dp, if (band == i) C.Text else C.Soft, CircleShape)
                            .clickable { band = i },
                    )
                }
            }
            val h = adjust.hsl
            ValueSlider(Icons.Rounded.Palette, "Hue", h.hue[band] * 100, -100f..100f, { "${it.roundToInt()}" }, Color(dots[band]), default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { a -> a.copy(hsl = a.hsl.with(band, h = v / 100f)) } }
            ValueSlider(Icons.Rounded.InvertColors, "Saturation", h.sat[band] * 100, -100f..100f, { "${it.roundToInt()}" }, Color(dots[band]), default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { a -> a.copy(hsl = a.hsl.with(band, s = v / 100f)) } }
            ValueSlider(Icons.Rounded.LightMode, "Lightness", h.lum[band] * 100, -100f..100f, { "${it.roundToInt()}" }, Color(dots[band]), default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { a -> a.copy(hsl = a.hsl.with(band, l = v / 100f)) } }
        } else if (current == "curves") {
            CurvesEditor(adjust.curves, onStart = vm::beginGesture, onEnd = vm::endGesture) { c -> set(true) { it.copy(curves = c) } }
        } else {
            val d = ADJ.first { it.key == current }
            ValueSlider(d.icon, d.label, d.get(adjust) * 100, (if (d.signed) -100f else 0f)..100f, { "${it.roundToInt()}" }, C.Accent, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                set(true) { d.set(it, v / 100f) }
            }
        }
    }
}

@Composable
fun ParamButton(label: String, icon: ImageVector, selected: Boolean, badge: String?, onClick: () -> Unit) {
    Column(Modifier.width(58.dp).clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.Surface2).then(if (selected) Modifier.border(2.dp, C.Accent, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) C.Accent else if (badge != null) C.Text else C.Muted, modifier = Modifier.size(20.dp))
            if (badge != null) Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp)).background(C.Text).padding(horizontal = 4.dp)) {
                Text(badge, style = Label.copy(fontSize = 9.sp, color = C.Bg))
            }
        }
        Text(label, style = Label.copy(fontSize = 10.sp, color = if (selected) C.Text else C.Muted), maxLines = 1)
    }
}

@Composable
fun PillButton(icon: ImageVector, label: String, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (accent) C.Accent else C.Surface2).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (accent) C.Bg else C.Text, modifier = Modifier.size(16.dp))
        Text(label, style = Body.copy(fontSize = 13.sp, color = if (accent) C.Bg else C.Text))
    }
}

@Composable
private fun CurvesEditor(curves: Curves, onStart: () -> Unit, onEnd: () -> Unit, onChange: (Curves) -> Unit) {
    var channel by remember { mutableIntStateOf(0) }
    val colors = listOf(C.Text, Color(0xFFFF5A5A), C.Good, C.Accent)
    val list = when (channel) { 1 -> curves.red; 2 -> curves.green; 3 -> curves.blue; else -> curves.master }
    val current by androidx.compose.runtime.rememberUpdatedState(list)
    val cur by androidx.compose.runtime.rememberUpdatedState(curves)
    var active by remember { mutableIntStateOf(-1) }
    fun set(pts: List<Pt>) = onChange(when (channel) { 1 -> cur.copy(red = pts); 2 -> cur.copy(green = pts); 3 -> cur.copy(blue = pts); else -> cur.copy(master = pts) })
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, c ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(c).then(if (i == channel) Modifier.border(3.dp, C.Accent, CircleShape) else Modifier).clickable { channel = i })
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Rounded.Refresh, "Reset curve", tint = C.Muted, size = 36.dp) { onStart(); set(Curves.IDENTITY); onEnd() }
    }
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(14.dp)).background(C.Track)
            .pointerInput(channel) {
                detectTapGestures(onDoubleTap = { o ->
                    val pts = current
                    val i = pts.indexOfFirst { abs(it.x * size.width - o.x) < 30 && abs((1 - it.y) * size.height - o.y) < 30 }
                    if (i > 0 && i < pts.lastIndex) { onStart(); set(pts.toMutableList().also { it.removeAt(i) }); onEnd() }
                })
            }
            .pointerInput(channel) {
                detectDragGestures(
                    onDragStart = { o ->
                        onStart()
                        val pts = current
                        val near = pts.indexOfFirst { abs(it.x * size.width - o.x) < 40 && abs((1 - it.y) * size.height - o.y) < 40 }
                        active = if (near >= 0) near else {
                            val np = Pt((o.x / size.width).coerceIn(0f, 1f), (1 - o.y / size.height).coerceIn(0f, 1f))
                            val l = (pts + np).sortedBy { it.x }
                            set(l)
                            l.indexOf(np)
                        }
                    },
                    onDragEnd = { active = -1; onEnd() },
                    onDragCancel = { active = -1; onEnd() },
                ) { ch, _ ->
                    val i = active
                    val pts = current
                    if (i in pts.indices) {
                        val lo = if (i == 0) 0f else pts[i - 1].x + 0.02f
                        val hi = if (i == pts.lastIndex) 1f else pts[i + 1].x - 0.02f
                        val x = if (i == 0 || i == pts.lastIndex) pts[i].x else (ch.position.x / size.width).coerceIn(lo, hi)
                        set(pts.toMutableList().also { it[i] = Pt(x, (1 - ch.position.y / size.height).coerceIn(0f, 1f)) })
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        for (k in 1..3) {
            drawLine(C.Line, Offset(w * k / 4, 0f), Offset(w * k / 4, h))
            drawLine(C.Line, Offset(0f, h * k / 4), Offset(w, h * k / 4))
        }
        drawLine(C.Dim, Offset(0f, h), Offset(w, 0f), 1.5f)
        val samples = com.reel.editor.fx.CurveLut.sample(list)
        val path = Path()
        for (i in 0 until 256) {
            val x = w * i / 255f
            val y = h * (1 - samples[i] / 255f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colors[channel], style = Stroke(2.5f.dp.toPx()))
        list.forEachIndexed { i, p ->
            val c = Offset(p.x * w, (1 - p.y) * h)
            drawCircle(if (i == active) colors[channel] else C.Track, 8.dp.toPx(), c)
            drawCircle(colors[channel], 8.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------- canvas

@Composable
fun CanvasPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val clip = vm.targetClip()
    var tab by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SheetHeader("Canvas", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Ratio", "Background"), tab, { tab = it }) })
        if (tab == 0) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.reel.editor.model.AspectRatio.entries.forEach { r ->
                    val sel = project.canvas.ratio == r
                    Column(
                        Modifier.width(60.dp).height(72.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) C.Surface2 else Color.Transparent).clickable { vm.edit { it.copy(canvas = it.canvas.copy(ratio = r)) } }.padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                    ) {
                        val (bw, bh) = if (r.w == 0) 14 to 24 else { val k = 24f / maxOf(r.w, r.h); (r.w * k).roundToInt() to (r.h * k).roundToInt() }
                        Box(Modifier.width(bw.dp).height(bh.dp).border(2.dp, if (sel) C.Accent else C.Dim, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(r.label, style = Label.copy(color = if (sel) C.Text else C.Muted))
                    }
                }
            }
            if (clip != null) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Icons.Rounded.Details, if (clip.fit == com.reel.editor.model.Fit.FIT) "Fill" else "Fit") {
                    vm.updateClip(clip.id) { it.copy(fit = if (it.fit == com.reel.editor.model.Fit.FIT) com.reel.editor.model.Fit.FILL else com.reel.editor.model.Fit.FIT) }
                }
                PillButton(Icons.Rounded.RotateRight, "Rotate") { vm.updateClip(clip.id) { it.copy(transform = it.transform.copy(rotation = (it.transform.rotation + 90f) % 360f)) } }
                PillButton(Icons.Rounded.Flip, "Flip") { vm.updateClip(clip.id) { it.copy(flipH = !it.flipH) } }
            }
        } else {
            val bg = project.canvas.background
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val blurSel = bg.mode == com.reel.editor.model.BackgroundMode.BLUR
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFF9A62), Color(0xFF3B2A66)))).then(if (blurSel) Modifier.border(2.dp, C.Accent, CircleShape) else Modifier)
                    .clickable { vm.edit { it.copy(canvas = it.canvas.copy(background = bg.copy(mode = com.reel.editor.model.BackgroundMode.BLUR))) } }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.BlurOn, "Blur", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                listOf(0xFF000000, 0xFFFFFFFF, 0xFFFF7A59, 0xFFF2D4A8, 0xFF2DD4BF, 0xFF4C8DFF, 0xFFA78BFA, 0xFF1E1E27).forEach { col ->
                    val sel = bg.mode == com.reel.editor.model.BackgroundMode.COLOR && bg.color == col
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(col)).border(if (sel) 3.dp else 1.dp, if (sel) C.Accent else C.Line, CircleShape)
                        .clickable { vm.edit { it.copy(canvas = it.canvas.copy(background = bg.copy(mode = com.reel.editor.model.BackgroundMode.COLOR, color = col))) } })
                }
            }
            if (bg.mode == com.reel.editor.model.BackgroundMode.BLUR) {
                ValueSlider(Icons.Rounded.BlurOn, "Blur", bg.blur * 100, 0f..100f, { "${it.roundToInt()}" }, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                    vm.live { it.copy(canvas = it.canvas.copy(background = it.canvas.background.copy(blur = v / 100f))) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- chroma key

@Composable
fun ChromaPanel(vm: EditorViewModel, clip: Clip, picking: Boolean, onPick: () -> Unit, onClose: () -> Unit) {
    ChromaBody(vm, clip.chroma, picking, onPick, onClose) { g, f -> vm.updateClip(clip.id, g) { it.copy(chroma = f(it.chroma)) } }
}

@Composable
fun ChromaBody(vm: EditorViewModel, k: com.reel.editor.model.ChromaKey, picking: Boolean, onPick: () -> Unit, onClose: () -> Unit, set: (Boolean, (com.reel.editor.model.ChromaKey) -> com.reel.editor.model.ChromaKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetHeader("Chroma key", Icons.Rounded.InvertColors, iconTint = C.Good, onClose = onClose, onDone = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (picking) C.Accent else C.Text).clickable(onClick = onPick), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Colorize, "Pick color from video", tint = C.Bg)
            }
            listOf(0xFF2BD46A, 0xFF1F6FFF).forEach { col ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(col)).border(if (k.color == col && k.enabled) 3.dp else 0.dp, C.Accent, CircleShape)
                    .clickable { set(false) { it.copy(enabled = true, color = col) } })
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Surface2).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(k.color)))
                Text("#%06X".format(k.color and 0xFFFFFF), style = Mono.copy(color = C.Text))
            }
            Spacer(Modifier.weight(1f))
            Switch(k.enabled, { e -> set(false) { it.copy(enabled = e) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
        }
        ValueSlider(Icons.Rounded.InvertColors, "Strength", k.similarity * 100, 0f..100f, { "${it.roundToInt()}" }, C.Good, default = 60f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(similarity = v / 100f, enabled = true) } }
        ValueSlider(Icons.Rounded.BlurOn, "Edge", k.smoothness * 100, 0f..100f, { "${it.roundToInt()}" }, C.Good, default = 20f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(smoothness = v / 100f) } }
        ValueSlider(Icons.Rounded.Opacity, "Spill", k.spill * 100, 0f..100f, { "${it.roundToInt()}" }, C.Good, default = 30f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(spill = v / 100f) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() = this.collectAsStateWithLifecycle()

@Suppress("unused")
private fun lnSafe(x: Float) = ln(x.coerceAtLeast(1e-6f))

@Composable
fun DeleteRow(onDelete: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp)) { PillButton(Icons.Rounded.Delete, "Delete", onClick = onDelete) }
}
