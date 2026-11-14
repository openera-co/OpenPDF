package com.reel.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import com.reel.editor.model.TrackFlags
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reel.editor.edit.Selection
import com.reel.editor.media.Thumbnails
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Placed
import com.reel.editor.model.Project
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimelineMath
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the timeline can do to the project; implemented by the editor screen. */
interface TimelineActions {
    fun scrubStart()
    fun scrub(timeUs: Long)
    fun scrubEnd()
    fun select(s: Selection)
    fun openTransition(clipId: String)
    fun gestureStart()
    fun gestureEnd()
    fun trimClip(clipId: String, left: Boolean, deltaUs: Long)
    fun moveBar(sel: Selection, startUs: Long)
    fun resizeBar(sel: Selection, left: Boolean, timeUs: Long)
    fun reorder(from: Int, to: Int)
    /** Eye, speaker and lock switches in the left column. */
    fun toggleTrack(kind: com.reel.editor.model.TrackFlags.Kind, key: String)
    fun muteAudio(ids: List<String>, muted: Boolean)
    fun addClips()
}

private sealed interface Hit {
    data object Empty : Hit
    data class ClipBody(val p: Placed) : Hit
    data class ClipHandle(val p: Placed, val left: Boolean) : Hit
    data class Transition(val clipId: String) : Hit
    data class Bar(val sel: Selection, val startUs: Long, val endUs: Long) : Hit
    data class BarHandle(val sel: Selection, val left: Boolean) : Hit
}

private data class BarItem(val sel: Selection, val startUs: Long, val endUs: Long, val label: String, val color: Color, val fill: Color, val row: Int)

