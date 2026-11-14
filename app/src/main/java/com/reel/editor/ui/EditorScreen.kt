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
    var showExport by remember { mutableStateOf(false) }
    var transitionFor by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val addClips = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> vm.addMedia(uris) }
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.replaceClip(uri) }

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
                PreviewOverlay(vm, selection, project, pos, aspect, picking) { uv ->
                    picking = false
                    val clip = vm.targetClip() ?: return@PreviewOverlay
                    scope.launch {
                        val color = withContext(Dispatchers.IO) { sampleColor(ctx, project, clip.id, pos, uv, aspect) }
                        if (color != null) vm.updateClip(clip.id) { it.copy(chroma = it.chroma.copy(enabled = true, color = color)) }
                    }
                }
            }
            if (project.clips.isEmpty() && !busy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBtn(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, "Add clips", tint = C.Accent, size = 64.dp, iconSize = 30.dp, background = C.Surface) { addClips.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                }
            }
            if (busy) CircularProgressIndicator(color = C.Accent)
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
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (selection is Selection.ClipSel || selection is Selection.TextSel || selection is Selection.StickerSel) {
                    val keys = transformInfo(project, selection, pos)
                    val on = keys?.let { (_, k, s) -> k.any { abs(s + it.timeUs - pos) < 40_000 } } ?: false
                    IconBtn(Icons.Rounded.Diamond, if (on) "Remove keyframe" else "Add keyframe", tint = if (on) C.Key else C.Text, iconSize = 20.dp) { vm.toggleKeyframe() }
                }
                IconBtn(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = canUndo, iconSize = 20.dp) { vm.undo() }
                IconBtn(Icons.AutoMirrored.Rounded.Redo, "Redo", enabled = canRedo, iconSize = 20.dp) { vm.redo() }
                IconBtn(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Full screen", iconSize = 20.dp) { fullscreen = !fullscreen }
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
                Timeline(
                    project, pos, selection, waveforms, vm.thumbnails,
                    actions = remember(vm) { actionsFor(vm, addClips, { transitionFor = it }) },
                    modifier = Modifier.fillMaxWidth().height(tlHeight),
                )
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
    val lanes = p.audio.sortedBy { it.startUs }.fold(mutableListOf<Long>()) { acc, a ->
        val i = acc.indexOfFirst { it <= a.startUs }
        if (i >= 0) acc[i] = a.endUs else acc += a.endUs
        acc
    }.size
    return (178 + lanes.coerceAtMost(3) * 38).dp
}

