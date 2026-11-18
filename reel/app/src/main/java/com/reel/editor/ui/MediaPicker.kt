package com.reel.editor.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reel.editor.media.MediaLibrary
import com.reel.editor.media.MediaLibrary.Kind

/** What the caller wants picked. [audioFromVideo] lets audio requests use a video's soundtrack. */
class PickerRequest(
    val kinds: Set<Kind>,
    val multi: Boolean,
    val audioFromVideo: Boolean = false,
    val onResult: (List<Uri>) -> Unit,
)

class PickerController {
    var request by mutableStateOf<PickerRequest?>(null)
        private set

    fun open(r: PickerRequest) { request = r }
    fun close() { request = null }
}

val LocalPicker = staticCompositionLocalOf<PickerController> { error("No picker host") }

/** Hosts the in-app media library over [content]. */
@Composable
fun MediaPickerHost(content: @Composable () -> Unit) {
    val controller = remember { PickerController() }
    CompositionLocalProvider(LocalPicker provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            val req = controller.request
            AnimatedVisibility(req != null, enter = slideInVertically { it / 6 } + fadeIn(), exit = slideOutVertically { it / 6 } + fadeOut()) {
                // Keep showing the last request while animating out.
                var last by remember { mutableStateOf<PickerRequest?>(null) }
                if (req != null) last = req
                last?.let { r ->
                    if (Kind.AUDIO in r.kinds) AudioPicker(r, onClose = controller::close)
                    else VisualPicker(r, onClose = controller::close)
                }
            }
        }
    }
}

private fun formatDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Loads entries for [kinds] once access is granted, and reloads when the app comes back. */
@Composable
private fun rememberLibrary(kinds: Set<Kind>): Triple<List<MediaLibrary.Entry>?, MediaLibrary.Access, () -> Unit> {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(MediaLibrary.access(ctx, kinds)) }
    var version by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = MediaLibrary.access(ctx, kinds)
        version++
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { access = MediaLibrary.access(ctx, kinds); version++ } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { if (access == MediaLibrary.Access.NONE) ask.launch(MediaLibrary.permissionsFor(kinds)) }
    val entries by produceState<List<MediaLibrary.Entry>?>(null, access, version) {
        value = if (access == MediaLibrary.Access.NONE) emptyList() else MediaLibrary.load(ctx, kinds)
    }
    return Triple(entries, access) { ask.launch(MediaLibrary.permissionsFor(kinds)) }
}

