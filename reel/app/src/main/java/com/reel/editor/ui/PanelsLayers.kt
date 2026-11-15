package com.reel.editor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.model.setKey
import com.reel.editor.model.keyAt
import com.reel.editor.edit.Selection
import com.reel.editor.fx.Fonts
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.fx.TextMotion
import com.reel.editor.model.AudioKind
import com.reel.editor.model.Easing
import com.reel.editor.model.TextAlignment
import com.reel.editor.model.TextAnim
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.interpolate
import com.reel.editor.model.ease
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val SWATCHES = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFF7A59, 0xFFE8576B, 0xFFFFD36B, 0xFF3DDC84, 0xFF2DD4BF, 0xFF4C8DFF, 0xFFA78BFA, 0xFFF472B6)

@Composable
fun TextPanel(vm: EditorViewModel, layer: TextLayer, startTab: Int, onClose: () -> Unit) {
    var tab by remember(layer.id) { mutableIntStateOf(startTab) }
    fun update(gesture: Boolean = false, f: (TextLayer) -> TextLayer) {
        val change: (com.reel.editor.model.Project) -> com.reel.editor.model.Project = { p -> p.copy(texts = p.texts.map { if (it.id == layer.id) f(it) else it }) }
        if (gesture) vm.live(change) else vm.edit(change)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Text", onClose = onClose, onDone = onClose, center = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Icons.Rounded.TextFields to "Edit", Icons.Rounded.FormatColorFill to "Style", Icons.Rounded.Animation to "Animate").forEachIndexed { i, (ic, d) ->
                    IconBtn(ic, d, tint = if (tab == i) C.Accent else C.Muted, background = if (tab == i) C.Surface2 else Color.Transparent, size = 40.dp, iconSize = 20.dp) { tab = i }
                }
            }
        })
        when (tab) {
            0 -> {
                val focus = remember { FocusRequester() }
                var text by remember(layer.id) { mutableStateOf(layer.text) }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; update(true) { l -> l.copy(text = it) } },
                    textStyle = Body.copy(fontSize = 17.sp),
                    cursorBrush = SolidColor(C.Accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(C.Bg).padding(14.dp).focusRequester(focus),
                )
                LaunchedEffect(layer.id) { delay(150); runCatching { focus.requestFocus() } }
                if (!layer.isCaption) TextStylePresets(layer.style) { st -> update { it.copy(style = st.copy(sizeSp = it.style.sizeSp, align = it.style.align, curve = it.style.curve)) } }
                FontPicker(layer.style.font) { id -> update { it.copy(style = it.style.copy(font = id)) } }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(TextAlignment.START to Icons.Rounded.FormatAlignLeft, TextAlignment.CENTER to Icons.Rounded.FormatAlignCenter, TextAlignment.END to Icons.Rounded.FormatAlignRight).forEach { (a, ic) ->
                        IconBtn(ic, a.name, tint = if (layer.style.align == a) C.Accent else C.Muted, background = C.Surface2, size = 40.dp) { update { it.copy(style = it.style.copy(align = a)) } }
                    }
                    IconBtn(Icons.Rounded.FormatBold, "Bold", tint = if (layer.style.bold) C.Accent else C.Muted, background = C.Surface2, size = 40.dp) { update { it.copy(style = it.style.copy(bold = !it.style.bold)) } }
                    IconBtn(Icons.Rounded.FormatItalic, "Italic", tint = if (layer.style.italic) C.Accent else C.Muted, background = C.Surface2, size = 40.dp) { update { it.copy(style = it.style.copy(italic = !it.style.italic)) } }
                    Spacer(Modifier.weight(1f))
                    IconBtn(Icons.Rounded.Delete, "Delete text", tint = C.Muted, background = C.Surface2, size = 40.dp) { vm.deleteSelected() }
                }
            }
            1 -> {
                var sub by remember { mutableIntStateOf(0) }
                Segmented(listOf("Color", "Fade", "Stroke", "Label", "Size"), sub, { sub = it }, Modifier.padding(horizontal = 12.dp))
                when (sub) {
                    0 -> SwatchRow(layer.style.color) { c -> update { it.copy(style = it.style.copy(color = c)) } }
                    // Second colour for a top-to-bottom gradient.
                    1 -> SwatchRow(layer.style.gradient ?: 0, allowNone = true) { c -> update { it.copy(style = it.style.copy(gradient = if (c == 0L) null else c)) } }
                    2 -> {
                        SwatchRow(layer.style.strokeColor) { c -> update { it.copy(style = it.style.copy(strokeColor = c, strokeWidth = if (it.style.strokeWidth == 0f) 3f else it.style.strokeWidth)) } }
                        ValueSlider(Icons.Rounded.BorderColor, "Stroke", layer.style.strokeWidth, 0f..10f, { "%.1f".format(it) }, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> update(true) { it.copy(style = it.style.copy(strokeWidth = v)) } }
                    }
                    3 -> SwatchRow(layer.style.labelColor ?: 0, allowNone = true) { c -> update { it.copy(style = it.style.copy(labelColor = if (c == 0L) null else c)) } }
                    4 -> {
                        ValueSlider(Icons.Rounded.FormatSize, "Size", layer.style.sizeSp, 14f..140f, { "${it.roundToInt()}" }, default = 64f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> update(true) { it.copy(style = it.style.copy(sizeSp = v)) } }
                        ValueSlider(Icons.Rounded.SpaceBar, "Spacing", layer.style.letterSpacing * 100, -5f..40f, { "${it.roundToInt()}" }, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> update(true) { it.copy(style = it.style.copy(letterSpacing = v / 100f)) } }
                        // Bend one-line text along an arc.
                        ValueSlider(Icons.Rounded.Gradient, "Curve", layer.style.curve * 100, -100f..100f, { "${it.roundToInt()}" }, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> update(true) { it.copy(style = it.style.copy(curve = v / 100f)) } }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEXT_PRESETS.forEach { (name, st) ->
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(C.Bg).clickable { update { it.copy(style = st.copy(sizeSp = it.style.sizeSp, font = st.font)) } }, contentAlignment = Alignment.Center) {
                            Text(name, style = Body.copy(fontSize = 20.sp, color = Color(st.color), fontWeight = FontWeight.Black, background = st.labelColor?.let { Color(it) } ?: Color.Transparent))
                        }
                    }
                }
            }
            else -> {
                var sub by remember { mutableIntStateOf(0) }
                Segmented(listOf("In", "Out", "Loop"), sub, { sub = it }, Modifier.padding(horizontal = 12.dp))
                val ids = when (sub) { 0 -> TextMotion.inAnims; 1 -> TextMotion.outAnims; else -> TextMotion.loopAnims }
                val current = when (sub) { 0 -> layer.animIn; 1 -> layer.animOut; else -> layer.loop }
                val tick = rememberThumbTick()
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 170.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ids) { id ->
                        val sel = (current?.id ?: "none") == id
                        TextAnimTile(id, sub, sel, tick) {
                            val a = if (id == "none") null else TextAnim(id, current?.durationUs ?: if (sub == 2) 0 else 500_000)
                            update { when (sub) { 0 -> it.copy(animIn = a); 1 -> it.copy(animOut = a); else -> it.copy(loop = a) } }
                            vm.preview.seekTo(if (sub == 1) (layer.startUs + layer.durationUs - 900_000).coerceAtLeast(layer.startUs) else layer.startUs)
                            vm.preview.play()
                        }
                    }
                }
                if (current != null && sub < 2) {
                    ValueSlider(Icons.Rounded.AccessTime, "Duration", current.durationUs / 1e6f, 0.1f..(layer.durationUs / 2e6f).coerceAtLeast(0.2f), { "%.1fs".format(it) }, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                        update(true) { if (sub == 0) it.copy(animIn = it.animIn?.copy(durationUs = (v * 1e6).toLong())) else it.copy(animOut = it.animOut?.copy(durationUs = (v * 1e6).toLong())) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal val TEXT_PRESETS = listOf(
    "Aa" to TextStyle(color = 0xFFFFFFFF, shadow = true),
    "Aa" to TextStyle(color = 0xFFFFD36B, strokeColor = 0xFF1E1A2E, strokeWidth = 3f, shadow = false, font = "black"),
    "Aa" to TextStyle(color = 0xFF0B0B0F, labelColor = 0xFFFFFFFF, shadow = false),
    "Aa" to TextStyle(color = 0xFFFFFFFF, labelColor = 0xFFFF4D6D, shadow = false, font = "condensed"),
    "Aa" to TextStyle(color = 0xFF2DD4BF, strokeColor = 0xFF000000, strokeWidth = 4f, font = "black"),
    "Aa" to TextStyle(color = 0xFFFFFFFF, font = "serif-italic", shadow = true),
    "Aa" to TextStyle(color = 0xFFFFFFFF, strokeColor = 0xFF000000, strokeWidth = 4f, font = "montserrat", bold = true, shadow = false),
    "Aa" to TextStyle(color = 0xFFFFD36B, font = "anton", shadow = true),
    "Aa" to TextStyle(color = 0xFF0B0B0F, labelColor = 0xFFFFD36B, font = "bebasneue", shadow = false),
    "Aa" to TextStyle(color = 0xFFFFFFFF, font = "pacifico", shadow = true),
    "Aa" to TextStyle(color = 0xFF2DD4BF, strokeColor = 0xFF0B0B0F, strokeWidth = 3f, font = "bangers", shadow = false),
)


@Composable
private fun SwatchRow(current: Long, allowNone: Boolean = false, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowNone) Box(Modifier.size(36.dp).clip(CircleShape).background(C.Bg).border(if (current == 0L) 3.dp else 1.dp, if (current == 0L) C.Accent else C.Line, CircleShape).clickable { onPick(0) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Block, "None", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
        SWATCHES.forEach { c ->
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c)).border(if (current == c) 3.dp else 1.dp, if (current == c) C.Accent else C.Line, CircleShape).clickable { onPick(c) })
        }
    }
}