private fun actionsFor(vm: EditorViewModel, addClips: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>, openTransition: (String) -> Unit) = object : TimelineActions {
    override fun scrubStart() = vm.preview.setScrubbing(true)
    override fun scrub(timeUs: Long) = vm.preview.seekTo(timeUs)
    override fun scrubEnd() = vm.preview.setScrubbing(false)
    override fun select(s: Selection) { vm.select(s); vm.openPanel(null) }
    override fun openTransition(clipId: String) { vm.preview.pause(); openTransition(clipId) }
    override fun gestureStart() = vm.beginGesture()
    override fun gestureEnd() = vm.endGesture()
    override fun trimClip(clipId: String, left: Boolean, deltaUs: Long) {
        vm.live { p -> TimelineMath.trim(p, clipId, left, deltaUs) }
        // Show the frame at the edge being trimmed.
        val placed = TimelineMath.place(vm.project.value.clips).firstOrNull { it.clip.id == clipId } ?: return
        vm.preview.seekTo(if (left) placed.startUs else placed.endUs - 33_000)
    }
    override fun moveBar(sel: Selection, startUs: Long) = vm.live { p ->
        when (sel) {
            is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.EffectSel -> p.copy(effects = p.effects.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.FilterSel -> p.copy(filters = p.filters.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            is Selection.AudioSel -> p.copy(audio = p.audio.map { if (it.id == sel.id) it.copy(startUs = startUs) else it })
            else -> p
        }
    }
    override fun resizeBar(sel: Selection, left: Boolean, timeUs: Long) = vm.live { p ->
        fun span(start: Long, dur: Long): Pair<Long, Long> {
            val end = start + dur
            return if (left) { val s = timeUs.coerceIn(0, end - 200_000); s to end - s } else start to (timeUs - start).coerceAtLeast(200_000)
        }
        when (sel) {
            is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.EffectSel -> p.copy(effects = p.effects.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.FilterSel -> p.copy(filters = p.filters.map { if (it.id == sel.id) span(it.startUs, it.durationUs).let { (s, d) -> it.copy(startUs = s, durationUs = d) } else it })
            is Selection.AudioSel -> p.copy(audio = p.audio.map { a ->
                if (a.id != sel.id) a else if (left) {
                    val newStart = timeUs.coerceIn(a.startUs - a.trimStartUs, a.endUs - 200_000)
                    a.copy(startUs = newStart, trimStartUs = a.trimStartUs + (newStart - a.startUs))
                } else a.copy(trimEndUs = (a.trimStartUs + (timeUs - a.startUs)).coerceIn(a.trimStartUs + 200_000, a.sourceDurationUs))
            })
            else -> p
        }
    }
    override fun reorder(from: Int, to: Int) = vm.edit { TimelineMath.move(it, from, to) }
    override fun addClips() = addClips.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
}

@Composable
private fun Toolbar(
    vm: EditorViewModel,
    selection: Selection,
    addClips: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    replace: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
) {
    Row(Modifier.fillMaxWidth().height(72.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selection != Selection.None) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Done", size = 52.dp) { vm.select(Selection.None) }
        }
        when (selection) {
            Selection.None -> {
                ToolButton(Icons.Rounded.ContentCut, "Edit") { vm.clipAtPlayhead()?.let { vm.select(Selection.ClipSel(it.id)) } ?: addClips.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                ToolButton(Icons.Rounded.LibraryMusic, "Audio") { vm.select(Selection.None); vm.openPanel(Panel.AUDIO) }
                ToolButton(Icons.Rounded.TextFields, "Text") { vm.addText() }
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
                ToolButton(Icons.Rounded.InvertColors, "Chroma") { vm.openPanel(Panel.CHROMA) }
                ToolButton(Icons.Rounded.ContentCut, "Cutout", active = vm.targetClip()?.cutout?.enabled == true) { vm.openPanel(Panel.CUTOUT) }
                ToolButton(Icons.Rounded.AspectRatio, "Canvas") { vm.openPanel(Panel.CANVAS) }
                ToolButton(Icons.Rounded.AcUnit, "Freeze") { vm.freezeFrame() }
                ToolButton(Icons.Rounded.FindReplace, "Replace") { replace.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.TextSel -> {
                ToolButton(Icons.Rounded.Edit, "Edit") { vm.openPanel(Panel.TEXT_EDIT) }
                ToolButton(Icons.Rounded.Palette, "Style") { vm.openPanel(Panel.TEXT_STYLE) }
                ToolButton(Icons.Rounded.Animation, "Animate") { vm.openPanel(Panel.TEXT_ANIM) }
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.ContentCut, "Split") { vm.split() }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.StickerSel -> {
                ToolButton(Icons.Rounded.Diamond, "Keyframes") { vm.openPanel(Panel.KEYFRAMES) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.AudioSel -> {
                ToolButton(Icons.Rounded.VolumeUp, "Volume") { vm.openPanel(Panel.AUDIO) }
                ToolButton(Icons.Rounded.RecordVoiceOver, "Voice") { vm.openPanel(Panel.VOICE) }
                ToolButton(Icons.Rounded.GraphicEq, "Beats") { vm.openPanel(Panel.BEATS) }
                ToolButton(Icons.Rounded.ContentCut, "Split") { vm.split() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.EffectSel -> {
                ToolButton(Icons.Rounded.SwapHoriz, "Replace") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.Tune, "Adjust") { vm.openPanel(Panel.EFFECTS) }
                ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ToolButton(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            is Selection.FilterSel -> {
                ToolButton(Icons.Rounded.FilterVintage, "Filter") { vm.openPanel(Panel.FILTERS) }
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
    when (panel) {
        Panel.SPEED -> clip?.let { SpeedPanel(vm, it, onClose) }
        Panel.VOLUME -> clip?.let { VolumePanel(vm, it, onClose) }
        Panel.ANIMATION -> clip?.let { AnimationPanel(vm, it, onClose) }
        Panel.FILTERS -> FiltersPanel(vm, onClose)
        Panel.ADJUST -> clip?.let { AdjustPanel(vm, it, onClose) }
        Panel.EFFECTS -> EffectsPanel(vm, onClose)
        Panel.TEXT_EDIT -> text?.let { TextPanel(vm, it, 0, onClose) }
        Panel.TEXT_STYLE -> text?.let { TextPanel(vm, it, 1, onClose) }
        Panel.TEXT_ANIM -> text?.let { TextPanel(vm, it, 2, onClose) }
        Panel.STICKERS -> StickerPanel(vm, onClose)
        Panel.AUDIO -> AudioPanel(vm, onClose)
        Panel.CANVAS -> CanvasPanel(vm, onClose)
        Panel.KEYFRAMES -> KeyframePanel(vm, onClose)
        Panel.CHROMA -> clip?.let { ChromaPanel(vm, it, picking, onPick, onClose) }
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
    onPickColor: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val sel by androidx.compose.runtime.rememberUpdatedState(selection)
    val pick by androidx.compose.runtime.rememberUpdatedState(picking)
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val movable = sel is Selection.TextSel || sel is Selection.StickerSel || sel is Selection.ClipSel
                var moved = false
                var started = false
                do {
                    val e = awaitPointerEvent()
                    val zoom = e.calculateZoom()
                    val pan = e.calculatePan()
                    val rot = e.calculateRotation()
                    if (!moved && (pan.getDistance() > viewConfiguration.touchSlop / 4 || abs(zoom - 1f) > 0.01f || abs(rot) > 0.5f)) {
                        if (e.changes.any { it.positionChanged() }) moved = true
                    }
                    if (moved && movable && !pick) {
                        if (!started) { vm.beginGesture(); started = true }
                        val dx = pan.x / size.width
                        val dy = pan.y / size.height
                        vm.setTransformAtPlayhead(gesture = true) { t ->
                            t.copy(x = t.x + dx, y = t.y + dy, scale = (t.scale * zoom).coerceIn(0.05f, 8f), rotation = t.rotation + rot)
                        }
                        e.changes.forEach { it.consume() }
                    }
                } while (e.changes.any { it.pressed })
                if (started) vm.endGesture()
                if (!moved) {
                    if (pick) onPickColor(Offset(down.position.x / size.width, down.position.y / size.height))
                    else vm.preview.toggle()
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
        aspect.hashCode()
    }
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
