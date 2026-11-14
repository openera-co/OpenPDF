package com.reel.editor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.model.Clip
import com.reel.editor.model.CutoutMode
import kotlin.math.roundToInt

private val CUT_COLORS = listOf(0xFF14141C, 0xFFFFFFFF, 0xFF2BD46A, 0xFF1F6FFF, 0xFFFF4D6D, 0xFFFFD36B, 0xFF8B5CF6, 0xFF0EA5E9)

/** Background cutout: keeps people, replaces the rest with a color, a soft blur or a picture. */
@Composable
fun CutoutPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    val cut = clip.cutout
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { vm.getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateClip(clip.id) { it.copy(cutout = it.cutout.copy(enabled = true, mode = CutoutMode.IMAGE, imageUri = uri.toString())) }
        }
    }
    fun set(change: (com.reel.editor.model.Cutout) -> com.reel.editor.model.Cutout) = vm.updateClip(clip.id) { it.copy(cutout = change(it.cutout)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetHeader("Cutout", Icons.Rounded.ContentCut, iconTint = C.Overlay, onClose = onClose, onDone = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("Blur", "Color", "Image"), when (cut.mode) { CutoutMode.BLUR -> 0; CutoutMode.COLOR -> 1; CutoutMode.IMAGE -> 2 }, { i ->
                when (i) {
                    0 -> set { it.copy(enabled = true, mode = CutoutMode.BLUR) }
                    1 -> set { it.copy(enabled = true, mode = CutoutMode.COLOR) }
                    else -> if (cut.imageUri == null) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else set { it.copy(enabled = true, mode = CutoutMode.IMAGE) }
                }
            })
            Spacer(Modifier.weight(1f))
            Switch(cut.enabled, { e -> set { it.copy(enabled = e) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
        }
        when (cut.mode) {
            CutoutMode.BLUR -> ValueSlider(Icons.Rounded.BlurOn, "Blur", cut.blur * 100, 0f..100f, { "${it.roundToInt()}" }, C.Overlay, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
                vm.updateClip(clip.id, true) { it.copy(cutout = it.cutout.copy(blur = v / 100f, enabled = true)) }
            }
            CutoutMode.COLOR -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Palette, null, tint = C.Muted) }
                CUT_COLORS.forEach { col ->
                    val sel = cut.color == col
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(col))
                            .border(if (sel) 3.dp else 1.dp, if (sel) C.Accent else C.Line, RoundedCornerShape(12.dp))
                            .clickable { set { it.copy(enabled = true, mode = CutoutMode.COLOR, color = col) } },
                    )
                }
            }
            CutoutMode.IMAGE -> Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (cut.imageUri == null) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.Image, if (cut.imageUri == null) "Pick picture" else "Change picture") {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        ValueSlider(Icons.Rounded.Gradient, "Edge", cut.edge * 100, 0f..100f, { "${it.roundToInt()}" }, C.Overlay, default = 35f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.updateClip(clip.id, true) { it.copy(cutout = it.cutout.copy(edge = v / 100f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
