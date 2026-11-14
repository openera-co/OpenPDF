package com.reel.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reel.editor.update.Updater
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun mb(bytes: Long) = if (bytes < 1_000_000) "${(bytes / 1000).coerceAtLeast(1)} KB" else "%.1f MB".format(bytes / 1e6)

/** The update card on the home screen: shown only while there's something to say. */
@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateCompat()
    val s = state
    AnimatedVisibility(s !is Updater.State.Idle) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = C.Accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (s) {
                    is Updater.State.Available -> {
                        Text("Reel ${s.info.versionName}", style = Title)
                        Text(mb(s.downloadBytes), style = Body.copy(color = C.Muted))
                    }
                    is Updater.State.Downloading -> {
                        Text("Reel ${s.info.versionName}", style = Title)
                        LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = C.Accent, trackColor = C.Surface2)
                    }
                    Updater.State.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = C.Accent, trackColor = C.Surface2)
                    Updater.State.Verifying, Updater.State.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = C.Good, trackColor = C.Surface2)
                    Updater.State.UpToDate -> Text("Up to date", style = Title)
                    is Updater.State.Failed -> Text(s.message, style = Body.copy(color = C.Voice))
                    Updater.State.Idle -> Unit
                }
            }
            when (s) {
                is Updater.State.Available -> PillButton(Icons.Rounded.SystemUpdate, "Update", accent = true) { scope.launch { Updater.install(ctx) } }
                is Updater.State.Downloading -> Text("${(s.fraction * 100).roundToInt()}%", style = Mono)
                is Updater.State.Failed -> IconBtn(Icons.Rounded.Refresh, "Try again", background = C.Surface2, size = 36.dp) { scope.launch { Updater.check(ctx) } }
                else -> Unit
            }
            if (s is Updater.State.Available || s is Updater.State.Failed || s is Updater.State.UpToDate) {
                IconBtn(Icons.Rounded.Close, "Hide", size = 32.dp) { Updater.dismiss() }
            }
        }
    }
}

/** Check now, or set where updates come from. */
@Composable
fun UpdateSourceDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Updater.url(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = C.Accent) },
        text = {
            OutlinedTextField(url, { url = it }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Link, null) }, placeholder = { Text("https://…/update.json") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton({ Updater.setUrl(ctx, url); onDismiss(); scope.launch { Updater.check(ctx) } }) { Text("Check") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