@Composable
fun Timeline(
    project: Project,
    positionUs: Long,
    selection: Selection,
    waveforms: Map<String, FloatArray>,
    thumbnails: Thumbnails,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    var pxPerSec by remember { mutableStateOf(with(density) { 56.dp.toPx() }) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    var reorder by remember { mutableStateOf<Pair<Int, Float>?>(null) } // index being dragged, finger x
    val placed = remember(project.clips) { TimelineMath.place(project.clips) }
    val total = placed.lastOrNull()?.endUs ?: 0L

    // Row layout (px)
    val rulerH = with(density) { 22.dp.toPx() }
    val barH = with(density) { 24.dp.toPx() }
    val gap = with(density) { 6.dp.toPx() }
    val clipH = with(density) { 60.dp.toPx() }
    val audioH = with(density) { 32.dp.toPx() }
    val handleW = with(density) { 14.dp.toPx() }
    val corner = with(density) { 8.dp.toPx() }

    val catalog = com.reel.editor.fx.FxCatalog.get(androidx.compose.ui.platform.LocalContext.current)
    val names = remember(catalog) {
        (catalog.effects + catalog.filters).associate { it.id to it.name } + catalog.looks.associate { it.id to it.name }
    }
    // Every kind of layer has its own lanes; overlapping layers stack instead of covering each other.
    val layout = remember(project) { com.reel.editor.model.TrackLayout.assign(com.reel.editor.model.TrackLayout.itemsOf(project)) }
    val bars = remember(project, names, layout) {
        val list = ArrayList<BarItem>()
        fun row(k: String) = layout.rowOf[k] ?: 0
        project.effects.forEach { list += BarItem(Selection.EffectSel(it.id), it.startUs, it.startUs + it.durationUs, names[it.fx.id] ?: it.fx.id, C.Effect, Color(0xFF3B2F66), row("fx:${it.id}")) }
        project.filters.forEach { list += BarItem(Selection.FilterSel(it.id), it.startUs, it.startUs + it.durationUs, (it.lookId ?: it.shaderFx?.id)?.let { id -> names[id] ?: id } ?: "Filter", C.Music.copy(alpha = 0.9f), Color(0xFF143A3A), row("filter:${it.id}")) }
        project.adjustments.forEach { list += BarItem(Selection.AdjustSel(it.id), it.startUs, it.startUs + it.durationUs, "Adjust", C.Accent, Color(0xFF1A2E5A), row("adjust:${it.id}")) }
        project.texts.forEach { list += BarItem(Selection.TextSel(it.id), it.startUs, it.startUs + it.durationUs, it.text.replace('\n', ' '), C.TextTrack, Color(0xFF5A2A20), row("text:${it.id}")) }
        project.stickers.forEach { list += BarItem(Selection.StickerSel(it.id), it.startUs, it.startUs + it.durationUs, "Sticker", C.Overlay, Color(0xFF4A1D38), row("sticker:${it.id}")) }
        project.shapes.forEach { list += BarItem(Selection.ShapeSel(it.id), it.startUs, it.startUs + it.durationUs, it.shape.replaceFirstChar { c -> c.uppercase() }, Color(0xFFFFD36B), Color(0xFF4A3E14), row("shape:${it.id}")) }
        project.overlays.forEach { list += BarItem(Selection.OverlaySel(it.id), it.startUs, it.endUs, if (it.kind == ClipKind.PHOTO) "Photo" else "Video", C.Overlay, Color(0xFF3A1430), row("overlay:${it.id}")) }
        list
    }
    // Two lanes always show so the layout doesn't jump when the first layer arrives.
    val layerRows = max(layout.rows, 2)
    val currentRows by rememberUpdatedState(layerRows)
    var scrollY by remember { mutableFloatStateOf(0f) }
    fun rowY(r: Int) = rulerH + gap + r * (barH + gap) - scrollY
    fun clipYNow() = rulerH + gap + currentRows * (barH + gap) - scrollY
    val audioLanes = remember(project.audio) {
        val lanes = ArrayList<MutableList<com.reel.editor.model.AudioItem>>()
        for (a in project.audio.sortedBy { it.startUs }) {
            (lanes.firstOrNull { it.last().endUs <= a.startUs } ?: mutableListOf<com.reel.editor.model.AudioItem>().also { lanes += it }) += a
        }
        lanes
    }

    val pos by rememberUpdatedState(positionUs)
    val scale by rememberUpdatedState(pxPerSec)
    val currentPlaced by rememberUpdatedState(placed)
    val currentBars by rememberUpdatedState(bars)
    val currentSel by rememberUpdatedState(selection)
    val currentLanes by rememberUpdatedState(audioLanes)
    val currentTotal by rememberUpdatedState(total)
    fun audioYNow() = clipYNow() + clipH + gap * 1.5f
    fun contentBottom() = audioYNow() + scrollY + currentLanes.size * (audioH + gap) + gap

    BoxWithConstraints(modifier.clipToBounds().background(C.Track)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val currentHeight by rememberUpdatedState(heightPx)
        fun maxScroll() = (contentBottom() - currentHeight).coerceAtLeast(0f)
        // Removing layers can leave the view scrolled past the content.
        SideEffect { if (scrollY > maxScroll()) scrollY = maxScroll() }
        val clipY = clipYNow()
        val audioY = audioYNow()
        val centerX = widthPx / 2f
        fun xOf(t: Long) = centerX + (t - pos) / 1e6f * scale
        fun tOf(x: Float) = (pos + ((x - centerX) / scale * 1e6f).toLong())

        // Load thumbnails for visible tiles.
        val tileW = clipH * 0.75f
        val visibleKey = (positionUs / 250_000) * 31 + pxPerSec.roundToInt() + placed.size * 1000
        LaunchedEffect(visibleKey, placed) {
            val t0 = tOf(-tileW)
            val t1 = tOf(widthPx + tileW)
            for (p in placed) {
                if (p.endUs < t0 || p.startUs > t1) continue
                val tiles = max(1, (p.durationUs / 1e6f * scale / tileW).toInt() + 1)
                for (i in 0 until tiles) {
                    val tl = p.startUs + (i * tileW / scale * 1e6f).toLong()
                    if (tl < t0 || tl > t1) continue
                    val media = mediaTimeOf(p, tl)
                    val key = thumbKey(p.clip.uri, media)
                    if (thumbs.containsKey(key)) continue
                    launch {
                        thumbnails.frame(p.clip.uri, media, (clipH * 0.8f).toInt(), p.clip.kind == ClipKind.PHOTO)?.let { thumbs[key] = it.asImageBitmap() }
                    }
                }
            }
        }

        fun hitTest(o: Offset): Hit {
            val sel = currentSel
            val t = tOf(o.x)
            val clipY = clipYNow()
            val audioY = audioYNow()
            if (o.y < rulerH) return Hit.Empty
            // Selected clip handles win over everything.
            if (sel is Selection.ClipSel) currentPlaced.firstOrNull { it.clip.id == sel.id }?.let { p ->
                if (o.y in clipY - gap..clipY + clipH + gap) {
                    if (abs(o.x - (xOf(p.startUs) - handleW / 2)) < handleW * 1.4f) return Hit.ClipHandle(p, true)
                    if (abs(o.x - (xOf(p.endUs) + handleW / 2)) < handleW * 1.4f) return Hit.ClipHandle(p, false)
                }
            }
            currentBars.firstOrNull { it.sel == sel }?.let { b ->
                val y = rowY(b.row)
                if (o.y in y - gap..y + barH + gap) {
                    if (abs(o.x - xOf(b.startUs)) < handleW * 1.2f) return Hit.BarHandle(b.sel, true)
                    if (abs(o.x - xOf(b.endUs)) < handleW * 1.2f) return Hit.BarHandle(b.sel, false)
                }
            }
            if (o.y in clipY..clipY + clipH) {
                for (p in currentPlaced) if (p.index > 0 && abs(o.x - xOf(p.startUs)) < 14 * density.density) return Hit.Transition(p.clip.id)
                currentPlaced.firstOrNull { t >= it.startUs && t < it.endUs }?.let { return Hit.ClipBody(it) }
            }
            if (o.y < clipY) {
                val r = ((o.y - (rulerH + gap - scrollY)) / (barH + gap)).toInt()
                val inBar = (o.y - rowY(r)) <= barH + gap / 2
                if (inBar) currentBars.lastOrNull { it.row == r && t in it.startUs until it.endUs }?.let { return Hit.Bar(it.sel, it.startUs, it.endUs) }
            }
            currentLanes.forEachIndexed { i, lane ->
                val y = audioY + i * (audioH + gap)
                if (o.y in y..y + audioH) lane.firstOrNull { t >= it.startUs && t < it.endUs }?.let {
                    return if (sel == Selection.AudioSel(it.id)) Hit.Bar(Selection.AudioSel(it.id), it.startUs, it.endUs) else Hit.Bar(Selection.AudioSel(it.id), it.startUs, it.endUs)
                }
            }
            return Hit.Empty
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = hitTest(down.position)
                    val slop = viewConfiguration.touchSlop
                    var mode = 0 // 0 undecided, 1 scrub, 2 zoom, 3 trim, 4 move, 5 resize, 6 reorder
                    var moved = Offset.Zero
                    var lastSpan = 0f
                    val startPos = pos
                    var accDx = 0f
                    // Long press on a clip enters reorder mode; a quick lift is a tap.
                    if (hit is Hit.ClipBody) {
                        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var r = ""
                            while (r.isEmpty()) {
                                val e = awaitPointerEvent()
                                moved += e.changes[0].positionChange()
                                if (e.changes.any { !it.pressed }) r = "up"
                                else if (e.changes.size > 1 || moved.getDistance() > slop) r = "move"
                            }
                            r
                        }
                        when (early) {
                            null -> {
                                mode = 6
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorder = hit.p.index to down.position.x
                            }
                            "up" -> { tap(hit, actions); return@awaitEachGesture }
                        }
                    }
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = e.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val span = hypot(a.x - b.x, a.y - b.y)
                            if (mode != 2) { if (mode == 1) actions.scrubEnd(); mode = 2; lastSpan = span }
                            if (lastSpan > 0f) pxPerSec = (pxPerSec * span / lastSpan).coerceIn(4f * density.density, 1200f * density.density)
                            lastSpan = span
                            e.changes.forEach { it.consume() }
                            continue
                        }
                        val ch = pressed[0]
                        val d = ch.positionChange()
                        moved += d
                        if (mode == 0 && moved.getDistance() > slop && abs(moved.y) > abs(moved.x) * 1.3f && maxScroll() > 0f && hit !is Hit.ClipHandle && hit !is Hit.BarHandle) {
                            mode = 7
                        }
                        if (mode == 0 && moved.getDistance() > slop) {
                            mode = when (hit) {
                                is Hit.ClipHandle -> { actions.gestureStart(); 3 }
                                is Hit.Bar -> if (hit.sel == currentSel) { actions.gestureStart(); 4 } else { actions.scrubStart(); 1 }
                                is Hit.BarHandle -> { actions.gestureStart(); 5 }
                                else -> { actions.scrubStart(); 1 }
                            }
                        }
                        when (mode) {
                            1 -> actions.scrub((startPos - (moved.x / scale * 1e6f).toLong()).coerceIn(0, currentTotal))
                            3 -> {
                                val h = hit as Hit.ClipHandle
                                accDx += d.x
                                val deltaUs = (d.x / scale * 1e6f).toLong()
                                if (deltaUs != 0L) actions.trimClip(h.p.clip.id, h.left, deltaUs)
                            }
                            4 -> {
                                val b = hit as Hit.Bar
                                val raw = b.startUs + (moved.x / scale * 1e6f).toLong()
                                val snapped = TimelineMath.snap(raw, listOf(pos, 0L) + currentPlaced.flatMap { listOf(it.startUs, it.endUs) }, (8 * density.density / scale * 1e6f).toLong())
                                actions.moveBar(b.sel, snapped.coerceAtLeast(0))
                            }
                            5 -> {
                                val h = hit as Hit.BarHandle
                                val t = tOf(ch.position.x)
                                val snapped = TimelineMath.snap(t, listOf(pos) + currentPlaced.flatMap { listOf(it.startUs, it.endUs) }, (8 * density.density / scale * 1e6f).toLong())
                                actions.resizeBar(h.sel, h.left, snapped)
                            }
                            6 -> reorder = reorder?.copy(second = ch.position.x)
                            7 -> scrollY = (scrollY - d.y).coerceIn(0f, maxScroll())
                        }
                        ch.consume()
                    }
                    when (mode) {
                        0 -> tap(hit, actions)
                        1 -> actions.scrubEnd()
                        3, 4, 5 -> actions.gestureEnd()
                        6 -> {
                            val r = reorder
                            reorder = null
                            if (r != null) {
                                val to = reorderTarget(currentPlaced.size, r.second, centerX, clipH)
                                if (to != r.first) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.reorder(r.first, to) }
                            }
                        }
                    }
                }
            },
        ) {
            // Bars, culled to what's on screen.
            for (b in bars) {
                val y = rowY(b.row)
                if (y + barH < rulerH || y > size.height) continue
                val x0 = xOf(b.startUs)
                val x1 = xOf(b.endUs)
                if (x1 < 0 || x0 > size.width) continue
                val key = com.reel.editor.edit.trackKeyOf(b.sel)
                val off = key != null && project.tracks.isHidden(key)
                drawBar(measurer, x0, x1, y, barH, b.label, if (off) b.color.copy(alpha = 0.35f) else b.color, if (off) b.fill.copy(alpha = 0.35f) else b.fill, b.sel == selection, corner)
            }
            val r = reorder
            if (r != null) drawReorder(placed, thumbs, r, centerX, clipY, clipH, corner)
            else {
                for (p in placed) {
                    val x0 = xOf(p.startUs)
                    val x1 = xOf(p.endUs)
                    if (x1 < 0 || x0 > size.width) continue
                    drawClip(p, x0, x1, clipY, clipH, tileW, scale, thumbs, waveforms[p.clip.uri], corner, measurer)
                }
                // A hidden video track stays visible on the timeline, just dimmed.
                if (project.tracks.isHidden(TrackFlags.VIDEO) && placed.isNotEmpty()) {
                    drawRect(C.Track.copy(alpha = 0.65f), Offset(xOf(0), clipY), Size(xOf(total) - xOf(0), clipH))
                }
                // Transition markers
                for (p in placed) if (p.index > 0) {
                    val x = xOf(p.startUs)
                    val s = 22 * density.density
                    val has = p.clip.transitionIn != null
                    drawRoundRect(if (has) C.Accent else C.Text, Offset(x - s / 2, clipY + clipH / 2 - s / 2), Size(s, s), CornerRadius(6 * density.density))
                    val icon = Path().apply {
                        moveTo(x - s * 0.3f, clipY + clipH / 2 - s * 0.28f); lineTo(x, clipY + clipH / 2); lineTo(x - s * 0.3f, clipY + clipH / 2 + s * 0.28f); close()
                        moveTo(x + s * 0.3f, clipY + clipH / 2 - s * 0.28f); lineTo(x, clipY + clipH / 2); lineTo(x + s * 0.3f, clipY + clipH / 2 + s * 0.28f); close()
                    }
                    drawPath(icon, C.Bg)
                }
                // Selection frame with handles and keyframes
                (selection as? Selection.ClipSel)?.let { s ->
                    placed.firstOrNull { it.clip.id == s.id }?.let { p ->
                        val x0 = xOf(p.startUs)
                        val x1 = xOf(p.endUs)
                        val sw = 3 * density.density
                        drawRoundRect(Color.White, Offset(x0 - handleW, clipY - sw), Size(x1 - x0 + handleW * 2, clipH + sw * 2), CornerRadius(corner + 2), style = Stroke(sw))
                        drawRoundRect(Color.White, Offset(x0 - handleW, clipY - sw), Size(handleW, clipH + sw * 2), CornerRadius(corner))
                        drawRoundRect(Color.White, Offset(x1, clipY - sw), Size(handleW, clipH + sw * 2), CornerRadius(corner))
                        drawLine(C.Bg, Offset(x0 - handleW / 2, clipY + clipH * 0.35f), Offset(x0 - handleW / 2, clipY + clipH * 0.65f), 2.5f * density.density)
                        drawLine(C.Bg, Offset(x1 + handleW / 2, clipY + clipH * 0.35f), Offset(x1 + handleW / 2, clipY + clipH * 0.65f), 2.5f * density.density)
                        for (k in p.clip.keyframes) {
                            val kx = xOf(p.startUs + k.timeUs)
                            val on = abs(p.startUs + k.timeUs - pos) < 40_000
                            drawDiamond(Offset(kx, clipY + clipH - 6 * density.density), 5 * density.density, if (on) C.Key else C.Text)
                        }
                    }
                }
            }
            // Audio lanes
            audioLanes.forEachIndexed { i, lane ->
                val y = audioY + i * (audioH + gap)
                for (a in lane) {
                    val x0 = xOf(a.startUs)
                    val x1 = xOf(a.endUs)
                    if (x1 < 0 || x0 > size.width) continue
                    drawAudio(a, x0, x1, y, audioH, waveforms[a.uri], selection == Selection.AudioSel(a.id), corner, scale, measurer)
                }
            }
            // Ruler stays pinned above the scrolled rows.
            drawRect(C.Track, Offset.Zero, Size(size.width, rulerH))
            drawRuler(measurer, pos, scale, centerX, rulerH)
            if (maxScroll() > 0f) {
                // Scroll indicator at the right edge.
                val view = size.height - rulerH
                val content = contentBottom() - rulerH
                val h = (view * view / content).coerceAtLeast(16 * density.density)
                val y = rulerH + (view - h) * (scrollY / maxScroll())
                drawRoundRect(C.Dim, Offset(size.width - 4 * density.density, y), Size(3 * density.density, h), CornerRadius(2 * density.density))
            }
            // Playhead
            drawLine(Color.White, Offset(centerX, 4f), Offset(centerX, size.height - 4f), 2 * density.density)
            drawCircle(Color.White, 5 * density.density, Offset(centerX, 6 * density.density))
        }

        TrackColumn(project, layout, audioLanes.map { lane -> lane.map { it.id } to lane.all { it.muted } }, rowY = ::rowY, rulerH = rulerH, barH = barH,
            clipY = clipY, clipH = clipH, audioY = audioY, audioH = audioH, gap = gap, heightPx = heightPx, actions = actions)

        // Sticky add button at the right, vertically centred on the video row.
        Box(
            Modifier.offset { IntOffset((widthPx - with(density) { 52.dp.toPx() }).roundToInt(), (clipY + clipH / 2 - with(density) { 20.dp.toPx() }).roundToInt()) }
                .size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.Text),
            contentAlignment = Alignment.Center,
        ) {
            IconBtn(Icons.Rounded.Add, "Add clips", tint = C.Bg, size = 40.dp) { actions.addClips() }
        }
    }
}

