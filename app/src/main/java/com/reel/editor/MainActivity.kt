package com.reel.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.ui.EditorScreen
import com.reel.editor.ui.ExportScreen
import com.reel.editor.ui.HomeScreen
import com.reel.editor.ui.ReelTheme

class MainActivity : ComponentActivity() {
    private val shared = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bars use light icons on a transparent background.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleShare(intent)
        val app = application as ReelApp
        setContent {
            ReelTheme {
                // "home" | "edit:<project id or new>:<session>" | "export:<project id>:<session>"
                var screen by rememberSaveable { mutableStateOf("home") }
                var pendingUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
                    if (uris.isNotEmpty()) {
                        pendingUris = uris.map { it.toString() }
                        screen = "edit:new:${System.nanoTime()}"
                    }
                }
                val incoming by shared
                LaunchedEffect(incoming) {
                    if (incoming.isNotEmpty()) {
                        pendingUris = incoming.map { it.toString() }
                        shared.value = emptyList()
                        screen = "edit:new:${System.nanoTime()}"
                    }
                }
                AnimatedContent(screen, contentKey = { it.substringBefore(':') + it.substringAfterLast(':') }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { current ->
                    val parts = current.split(':')
                    when (parts[0]) {
                        "home" -> HomeScreen(
                            app.store, app.thumbnails,
                            onNew = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                            onOpen = { id -> screen = "edit:$id:${System.nanoTime()}" },
                        )
                        else -> {
                            val session = parts.getOrElse(2) { "0" }
                            val vm: EditorViewModel = viewModel(key = "editor-$session")
                            LaunchedEffect(session) {
                                vm.open(parts.getOrNull(1)?.takeIf { it != "new" })
                                if (pendingUris.isNotEmpty()) {
                                    vm.addMedia(pendingUris.map(Uri::parse))
                                    pendingUris = emptyList()
                                }
                            }
                            if (parts[0] == "edit") {
                                EditorScreen(vm, onClose = { screen = "home" }, onExport = { screen = "export:${vm.project.value.id}:$session" })
                            } else {
                                ExportScreen(vm, onBackToEdit = { screen = "edit:${vm.project.value.id}:$session" }, onHome = { screen = "home" })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        // Other apps can send anything; keep only real Uris and never crash on bad extras.
        val uris: List<Uri> = try {
            @Suppress("DEPRECATION")
            when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(intent.extras?.get(Intent.EXTRA_STREAM) as? Uri)
                Intent.ACTION_SEND_MULTIPLE -> (intent.extras?.get(Intent.EXTRA_STREAM) as? List<*>).orEmpty().filterIsInstance<Uri>()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (uris.isNotEmpty()) shared.value = uris
    }
}