// ---------------------------------------------------------------------------- stickers

@Composable
fun StickerPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val picker = LocalPicker.current
    val pickImage = {
        picker.open(PickerRequest(setOf(com.reel.editor.media.MediaLibrary.Kind.PHOTO), multi = false) { list ->
            list.firstOrNull()?.let { uri ->
                runCatching { vm.getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                vm.addSticker(uri.toString())
                onClose()
            }
        })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Stickers", onClose = onClose)
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 170.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface2).clickable { pickImage() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Image, "Image from gallery", tint = C.Text)
                }
            }
            items(LayerBitmaps.stickerShapes) { s ->
                val bmp = remember(s) { LayerBitmaps.shape(s, 128).asImageBitmap() }
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface2).clickable { vm.addSticker("shape:$s"); onClose() }, contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(bmp, s, Modifier.size(40.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}


// ---------------------------------------------------------------------------- audio

@Composable
fun AudioPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val selected = (selection as? Selection.AudioSel)?.let { s -> project.audio.firstOrNull { it.id == s.id } }
    val picker = LocalPicker.current
    val pickMusic = { picker.open(PickerRequest(setOf(com.reel.editor.media.MediaLibrary.Kind.AUDIO), multi = false, audioFromVideo = true) { u -> u.firstOrNull()?.let { vm.addAudio(it, AudioKind.MUSIC) } }) }
    var showSounds by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Pair<MediaRecorder, File>?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    var recPos by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) vm.toast("Microphone permission is needed to record") }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { askMic.launch(Manifest.permission.RECORD_AUDIO); return }
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val f = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(f.absolutePath)
        }
        try {
            r.prepare(); r.start()
        } catch (e: Exception) { r.release(); vm.toast("Couldn't start recording"); return }
        recStart = System.currentTimeMillis()
        recPos = vm.preview.positionUs.value
        recording = r to f
        vm.preview.setVolumeMuted(true)
        vm.preview.play()
    }

    fun stopRec() {
        val (r, f) = recording ?: return
        recording = null
        vm.preview.pause()
        vm.preview.setVolumeMuted(false)
        runCatching { r.stop() }
        r.release()
        if (f.length() > 0) {
            vm.preview.seekTo(recPos)
            vm.addAudio(Uri.fromFile(f), AudioKind.VOICEOVER, "Voiceover")
        }
    }

    LaunchedEffect(recording) { while (recording != null) { elapsed = System.currentTimeMillis() - recStart; delay(100) } }
    DisposableEffect(Unit) { onDispose { recording?.let { (r, _) -> runCatching { r.stop() }; r.release() }; vm.preview.setVolumeMuted(false) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Audio", Icons.Rounded.LibraryMusic, iconTint = C.Music, onClose = onClose, onDone = onClose)
        if (selected != null) {
            val pos by vm.preview.positionUs.collectAsStateCompat()
            val local = pos - selected.startUs
            val inside = local in 0..selected.durationUs
            fun upd(f: (com.reel.editor.model.AudioItem) -> com.reel.editor.model.AudioItem) = vm.live { p -> p.copy(audio = p.audio.map { if (it.id == selected.id) f(it) else it }) }
            ValueSlider(
                Icons.Rounded.VolumeUp, "Volume", com.reel.editor.model.interpolateValue(selected.volume, selected.volumeKeys, local) * 100, 0f..200f, { "${it.roundToInt()}%" }, C.Music, default = 100f,
                key = SliderKey(inside && selected.volumeKeys.keyAt(local) != null, selected.volumeKeys.isNotEmpty()) {
                    if (!inside) return@SliderKey
                    vm.beginGesture()
                    upd { a ->
                        val ex = a.volumeKeys.keyAt(local)
                        if (ex != null) a.copy(volumeKeys = a.volumeKeys - ex) else a.copy(volumeKeys = a.volumeKeys.setKey(local, com.reel.editor.model.interpolateValue(a.volume, a.volumeKeys, local)))
                    }
                    vm.endGesture()
                },
                onStart = vm::beginGesture, onEnd = vm::endGesture,
            ) { v -> upd { a -> if (a.volumeKeys.isEmpty() || !inside) a.copy(volume = v / 100f) else a.copy(volumeKeys = a.volumeKeys.setKey(local, v / 100f)) } }
            val maxFade = (selected.durationUs / 2e6f).coerceAtLeast(0.1f)
            ValueSlider(Icons.Rounded.Gradient, "Fade in", selected.fadeInUs / 1e6f, 0f..maxFade, { "%.1fs".format(it) }, C.Music, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.live { p -> p.copy(audio = p.audio.map { if (it.id == selected.id) it.copy(fadeInUs = (v * 1e6).toLong()) else it }) }
            }
            ValueSlider(Icons.Rounded.Gradient, "Fade out", selected.fadeOutUs / 1e6f, 0f..maxFade, { "%.1fs".format(it) }, C.Music, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.live { p -> p.copy(audio = p.audio.map { if (it.id == selected.id) it.copy(fadeOutUs = (v * 1e6).toLong()) else it }) }
            }
            if (selected.kind == AudioKind.MUSIC) {
                // Auto-duck: the music dips while someone speaks.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.RecordVoiceOver, null, tint = C.Music, modifier = Modifier.size(20.dp))
                    Text("Auto duck", style = Body)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Switch(selected.duck, { vm.setDuck(selected.id, it) }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = C.Music))
                }
                if (selected.duck) {
                    ValueSlider(Icons.Rounded.VolumeDown, "Duck depth", -selected.duckDb, 3f..30f, { "-${it.roundToInt()} dB" }, C.Music, default = 14f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                        vm.live { p -> p.copy(audio = p.audio.map { if (it.id == selected.id) it.copy(duckDb = -v) else it }) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                PillButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction(Icons.Rounded.LibraryMusic, "Music", C.Music, Modifier.weight(1f)) { pickMusic() }
                BigAction(if (recording != null) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording != null) formatTime(elapsed * 1000) else "Voiceover", C.Voice, Modifier.weight(1f)) {
                    if (recording != null) stopRec() else startRec()
                }
                BigAction(Icons.Rounded.GraphicEq, "Sounds", C.Sfx, Modifier.weight(1f), selected = showSounds) { showSounds = !showSounds }
            }
            if (showSounds) SoundsGrid(vm)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) color.copy(alpha = 0.16f) else C.Surface2).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = Body)
    }
}

