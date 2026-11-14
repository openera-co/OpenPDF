package com.reel.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.edit.Selection
import com.reel.editor.model.VoiceEffects
import com.reel.editor.model.VoiceFx
import kotlin.math.roundToInt

private fun voiceIcon(id: String): ImageVector = when (id) {
    "chipmunk" -> Icons.Rounded.Pets
    "helium" -> Icons.Rounded.KeyboardDoubleArrowUp
    "deep" -> Icons.Rounded.KeyboardDoubleArrowDown
    "monster" -> Icons.Rounded.SentimentVeryDissatisfied
    "robot" -> Icons.Rounded.SmartToy
    "alien" -> Icons.Rounded.RocketLaunch
    "phone" -> Icons.Rounded.Call
    "megaphone" -> Icons.Rounded.Campaign
    "radio" -> Icons.Rounded.Radio
    "echo" -> Icons.Rounded.SurroundSound
    "cave" -> Icons.Rounded.Landscape
    "hall" -> Icons.Rounded.Church
    "room" -> Icons.Rounded.MeetingRoom
    "underwater" -> Icons.Rounded.Water
    "lofi" -> Icons.Rounded.Album
    "vibrato" -> Icons.Rounded.Waves
    else -> Icons.Rounded.Air
}

/** Vocal enhancer, noise reduction, steady level and voice effects for a clip or audio item. */
@Composable
fun VoicePanel(vm: EditorViewModel, target: Selection, voice: VoiceFx, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetHeader("Voice", Icons.Rounded.RecordVoiceOver, iconTint = C.Voice, onClose = onClose, onDone = onClose)
        ValueSlider(Icons.Rounded.AutoFixHigh, "Enhance", voice.enhance * 100, 0f..100f, { "${it.roundToInt()}" }, C.Voice, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.setVoice(target, true) { it.copy(enhance = v / 100f) }
        }
        ValueSlider(Icons.Rounded.NoiseAware, "Noise reduction", voice.denoise * 100, 0f..100f, { "${it.roundToInt()}" }, C.Voice, default = 0f, onStart = vm::beginGesture, onEnd = vm::endGesture) { v ->
            vm.setVoice(target, true) { it.copy(denoise = v / 100f) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (voice.normalize) C.Voice else C.Surface2)
                    .clickable(role = Role.Switch) { vm.setVoice(target) { it.copy(normalize = !it.normalize) } }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Compress, null, tint = if (voice.normalize) C.Bg else C.Text, modifier = Modifier.size(18.dp))
                Text("Even level", style = Label.copy(color = if (voice.normalize) C.Bg else C.Text), modifier = Modifier.padding(start = 8.dp))
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.weight(1f, fill = false).heightIn(min = 116.dp, max = 200.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { FxTile("None", voice.effect == null, Icons.Rounded.Block) { vm.setVoice(target) { it.copy(effect = null) } } }
            items(VoiceEffects.all, key = { it.id }) { e ->
                FxTile(e.name, voice.effect == e.id, voiceIcon(e.id)) {
                    vm.setVoice(target) { it.copy(effect = e.id) }
                    // Hear it straight away.
                    vm.preview.play()
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp))
    }
}
