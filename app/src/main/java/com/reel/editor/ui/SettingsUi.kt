package com.reel.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reel.editor.BuildConfig
import com.reel.editor.Settings
import com.reel.editor.model.AspectRatio

private fun dirSize(f: java.io.File): Long = f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/** Defaults for new projects and exports, updates, storage. */
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var ratio by remember { mutableStateOf(Settings.defaultRatio(ctx)) }
    var height by remember { mutableIntStateOf(Settings.exportHeight(ctx)) }
    var fps by remember { mutableIntStateOf(Settings.exportFps(ctx)) }
    var cache by remember { mutableLongStateOf(dirSize(ctx.cacheDir)) }
    var updates by remember { mutableStateOf(false) }
    if (updates) UpdateSourceDialog { updates = false }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = Title, modifier = Modifier.weight(1f))
            IconBtn(Icons.Rounded.Close, "Close", onClick = onClose)
        }
        Section(Icons.Rounded.AspectRatio, "New projects") {
            listOf(AspectRatio.R9_16, AspectRatio.R1_1, AspectRatio.R4_5, AspectRatio.R16_9, AspectRatio.ORIGINAL).forEach { r ->
                Chip(r.label, ratio == r) { ratio = r; Settings.setDefaultRatio(ctx, r) }
            }
        }
        Section(Icons.Rounded.HighQuality, "Export size") {
            listOf(720 to "720p", 1080 to "1080p", 1440 to "2K", 2160 to "4K").forEach { (h, label) -> Chip(label, height == h) { height = h; Settings.setExportHeight(ctx, h) } }
        }
        Section(Icons.Rounded.Speed, "Frame rate") {
            listOf(24, 30, 60).forEach { f -> Chip("$f fps", fps == f) { fps = f; Settings.setExportFps(ctx, f) } }
        }
        Section(Icons.Rounded.SystemUpdate, "Updates") { Chip("Update source", false) { updates = true } }
        Section(Icons.Rounded.CleaningServices, "Storage") {
            Chip("Clear cache · ${cache / 1_000_000} MB", false) {
                ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                cache = dirSize(ctx.cacheDir)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Info, null, tint = C.Muted, modifier = Modifier.size(18.dp))
            Text("Reel ${BuildConfig.VERSION_NAME}", style = Body.copy(color = C.Muted))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Section(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(18.dp))
            Text(title, style = Body)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