// ---------------------------------------------------------------------------- keyframes / transform

@Composable
fun KeyframePanel(vm: EditorViewModel, onClose: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val pos by vm.preview.positionUs.collectAsStateCompat()
    val info = remember(project, selection, pos) { transformInfo(project, selection, pos) }
    if (info == null) {
        Column { SheetHeader("Keyframes", onClose = onClose); Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Diamond, null, tint = C.Muted) } }
        return
    }
    val (t, keys, start) = info
    val onKey = keys.any { abs(start + it.timeUs - pos) < 40_000 }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetHeader("Keyframes", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Values", "Graph"), tab, { tab = it }) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.Rounded.ChevronLeft, "Previous keyframe", background = C.Surface2, size = 40.dp) {
                keys.map { start + it.timeUs }.filter { it < pos - 40_000 }.maxOrNull()?.let { vm.preview.seekTo(it) }
            }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (onKey) C.Key.copy(alpha = 0.2f) else C.Surface2).clickable { vm.toggleKeyframe() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Diamond, if (onKey) "Remove keyframe" else "Add keyframe", tint = if (onKey) C.Key else C.Text)
            }
            IconBtn(Icons.Rounded.ChevronRight, "Next keyframe", background = C.Surface2, size = 40.dp) {
                keys.map { start + it.timeUs }.filter { it > pos + 40_000 }.minOrNull()?.let { vm.preview.seekTo(it) }
            }
            Spacer(Modifier.weight(1f))
            Text("${keys.size}", style = Mono.copy(color = C.Key))
        }
        if (tab == 0) {
        ValueSlider(Icons.Rounded.OpenWith, "X", t.x * 100, -50f..150f, { "${it.roundToInt()}" }, C.Key, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> vm.setTransformAtPlayhead(true) { it.copy(x = v / 100f) } }
        ValueSlider(Icons.Rounded.OpenWith, "Y", t.y * 100, -50f..150f, { "${it.roundToInt()}" }, C.Key, default = 50f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> vm.setTransformAtPlayhead(true) { it.copy(y = v / 100f) } }
        ValueSlider(Icons.Rounded.ZoomOutMap, "Scale", t.scale * 100, 5f..400f, { "${it.roundToInt()}%" }, C.Key, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> vm.setTransformAtPlayhead(true) { it.copy(scale = v / 100f) } }
        ValueSlider(Icons.Rounded.RotateRight, "Rotation", t.rotation, -180f..180f, { "${it.roundToInt()}°" }, C.Key, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> vm.setTransformAtPlayhead(true) { it.copy(rotation = v) } }
        ValueSlider(Icons.Rounded.Opacity, "Opacity", t.opacity * 100, 0f..100f, { "${it.roundToInt()}%" }, C.Key, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v -> vm.setTransformAtPlayhead(true) { it.copy(opacity = v / 100f) } }
        } else {
        // How the chosen property changes over the whole layer.
        val span = itemSpan(project, selection)
        if (span != null && keys.isNotEmpty()) {
            var prop by remember { mutableStateOf(GraphProp.SCALE) }
            GraphPropChips(prop) { prop = it }
            ValueGraph(span.first, keys, start, span.second, pos, prop, onSeek = { vm.preview.seekTo(it) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        // Easing of the segment arriving at the next keyframe after the playhead.
        val next = keys.sortedBy { it.timeUs }.firstOrNull { start + it.timeUs > pos }
        if (next != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ShowChart, null, tint = C.Muted, modifier = Modifier.size(18.dp))
                Easing.PRESETS.forEach { e ->
                    Chip(e.name, next.easing == e) { vm.edit { p -> setEasing(p, selection, next.timeUs, e) } }
                }
                if (next.easing !in Easing.PRESETS) Chip("Custom", true) {}
            }
            EasingEditor(next.easing, onStart = vm::beginGesture, onChange = { e -> vm.live { p -> setEasing(p, selection, next.timeUs, e) } }, onEnd = vm::endGesture)
        }
        if (keys.size < 2) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ShowChart, null, tint = C.Dim) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun setEasing(p: com.reel.editor.model.Project, sel: Selection, timeUs: Long, e: Easing): com.reel.editor.model.Project {
    fun fix(keys: List<com.reel.editor.model.Keyframe>) = keys.map { if (it.timeUs == timeUs) it.copy(easing = e) else it }
    return when (sel) {
        is Selection.ClipSel -> p.copy(clips = p.clips.map { if (it.id == sel.id) it.copy(keyframes = fix(it.keyframes)) else it })
        is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) it.copy(keyframes = fix(it.keyframes)) else it })
        is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) it.copy(keyframes = fix(it.keyframes)) else it })
        is Selection.ShapeSel -> p.copy(shapes = p.shapes.map { if (it.id == sel.id) it.copy(keyframes = fix(it.keyframes)) else it })
        is Selection.OverlaySel -> p.copy(overlays = p.overlays.map { if (it.id == sel.id) it.copy(keyframes = fix(it.keyframes)) else it })
        else -> p
    }
}

