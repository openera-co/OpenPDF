package com.reel.editor.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.edit.Panel
import com.reel.editor.edit.Selection
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.fx.RenderPlans
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.interpolate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit, onExport: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val panel by vm.panel.collectAsStateCompat()
    val playing by vm.preview.playing.collectAsStateCompat()
    val pos by vm.preview.positionUs.collectAsStateCompat()
    val canUndo by vm.canUndo.collectAsStateCompat()
    val canRedo by vm.canRedo.collectAsStateCompat()
    val message by vm.message.collectAsStateCompat()
    val busy by vm.busy.collectAsStateCompat()
    val waveforms by vm.waveforms.collectAsStateCompat()
    val previewError by vm.preview.error.collectAsStateCompat()
    var fullscreen by remember { mutableStateOf(false) }
    var safeMode by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showExport by remember { mutableStateOf(false) }
    var transitionFor by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = LocalPicker.current
    val visual = setOf(com.reel.editor.media.MediaLibrary.Kind.VIDEO, com.reel.editor.media.MediaLibrary.Kind.PHOTO)
    val addClips = remember(vm) { { picker.open(PickerRequest(visual, multi = true) { vm.addMedia(it) }) } }
    val replace = remember(vm) { { picker.open(PickerRequest(visual, multi = false) { u -> u.firstOrNull()?.let(vm::replaceClip) }) } }

    BackHandler {
        when {
            fullscreen -> fullscreen = false
            transitionFor != null -> transitionFor = null
            panel != null -> vm.openPanel(null)
            selection != Selection.None -> vm.select(Selection.None)
            else -> { vm.saveNow(); onClose() }
        }
    }

    val total = TimelineMath.duration(project)
    val (cw, ch) = RenderPlans.canvasSize(project, 1280)

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Top bar
        AnimatedVisibility(!fullscreen) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.Close, "Close project") { vm.saveNow(); onClose() }
                Text(project.name, style = Body.copy(color = C.Muted), maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Row(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent).clickable(enabled = project.clips.isNotEmpty()) { vm.preview.pause(); showExport = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FileUpload, null, tint = C.Bg, modifier = Modifier.size(16.dp))
                    Text("Export", style = Title.copy(fontSize = 14.sp, color = C.Bg), modifier = Modifier.padding(start = 6.dp))
                }
                Box(Modifier.width(8.dp))
            }
        }

        // Preview
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
            val aspect = cw.toFloat() / ch
            val boxAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val fit = if (aspect > boxAspect) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxSize().aspectRatio(aspect, matchHeightConstraintsFirst = true)
            BoxWithConstraints(fit.clipToBounds(), contentAlignment = Alignment.Center) {
                // The video surface keeps the largest size it has had for this aspect and is scaled
                // down visually when panels open, so resizing never re-allocates the GL surface.
                val curW = constraints.maxWidth
                var baseW by remember(aspect) { mutableIntStateOf(0) }
                if (curW > baseW) baseW = curW
                val density = androidx.compose.ui.platform.LocalDensity.current
                val baseWdp = with(density) { baseW.coerceAtLeast(1).toDp() }
                val scale = if (baseW > 0) curW.toFloat() / baseW else 1f
                AndroidView(
                    factory = { c -> TextureView(c).also { it.isOpaque = true; vm.preview.attach(it) } },
                    onRelease = { vm.preview.detach(it) },
                    modifier = Modifier.requiredSize(baseWdp, baseWdp / aspect).graphicsLayer { scaleX = scale; scaleY = scale },
                )
                PreviewOverlay(vm, selection, project, pos, aspect, picking, safeMode) { uv ->
                    picking = false
                    (selection as? Selection.OverlaySel)?.let { os ->
                        val o = project.overlays.firstOrNull { it.id == os.id } ?: return@PreviewOverlay
                        scope.launch {
                            val color = withContext(Dispatchers.IO) { sampleOverlayColor(ctx, o, pos, uv, aspect) }
                            if (color != null) vm.updateOverlay(o.id) { it.copy(chroma = it.chroma.copy(enabled = true, color = color)) }
                        }
                        return@PreviewOverlay
                    }
                    val clip = vm.targetClip() ?: return@PreviewOverlay
                    scope.launch {
                        val color = withContext(Dispatchers.IO) { sampleColor(ctx, project, clip.id, pos, uv, aspect) }
                        if (color != null) vm.updateClip(clip.id) { it.copy(chroma = it.chroma.copy(enabled = true, color = color)) }
                    }
                }
            }
            if (project.clips.isEmpty() && !busy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBtn(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, "Add clips", tint = C.Accent, size = 64.dp, iconSize = 30.dp, background = C.Surface) { addClips() }
                }
            }
            if (busy) CircularProgressIndicator(color = C.Accent)
            // Long clip jobs (reverse) show their progress over the video.
            val work by vm.work.collectAsStateCompat()
            work?.let { (label, f) ->
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(16.dp)).background(C.Bg.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.SettingsBackupRestore, label, tint = C.Accent)
                    androidx.compose.material3.LinearProgressIndicator(progress = { f }, modifier = Modifier.width(140.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = C.Accent, trackColor = C.Surface2)
                    Text("${(f * 100).toInt()}%", style = Mono)
                    IconBtn(androidx.compose.material.icons.Icons.Rounded.Close, "Stop", size = 32.dp) { vm.cancelWork() }
                }
            }
            if (picking) Box(Modifier.align(Alignment.TopCenter).padding(10.dp).clip(RoundedCornerShape(14.dp)).background(C.Bg.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(Icons.Rounded.InvertColors, "Tap a color", tint = C.Good)
            }
            previewError?.let { Text(it, style = Label.copy(color = C.Voice), modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) }
        }

        // Playback row
        Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                Text(formatTime(pos), style = Mono)
                Text(" / ${formatTime(total)}", style = Mono.copy(color = C.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            }
            IconBtn(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", iconSize = 28.dp, enabled = project.clips.isNotEmpty()) { vm.preview.toggle() }
            Row(Modifier.weight(1.5f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // Save frame needs the storage permission on Android 9 and older.
                val askWrite = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
                    if (ok) vm.saveFrame() else vm.toast("Allow storage to save frames")
                }
                // Less frequent tools share one menu so the row fits narrow phones.
                var more by remember { mutableStateOf(false) }
                Box {
                    IconBtn(Icons.Rounded.MoreVert, "More", tint = if (safeMode > 0) C.Accent else C.Text, size = 38.dp, iconSize = 20.dp) { more = true }
                    androidx.compose.material3.DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Save frame") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                            more = false
                            val needs = android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (needs) askWrite.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.saveFrame()
                        })
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (safeMode == 0) "Safe area" else SAFE_AREAS[safeMode - 1].name) },
                            leadingIcon = { Icon(Icons.Rounded.Smartphone, null, tint = if (safeMode > 0) C.Accent else C.Text) },
                            onClick = {
                                val (sw, sh) = com.reel.editor.fx.RenderPlans.canvasSize(project, 1280)
                                if (sw.toFloat() / sh > 0.7f) { more = false; vm.toast("Safe areas are for 9:16 videos"); return@DropdownMenuItem }
                                // Stays open so you can step through TikTok, Reels, Shorts, off.
                                safeMode = (safeMode + 1) % SAFE_AREAS.size.plus(1)
                            },
                        )
                    }
                }
                if (transformInfo(project, selection, pos) != null) {
                    val keys = transformInfo(project, selection, pos)
                    val on = keys?.let { (_, k, s) -> k.any { abs(s + it.timeUs - pos) < 40_000 } } ?: false
                    IconBtn(Icons.Rounded.Diamond, if (on) "Remove keyframe" else "Add keyframe", tint = if (on) C.Key else C.Text, size = 38.dp, iconSize = 20.dp) { vm.toggleKeyframe() }
                }
                // Hold undo to see every step and jump back several at once.
                IconBtn(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = canUndo, size = 38.dp, iconSize = 20.dp, onLongClick = { vm.openPanel(Panel.HISTORY) }) { vm.undo() }
                IconBtn(Icons.AutoMirrored.Rounded.Redo, "Redo", enabled = canRedo, size = 38.dp, iconSize = 20.dp) { vm.redo() }
                IconBtn(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Full screen", size = 38.dp, iconSize = 20.dp) { fullscreen = !fullscreen }
            }
        }

        // With a panel open the timeline keeps only its main rows so the preview stays large.
        val compact = (panel != null && panel != Panel.AUDIO && panel != Panel.BEATS) || transitionFor != null
        val fullTl = timelineHeight(project)
        val tlHeight by androidx.compose.animation.core.animateDpAsState(
            if (compact) minOf(fullTl, 150.dp) else fullTl,
            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
            label = "tl",
        )
        val panelMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f).dp
        AnimatedVisibility(!fullscreen, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            Column {
                // While typing, give the space to the video so the text can be seen on it.
                val imeOpen = WindowInsets.isImeVisible
                AnimatedVisibility(!imeOpen) {
                    Timeline(
                        project, pos, selection, waveforms, vm.thumbnails,
                        actions = remember(vm) { actionsFor(vm, addClips, { transitionFor = it }) },
                        modifier = Modifier.fillMaxWidth().height(tlHeight),
                    )
                }
                AnimatedContent(
                    targetState = Triple(panel, transitionFor, selection::class),
                    transitionSpec = { (fadeIn() + slideInVertically { it / 4 }) togetherWith (fadeOut() + slideOutVertically { it / 4 }) },
                    label = "bottom",
                ) { (pnl, trans, _) ->
                    Box(Modifier.fillMaxWidth().heightIn(max = panelMax).background(if (pnl != null || trans != null) C.Surface else C.Bg)) {
                        when {
                            trans != null -> TransitionPanel(vm, trans) { transitionFor = null }
                            pnl != null -> PanelHost(vm, pnl, picking, { picking = !picking }) { vm.openPanel(null) }
                            else -> Toolbar(vm, selection, addClips, replace)
                        }
                    }
                }
            }
        }
    }

    message?.let { m ->
        Box(Modifier.fillMaxSize().padding(bottom = 140.dp), contentAlignment = Alignment.BottomCenter) {
            Text(m, style = Body, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface2).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }

    if (showExport) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showExport = false }, sheetState = sheet, containerColor = C.Surface) {
            ExportSheet(vm, onClose = { showExport = false }) { showExport = false; onExport() }
        }
    }
}