private fun tap(hit: Hit, a: TimelineActions) {
    when (hit) {
        is Hit.ClipBody -> a.select(Selection.ClipSel(hit.p.clip.id))
        is Hit.ClipHandle -> a.select(Selection.ClipSel(hit.p.clip.id))
        is Hit.Transition -> a.openTransition(hit.clipId)
        is Hit.Bar -> a.select(hit.sel)
        is Hit.BarHandle -> a.select(hit.sel)
        Hit.Empty -> a.select(Selection.None)
    }
}

private fun thumbKey(uri: String, mediaUs: Long) = "$uri|${mediaUs / Thumbnails.GRID_US}"

private fun mediaTimeOf(p: Placed, timelineUs: Long): Long {
    val c = p.clip
    if (c.kind == ClipKind.PHOTO) return 0
    val local = (timelineUs - p.startUs).coerceIn(0, p.durationUs)
    return c.trimStartUs + SpeedMath.timelineToMedia(c.speed, c.trimmedDurationUs, local)
}

private fun reorderTarget(count: Int, fingerX: Float, centerX: Float, tile: Float): Int {
    val step = tile + 8f
    val start = centerX - count * step / 2f
    return floor((fingerX - start) / step).toInt().coerceIn(0, count - 1)
}

private fun DrawScope.drawRuler(m: TextMeasurer, pos: Long, scale: Float, centerX: Float, h: Float) {
    // Pick a label step that keeps labels ~80px apart.
    val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0)
    val step = steps.firstOrNull { it * scale >= 80f } ?: 120.0
    val t0 = pos / 1e6 - centerX / scale
    val t1 = pos / 1e6 + (size.width - centerX) / scale
    var t = floor(t0 / step) * step
    while (t <= t1) {
        if (t >= 0) {
            val x = centerX + ((t - pos / 1e6) * scale).toFloat()
            val label = if (step < 1) "%.1f".format(t) else formatTime((t * 1e6).toLong(), tenths = false)
            val layout = m.measure(label, Label.copy(fontSize = 9.sp, color = C.Muted))
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 4f))
            val mid = x + (step / 2 * scale).toFloat()
            drawCircle(C.Dim, 1.5f, Offset(mid, h * 0.45f))
        }
        t += step
    }
}

