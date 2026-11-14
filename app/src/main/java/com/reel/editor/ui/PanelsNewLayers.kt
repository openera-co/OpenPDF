package com.reel.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.edit.Panel
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.fx.TextMotion
import com.reel.editor.media.MediaLibrary
import com.reel.editor.model.AdjustmentLayer
import com.reel.editor.model.BlendMode
import com.reel.editor.model.FxRef
import com.reel.editor.model.MaskShape
import com.reel.editor.model.OverlayClip
import com.reel.editor.model.ShapeLayer
import com.reel.editor.model.TextAnim
import com.reel.editor.model.interpolateValue
import com.reel.editor.model.keyAt
import com.reel.editor.model.setKey
import kotlin.math.roundToInt

private val SHAPE_COLORS = listOf(0xFFFFFFFF, 0xFF0B0B0F, 0xFFFF7A59, 0xFFE8576B, 0xFFFFD36B, 0xFF3DDC84, 0xFF2DD4BF, 0xFF4C8DFF, 0xFFA78BFA, 0xFFF472B6)

@Composable
private fun ShapeThumb(kind: String, color: Long = 0xFFFFFFFF, selected: Boolean = false, onClick: () -> Unit) {
    val img = remember(kind, color) {
        LayerBitmaps.shapeLayer(ShapeLayer(shape = kind, fill = color, startUs = 0, durationUs = 1, aspect = if (kind == "line" || kind == "arrow") 2f else 1f), 100).asImageBitmap()
    }
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2)
            .then(if (selected) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Image(img, kind, Modifier.size(32.dp)) }
}

@Composable
private fun AddTile(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Text(label, style = Label.copy(color = C.Text), maxLines = 1)
    }
}

/** Everything that can sit above the video. */
@Composable
fun AddLayerPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val picker = LocalPicker.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SheetHeader("Layer", Icons.Rounded.Layers, onClose = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AddTile(Icons.Rounded.PictureInPicture, "Overlay", C.Overlay, Modifier.weight(1f)) {
                picker.open(PickerRequest(setOf(MediaLibrary.Kind.VIDEO, MediaLibrary.Kind.PHOTO), multi = true) { uris -> if (uris.isNotEmpty()) { vm.addOverlays(uris); vm.openPanel(null) } })
            }
            AddTile(Icons.Rounded.Tune, "Adjust", C.Accent, Modifier.weight(1f)) { vm.addAdjustmentLayer() }
            AddTile(Icons.Rounded.TextFields, "Text", C.TextTrack, Modifier.weight(1f)) { vm.addText() }
            AddTile(Icons.Rounded.EmojiEmotions, "Sticker", C.Effect, Modifier.weight(1f)) { vm.openPanel(Panel.STICKERS) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Category, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            LayerBitmaps.shapeKinds.forEach { k -> ShapeThumb(k) { vm.addShape(k); vm.openPanel(Panel.SHAPE) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- shape

@Composable
fun ShapePanel(vm: EditorViewModel, l: ShapeLayer, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    fun set(g: Boolean = false, f: (ShapeLayer) -> ShapeLayer) = vm.updateShape(l.id, g, f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Shape", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Shape", "Fill", "Line"), tab, { tab = it }) })
        when (tab) {
            0 -> {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayerBitmaps.shapeKinds.forEach { k -> ShapeThumb(k, l.fill ?: 0xFFFFFFFF, l.shape == k) { set { it.copy(shape = k) } } }
                }
                ValueSlider(Icons.Rounded.CropSquare, "Width", l.aspect * 100, 20f..400f, { "${it.roundToInt()}" }, C.Effect, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(aspect = v / 100f) } }
                if (l.shape == "roundrect" || l.shape == "bubble") {
                    ValueSlider(Icons.Rounded.RoundedCorner, "Corner", l.corner * 200, 0f..100f, { "${it.roundToInt()}" }, C.Effect, default = 36f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(corner = v / 200f) } }
                }
            }
            1 -> ColorRow(l.fill, allowNone = l.strokeWidth > 0f) { c -> set { it.copy(fill = c) } }
            else -> {
                ColorRow(l.stroke, allowNone = false) { c -> set { it.copy(stroke = c ?: 0xFFFFFFFF, strokeWidth = if (it.strokeWidth == 0f) 4f else it.strokeWidth) } }
                ValueSlider(Icons.Rounded.LineWeight, "Width", l.strokeWidth, 0f..30f, { "${it.roundToInt()}" }, C.Effect, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                    // A shape with no fill needs a visible outline.
                    set(true) { it.copy(strokeWidth = if (it.fill == null) v.coerceAtLeast(1f) else v) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColorRow(current: Long?, allowNone: Boolean, onPick: (Long?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowNone) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(C.Surface2).border(if (current == null) 3.dp else 1.dp, if (current == null) C.Accent else C.Line, CircleShape).clickable { onPick(null) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Block, "No fill", tint = C.Muted, modifier = Modifier.size(18.dp))
            }
        }
        SHAPE_COLORS.forEach { c ->
            val sel = current == c
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c)).border(if (sel) 3.dp else 1.dp, if (sel) C.Accent else C.Line, CircleShape).clickable { onPick(c) })
        }
    }
}