private fun timelineHeight(p: com.reel.editor.model.Project): androidx.compose.ui.unit.Dp {
    // Layer lanes above the video: two are always there; more grow the timeline up to five, then it scrolls.
    val layerRows = com.reel.editor.model.TrackLayout.assign(com.reel.editor.model.TrackLayout.itemsOf(p)).rows.coerceIn(2, 5)
    val lanes = p.audio.sortedBy { it.startUs }.fold(mutableListOf<Long>()) { acc, a ->
        val i = acc.indexOfFirst { it <= a.startUs }
        if (i >= 0) acc[i] = a.endUs else acc += a.endUs
        acc
    }.size
    return (178 + (layerRows - 2) * 30 + lanes.coerceAtMost(3) * 38).dp
}

private fun actionsFor(vm: EditorViewModel, addClips: () -> Unit, openTransition: (String) -> Unit) = object : TimelineActions {
    override fun scrubStart() = vm.preview.setScrubbing(true)
    override fun scrub(timeUs: Long) = vm.preview.seekTo(timeUs)
    override fun scrubEnd() = vm.preview.setScrubbing(false)
    override fun select(s: Selection) { vm.tapSelect(s); vm.openPanel(null) }
    override fun openTransition(clipId: String) { vm.preview.pause(); openTransition(clipId) }
    /** The project as it was when a group drag began; each move offsets from it. */
    private var groupBase: com.reel.editor.model.Project? = null
    override fun gestureStart() { groupBase = vm.project.value; vm.beginGesture() }
    override fun gestureEnd() = vm.endGesture()
    override fun trimClip(clipId: String, left: Boolean, deltaUs: Long) {
        if (locked(Selection.ClipSel(clipId))) return
        vm.live { p -> TimelineMath.trim(p, clipId, left, deltaUs) }
        // Show the frame at the edge being trimmed.
        val placed = TimelineMath.place(vm.project.value.clips).firstOrNull { it.clip.id == clipId } ?: return
        vm.preview.seekTo(if (left) placed.startUs else placed.endUs - 33_000)
    }
    private fun startOf(p: com.reel.editor.model.Project, s: Selection): Long? = when (s) {
        is Selection.TextSel -> p.texts.firstOrNull { it.id == s.id }?.startUs
        is Selection.StickerSel -> p.stickers.firstOrNull { it.id == s.id }?.startUs
        is Selection.EffectSel -> p.effects.firstOrNull { it.id == s.id }?.startUs
        is Selection.FilterSel -> p.filters.firstOrNull { it.id == s.id }?.startUs
        is Selection.AudioSel -> p.audio.firstOrNull { it.id == s.id }?.startUs
        is Selection.ShapeSel -> p.shapes.firstOrNull { it.id == s.id }?.startUs
        is Selection.AdjustSel -> p.adjustments.firstOrNull { it.id == s.id }?.startUs
        is Selection.OverlaySel -> p.overlays.firstOrNull { it.id == s.id }?.startUs
        else -> null
    }

    /** Moves every picked item by the same amount as the one being dragged (clips stay in sequence). */
    private fun moveGroup(group: Selection.MultiSel, dragged: Selection, startUs: Long) {
        val base = groupBase ?: return
        val delta = startUs - (startOf(base, dragged) ?: return)
        // No item may go before the start.
        val minStart = group.items.mapNotNull { startOf(base, it) }.minOrNull() ?: return
        val d = maxOf(delta, -minStart)
        val items = group.items.filterNot { locked(it) }.toSet()
        fun <T> List<T>.shift(sel: (T) -> Selection, start: (T) -> Long, set: (T, Long) -> T, baseList: List<T>): List<T> =
            map { cur -> if (sel(cur) in items) baseList.firstOrNull { sel(it) == sel(cur) }?.let { b -> set(cur, start(b) + d) } ?: cur else cur }
        vm.live { p ->
            p.copy(
                texts = p.texts.shift({ Selection.TextSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v, anchorClipId = null) }, base.texts),
                stickers = p.stickers.shift({ Selection.StickerSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.stickers),
                effects = p.effects.shift({ Selection.EffectSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.effects),
                filters = p.filters.shift({ Selection.FilterSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.filters),
                audio = p.audio.shift({ Selection.AudioSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.audio),
                shapes = p.shapes.shift({ Selection.ShapeSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.shapes),
                adjustments = p.adjustments.shift({ Selection.AdjustSel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.adjustments),
                overlays = p.overlays.shift({ Selection.OverlaySel(it.id) }, { it.startUs }, { t, v -> t.copy(startUs = v) }, base.overlays),
            )
        }
    }

    override fun moveBar(sel: Selection, startUs: Long) = (vm.selection.value as? Selection.MultiSel)?.takeIf { sel in it.items }?.let { moveGroup(it, sel, startUs) }
        ?: if (locked(sel)) Unit else vm.live { p ->
        when (sel) {
            is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) it.copy(startUs = startUs, anchorClipId = null) else it })
            is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.EffectSel -> p.copy(effects = p.effects.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.FilterSel -> p.copy(filters = p.filters.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.AudioSel -> p.copy(audio = p.audio.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.ShapeSel -> p.copy(shapes = p.shapes.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.AdjustSel -> p.copy(adjustments = p.adjustments.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.OverlaySel -> p.copy(overlays = p.overlays.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            else -> p
        }
    }
    override fun resizeBar(sel: Selection, left: Boolean, timeUs: Long) = if (locked(sel)) Unit else vm.live { p ->
        fun span(start: Long, dur: Long): Pair<Long, Long> {
            val end = start + dur
            return if (left) { val s = timeUs.coerceIn(0, end - 200_000); s to end - s } else start to (timeUs - start).coerceAtLeast(200_000)
        }
        when (sel) {
            is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d, anchorClipId = null) } else it })
            is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.EffectSel -> p.copy(effects = p.effects.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.FilterSel -> p.copy(filters = p.filters.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.AudioSel -> p.copy(audio = p.audio.map { a ->
                if (a.id != sel.id) a else if (left) {
                    val newStart = timeUs.coerceIn(a.startUs - a.trimStartUs, a.endUs - 200_000)
                    a.copy(startUs = newStart, trimStartUs = a.trimStartUs + (newStart - a.startUs))
                } else a.copy(trimEndUs = (a.trimStartUs + (timeUs - a.startUs)).coerceIn(a.trimStartUs + 200_000, a.sourceDurationUs))
            })
            is Selection.ShapeSel -> p.copy(shapes = p.shapes.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.AdjustSel -> p.copy(adjustments = p.adjustments.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.OverlaySel -> p.copy(overlays = p.overlays.map { o ->
                // Photos can be stretched to any length; videos stop at their own ends.
                val photo = o.kind == com.reel.editor.model.ClipKind.PHOTO
                if (o.id != sel.id) o else if (left) {
                    val minStart = if (photo) 0 else (o.startUs - o.trimStartUs).coerceAtLeast(0)
                    val newStart = timeUs.coerceIn(minStart, o.endUs - 200_000)
                    if (photo) o.copy(startUs = newStart, trimEndUs = o.trimEndUs - (newStart - o.startUs))
                    else o.copy(startUs = newStart, trimStartUs = o.trimStartUs + (newStart - o.startUs))
                } else o.copy(trimEndUs = (o.trimStartUs + (timeUs - o.startUs)).coerceIn(o.trimStartUs + 200_000, if (photo) 3_600_000_000 else o.sourceDurationUs))
            })
            else -> p
        }
    }
    override fun reorder(from: Int, to: Int) { if (!locked(Selection.ClipSel(""))) vm.edit { TimelineMath.move(it, from, to) } }
    override fun toggleTrack(kind: com.reel.editor.model.TrackFlags.Kind, key: String) = vm.toggleTrack(kind, key)
    override fun muteAudio(ids: List<String>, muted: Boolean) = vm.muteAudio(ids, muted)
    private var warned = 0L
    /** Locked tracks ignore moves and trims; one short note per gesture. */
    private fun locked(s: Selection): Boolean {
        if (!vm.isLocked(s)) return false
        val now = System.currentTimeMillis()
        if (now - warned > 1500) { warned = now; vm.toast("Track is locked") }
        return true
    }
    override fun addClips() = addClips()
}

@Composable
private fun Toolbar(
    vm: EditorViewModel,
    selection: Selection,
    addClips: () -> Unit,
    replace: () -> Unit,
) {
    val canPaste by vm.canPaste.collectAsStateCompat()
    Row(Modifier.fillMaxWidth().height(72.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selection != Selection.None) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Done", size = 52.dp) { vm.select(Selection.None) }
        }
        when (selection) {
            Selection.None -> {
                ToolButton(Icons.Rounded.ContentCut, "Edit") { vm.clipAtPlayhead()?.let { vm.select(Selection.ClipSel(it.id)) } ?: addClips() }
                ToolButton(Icons.Rounded.LibraryMusic, "Audio") { vm.select(Selection.None); vm.openPanel(Panel.AUDIO) }
                ToolButton(Icons.Rounded.TextFields, "Text") { vm.addText() }
                ToolButton(Icons.Rounded.Layers, "Layer") { vm.openPanel(Panel.ADD_LAYER) }
                ToolButton(Icons.Rounded.ClosedCaption, "Captions") { vm.openPanel(Panel.CAPTIONS) }
                ToolButton(Icons.Rounded.EmojiEmotions, "Stickers") { vm.openPanel(Panel.STICKERS) }
                ToolButton(Icons.Rounded.AutoAwesome, "Effects") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.FilterVintage, "Filters") { vm.openPanel(Panel.FILTERS) }
                ToolButton(Icons.Rounded.Tune, "Adjust") { vm.clipAtPlayhead()?.let { vm.select(Selection.ClipSel(it.id)); vm.openPanel(Panel.ADJUST) } }
                ToolButton(Icons.Rounded.AspectRatio, "Canvas") { vm.openPanel(Panel.CANVAS) }
            }
            is Selection.ClipSel -> {
                ToolButton(Icons.Rounded.ContentCut, "Split") { vm.split() }
                ToolButton(Icons.Rounded.Speed, "Speed") { vm.openPanel(Panel.SPEED) }
                ToolButton(Icons.Rounded.VolumeUp, "Volume") { vm.openPanel(Panel.VOLUME) }
                ToolButton(Icons.Rounded.RecordVoiceOver, "Voice", active = vm.targetClip()?.voice?.isIdentity == false) { vm.openPanel(Panel.VOICE) }
                ToolButton(Icons.Rounded.Animation, "Animate") { vm.openPanel(Panel.ANIMATION) }
                ToolButton(Icons.Rounded.FilterVintage, "Filters") { vm.openPanel(Panel.FILTERS) }
                ToolButton(Icons.Rounded.Tune, "Adjust") { vm.openPanel(Panel.ADJUST) }
                ToolButton(Icons.Rounded.AutoAwesome, "Effects") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.Face, "Reframe") { vm.openPanel(Panel.TRACK) }
                ToolButton(Icons.Rounded.InvertColors, "Chroma") { vm.openPanel(Panel.CHROMA) }
                ToolButton(Icons.Rounded.ContentCut, "Cutout", active = vm.targetClip()?.cutout?.enabled == true) { vm.openPanel(Panel.CUTOUT) }
                ToolButton(Icons.Rounded.Crop, "Crop") { vm.openPanel(Panel.CROP) }
                ToolButton(Icons.Rounded.Star, "Mask") { vm.openPanel(Panel.MASK) }
                ToolButton(Icons.Rounded.BlurOn, "Blur face", active = vm.targetClip()?.privacy != null) { vm.openPanel(Panel.PRIVACY) }
                ToolButton(Icons.Rounded.RotateRight, "Rotate") { vm.rotateClip() }
                ToolButton(Icons.Rounded.SettingsBackupRestore, "Reverse", active = vm.targetClip()?.reversedFrom != null) { vm.reverseClip() }
                ToolButton(Icons.Rounded.AspectRatio, "Canvas") { vm.openPanel(Panel.CANVAS) }
                ToolButton(Icons.Rounded.AcUnit, "Freeze") { vm.freezeFrame() }
                ToolButton(Icons.Rounded.MusicNote, "Extract audio") { vm.extractAudio() }
                ToolButton(Icons.Rounded.FindReplace, "Replace") { replace() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Brush, "Copy style") { vm.copyStyle() }
                if (canPaste) ToolButton(Icons.Rounded.ContentPaste, "Paste style") { vm.pasteStyle() }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.TextSel -> {
                ToolButton(Icons.Rounded.Edit, "Edit") { vm.openPanel(Panel.TEXT_EDIT) }
                ToolButton(Icons.Rounded.Palette, "Style") { vm.openPanel(Panel.TEXT_STYLE) }
                ToolButton(Icons.Rounded.Animation, "Animate") { vm.openPanel(Panel.TEXT_ANIM) }
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.Face, "Track") { vm.openPanel(Panel.TRACK) }
                ToolButton(Icons.Rounded.RecordVoiceOver, "Read aloud") { vm.speakText() }
                ToolButton(Icons.Rounded.ContentCut, "Split") { vm.split() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Brush, "Copy style") { vm.copyStyle() }
                if (canPaste) ToolButton(Icons.Rounded.ContentPaste, "Paste style") { vm.pasteStyle() }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.StickerSel -> {
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.Face, "Track") { vm.openPanel(Panel.TRACK) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.AudioSel -> {
                ToolButton(Icons.Rounded.VolumeUp, "Volume") { vm.openPanel(Panel.AUDIO) }
                ToolButton(Icons.Rounded.RecordVoiceOver, "Voice") { vm.openPanel(Panel.VOICE) }
                ToolButton(Icons.Rounded.GraphicEq, "Beats") { vm.openPanel(Panel.BEATS) }
                ToolButton(Icons.Rounded.ContentCut, "Split") { vm.split() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.EffectSel -> {
                ToolButton(Icons.Rounded.SwapHoriz, "Replace") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.Tune, "Adjust") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.FilterSel -> {
                ToolButton(Icons.Rounded.FilterVintage, "Filter") { vm.openPanel(Panel.FILTERS) }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.ShapeSel -> {
                ToolButton(Icons.Rounded.Category, "Shape") { vm.openPanel(Panel.SHAPE) }
                ToolButton(Icons.Rounded.Animation, "Animate") { vm.openPanel(Panel.TEXT_ANIM) }
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.Face, "Track") { vm.openPanel(Panel.TRACK) }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Brush, "Copy style") { vm.copyStyle() }
                if (canPaste) ToolButton(Icons.Rounded.ContentPaste, "Paste style") { vm.pasteStyle() }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.AdjustSel -> {
                ToolButton(Icons.Rounded.Tune, "Adjust") { vm.openPanel(Panel.ADJUST_LAYER) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.MultiSel -> {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Text("${selection.items.size}", style = Title.copy(color = C.Accent)) }
                if (canPaste) ToolButton(Icons.Rounded.ContentPaste, "Paste style") { vm.pasteStyle() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.OverlaySel -> {
                ToolButton(Icons.Rounded.Layers, "Blend") { vm.openPanel(Panel.OVERLAY) }
                ToolButton(Icons.Rounded.Star, "Mask") { vm.openPanel(Panel.MASK) }
                ToolButton(Icons.Rounded.InvertColors, "Chroma") { vm.openPanel(Panel.CHROMA) }
                ToolButton(Icons.Rounded.Face, "Track") { vm.openPanel(Panel.TRACK) }
                ToolButton(Icons.Rounded.Animation, "Animate") { vm.openPanel(Panel.TEXT_ANIM) }
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.Checklist, "Select") { vm.startMultiSelect() }
                ToolButton(Icons.Rounded.Brush, "Copy style") { vm.copyStyle() }
                if (canPaste) ToolButton(Icons.Rounded.ContentPaste, "Paste style") { vm.pasteStyle() }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
        }
    }
}

@Composable
private fun PanelHost(vm: EditorViewModel, panel: Panel, picking: Boolean, onPick: () -> Unit, onClose: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val selection by vm.selection.collectAsStateCompat()
    val clip = (selection as? Selection.ClipSel)?.let { s -> project.clips.firstOrNull { it.id == s.id } } ?: vm.targetClip()
    val text = (selection as? Selection.TextSel)?.let { s -> project.texts.firstOrNull { it.id == s.id } }
    val shape = (selection as? Selection.ShapeSel)?.let { s -> project.shapes.firstOrNull { it.id == s.id } }
    val adjustLayer = (selection as? Selection.AdjustSel)?.let { s -> project.adjustments.firstOrNull { it.id == s.id } }
    val overlay = (selection as? Selection.OverlaySel)?.let { s -> project.overlays.firstOrNull { it.id == s.id } }
    when (panel) {
        Panel.SPEED -> clip?.let { SpeedPanel(vm, it, onClose) }
        Panel.VOLUME -> clip?.let { VolumePanel(vm, it, onClose) }
        Panel.ANIMATION -> clip?.let { AnimationPanel(vm, it, onClose) }
        Panel.FILTERS -> FiltersPanel(vm, onClose)
        Panel.ADJUST -> clip?.let { AdjustPanel(vm, it, onClose) }
        Panel.EFFECTS -> EffectsPanel(vm, onClose)
        Panel.TEXT_EDIT -> text?.let { TextPanel(vm, it, 0, onClose) }
        Panel.TEXT_STYLE -> text?.let { TextPanel(vm, it, 1, onClose) }
        Panel.TEXT_ANIM -> when {
            text != null -> TextPanel(vm, text, 2, onClose)
            shape != null -> LayerAnimPanel(vm, shape.startUs, shape.durationUs, shape.animIn, shape.animOut, onClose) { i, a -> vm.updateShape(shape.id) { if (i) it.copy(animIn = a) else it.copy(animOut = a) } }
            overlay != null -> LayerAnimPanel(vm, overlay.startUs, overlay.timelineDurationUs, overlay.animIn, overlay.animOut, onClose) { i, a -> vm.updateOverlay(overlay.id) { if (i) it.copy(animIn = a) else it.copy(animOut = a) } }
            else -> Unit
        }
        Panel.STICKERS -> StickerPanel(vm, onClose)
        Panel.AUDIO -> AudioPanel(vm, onClose)
        Panel.CANVAS -> CanvasPanel(vm, onClose)
        Panel.KEYFRAMES -> KeyframePanel(vm, onClose)
        Panel.CHROMA -> when (val sel = selection) {
            is Selection.OverlaySel -> project.overlays.firstOrNull { it.id == sel.id }?.let { o -> ChromaBody(vm, o.chroma, picking, onPick, onClose) { g, f -> vm.updateOverlay(o.id, g) { it.copy(chroma = f(it.chroma)) } } }
            else -> clip?.let { ChromaPanel(vm, it, picking, onPick, onClose) }
        }
        Panel.ADD_LAYER -> AddLayerPanel(vm, onClose)
        Panel.CROP -> clip?.let { CropPanel(vm, it, onClose) }
        Panel.HISTORY -> HistoryPanel(vm, onClose)
        Panel.PRIVACY -> clip?.let { PrivacyPanel(vm, it, onClose) }
        Panel.TRACK -> TrackPanel(vm, clipMode = selection is Selection.ClipSel, onClose = onClose)
        Panel.SHAPE -> shape?.let { ShapePanel(vm, it, onClose) }
        Panel.ADJUST_LAYER -> adjustLayer?.let { AdjustLayerPanel(vm, it, onClose) }
        Panel.OVERLAY -> overlay?.let { OverlayPanel(vm, it, onClose) }
        Panel.MASK -> when {
            overlay != null -> MaskPanel(vm, overlay, onClose)
            else -> clip?.let { c -> MaskBody(vm, c.mask, onClose) { g, f -> vm.updateClip(c.id, g) { it.copy(mask = f(it.mask)) } } }
        }
        Panel.CAPTIONS -> CaptionsPanel(vm, onClose)
        Panel.CUTOUT -> clip?.let { CutoutPanel(vm, it, onClose) }
        Panel.VOICE -> when (val sel = selection) {
            is Selection.AudioSel -> project.audio.firstOrNull { it.id == sel.id }?.let { VoicePanel(vm, sel, it.voice, onClose) }
            else -> clip?.let { VoicePanel(vm, Selection.ClipSel(it.id), it.voice, onClose) }
        }
        Panel.BEATS -> (selection as? Selection.AudioSel)?.let { s -> project.audio.firstOrNull { it.id == s.id } }?.let { BeatsPanel(vm, it, onClose) }
        else -> Unit
    }
}

/** Drag, pinch and twist the selected layer; tap toggles playback or picks a key color. */
@Composable
private fun PreviewOverlay(
    vm: EditorViewModel,
    selection: Selection,
    project: com.reel.editor.model.Project,
    pos: Long,
    aspect: Float,
    picking: Boolean,
    safeMode: Int,
    onPickColor: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val sel by androidx.compose.runtime.rememberUpdatedState(selection)
    val pick by androidx.compose.runtime.rememberUpdatedState(picking)
    val proj by androidx.compose.runtime.rememberUpdatedState(project)
    val now by androidx.compose.runtime.rememberUpdatedState(pos)
    // Snap guides shown while a layer is held on a centre line.
    var guideV by remember { mutableStateOf(false) }
    var guideH by remember { mutableStateOf(false) }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val movable = sel is Selection.TextSel || sel is Selection.StickerSel || sel is Selection.ClipSel || sel is Selection.ShapeSel || sel is Selection.OverlaySel
                var moved = false
                var started = false
                var holding = false
                var maxPointers = 1
                var totalMove = Offset.Zero
                var startT: com.reel.editor.model.Transform? = null
                var accX = 0f
                var accY = 0f
                var accRot = 0f
                while (true) {
                    // A still finger for a moment shows the original, without effects.
                    val waitHold = !moved && !holding && maxPointers == 1 && !pick
                    val e = if (waitHold) withTimeoutOrNull(450) { awaitPointerEvent() } else awaitPointerEvent()
                    if (e == null) {
                        holding = true
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        vm.preview.showOriginal(true)
                        continue
                    }
                    maxPointers = maxOf(maxPointers, e.changes.count { it.pressed })
                    val zoom = e.calculateZoom()
                    var pan = e.calculatePan()
                    val rot = e.calculateRotation()
                    totalMove += pan
                    if (!moved && (pan.getDistance() > viewConfiguration.touchSlop / 4 || abs(zoom - 1f) > 0.01f || abs(rot) > 0.5f)) {
                        if (e.changes.any { it.positionChanged() }) moved = true
                    }
                    // A hold that turns into a drag is a drag: stop showing the original and move.
                    if (holding && totalMove.getDistance() > viewConfiguration.touchSlop) {
                        holding = false
                        vm.preview.showOriginal(false)
                        moved = true
                        pan = totalMove
                    }
                    if (moved && movable && !pick && !holding) {
                        if (!started) {
                            vm.beginGesture(); started = true
                            startT = transformInfo(proj, sel, now)?.first
                        }
                        accX += pan.x / size.width
                        accY += pan.y / size.height
                        accRot += rot
                        val st = startT
                        if (st != null) {
                            // Free position from the finger, then pulled onto the centre lines when close.
                            val thrX = 10 * density.density / size.width
                            val thrY = 10 * density.density / size.height
                            val rawX = st.x + accX
                            val rawY = st.y + accY
                            val snapX = kotlin.math.abs(rawX - 0.5f) < thrX
                            val snapY = kotlin.math.abs(rawY - 0.5f) < thrY
                            val rawR = st.rotation + accRot
                            val nearest = Math.round(rawR / 90f) * 90f
                            val snapR = kotlin.math.abs(rawR - nearest) < 4f
                            if ((snapX && !guideV) || (snapY && !guideH)) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            guideV = snapX; guideH = snapY
                            vm.setTransformAtPlayhead(gesture = true) { t ->
                                t.copy(
                                    x = (if (snapX) 0.5f else rawX).coerceIn(-0.5f, 1.5f),
                                    y = (if (snapY) 0.5f else rawY).coerceIn(-0.5f, 1.5f),
                                    scale = (t.scale * zoom).coerceIn(0.05f, 8f),
                                    rotation = if (snapR) nearest else rawR,
                                )
                            }
                        }
                        e.changes.forEach { it.consume() }
                    }
                    if (e.changes.none { it.pressed }) break
                }
                if (started) vm.endGesture()
                guideV = false; guideH = false
                when {
                    holding -> vm.preview.showOriginal(false)
                    // Two-finger tap undoes, three-finger tap redoes.
                    !moved && maxPointers == 2 -> vm.undo()
                    !moved && maxPointers >= 3 -> vm.redo()
                    !moved -> if (pick) onPickColor(Offset(down.position.x / size.width, down.position.y / size.height)) else vm.preview.toggle()
                }
            }
        },
    ) {
        // Dashed frame around the selected text or sticker.
        val box = when (val s = selection) {
            is Selection.TextSel -> project.texts.firstOrNull { it.id == s.id }?.takeIf { pos in it.startUs until it.startUs + it.durationUs }?.let { l ->
                val t = interpolate(l.transform, l.keyframes, pos - l.startUs)
                val hUnits = l.style.sizeSp * LayerBitmaps.pxPerUnit(1280) * 1.25f * (l.text.count { it == '\n' } + 1) / 1280f * t.scale
                val longest = l.text.split('\n').maxOf { it.length }.coerceAtLeast(1)
                val wUnits = hUnits * 0.55f * longest / (l.text.count { it == '\n' } + 1)
                Triple(t, wUnits, hUnits)
            }
            is Selection.StickerSel -> project.stickers.firstOrNull { it.id == s.id }?.takeIf { pos in it.startUs until it.startUs + it.durationUs }?.let { l ->
                val t = interpolate(l.transform, l.keyframes, pos - l.startUs)
                Triple(t, t.scale, t.scale)
            }
            is Selection.ShapeSel -> project.shapes.firstOrNull { it.id == s.id }?.takeIf { pos in it.startUs until it.startUs + it.durationUs }?.let { l ->
                val t = interpolate(l.transform, l.keyframes, pos - l.startUs)
                Triple(t, t.scale * l.aspect, t.scale)
            }
            is Selection.OverlaySel -> project.overlays.firstOrNull { it.id == s.id }?.takeIf { pos in it.startUs until it.endUs }?.let { o ->
                val t = interpolate(o.transform, o.keyframes, pos - o.startUs)
                val rot = o.rotationDegrees % 180 != 0
                val sw = (if (rot) o.sourceHeight else o.sourceWidth).coerceAtLeast(1).toFloat()
                val sh = (if (rot) o.sourceWidth else o.sourceHeight).coerceAtLeast(1).toFloat()
                // Overlay fits inside the canvas at scale 1, like the shader's fitSize.
                val canvasAspect = size.width / size.height
                val srcAspect = sw / sh
                val (fw, fh) = if (srcAspect > canvasAspect) canvasAspect to canvasAspect / srcAspect else srcAspect to 1f
                Triple(t, fw * t.scale, fh * t.scale)
            }
            else -> null
        }
        if (box != null) {
            val (t, wu, hu) = box
            val w = wu * size.height
            val h = hu * size.height
            val c = Offset(t.x * size.width, t.y * size.height)
            rotate(t.rotation, c) {
                drawRect(Color.White, Offset(c.x - w / 2 - 6, c.y - h / 2 - 6), Size(w + 12, h + 12), style = Stroke(1.5f * density.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
            }
        }
        if (selection is Selection.TextSel || selection is Selection.StickerSel) {
            drawLine(C.TextTrack.copy(alpha = 0.0f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
        }
        // Where the app's own buttons and captions will cover the video.
        if (safeMode > 0) SAFE_AREAS.getOrNull(safeMode - 1)?.let { sa ->
            val shade = Color.Black.copy(alpha = 0.45f)
            for (r in sa.covered) drawRect(shade, Offset(r.left * size.width, r.top * size.height), Size(r.width * size.width, r.height * size.height))
            val k = sa.safe
            drawRect(C.Good, Offset(k.left * size.width, k.top * size.height), Size(k.width * size.width, k.height * size.height),
                style = Stroke(1.5f * density.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        // Centre guides while snapped.
        if (guideV) drawLine(C.Overlay, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f * density.density)
        if (guideH) drawLine(C.Overlay, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f * density.density)
        aspect.hashCode()
    }
}

/** What a platform's app draws over a 9:16 video, as fractions of the frame (approximate). */
private class SafeArea(val name: String, val covered: List<androidx.compose.ui.geometry.Rect>, val safe: androidx.compose.ui.geometry.Rect)

private val SAFE_AREAS = listOf(
    SafeArea("TikTok", listOf(
        androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 0.07f),          // top tabs
        androidx.compose.ui.geometry.Rect(0.85f, 0.38f, 1f, 0.84f),    // like, comment, share
        androidx.compose.ui.geometry.Rect(0f, 0.80f, 1f, 1f),          // name, caption, sound
    ), androidx.compose.ui.geometry.Rect(0.06f, 0.08f, 0.84f, 0.79f)),
    SafeArea("Reels", listOf(
        androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 0.07f),
        androidx.compose.ui.geometry.Rect(0.86f, 0.46f, 1f, 0.90f),
        androidx.compose.ui.geometry.Rect(0f, 0.82f, 1f, 1f),
    ), androidx.compose.ui.geometry.Rect(0.06f, 0.08f, 0.85f, 0.81f)),
    SafeArea("Shorts", listOf(
        androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 0.06f),
        androidx.compose.ui.geometry.Rect(0.85f, 0.40f, 1f, 0.88f),
        androidx.compose.ui.geometry.Rect(0f, 0.79f, 1f, 1f),
    ), androidx.compose.ui.geometry.Rect(0.06f, 0.07f, 0.84f, 0.78f)),
)

/** Reads the overlay's pixel under a preview tap, undoing its placement. */
private fun sampleOverlayColor(ctx: android.content.Context, o: com.reel.editor.model.OverlayClip, pos: Long, uv: Offset, canvasAspect: Float): Long? {
    val local = (pos - o.startUs).coerceIn(0, o.timelineDurationUs)
    val bmp = if (o.kind == com.reel.editor.model.ClipKind.PHOTO) {
        ctx.contentResolver.openInputStream(Uri.parse(o.uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
    } else {
        val r = MediaMetadataRetriever()
        try { r.setDataSource(ctx, Uri.parse(o.uri)); r.getFrameAtTime(o.trimStartUs + local, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } catch (e: Exception) { null } finally { r.release() }
    } ?: return null
    val t = interpolate(o.transform, o.keyframes, local)
    val srcAspect = bmp.width.toFloat() / bmp.height
    val (fw, fh) = if (srcAspect > canvasAspect) 1f to canvasAspect / srcAspect else srcAspect / canvasAspect to 1f
    // Canvas uv -> overlay local uv: undo translate, rotate (in canvas-aspect space) and scale.
    var dx = (uv.x - t.x) * canvasAspect
    var dy = uv.y - t.y
    val a = Math.toRadians(-t.rotation.toDouble())
    val rx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a)).toFloat()
    val ry = (dx * kotlin.math.sin(a) + dy * kotlin.math.cos(a)).toFloat()
    dx = rx / canvasAspect; dy = ry
    val u = dx / (fw * t.scale) + 0.5f
    val v = dy / (fh * t.scale) + 0.5f
    if (u !in 0f..1f || v !in 0f..1f) return null
    val px = bmp.getPixel((u * (bmp.width - 1)).toInt(), (v * (bmp.height - 1)).toInt())
    return 0xFF000000L or (px.toLong() and 0xFFFFFF)
}

/** Reads the source pixel under a preview tap (ignores keyframed motion). */
private fun sampleColor(ctx: android.content.Context, project: com.reel.editor.model.Project, clipId: String, pos: Long, uv: Offset, canvasAspect: Float): Long? {
    val placed = TimelineMath.place(project.clips).firstOrNull { it.clip.id == clipId } ?: return null
    val c = placed.clip
    val bmp = if (c.kind == com.reel.editor.model.ClipKind.PHOTO) {
        ctx.contentResolver.openInputStream(Uri.parse(c.uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
    } else {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(c.uri))
            val media = c.trimStartUs + SpeedMath.timelineToMedia(c.speed, c.trimmedDurationUs, (pos - placed.startUs).coerceAtLeast(0))
            r.getFrameAtTime(media, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) { null } finally { r.release() }
    } ?: return null
    val ca = bmp.width.toFloat() / bmp.height
    // Map canvas uv to content uv for a centred fit.
    val (sx, sy) = if (ca > canvasAspect) 1f to canvasAspect / ca else ca / canvasAspect to 1f
    val u = ((uv.x - 0.5f) / sx + 0.5f).coerceIn(0f, 1f)
    val v = ((uv.y - 0.5f) / sy + 0.5f).coerceIn(0f, 1f)
    val px = bmp.getPixel((u * (bmp.width - 1)).toInt(), (v * (bmp.height - 1)).toInt())
    return 0xFF000000L or (px.toLong() and 0xFFFFFF)
}

@Suppress("unused")
private val unusedArrow = Icons.Rounded.KeyboardArrowDown