private fun DrawScope.drawBar(m: TextMeasurer, x0: Float, x1: Float, y: Float, h: Float, label: String, color: Color, fill: Color, selected: Boolean, corner: Float) {
    if (x1 < 0 || x0 > size.width) return
    drawRoundRect(fill, Offset(x0, y), Size(max(4f, x1 - x0), h), CornerRadius(corner * 0.8f))
    drawRoundRect(if (selected) Color.White else color, Offset(x0, y), Size(max(4f, x1 - x0), h), CornerRadius(corner * 0.8f), style = Stroke(if (selected) 2.5f * density else 1.2f * density))
    if (selected) {
        drawRoundRect(Color.White, Offset(x0 - 3 * density, y), Size(6 * density, h), CornerRadius(3 * density))
        drawRoundRect(Color.White, Offset(x1 - 3 * density, y), Size(6 * density, h), CornerRadius(3 * density))
    }
    clipRect(max(x0, 0f) + 6 * density, y, min(x1, size.width) - 6 * density, y + h) {
        val layout = m.measure(label, Label.copy(fontSize = 11.sp, color = Color.White), maxLines = 1)
        drawText(layout, topLeft = Offset(max(x0, 0f) + 8 * density, y + (h - layout.size.height) / 2))
    }
}

private fun DrawScope.drawClip(p: Placed, x0: Float, x1: Float, y: Float, h: Float, tileW: Float, scale: Float, thumbs: Map<String, ImageBitmap>, peaks: FloatArray?, corner: Float, m: TextMeasurer) {
    val w = x1 - x0
    val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x0, y, x1 - 2 * density, y + h), CornerRadius(corner))) }
    drawPath(path, C.Surface2)
    clipRect(max(0f, x0), y, min(size.width, x1 - 2 * density), y + h) {
        val tiles = max(1, (w / tileW).toInt() + 1)
        val firstVisible = max(0, ((-x0) / tileW).toInt() - 1)
        for (i in firstVisible until tiles) {
            val tx = x0 + i * tileW
            if (tx > size.width) break
            val tl = p.startUs + (i * tileW / scale * 1e6f).toLong()
            val img = thumbs[thumbKey(p.clip.uri, mediaTimeOf(p, tl))]
            if (img != null) {
                val iw = img.width.toFloat()
                val ih = img.height.toFloat()
                val sc = max(tileW / iw, h / ih)
                val sw = (tileW / sc).toInt().coerceAtMost(img.width)
                val sh = (h / sc).toInt().coerceAtMost(img.height)
                drawImage(img, srcOffset = IntOffset(((iw - sw) / 2).toInt(), ((ih - sh) / 2).toInt()), srcSize = IntSize(sw, sh), dstOffset = IntOffset(tx.toInt(), y.toInt()), dstSize = IntSize(tileW.toInt() + 1, h.toInt()))
            }
        }
        // Clip audio as a thin waveform along the bottom.
        if (peaks != null && p.clip.kind == ClipKind.VIDEO && !p.clip.muted) {
            val bh = h * 0.22f
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(x0, y + h - bh), Size(w, bh))
            drawWave(peaks, p.clip.trimStartUs, p.clip.trimEndUs, x0, x1, y + h - bh, bh, C.Music.copy(alpha = 0.85f))
        }
    }
    // Duration badge and speed badge
    val secs = "%.1fs".format(p.durationUs / 1e6)
    val layout = m.measure(secs, Label.copy(fontSize = 10.sp, color = Color.White))
    if (w > layout.size.width + 16 * density) {
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(x0 + 5 * density, y + 4 * density), Size(layout.size.width + 10 * density, layout.size.height + 2 * density), CornerRadius(5 * density))
        drawText(layout, topLeft = Offset(x0 + 10 * density, y + 5 * density))
    }
    val sp = p.clip.speed
    if (sp.curve != null || sp.constant != 1f) {
        val txt = if (sp.curve != null) (sp.curveName ?: "Curve") else "%.1fx".format(sp.constant)
        val l = m.measure(txt, Label.copy(fontSize = 10.sp, color = C.Bg))
        val bx = x0 + 5 * density
        val by = y + h - l.size.height - 6 * density
        if (w > l.size.width + 16 * density) {
            drawRoundRect(C.Key, Offset(bx, by), Size(l.size.width + 10 * density, l.size.height + 2 * density), CornerRadius(5 * density))
            drawText(l, topLeft = Offset(bx + 5 * density, by + 1 * density))
        }
    }
}