// ---------------------------------------------------------------------------- animation for shapes and overlays

@Composable
fun LayerAnimPanel(vm: EditorViewModel, startUs: Long, durationUs: Long, animIn: TextAnim?, animOut: TextAnim?, onClose: () -> Unit, set: (inAnim: Boolean, TextAnim?) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    val ids = if (sub == 0) TextMotion.inAnims.filter { it != "typewriter" && it != "wipe" } else TextMotion.outAnims
    val current = if (sub == 0) animIn else animOut
    val tick = rememberThumbTick()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Animate", onClose = onClose, onDone = onClose, center = { Segmented(listOf("In", "Out"), sub, { sub = it }) })
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 190.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ids) { id ->
                TextAnimTile(id, sub, (current?.id ?: "none") == id, tick) {
                    set(sub == 0, if (id == "none") null else TextAnim(id, current?.durationUs ?: 400_000))
                    vm.preview.seekTo(if (sub == 1) (startUs + durationUs - 900_000).coerceAtLeast(startUs) else startUs)
                    vm.preview.play()
                }
            }
        }
        if (current != null) {
            ValueSlider(Icons.Rounded.Opacity, "Duration", current.durationUs / 1e6f, 0.1f..(durationUs / 2e6f).coerceAtLeast(0.2f), { "%.1fs".format(it) }, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                set(sub == 0, current.copy(durationUs = (v * 1e6).toLong()))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- adjustment layer

@Composable
fun AdjustLayerPanel(vm: EditorViewModel, l: AdjustmentLayer, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val catalog = remember { FxCatalog.get(ctx) }
    val pos by vm.preview.positionUs.collectAsStateCompat()
    var tab by remember { mutableIntStateOf(0) }
    val local = pos - l.startUs
    val inside = local in 0..l.durationUs
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Adjustment", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Adjust", "Look", "Effect"), tab, { tab = it }) })
        val shown = interpolateValue(l.amount, l.amountKeys, local)
        ValueSlider(
            Icons.Rounded.Gradient, "Amount", shown * 100, 0f..100f, { "${it.roundToInt()}" }, C.Accent, default = 100f,
            key = SliderKey(atPlayhead = inside && l.amountKeys.keyAt(local) != null, any = l.amountKeys.isNotEmpty()) {
                if (!inside) return@SliderKey
                vm.updateAdjustLayer(l.id) { a ->
                    val ex = a.amountKeys.keyAt(local)
                    if (ex != null) a.copy(amountKeys = a.amountKeys - ex) else a.copy(amountKeys = a.amountKeys.setKey(local, interpolateValue(a.amount, a.amountKeys, local)))
                }
            },
            onStart = vm::beginGesture, onEnd = vm::endGesture,
        ) { v ->
            vm.updateAdjustLayer(l.id, true) { a -> if (a.amountKeys.isEmpty() || !inside) a.copy(amount = v / 100f) else a.copy(amountKeys = a.amountKeys.setKey(local, v / 100f)) }
        }
        when (tab) {
            0 -> AdjustBody(vm, l.adjust) { g, f -> vm.updateAdjustLayer(l.id, g) { it.copy(adjust = f(it.adjust)) } }
            1 -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FxTile("None", l.lookId == null, Icons.Rounded.Block) { vm.updateAdjustLayer(l.id) { it.copy(lookId = null) } }
                catalog.looks.forEach { look ->
                    Box(Modifier.width(64.dp)) { FxTile(look.name, l.lookId == look.id, Icons.Rounded.FilterVintage) { vm.updateAdjustLayer(l.id) { it.copy(lookId = look.id) } } }
                }
            }
            else -> {
                val tick = rememberThumbTick()
                val src = rememberTargetSource(vm, vm.targetClip())
                val currentFx = l.effects.firstOrNull()
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 190.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { FxTile("None", currentFx == null, Icons.Rounded.Block) { vm.updateAdjustLayer(l.id) { it.copy(effects = emptyList()) } } }
                    items(catalog.effects, key = { it.id }) { f ->
                        ShaderFxTile(catalog, f, com.reel.editor.fx.FxThumbs.Mode.STATIC, src, tick, currentFx?.id == f.id, fxBrush(f), Icons.Rounded.AutoAwesome) { vm.updateAdjustLayer(l.id) { it.copy(effects = listOf(FxRef(f.id))) } }
                    }
                }
                if (currentFx != null) catalog.fx(currentFx.id)?.let { def -> ParamSliders(vm, def, currentFx) { r -> vm.updateAdjustLayer(l.id, true) { it.copy(effects = listOf(r)) } } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- overlay

private fun blendIcon(b: BlendMode): ImageVector = when (b) {
    BlendMode.NORMAL -> Icons.Rounded.Layers
    BlendMode.SCREEN, BlendMode.LIGHTEN, BlendMode.ADD -> Icons.Rounded.AutoAwesome
    BlendMode.MULTIPLY, BlendMode.DARKEN -> Icons.Rounded.FlipToBack
    else -> Icons.Rounded.Gradient
}

@Composable
fun OverlayPanel(vm: EditorViewModel, o: OverlayClip, onClose: () -> Unit) {
    val pos by vm.preview.positionUs.collectAsStateCompat()
    val local = pos - o.startUs
    val inside = local in 0..o.timelineDurationUs
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Overlay", Icons.Rounded.PictureInPicture, iconTint = C.Overlay, onClose = onClose, onDone = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlendMode.entries.forEach { b -> Chip(b.label, o.blend == b, icon = blendIcon(b)) { vm.updateOverlay(o.id) { it.copy(blend = b) } } }
        }
        val t = com.reel.editor.model.interpolate(o.transform, o.keyframes, local)
        ValueSlider(Icons.Rounded.Opacity, "Opacity", t.opacity * 100, 0f..100f, { "${it.roundToInt()}%" }, C.Overlay, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.setTransformAtPlayhead(true) { it.copy(opacity = v / 100f) }
        }
        ValueSlider(Icons.Rounded.ZoomOutMap, "Scale", t.scale * 100, 5f..300f, { "${it.roundToInt()}%" }, C.Overlay, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.setTransformAtPlayhead(true) { it.copy(scale = v / 100f) }
        }
        ValueSlider(Icons.Rounded.RotateRight, "Rotation", t.rotation, -180f..180f, { "${it.roundToInt()}°" }, C.Overlay, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.setTransformAtPlayhead(true) { it.copy(rotation = v) }
        }
        if (o.hasAudio && o.kind == com.reel.editor.model.ClipKind.VIDEO) {
            val vol = interpolateValue(o.volume, o.volumeKeys, local)
            ValueSlider(
                Icons.Rounded.VolumeUp, "Volume", vol * 100, 0f..200f, { "${it.roundToInt()}" }, C.Music, default = 100f,
                key = SliderKey(inside && o.volumeKeys.keyAt(local) != null, o.volumeKeys.isNotEmpty()) {
                    if (!inside) return@SliderKey
                    vm.updateOverlay(o.id) { x ->
                        val ex = x.volumeKeys.keyAt(local)
                        if (ex != null) x.copy(volumeKeys = x.volumeKeys - ex) else x.copy(volumeKeys = x.volumeKeys.setKey(local, interpolateValue(x.volume, x.volumeKeys, local)))
                    }
                },
                onStart = vm::beginGesture, onEnd = vm::endGesture,
            ) { v -> vm.updateOverlay(o.id, true) { x -> if (x.volumeKeys.isEmpty() || !inside) x.copy(volume = v / 100f, muted = false) else x.copy(volumeKeys = x.volumeKeys.setKey(local, v / 100f)) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun maskIcon(m: MaskShape): ImageVector = when (m) {
    MaskShape.NONE -> Icons.Rounded.Block
    MaskShape.CIRCLE -> Icons.Rounded.RoundedCorner
    MaskShape.RECT -> Icons.Rounded.CropSquare
    MaskShape.LINEAR -> Icons.Rounded.Gradient
    MaskShape.HEART -> Icons.Rounded.EmojiEmotions
    MaskShape.STAR -> Icons.Rounded.Star
}

@Composable
private fun MaskTile(m: MaskShape, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2)
            .then(if (selected) Modifier.border(2.dp, C.Overlay, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val kind = when (m) { MaskShape.CIRCLE -> "circle"; MaskShape.RECT -> "rect"; MaskShape.HEART -> "heart"; MaskShape.STAR -> "star"; else -> null }
        if (kind != null) {
            val img = remember(kind) { LayerBitmaps.shapeLayer(ShapeLayer(shape = kind, startUs = 0, durationUs = 1, fill = 0xFFFFFFFF), 100).asImageBitmap() }
            Image(img, m.name, Modifier.size(28.dp))
        } else if (m == MaskShape.LINEAR) {
            Canvas(Modifier.size(28.dp)) {
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color.Transparent)))
            }
        } else Icon(maskIcon(m), "No mask", tint = C.Muted)
    }
}

@Composable
fun MaskPanel(vm: EditorViewModel, o: OverlayClip, onClose: () -> Unit) {
    val m = o.mask
    fun set(g: Boolean = false, f: (com.reel.editor.model.Mask) -> com.reel.editor.model.Mask) = vm.updateOverlay(o.id, g) { it.copy(mask = f(it.mask)) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var tab by remember { mutableIntStateOf(0) }
        SheetHeader("Mask", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Shape", "Place"), tab, { tab = it }) })
        if (tab == 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MaskShape.entries.forEach { s -> MaskTile(s, m.shape == s) { set { it.copy(shape = s) } } }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.FlipToBack, "Invert", tint = if (m.invert) C.Overlay else C.Muted)
                Switch(m.invert, { v -> set { it.copy(invert = v) } }, enabled = m.shape != MaskShape.NONE, colors = SwitchDefaults.colors(checkedTrackColor = C.Overlay))
            }
            if (m.shape != MaskShape.NONE) {
                ValueSlider(Icons.Rounded.BorderStyle, "Feather", m.feather * 100, 0f..100f, { "${it.roundToInt()}" }, C.Overlay, default = 15f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(feather = v / 100f) } }
                ValueSlider(Icons.Rounded.ZoomOutMap, "Size", m.w * 100, 5f..150f, { "${it.roundToInt()}" }, C.Overlay, default = 70f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(w = v / 100f, h = if (it.shape == MaskShape.RECT) it.h else v / 100f) } }
            }
        } else if (m.shape == MaskShape.NONE) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Block, null, tint = C.Muted) }
        } else {
            if (m.shape == MaskShape.RECT) ValueSlider(Icons.Rounded.CropSquare, "Height", m.h * 100, 5f..150f, { "${it.roundToInt()}" }, C.Overlay, default = 70f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(h = v / 100f) } }
            ValueSlider(Icons.Rounded.OpenWith, "X", m.x * 100, 0f..100f, { "${it.roundToInt()}" }, C.Overlay, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(x = v / 100f) } }
            ValueSlider(Icons.Rounded.OpenWith, "Y", m.y * 100, 0f..100f, { "${it.roundToInt()}" }, C.Overlay, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(y = v / 100f) } }
            ValueSlider(Icons.Rounded.RotateRight, "Rotation", m.rotation, -180f..180f, { "${it.roundToInt()}°" }, C.Overlay, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> set(true) { it.copy(rotation = v) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- head tracking

/** Follow a head with the selected layer, or reframe the selected clip around it. */
@Composable
fun TrackPanel(vm: EditorViewModel, clipMode: Boolean, onClose: () -> Unit) {
    val progress by vm.trackProgress.collectAsStateCompat()
    var followSize by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader(if (clipMode) "Reframe" else "Track", Icons.Rounded.Face, iconTint = C.Accent, onClose = { vm.cancelTracking(); onClose() }, onDone = onClose)
        val p = progress
        if (p != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape), color = C.Accent, trackColor = C.Surface2)
                Text("${(p * 100).roundToInt()}%", style = Mono)
                IconBtn(Icons.Rounded.Close, "Stop", background = C.Surface2, size = 36.dp) { vm.cancelTracking() }
            }
        } else if (clipMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Icons.Rounded.CenterFocusStrong, "Center", accent = true) { vm.reframeClip(0.5f) }
                PillButton(Icons.AutoMirrored.Rounded.FormatAlignLeft, "Left third") { vm.reframeClip(0.36f) }
                PillButton(Icons.AutoMirrored.Rounded.FormatAlignRight, "Right third") { vm.reframeClip(0.64f) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Icons.Rounded.Face, "Follow head", accent = true) { vm.trackHead(followSize) }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ZoomOutMap, "Follow size", tint = if (followSize) C.Accent else C.Muted)
                Switch(followSize, { followSize = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------- crop

private val CROP_RATIOS = listOf("Free" to 0f, "1:1" to 1f, "9:16" to 9f / 16f, "4:5" to 0.8f, "3:4" to 0.75f, "16:9" to 16f / 9f, "4:3" to 4f / 3f)

/** Crops the clip: a ratio sets the biggest crop of that shape, then zoom and move it. */
@Composable
fun CropPanel(vm: EditorViewModel, clip: com.reel.editor.model.Clip, onClose: () -> Unit) {
    val (dw, dh) = com.reel.editor.fx.RenderPlans.displaySize(clip)
    val srcAspect = dw.toFloat() / dh.coerceAtLeast(1)
    val cr = clip.crop
    var ratio by remember(clip.id) { mutableStateOf(-1f) }
    // Zoom is measured against the ratio's own full-size crop.
    val full = com.reel.editor.model.CropRect.forRatio(if (ratio > 0f) ratio else 0f, srcAspect).let { if (ratio > 0f) it else com.reel.editor.model.CropRect() }
    val zoom = (full.w / cr.w.coerceAtLeast(0.01f)).coerceIn(1f, 8f)
    fun set(g: Boolean = false, f: (com.reel.editor.model.CropRect) -> com.reel.editor.model.CropRect) = vm.updateClip(clip.id, g) { it.copy(crop = f(it.crop)) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetHeader("Crop", Icons.Rounded.Crop, onClose = onClose, onDone = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CROP_RATIOS.forEach { (label, r) ->
                Chip(label, ratio == r) {
                    ratio = r
                    set { com.reel.editor.model.CropRect.forRatio(r, srcAspect) }
                }
            }
        }
        ValueSlider(Icons.Rounded.ZoomOutMap, "Zoom", zoom * 100, 100f..400f, { "${it.roundToInt()}%" }, C.Accent, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            set(true) { c -> val k = v / 100f; val nw = full.w / k; val nh = full.h / k; com.reel.editor.model.CropRect(0f, 0f, nw, nh).centredAt(c.x + c.w / 2, c.y + c.h / 2) }
        }
        if (cr.w < 0.999f) ValueSlider(Icons.Rounded.OpenWith, "X", (cr.x + cr.w / 2) * 100, (cr.w / 2 * 100)..((1 - cr.w / 2) * 100), { "${it.roundToInt()}" }, C.Accent, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            set(true) { it.centredAt(v / 100f, it.y + it.h / 2) }
        }
        if (cr.h < 0.999f) ValueSlider(Icons.Rounded.OpenWith, "Y", (cr.y + cr.h / 2) * 100, (cr.h / 2 * 100)..((1 - cr.h / 2) * 100), { "${it.roundToInt()}" }, C.Accent, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            set(true) { it.centredAt(it.x + it.w / 2, v / 100f) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(Icons.Rounded.Refresh, "Reset") { ratio = -1f; set { com.reel.editor.model.CropRect() } }
        }
        Spacer(Modifier.height(8.dp))
    }
}
