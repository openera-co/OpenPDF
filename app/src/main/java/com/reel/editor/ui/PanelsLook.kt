package com.reel.editor.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.edit.Selection
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.FxDef
import com.reel.editor.fx.LookDef
import com.reel.editor.model.ClipKind
import com.reel.editor.model.FxRef
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.TransitionRef
import com.reel.editor.nativebridge.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Applies a look to a small bitmap on the CPU for tile previews. */
private object LookPreview {
    private val luts = HashMap<Int, IntArray>()
    private const val N = 17

    fun apply(src: Bitmap, look: LookDef): Bitmap {
        val lut = synchronized(luts) { luts.getOrPut(look.index) { NativeCore.renderLut(look.index, N) ?: IntArray(N * N * N) } }
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = ((c shr 16) and 255) * (N - 1) / 255
            val g = ((c shr 8) and 255) * (N - 1) / 255
            val b = (c and 255) * (N - 1) / 255
            px[i] = lut[(r * N + g) * N + b]
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

@Composable
fun FiltersPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val catalog = remember { FxCatalog.get(ctx) }
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val clip = vm.targetClip()
    val timed = (selection as? Selection.FilterSel)?.let { s -> project.filters.firstOrNull { it.id == s.id } }
    var tab by remember { mutableIntStateOf(if (clip?.filter != null || timed?.shaderFx != null) 1 else 0) }
    val categories = remember { listOf("All") + catalog.looks.map { it.category }.distinct() }
    var cat by remember { mutableStateOf("All") }
    val base by produceState<Bitmap?>(null, clip?.uri) {
        val c = clip ?: return@produceState
        value = withContext(Dispatchers.IO) { vm.thumbnails.frame(c.uri, c.trimStartUs, 96, c.kind == ClipKind.PHOTO)?.let { Bitmap.createScaledBitmap(it, (it.width * 96f / it.height).roundToInt().coerceAtLeast(1), 96, true) } }
    }
    val currentLook = timed?.lookId ?: clip?.lookId
    val currentShader = timed?.shaderFx?.id ?: clip?.filter?.id

    fun applyLook(id: String?) {
        when {
            timed != null -> vm.edit { p -> p.copy(filters = p.filters.map { if (it.id == timed.id) it.copy(lookId = id, shaderFx = null) else it }) }
            clip != null -> vm.updateClip(clip.id) { it.copy(lookId = id) }
        }
    }
    fun applyShader(id: String?) {
        when {
            timed != null -> vm.edit { p -> p.copy(filters = p.filters.map { if (it.id == timed.id) it.copy(shaderFx = id?.let { FxRef(it) }, lookId = null) else it }) }
            clip != null -> vm.updateClip(clip.id) { it.copy(filter = id?.let { FxRef(it) }) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Filters", onClose = onClose, onDone = onClose, center = { Segmented(listOf("Looks", "Art"), tab, { tab = it }) })
        if (clip == null && timed == null) {
            Text("Add a clip first.", style = Body.copy(color = C.Muted), modifier = Modifier.padding(20.dp))
            return@Column
        }
        if (tab == 0) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { Chip(it, it == cat) { cat = it } }
            }
            val looks = catalog.looks.filter { cat == "All" || it.category == cat }
            LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { LookTile("None", base, currentLook == null) { applyLook(null) } }
                items(looks, key = { it.id }) { look ->
                    val img by produceState<Bitmap?>(null, base, look.id) { base?.let { b -> value = withContext(Dispatchers.Default) { LookPreview.apply(b, look) } } }
                    LookTile(look.name, img, currentLook == look.id) { applyLook(look.id) }
                }
            }
            if (currentLook != null) {
                val amount = timed?.amount ?: clip?.lookAmount ?: 1f
                ValueSlider(Icons.Rounded.Tune, "Intensity", amount * 100, 0f..100f, { "${it.roundToInt()}" }, C.Accent, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                    if (timed != null) vm.live { p -> p.copy(filters = p.filters.map { if (it.id == timed.id) it.copy(amount = v / 100f) else it }) }
                    else if (clip != null) vm.updateClip(clip.id, true) { it.copy(lookAmount = v / 100f) }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.weight(1f, fill = false).heightIn(min = 96.dp, max = 200.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { FxTile("None", currentShader == null, Icons.Rounded.Block) { applyShader(null) } }
                items(catalog.filters, key = { it.id }) { f -> FxTile(f.name, currentShader == f.id, Icons.Rounded.FilterVintage, brush = fxBrush(f)) { applyShader(f.id) } }
            }
            val ref = timed?.shaderFx ?: clip?.filter
            if (ref != null) {
                val amount = timed?.amount ?: ref.intensity
                ValueSlider(Icons.Rounded.Tune, "Intensity", amount * 100, 0f..100f, { "${it.roundToInt()}" }, C.Accent, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                    if (timed != null) vm.live { p -> p.copy(filters = p.filters.map { if (it.id == timed.id) it.copy(amount = v / 100f) else it }) }
                    else if (clip != null) vm.updateClip(clip.id, true) { it.copy(filter = it.filter?.copy(intensity = v / 100f)) }
                }
                catalog.fx(ref.id)?.let { def -> ParamSliders(vm, def, ref) { r -> if (timed != null) vm.live { p -> p.copy(filters = p.filters.map { if (it.id == timed.id) it.copy(shaderFx = r) else it }) } else if (clip != null) vm.updateClip(clip.id, true) { it.copy(filter = r) } } }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (timed == null && clip != null) {
                PillButton(Icons.Rounded.ContentCopy, "Apply to all") {
                    vm.edit { p -> p.copy(clips = p.clips.map { it.copy(lookId = clip.lookId, lookAmount = clip.lookAmount, filter = clip.filter) }) }
                    vm.toast("Applied to all clips")
                }
                PillButton(Icons.Rounded.Layers, "As track") { vm.addTimedFilter(clip.lookId ?: "fresh".takeIf { clip.filter == null }, clip.filter?.id) }
            } else if (timed != null) PillButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LookTile(name: String, img: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2).then(if (selected) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier)) {
            if (img != null) Image(img.asImageBitmap(), name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(name, style = Label.copy(color = if (selected) C.Text else C.Muted), maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Deterministic tile colors so every effect has a recognizable swatch. */
fun fxBrush(def: FxDef): Brush {
    val h = (def.id.hashCode() and 0xffff) / 65535f * 360f
    val a = Color.hsv(h, 0.55f, 0.75f)
    val b = Color.hsv((h + 50f) % 360f, 0.7f, 0.35f)
    return Brush.linearGradient(listOf(a, b))
}

@Composable
fun ParamSliders(vm: EditorViewModel, def: FxDef, ref: FxRef, onChange: (FxRef) -> Unit) {
    for (p in def.params.filter { it.type == "float" }) {
        val v = ref.params[p.name]?.firstOrNull() ?: p.default.firstOrNull() ?: 0f
        ValueSlider(Icons.Rounded.Tune, p.name, v, p.min..p.max, { if (p.max > 10) "${it.roundToInt()}" else "%.2f".format(it) }, C.Effect, default = p.default.firstOrNull(), onStart = vm::beginGesture, onEnd = vm::endGesture) { nv ->
            onChange(ref.copy(params = ref.params + (p.name to listOf(nv))))
        }
    }
}

@Composable
fun EffectsPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val catalog = remember { FxCatalog.get(ctx) }
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val sel = (selection as? Selection.EffectSel)?.let { s -> project.effects.firstOrNull { it.id == s.id } }
    val cats = remember { catalog.effects.map { it.category }.distinct() }
    var cat by remember { mutableStateOf(cats.firstOrNull() ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Effects", Icons.Rounded.AutoAwesome, iconTint = C.Effect, onClose = onClose, onDone = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { Chip(it, it == cat) { cat = it } }
        }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 96.dp, max = 180.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(catalog.effects.filter { it.category == cat }, key = { it.id }) { e ->
                FxTile(e.name, sel?.fx?.id == e.id, Icons.Rounded.AutoAwesome, brush = fxBrush(e)) {
                    if (sel != null) vm.edit { p -> p.copy(effects = p.effects.map { if (it.id == sel.id) it.copy(fx = FxRef(e.id)) else it }) }
                    else vm.addTimedEffect(e.id)
                    vm.preview.play()
                }
            }
        }
        if (sel != null) {
            ValueSlider(Icons.Rounded.Tune, "Intensity", sel.fx.intensity * 100, 0f..100f, { "${it.roundToInt()}" }, C.Effect, default = 100f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.live { p -> p.copy(effects = p.effects.map { if (it.id == sel.id) it.copy(fx = it.fx.copy(intensity = v / 100f)) else it }) }
            }
            catalog.fx(sel.fx.id)?.let { def -> ParamSliders(vm, def, sel.fx) { r -> vm.live { p -> p.copy(effects = p.effects.map { if (it.id == sel.id) it.copy(fx = r) else it }) } } }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                PillButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun TransitionPanel(vm: EditorViewModel, clipId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val catalog = remember { FxCatalog.get(ctx) }
    val project by vm.project.collectAsStateCompat()
    val clip = project.clips.firstOrNull { it.id == clipId } ?: return
    val placed = TimelineMath.place(project.clips)
    val p = placed.firstOrNull { it.clip.id == clipId } ?: return
    val prev = placed.getOrNull(p.index - 1) ?: return
    val cats = remember { listOf("All") + catalog.transitions.map { it.category }.distinct().sorted() }
    var cat by remember { mutableStateOf("All") }
    val maxDur = (minOf(p.durationUs, prev.durationUs) / 2 / 1e6f).coerceIn(0.1f, 2f)
    fun preview() {
        vm.preview.seekTo((p.startUs - 1_000_000).coerceAtLeast(0))
        vm.preview.play()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Transition", Icons.Rounded.SwapHoriz, onClose = onClose, onDone = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { Chip(it, it == cat) { cat = it } }
        }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 96.dp, max = 200.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FxTile("None", clip.transitionIn == null, Icons.Rounded.Block) { vm.updateClip(clip.id) { it.copy(transitionIn = null) } } }
            items(catalog.transitions.filter { cat == "All" || it.category == cat }, key = { it.id }) { t ->
                val h = (t.id.hashCode() and 0xffff) / 65535f * 360f
                FxTile(t.name, clip.transitionIn?.id == t.id, Icons.Rounded.SwapHoriz, brush = Brush.horizontalGradient(listOf(Color.hsv(h, 0.5f, 0.7f), Color.hsv((h + 180) % 360, 0.5f, 0.3f)))) {
                    val d = clip.transitionIn?.durationUs ?: 500_000
                    vm.updateClip(clip.id) { it.copy(transitionIn = TransitionRef(t.id, minOf(d, (maxDur * 1e6).toLong()))) }
                    preview()
                }
            }
        }
        clip.transitionIn?.let { tr ->
            ValueSlider(Icons.Rounded.AccessTime, "Duration", tr.durationUs / 1e6f, 0.1f..maxDur, { "%.1fs".format(it) }, onStart = vm::beginGesture, onEnd = { vm.endGesture(); preview() }) { v ->
                vm.updateClip(clip.id, true) { it.copy(transitionIn = it.transitionIn?.copy(durationUs = (v * 1e6).toLong())) }
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                PillButton(Icons.Rounded.ContentCopy, "Apply to all") {
                    vm.edit { pr -> pr.copy(clips = pr.clips.mapIndexed { i, c -> if (i == 0) c else c.copy(transitionIn = tr) }) }
                    vm.toast("Applied to every cut")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Suppress("unused")
private val unusedIcons = listOf(Icons.Rounded.BlurOn, Icons.Rounded.Speed)