private fun DrawScope.drawWave(peaks: FloatArray, fromUs: Long, toUs: Long, x0: Float, x1: Float, y: Float, h: Float, color: Color) {
    if (peaks.isEmpty()) return
    val perSec = 40f
    val barW = 2 * density
    var x = max(0f, x0)
    val end = min(size.width, x1)
    while (x < end) {
        val frac = (x - x0) / (x1 - x0)
        val t = fromUs + ((toUs - fromUs) * frac).toLong()
        val idx = (t / 1e6f * perSec).toInt().coerceIn(0, peaks.size - 1)
        val v = peaks[idx]
        val bh = max(1.5f, v * h)
        drawRect(color, Offset(x, y + (h - bh) / 2), Size(barW * 0.6f, bh))
        x += barW
    }
}

private fun DrawScope.drawAudio(a: com.reel.editor.model.AudioItem, x0: Float, x1: Float, y: Float, h: Float, peaks: FloatArray?, selected: Boolean, corner: Float, scale: Float, m: TextMeasurer) {
    val color = if (a.kind == com.reel.editor.model.AudioKind.VOICEOVER) C.Voice else C.Music
    val fill = if (a.kind == com.reel.editor.model.AudioKind.VOICEOVER) Color(0xFF3A1218) else Color(0xFF0F3B37)
    drawRoundRect(fill, Offset(x0, y), Size(x1 - x0, h), CornerRadius(corner))
    clipRect(max(0f, x0), y, min(size.width, x1), y + h) {
        if (peaks != null) drawWave(peaks, a.trimStartUs, a.trimEndUs, x0, x1, y + h * 0.35f, h * 0.6f, color.copy(alpha = 0.9f))
        val l = m.measure(a.title, Label.copy(fontSize = 10.sp, color = Color.White), maxLines = 1)
        drawText(l, topLeft = Offset(max(x0, 0f) + 7 * density, y + 3 * density))
        // Detected beats as dots, custom markers as diamonds.
        with(com.reel.editor.model.Beats) {
            for (b in a.visibleAutoBeats()) {
                if (b < a.trimStartUs || b > a.trimEndUs) continue
                val bx = x0 + (b - a.trimStartUs) / 1e6f * scale
                drawCircle(C.Key, 2.4f * density, Offset(bx, y + h - 4.5f * density))
            }
        }
        for (b in a.markersUs) {
            if (b < a.trimStartUs || b > a.trimEndUs) continue
            val bx = x0 + (b - a.trimStartUs) / 1e6f * scale
            drawLine(C.Key.copy(alpha = 0.55f), Offset(bx, y + 2 * density), Offset(bx, y + h - 8 * density), strokeWidth = 1.2f * density)
            drawDiamond(Offset(bx, y + h - 5 * density), 4f * density, C.Key)
        }
        // Fades as shaded wedges
        if (a.fadeInUs > 0) {
            val fw = a.fadeInUs / 1e6f * scale
            drawPath(Path().apply { moveTo(x0, y); lineTo(x0 + fw, y); lineTo(x0, y + h); close() }, Color.Black.copy(alpha = 0.45f))
        }
        if (a.fadeOutUs > 0) {
            val fw = a.fadeOutUs / 1e6f * scale
            drawPath(Path().apply { moveTo(x1, y); lineTo(x1 - fw, y); lineTo(x1, y + h); close() }, Color.Black.copy(alpha = 0.45f))
        }
    }
    drawRoundRect(if (selected) Color.White else color, Offset(x0, y), Size(x1 - x0, h), CornerRadius(corner), style = Stroke(if (selected) 2.5f * density else 1.2f * density))
}

