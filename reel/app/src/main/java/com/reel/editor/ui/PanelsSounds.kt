package com.reel.editor.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.media.SoundFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun SoundFx.Category.icon() = when (this) {
    SoundFx.Category.MOVES -> Icons.Rounded.Air
    SoundFx.Category.HITS -> Icons.Rounded.Vibration
    SoundFx.Category.UI -> Icons.Rounded.TouchApp
    SoundFx.Category.FUN -> Icons.Rounded.AutoAwesome
}

/** Built-in sound effects: tap a tile to hear it, tap + to drop it at the playhead. */
@Composable
fun SoundsGrid(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf(SoundFx.Category.MOVES) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() { player[0]?.let { runCatching { it.stop() }; it.release() }; player[0] = null; playing = null }
    fun play(s: SoundFx.Sfx) {
        stop()
        playing = s.id
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { SoundFx.file(ctx.filesDir, s) }.getOrNull() } ?: run { playing = null; return@launch }
            if (playing != s.id) return@launch
            player[0] = runCatching {
                MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    setOnCompletionListener { if (playing == s.id) playing = null }
                    prepare(); start()
                }
            }.getOrNull()
        }
    }
    DisposableEffect(Unit) { onDispose { stop() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoundFx.Category.entries.forEach { c -> Chip(c.label, c == cat) { cat = c } }
        }
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.heightIn(min = 120.dp, max = 260.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SoundFx.all.filter { it.category == cat }, key = { it.id }) { s ->
                val on = playing == s.id
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2)
                        .border(if (on) 2.dp else 0.dp, if (on) C.Sfx else C.Surface2, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "Play ${s.name}") { if (on) stop() else play(s) }
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (on) Icons.Rounded.GraphicEq else s.category.icon(), null, tint = C.Sfx, modifier = Modifier.size(18.dp))
                    Text(s.name, style = Label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(C.Sfx.copy(alpha = 0.18f))
                            .clickable(role = Role.Button, onClickLabel = "Add ${s.name}") { stop(); vm.addSound(s) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, "Add ${s.name}", tint = C.Sfx, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
