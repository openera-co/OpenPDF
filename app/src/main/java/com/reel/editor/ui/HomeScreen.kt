package com.reel.editor.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reel.editor.edit.ProjectStore
import com.reel.editor.media.Thumbnails
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Project
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(store: ProjectStore, thumbnails: Thumbnails, onNew: () -> Unit, onOpen: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    // Null until the first load, so "no drafts" never flashes while drafts are being read.
    val loaded by produceState<List<Project>?>(initialValue = null, version) { value = withContext(Dispatchers.IO) { store.list() } }
    val drafts = loaded.orEmpty()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(C.Bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = C.Bg, modifier = Modifier.size(20.dp))
                }
                Text("Reel", style = Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 10.dp))
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(20.dp)).background(C.Accent).clickable(onClick = onNew),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(C.Bg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "New project", tint = C.Bg)
                }
                Text("New project", style = Title.copy(fontSize = 17.sp, color = C.Bg), modifier = Modifier.padding(start = 12.dp))
            }
        }
        item(span = { GridItemSpan(2) }) {
            if (loaded != null) Text(if (drafts.isEmpty()) "No drafts yet" else "Drafts  ${drafts.size}", style = Title.copy(fontSize = 18.sp, color = if (drafts.isEmpty()) C.Muted else C.Text), modifier = Modifier.padding(top = 8.dp))
        }
        items(drafts, key = { it.id }) { d ->
            Column(
                Modifier.combinedClickable(onClick = { onOpen(d.id) }, onLongClick = { menuFor = d.id }),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.88f).clip(RoundedCornerShape(16.dp)).background(C.Surface)) {
                    val first = d.clips.firstOrNull()
                    if (first != null) {
                        val bmp by produceState<Bitmap?>(null, first.uri) {
                            value = thumbnails.frame(first.uri, first.trimStartUs, 320, first.kind == ClipKind.PHOTO)
                        }
                        androidx.compose.animation.AnimatedVisibility(bmp != null, enter = fadeIn(), exit = fadeOut()) {
                            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    } else Icon(Icons.Rounded.Movie, null, tint = C.Dim, modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(11.dp)).background(C.Bg.copy(alpha = 0.72f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(formatTime(TimelineMath.duration(d), tenths = false), style = Mono.copy(fontSize = 11.sp))
                    }
                    Box(Modifier.align(Alignment.TopEnd)) {
                        IconBtn(Icons.Rounded.MoreHoriz, "Draft options", size = 36.dp, iconSize = 20.dp) { menuFor = d.id }
                        DropdownMenu(expanded = menuFor == d.id, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) }, onClick = { menuFor = null; renaming = d })
                            DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                                menuFor = null
                                store.save(d.copy(id = newId(), name = d.name + " copy", updatedAt = System.currentTimeMillis()))
                                version++
                            })
                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menuFor = null; confirmDelete = d })
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.name, style = Body, maxLines = 1)
                    Text(relative(d.updatedAt), style = Label.copy(color = C.Muted, fontSize = 12.sp))
                }
            }
        }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.navigationBarsPadding()) }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = { TextButton(onClick = { store.save(p.copy(name = name.trim().ifEmpty { p.name })); renaming = null; version++ }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        )
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            confirmButton = { TextButton(onClick = { store.delete(p.id); confirmDelete = null; version++ }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("The draft is removed. Your original videos stay in the gallery.") },
        )
    }
    LaunchedEffect(Unit) { version++ }
}

private fun relative(t: Long): String {
    val d = (System.currentTimeMillis() - t) / 1000
    return when {
        d < 60 -> "Just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86400 -> "${d / 3600} h ago"
        d < 172800 -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(t))
    }
}