private fun DrawScope.drawDiamond(c: Offset, r: Float, color: Color) {
    rotate(45f, c) {
        drawRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        drawRect(C.Bg, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.5f * density))
    }
}

private fun DrawScope.drawReorder(placed: List<Placed>, thumbs: Map<String, ImageBitmap>, r: Pair<Int, Float>, centerX: Float, y: Float, tile: Float, corner: Float) {
    val n = placed.size
    val step = tile + 8f
    val start = centerX - n * step / 2f
    val target = reorderTarget(n, r.second, centerX, tile)
    var slot = 0
    for (i in 0 until n) {
        if (i == r.first) continue
        if (slot == target) slot++
        val x = start + slot * step
        drawTile(placed[i], thumbs, x, y, tile, corner, 1f)
        slot++
    }
    drawRoundRect(C.Accent, Offset(start + target * step, y), Size(tile, tile), CornerRadius(corner), style = Stroke(2 * density))
    drawTile(placed[r.first], thumbs, r.second - tile * 0.55f, y - tile * 0.25f, tile * 1.1f, corner, 1f)
}

private fun DrawScope.drawTile(p: Placed, thumbs: Map<String, ImageBitmap>, x: Float, y: Float, s: Float, corner: Float, alpha: Float) {
    drawRoundRect(C.Surface2, Offset(x, y), Size(s, s), CornerRadius(corner), alpha = alpha)
    val img = thumbs[thumbKey(p.clip.uri, mediaTimeOf(p, p.startUs))] ?: thumbs.entries.firstOrNull { it.key.startsWith(p.clip.uri + "|") }?.value
    if (img != null) {
        val side = min(img.width, img.height)
        clipRect(x, y, x + s, y + s) {
            drawImage(img, srcOffset = IntOffset((img.width - side) / 2, (img.height - side) / 2), srcSize = IntSize(side, side), dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(s.toInt(), s.toInt()), alpha = alpha)
        }
    }
}

