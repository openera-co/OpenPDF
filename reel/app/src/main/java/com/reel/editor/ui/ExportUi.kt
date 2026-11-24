package com.reel.editor.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.engine.ExportState
import com.reel.editor.engine.Exporter
import com.reel.editor.fx.RenderPlans
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Warning
import com.reel.editor.model.BitrateLevel
import com.reel.editor.model.ClipKind
import com.reel.editor.model.ExportSettings
import com.reel.editor.model.TimelineMath

private val HEIGHTS = listOf(720 to "720p", 1080 to "1080p", 1440 to "2K", 2160 to "4K")
private val RATES = listOf(24, 25, 30, 50, 60, 90, 120)

/** One tap: what each platform plays best. Short-video apps want 9:16 at 1080p, plain H.264. */
private class Preset(val name: String, val settings: ExportSettings, val vertical: Boolean)
private val PRESETS = listOf(
    Preset("TikTok", ExportSettings(1080, 30, BitrateLevel.RECOMMENDED, false), true),
    Preset("Reels", ExportSettings(1080, 30, BitrateLevel.RECOMMENDED, false), true),
    Preset("Shorts", ExportSettings(1080, 30, BitrateLevel.HIGHER, false), true),
    Preset("YouTube", ExportSettings(2160, 30, BitrateLevel.HIGHER, false), false),
    Preset("Small", ExportSettings(720, 30, BitrateLevel.LOWER, true), false),
)