@Composable
private fun Thumb(e: MediaLibrary.Entry, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, e.id) { value = MediaLibrary.thumbnail(ctx, e) }
    Box(modifier.background(C.Surface)) {
        bmp?.let { Image(it.asImageBitmap(), e.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun NoAccess(onAllow: () -> Unit, onFiles: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, tint = C.Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(Icons.Rounded.AddPhotoAlternate, "Allow access", accent = true, onClick = onAllow)
            PillButton(Icons.Rounded.Folder, "Files", onClick = onFiles)
        }
    }
}

@Composable
private fun VisualPicker(req: PickerRequest, onClose: () -> Unit) {
    val (entries, access, ask) = rememberLibrary(req.kinds)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { onClose(); req.onResult(if (req.multi) uris else uris.take(1)) }
    }
    val mimes = buildList { if (Kind.VIDEO in req.kinds) add("video/*"); if (Kind.PHOTO in req.kinds) add("image/*") }.toTypedArray()
    val selected = remember { mutableStateListOf<MediaLibrary.Entry>() }
    var tab by remember { mutableIntStateOf(0) } // 0 all, 1 video, 2 photo
    var album by remember { mutableStateOf<String?>(null) }
    var albumMenu by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf<MediaLibrary.Entry?>(null) }
    BackHandler { if (previewing != null) previewing = null else onClose() }

    fun finish(list: List<MediaLibrary.Entry>) { onClose(); req.onResult(list.map { it.uri }) }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Box {
                Row(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { albumMenu = true }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(album ?: "Recents", style = Title.copy(fontSize = 16.sp))
                    Icon(Icons.Rounded.ExpandMore, null, tint = C.Text, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(albumMenu, { albumMenu = false }, Modifier.background(C.Surface2)) {
                    DropdownMenuItem({ Text("Recents", style = Body) }, onClick = { album = null; albumMenu = false })
                    entries.orEmpty().map { it.album }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.forEach { (a, n) ->
                        DropdownMenuItem({ Text("$a  ·  $n", style = Body) }, onClick = { album = a; albumMenu = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Folder, "Browse files") { files.launch(mimes) }
        }
        if (req.kinds.size > 1) {
            Segmented(listOf("All", "Video", "Photo"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth())
        }
        if (access == MediaLibrary.Access.PARTIAL) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface).clickable(onClick = ask).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = C.Accent, modifier = Modifier.size(20.dp))
                Text("Choose more photos and videos", style = Body, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = C.Muted)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                access == MediaLibrary.Access.NONE -> NoAccess(ask) { files.launch(mimes) }
                entries == null -> Unit
                else -> {
                    val shown = entries.filter { e ->
                        (tab == 0 || (tab == 1 && e.kind == Kind.VIDEO) || (tab == 2 && e.kind == Kind.PHOTO)) && (album == null || e.album == album)
                    }
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(if (tab == 2) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.PlayArrow, "Nothing here", tint = C.Dim, modifier = Modifier.size(44.dp))
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { "${it.kind}${it.id}" }) { e ->
                            val num = selected.indexOfFirst { it.uri == e.uri } + 1
                            Box(
                                Modifier.aspectRatio(1f).combinedClickable(
                                    role = Role.Checkbox,
                                    onClick = {
                                        if (!req.multi) finish(listOf(e))
                                        else if (num > 0) selected.removeAt(num - 1) else selected += e
                                    },
                                    onLongClick = { previewing = e },
                                ),
                            ) {
                                Thumb(e, Modifier.fillMaxSize())
                                if (e.kind == Kind.VIDEO) {
                                    Text(formatDur(e.durationMs), style = Label.copy(color = Color.White, fontWeight = FontWeight.SemiBold, shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f)), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                                }
                                if (req.multi) {
                                    if (num > 0) {
                                        Box(Modifier.fillMaxSize().border(3.dp, C.Accent).background(C.Accent.copy(alpha = 0.18f)))
                                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                                            Text("$num", style = Label.copy(color = C.Bg, fontWeight = FontWeight.Bold))
                                        }
                                    } else {
                                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (req.multi && selected.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(C.Surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(selected.toList(), key = { it.uri.toString() }) { e ->
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))) {
                            Thumb(e, Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(C.Bg.copy(alpha = 0.75f)).clickable { selected.remove(e) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Remove", tint = C.Text, modifier = Modifier.size(12.dp))
                            }
                            if (e.kind == Kind.VIDEO) Text(formatDur(e.durationMs), style = Label.copy(fontSize = 10.sp, color = Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Accent).clickable { finish(selected.toList()) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Add (${selected.size})", style = Title.copy(color = C.Bg, fontSize = 16.sp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = C.Bg)
                }
            }
        }
    }
    previewing?.let { e -> MediaPreview(e) { previewing = null } }
}

/** Full-screen look at one item before adding it. */
@Composable
private fun MediaPreview(e: MediaLibrary.Entry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        if (e.kind == Kind.VIDEO) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    android.widget.VideoView(c).apply {
                        setVideoURI(e.uri)
                        setOnPreparedListener { mp -> mp.isLooping = true; start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxWidth().aspectRatio(if (e.width > 0 && e.height > 0) e.width.toFloat() / e.height else 9f / 16f),
            )
        } else {
            val bmp by produceState<Bitmap?>(null, e.id) { value = MediaLibrary.thumbnail(ctx, e, 1080) }
            bmp?.let { Image(it.asImageBitmap(), e.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        IconBtn(Icons.Rounded.Close, "Close preview", modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp), onClick = onClose)
    }
}

@Composable
private fun AudioPicker(req: PickerRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // 0 music, 1 from video
    val (entries, access, ask) = rememberLibrary(if (tab == 0) setOf(Kind.AUDIO) else setOf(Kind.VIDEO))
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { onClose(); req.onResult(listOf(uri)) } }
    var playing by remember { mutableStateOf<Uri?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() { runCatching { player[0]?.release() }; player[0] = null; playing = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    BackHandler { stop(); onClose() }
    fun toggle(uri: Uri) {
        if (playing == uri) { stop(); return }
        stop()
        player[0] = runCatching { MediaPlayer.create(ctx, uri)?.apply { setOnCompletionListener { stop() }; start() } }.getOrNull()
        if (player[0] != null) playing = uri
    }
    fun pick(uri: Uri) { stop(); onClose(); req.onResult(listOf(uri)) }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, "Close") { stop(); onClose() }
            Spacer(Modifier.weight(1f))
            if (req.audioFromVideo) Segmented(listOf("Music", "From video"), tab, { stop(); tab = it }) else Text("Music", style = Title.copy(fontSize = 16.sp))
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Folder, "Browse files") { files.launch(arrayOf("audio/*", "video/*")) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                access == MediaLibrary.Access.NONE -> NoAccess(ask) { files.launch(arrayOf("audio/*")) }
                entries == null -> Unit
                entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, "Nothing here", tint = C.Dim, modifier = Modifier.size(44.dp)) }
                tab == 0 -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth().clickable { toggle(e.uri) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (playing == e.uri) C.Music else C.Surface2), contentAlignment = Alignment.Center) {
                                Icon(if (playing == e.uri) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing == e.uri) "Stop" else "Listen", tint = if (playing == e.uri) C.Bg else C.Text)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(e.name, style = Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.artist, formatDur(e.durationMs)).joinToString("  ·  "), style = Label.copy(color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconBtn(Icons.Rounded.Add, "Use", background = C.Accent, tint = C.Bg, size = 38.dp, iconSize = 20.dp) { pick(e.uri) }
                        }
                    }
                }
                else -> LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(entries, key = { it.id }) { e ->
                        Box(Modifier.aspectRatio(1f).clickable { pick(e.uri) }) {
                            Thumb(e, Modifier.fillMaxSize())
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp))
                            Text(formatDur(e.durationMs), style = Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}