/** Small round switch in the track column. */
@Composable
private fun TrackSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) C.Surface2 else C.Accent.copy(alpha = 0.25f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, label, tint = if (on) C.Soft else C.Accent, modifier = Modifier.size(15.dp)) }
}

/**
 * The column at the left of the timeline: per track, the switches that make sense for it. Video
 * can be muted or hidden; text, stickers and effects hidden or locked; overlays hidden, muted or
 * locked; each music lane muted. "on" is the normal state, so switched-off icons stand out.
 */
@Composable
private fun TrackColumn(
    project: Project, layout: com.reel.editor.model.TrackLayout.Result, audioLanes: List<Pair<List<String>, Boolean>>,
    rowY: (Int) -> Float, rulerH: Float, barH: Float, clipY: Float, clipH: Float, audioY: Float, audioH: Float, gap: Float, heightPx: Float,
    actions: TimelineActions,
) {
    val density = LocalDensity.current
    val t = project.tracks
    val sw = with(density) { 26.dp.toPx() }
    val colW = 88.dp
    Box(Modifier.width(colW).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0.75f to C.Track, 1f to C.Track.copy(alpha = 0f))))
    @Composable
    fun RowOf(y: Float, h: Float, content: @Composable () -> Unit) {
        val top = y + (h - sw) / 2
        if (top + sw < rulerH || top > heightPx) return
        Row(Modifier.offset { IntOffset(with(density) { 6.dp.roundToPx() }, top.roundToInt()) }, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
    for ((g, rows) in layout.groupRows) {
        val y = rowY(rows.first)
        when (g) {
            com.reel.editor.model.TrackLayout.Group.EFFECTS -> RowOf(y, barH) {
                TrackSwitch(if (t.isHidden(TrackFlags.EFFECTS)) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show effects", !t.isHidden(TrackFlags.EFFECTS)) { actions.toggleTrack(TrackFlags.Kind.HIDE, TrackFlags.EFFECTS) }
                TrackSwitch(if (t.isLocked(TrackFlags.EFFECTS)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock effects", !t.isLocked(TrackFlags.EFFECTS)) { actions.toggleTrack(TrackFlags.Kind.LOCK, TrackFlags.EFFECTS) }
            }
            com.reel.editor.model.TrackLayout.Group.TEXT -> RowOf(y, barH) {
                TrackSwitch(if (t.isHidden(TrackFlags.TEXT)) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show text", !t.isHidden(TrackFlags.TEXT)) { actions.toggleTrack(TrackFlags.Kind.HIDE, TrackFlags.TEXT) }
                TrackSwitch(if (t.isLocked(TrackFlags.TEXT)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock text", !t.isLocked(TrackFlags.TEXT)) { actions.toggleTrack(TrackFlags.Kind.LOCK, TrackFlags.TEXT) }
            }
            com.reel.editor.model.TrackLayout.Group.STICKERS -> RowOf(y, barH) {
                TrackSwitch(if (t.isHidden(TrackFlags.STICKERS)) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show stickers and shapes", !t.isHidden(TrackFlags.STICKERS)) { actions.toggleTrack(TrackFlags.Kind.HIDE, TrackFlags.STICKERS) }
                TrackSwitch(if (t.isLocked(TrackFlags.STICKERS)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock stickers and shapes", !t.isLocked(TrackFlags.STICKERS)) { actions.toggleTrack(TrackFlags.Kind.LOCK, TrackFlags.STICKERS) }
            }
            com.reel.editor.model.TrackLayout.Group.OVERLAYS -> RowOf(y, barH) {
                TrackSwitch(if (t.isHidden(TrackFlags.OVERLAYS)) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show overlays", !t.isHidden(TrackFlags.OVERLAYS)) { actions.toggleTrack(TrackFlags.Kind.HIDE, TrackFlags.OVERLAYS) }
                if (project.overlays.any { it.hasAudio }) TrackSwitch(if (t.isMuted(TrackFlags.OVERLAYS)) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Overlay sound", !t.isMuted(TrackFlags.OVERLAYS)) { actions.toggleTrack(TrackFlags.Kind.MUTE, TrackFlags.OVERLAYS) }
                TrackSwitch(if (t.isLocked(TrackFlags.OVERLAYS)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock overlays", !t.isLocked(TrackFlags.OVERLAYS)) { actions.toggleTrack(TrackFlags.Kind.LOCK, TrackFlags.OVERLAYS) }
            }
        }
    }
    if (project.clips.isNotEmpty()) {
        val top = clipY + (clipH - sw * 2 - with(density) { 4.dp.toPx() }) / 2
        if (top + sw * 2 > rulerH && top < heightPx) {
            Column(Modifier.offset { IntOffset(with(density) { 6.dp.roundToPx() }, top.roundToInt()) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TrackSwitch(if (t.isMuted(TrackFlags.VIDEO)) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Video sound", !t.isMuted(TrackFlags.VIDEO)) { actions.toggleTrack(TrackFlags.Kind.MUTE, TrackFlags.VIDEO) }
                    TrackSwitch(if (t.isHidden(TrackFlags.VIDEO)) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show video", !t.isHidden(TrackFlags.VIDEO)) { actions.toggleTrack(TrackFlags.Kind.HIDE, TrackFlags.VIDEO) }
                }
                TrackSwitch(if (t.isLocked(TrackFlags.VIDEO)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock video", !t.isLocked(TrackFlags.VIDEO)) { actions.toggleTrack(TrackFlags.Kind.LOCK, TrackFlags.VIDEO) }
            }
        }
    }
    audioLanes.forEachIndexed { i, (ids, muted) ->
        RowOf(audioY + i * (audioH + gap), audioH) {
            TrackSwitch(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Audio sound", !muted) { actions.muteAudio(ids, !muted) }
        }
    }
}