@Composable
fun ExportSheet(vm: EditorViewModel, onClose: () -> Unit, onStart: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    var s by remember { mutableStateOf(ExportSettings(height = com.reel.editor.Settings.exportHeight(ctx0), fps = com.reel.editor.Settings.exportFps(ctx0))) }
    val (w, h) = RenderPlans.canvasSize(project, s.height)
    val dur = TimelineMath.duration(project)
    val bytes = when (s.format) {
        com.reel.editor.model.ExportFormat.VIDEO -> Exporter.estimateBytes(project, s, dur)
        // About 0.1 byte per pixel per frame with the fixed palette.
        com.reel.editor.model.ExportFormat.GIF -> RenderPlans.canvasSize(project, minOf(s.height, 360)).let { (w, h) -> (w.toLong() * h * minOf(s.fps, 15) * dur / 1_000_000 / 10) }
        com.reel.editor.model.ExportFormat.AUDIO -> 192_000L / 8 * dur / 1_000_000
    }
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export", style = Title.copy(fontSize = 18.sp), modifier = Modifier.weight(1f).padding(top = 12.dp))
            IconBtn(Icons.Rounded.Close, "Close", iconSize = 20.dp, onClick = onClose)
        }
        Segmented(listOf("Video", "GIF", "Sound"), s.format.ordinal, { s = s.copy(format = com.reel.editor.model.ExportFormat.entries[it]) }, Modifier.fillMaxWidth())
        when (s.format) {
            com.reel.editor.model.ExportFormat.VIDEO -> {
            var preset by remember { mutableStateOf<String?>(null) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { pr -> Chip(pr.name, preset == pr.name) { preset = pr.name; s = pr.settings } }
            }
            val needsVertical = PRESETS.firstOrNull { it.name == preset }?.vertical == true && project.canvas.ratio != com.reel.editor.model.AspectRatio.R9_16
            if (needsVertical) {
                // These apps fill the phone screen: offer to switch the canvas.
                Chip("9:16", false, icon = Icons.Rounded.Warning) { vm.edit { p -> p.copy(canvas = p.canvas.copy(ratio = com.reel.editor.model.AspectRatio.R9_16)) } }
            }
            Stepper("Resolution", "$w × $h", HEIGHTS.map { it.second }, HEIGHTS.indexOfFirst { it.first == s.height }) { s = s.copy(height = HEIGHTS[it].first) }
            Stepper("Frame rate", "${s.fps} fps", RATES.map { "$it" }, RATES.indexOf(s.fps)) { s = s.copy(fps = RATES[it]) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bitrate", style = Title.copy(fontSize = 14.sp))
                Segmented(listOf("Lower", "Recommended", "Higher"), s.bitrate.ordinal, { s = s.copy(bitrate = BitrateLevel.entries[it]) }, Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HEVC (smaller files)", style = Body, modifier = Modifier.weight(1f))
                Switch(s.hevc, { s = s.copy(hevc = it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
            }
            }
            com.reel.editor.model.ExportFormat.GIF -> {
                // GIFs stay small: short side 240 or 360, 10 or 15 frames a second.
                val (gw, gh) = RenderPlans.canvasSize(project, minOf(s.height, 360))
                Stepper("Size", "$gw × $gh", listOf("240p", "360p"), if (s.height <= 240) 0 else 1) { s = s.copy(height = if (it == 0) 240 else 360) }
                Stepper("Frame rate", "${minOf(s.fps, 15)} fps", listOf("10", "15"), if (s.fps <= 10) 0 else 1) { s = s.copy(fps = if (it == 0) 10 else 15) }
            }
            com.reel.editor.model.ExportFormat.AUDIO -> Unit
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Bg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, tint = C.Soft, modifier = Modifier.size(18.dp))
            Text("Estimated size", style = Body.copy(color = C.Soft), modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text(sizeText(bytes), style = Mono.copy(fontSize = 15.sp))
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(C.Accent).clickable(role = Role.Button) {
                vm.saveNow()
                vm.preview.suspendForExport()
                Exporter.start(vm.getApplication(), project, s)
                onStart()
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.FileUpload, null, tint = C.Bg)
            Text(when (s.format) { com.reel.editor.model.ExportFormat.GIF -> "Export GIF"; com.reel.editor.model.ExportFormat.AUDIO -> "Export sound"; else -> "Export video" }, style = Title.copy(fontSize = 16.sp, color = C.Bg), modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stepper(title: String, value: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { Text(title, style = Title.copy(fontSize = 14.sp), modifier = Modifier.weight(1f)); Text(value, style = Mono.copy(color = C.Muted)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { i, l ->
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (i == selected) C.Text else C.Surface2).clickable(role = Role.Button) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, style = Body.copy(fontSize = 13.sp, color = if (i == selected) C.Bg else C.Soft)) }
            }
        }
    }
}

private fun sizeText(b: Long) = if (b > 1_000_000_000) "%.1f GB".format(b / 1e9) else "${(b / 1e6).toInt().coerceAtLeast(1)} MB"

@Composable
fun ExportScreen(vm: EditorViewModel, onBackToEdit: () -> Unit, onHome: () -> Unit) {
    val state by Exporter.state.collectAsStateCompat()
    val project by vm.project.collectAsStateCompat()
    val ctx = LocalContext.current
    val first = project.clips.firstOrNull()
    val thumb by produceState<Bitmap?>(null, first?.uri) {
        first?.let { value = vm.thumbnails.frame(it.uri, it.trimStartUs, 720, it.kind == ClipKind.PHOTO) }
    }
    BackHandler { if (state is ExportState.Running) Unit else { Exporter.reset(); onBackToEdit() } }
    // The preview gave up its decoders for the export; take them back once it has ended.
    LaunchedEffect(state is ExportState.Running) { if (state !is ExportState.Running) vm.preview.resumeAfterExport() }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state !is ExportState.Running) IconBtn(Icons.Rounded.Close, "Home") { Exporter.reset(); onHome() }
        }
        Spacer(Modifier.height(20.dp))
        val progress = (state as? ExportState.Running)?.progress ?: if (state is ExportState.Done) 1f else 0f
        val animated by animateFloatAsState(progress, label = "progress")
        Box(Modifier.width(210.dp).fillMaxHeight(0.48f).clip(RoundedCornerShape(20.dp)).background(C.Surface)) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (state is ExportState.Running) Box(Modifier.fillMaxWidth().fillMaxHeight(1f - animated).background(C.Bg.copy(alpha = 0.72f)))
        }
        Spacer(Modifier.height(28.dp))
        when (val s = state) {
            is ExportState.Running -> {
                Text("${(s.progress * 100).toInt()}%", style = Title.copy(fontSize = 44.sp))
                Spacer(Modifier.height(8.dp))
                Text(s.etaSec?.let { "${it / 60}:%02d left".format(it % 60) } ?: "Starting", style = Body.copy(color = C.Muted))
                Spacer(Modifier.height(20.dp))
                Box(Modifier.width(300.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(RoundedCornerShape(3.dp)).background(C.Accent))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface2).clickable { Exporter.cancel() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Close, null, tint = C.Text); Text("Cancel", style = Title, modifier = Modifier.padding(start = 8.dp))
                }
            }
            is ExportState.Done -> {
                Row(Modifier.clip(RoundedCornerShape(17.dp)).background(C.Good.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = C.Good, modifier = Modifier.size(16.dp))
                    Text("Saved to gallery · ${sizeText(s.sizeBytes)}", style = Body.copy(color = C.Good), modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(C.Surface2).clickable {
                    val mime = when (s.file.extension) { "gif" -> "image/gif"; "m4a" -> "audio/mp4"; else -> "video/mp4" }
                    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, null))
                }.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Share, null, tint = C.Text); Text("Share", style = Title, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface2).clickable { Exporter.reset(); onBackToEdit() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, tint = C.Text); Text("Back to edit", style = Title, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Accent).clickable { Exporter.reset(); onHome() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("Done", style = Title.copy(color = C.Bg))
                    }
                }
            }
            is ExportState.Failed -> {
                Icon(Icons.Rounded.ErrorOutline, null, tint = C.Voice, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(s.message, style = Body, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface2).clickable { Exporter.reset(); onBackToEdit() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Back to edit", style = Title)
                }
            }
            ExportState.Cancelled, ExportState.Idle -> {
                Text("Export cancelled", style = Body.copy(color = C.Muted))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface2).clickable { Exporter.reset(); onBackToEdit() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Back to edit", style = Title)
                }
            }
        }
    }
}
