package com.reel.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.FxDef
import com.reel.editor.fx.FxThumbs
import com.reel.editor.fx.TextMotion
import com.reel.editor.fx.TransitionDef
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TimelineMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import kotlin.math.roundToInt

/** A prepared preview source: a small frame and a stable key for caching renders of it. */
data class FxSource(val bitmap: Bitmap, val key: String)

private const val TICK_MS = 90L

/** One clock for every animated tile in a panel; tiles read it only while drawing. */
@Composable
fun rememberThumbTick(): State<Int> {
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            tick.intValue++
        }
    }
    return tick
}

/** The clip's frame at [localUs] on the timeline (offset from the clip start), ready for previews. */
@Composable
fun rememberClipSource(vm: EditorViewModel, clip: Clip?, localUs: Long, tag: String = ""): FxSource? {
    val mediaUs = clip?.let {
        if (it.kind == ClipKind.PHOTO) 0L
        else it.trimStartUs + SpeedMath.timelineToMedia(it.speed, it.trimmedDurationUs, localUs.coerceAtLeast(0))
    } ?: 0L
    val bucket = mediaUs / 250_000
    val state = produceState<FxSource?>(null, clip?.uri, bucket) {
        val frame = clip?.let { vm.thumbnails.frame(it.uri, mediaUs, 240, it.kind == ClipKind.PHOTO) }
        val key = if (clip == null) "sample$tag" else "${clip.uri.hashCode()}:$bucket"
        value = FxSource(FxThumbs.prepare(frame, hueShift = tag == "b"), key)
    }
    return state.value
}

/**
 * The playhead in half-second steps, following it only while paused: scrubbing updates the
 * previews, playback does not re-render them every frame. Null until the first value arrives.
 */
@Composable
fun rememberPausedPlayheadUs(vm: EditorViewModel): Long? {
    val flow = remember(vm) {
        kotlinx.coroutines.flow.combine(vm.preview.positionUs, vm.preview.playing) { p, playing -> p to playing }
            .filter { !it.second }
            .map { it.first / 500_000 * 500_000 }
            .distinctUntilChanged()
    }
    val pos by flow.collectAsState(initial = -1L)
    return pos.takeIf { it >= 0 }
}

/** The clip's frame under the playhead when the playhead is on it, else its first frame. */
@Composable
fun rememberTargetSource(vm: EditorViewModel, clip: Clip?): FxSource? {
    val project by vm.project.collectAsStateCompat()
    val pos = rememberPausedPlayheadUs(vm) ?: 0L
    val placed = clip?.let { c -> TimelineMath.place(project.clips).firstOrNull { it.clip.id == c.id } }
    val local = placed?.let { (pos - it.startUs).takeIf { l -> l in 0 until it.durationUs } } ?: 0L
    return rememberClipSource(vm, clip, local)
}

/** The frame under the playhead, used where an effect is not tied to one clip. */
@Composable
fun rememberPlayheadSource(vm: EditorViewModel): FxSource? {
    val project by vm.project.collectAsStateCompat()
    val pos = rememberPausedPlayheadUs(vm) ?: 0L
    val placed = TimelineMath.clipAt(TimelineMath.place(project.clips), pos)
    return rememberClipSource(vm, placed?.clip, placed?.let { pos - it.startUs } ?: 0L)
}

/** Tile for a filter, effect or clip animation, rendered with its real shader. */
@Composable
fun ShaderFxTile(
    catalog: FxCatalog, def: FxDef, mode: FxThumbs.Mode, source: FxSource?, tick: State<Int>,
    selected: Boolean, fallback: Brush, icon: ImageVector, onClick: () -> Unit,
) {
    val frames by produceState(source?.let { FxThumbs.cached("fx:${def.id}:$mode:${it.key}") }?.map { it.asImageBitmap() }, def.id, mode, source?.key) {
        val s = source ?: return@produceState
        value = FxThumbs.fx(catalog, def, mode, s.bitmap, s.key)?.map { it.asImageBitmap() }
    }
    PreviewTile(def.name, selected, frames, tick, fallback, icon, onClick)
}

/** Tile for a transition, played from the previous clip into this one. */
@Composable
fun TransitionTile(
    catalog: FxCatalog, def: TransitionDef, from: FxSource?, to: FxSource?, tick: State<Int>,
    selected: Boolean, fallback: Brush, icon: ImageVector, onClick: () -> Unit,
) {
    val key = if (from != null && to != null) "${from.key}>${to.key}" else null
    val frames by produceState(key?.let { FxThumbs.cached("tr:${def.id}:$it") }?.map { it.asImageBitmap() }, def.id, key) {
        if (from == null || to == null || key == null) return@produceState
        value = FxThumbs.transition(catalog, def, from.bitmap, to.bitmap, key)?.map { it.asImageBitmap() }
    }
    PreviewTile(def.name, selected, frames, tick, fallback, icon, onClick)
}

@Composable
fun PreviewTile(name: String, selected: Boolean, frames: List<ImageBitmap>?, tick: State<Int>, fallback: Brush, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(fallback)
                .then(if (selected) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (frames.isNullOrEmpty()) {
                Icon(icon, null, tint = if (selected) C.Accent else C.Text, modifier = Modifier.size(22.dp))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    // Reading the tick here redraws the tile without recomposing it.
                    val img = frames[if (frames.size == 1) 0 else tick.value % frames.size]
                    drawCropped(img)
                }
            }
        }
        Text(name, style = Label.copy(color = if (selected) C.Text else C.Muted), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropped(img: ImageBitmap) {
    val dstAspect = size.width / size.height
    val srcAspect = img.width.toFloat() / img.height
    val (sw, sh) = if (srcAspect > dstAspect) (img.height * dstAspect).roundToInt() to img.height else img.width to (img.width / dstAspect).roundToInt()
    drawImage(
        img,
        srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
        srcSize = IntSize(sw.coerceAtLeast(1), sh.coerceAtLeast(1)),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

/** Text animation tile: the real motion curves applied to a sample word, looping. */
@Composable
fun TextAnimTile(id: String, phase: Int, selected: Boolean, tick: State<Int>, onClick: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2)
                .then(if (selected) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val word = "Text"
            // 22 ticks per loop: 11 animate, 11 hold (in), or the reverse (out).
            val k = tick.value % 22
            val timeSec = tick.value * TICK_MS / 1000f
            val motion = when {
                id == "none" -> com.reel.editor.fx.LayerMotion()
                phase == 0 -> TextMotion.appear(id, (k / 10f).coerceAtMost(1f), timeSec)
                phase == 1 -> TextMotion.appear(id, (1f - (k - 11) / 10f).coerceIn(0f, 1f), timeSec)
                else -> TextMotion.loop(id, timeSec)
            }
            val shown = if (motion.reveal >= 1f) word else word.take((word.length * motion.reveal).toInt())
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = motion.opacity.coerceIn(0f, 1f)
                scaleX = motion.scale
                scaleY = motion.scale
                // Motion offsets are fractions of the canvas; scale them up so they read in a small tile.
                translationX = motion.dx * size.width * 1.6f
                translationY = motion.dy * size.height * 2.4f
                rotationZ = motion.rotationDeg
            }, contentAlignment = Alignment.Center) {
                Text(shown, style = Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Black, color = if (selected) C.Accent else Color.White))
            }
        }
        Text(TextMotion.label(id), style = Label.copy(color = if (selected) C.Text else C.Muted), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