/** The layer's untouched transform and its length, for the value graph. */
private fun itemSpan(p: com.reel.editor.model.Project, sel: Selection): Pair<com.reel.editor.model.Transform, Long>? = when (sel) {
    is Selection.ClipSel -> TimelineMath.place(p.clips).firstOrNull { it.clip.id == sel.id }?.let { it.clip.transform to it.durationUs }
    is Selection.TextSel -> p.texts.firstOrNull { it.id == sel.id }?.let { it.transform to it.durationUs }
    is Selection.StickerSel -> p.stickers.firstOrNull { it.id == sel.id }?.let { it.transform to it.durationUs }
    is Selection.ShapeSel -> p.shapes.firstOrNull { it.id == sel.id }?.let { it.transform to it.durationUs }
    is Selection.OverlaySel -> p.overlays.firstOrNull { it.id == sel.id }?.let { it.transform to it.timelineDurationUs }
    else -> null
}

fun transformInfo(p: com.reel.editor.model.Project, sel: Selection, pos: Long): Triple<com.reel.editor.model.Transform, List<com.reel.editor.model.Keyframe>, Long>? = when (sel) {
    is Selection.ClipSel -> TimelineMath.place(p.clips).firstOrNull { it.clip.id == sel.id }?.let { Triple(interpolate(it.clip.transform, it.clip.keyframes, pos - it.startUs), it.clip.keyframes, it.startUs) }
    is Selection.TextSel -> p.texts.firstOrNull { it.id == sel.id }?.let { Triple(interpolate(it.transform, it.keyframes, pos - it.startUs), it.keyframes, it.startUs) }
    is Selection.StickerSel -> p.stickers.firstOrNull { it.id == sel.id }?.let { Triple(interpolate(it.transform, it.keyframes, pos - it.startUs), it.keyframes, it.startUs) }
    is Selection.ShapeSel -> p.shapes.firstOrNull { it.id == sel.id }?.let { Triple(interpolate(it.transform, it.keyframes, pos - it.startUs), it.keyframes, it.startUs) }
    is Selection.OverlaySel -> p.overlays.firstOrNull { it.id == sel.id }?.let { Triple(interpolate(it.transform, it.keyframes, pos - it.startUs), it.keyframes, it.startUs) }
    else -> null
}



@Suppress("unused")
private val unusedW = Modifier.width(1.dp)

/** Fonts by category; each name is drawn in its own typeface. */
@Composable
private fun FontPicker(selected: String, onPick: (String) -> Unit) {
    val current = Fonts.font(selected)
    var cat by remember { mutableStateOf(current.category) }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Fonts.Category.entries.forEach { c -> Chip(c.label, c == cat) { cat = c } }
    }
    val list = Fonts.all.filter { it.category == cat }
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = list.indexOfFirst { it.id == selected }.coerceAtLeast(0))
    androidx.compose.foundation.lazy.LazyRow(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.size, key = { list[it].id }) { i ->
            val f = list[i]
            val sel = selected == f.id
            val family = remember(f.id) { androidx.compose.ui.text.font.FontFamily(Fonts.typeface(f.id)) }
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2).then(if (sel) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onPick(f.id) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(f.label, style = Body.copy(fontSize = 17.sp, fontFamily = family, color = if (sel) C.Text else C.Soft), maxLines = 1)
            }
        }
    }
}
