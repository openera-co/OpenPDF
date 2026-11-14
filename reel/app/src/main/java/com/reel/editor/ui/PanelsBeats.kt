package com.reel.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.model.AudioItem
import com.reel.editor.model.Beats

private val DENSITY = listOf(1, 2, 4)

/** Beat markers on a music item: detected beats (with density) and custom markers. */
@Composable
fun BeatsPanel(vm: EditorViewModel, item: AudioItem, onClose: () -> Unit) {
    val waveforms by vm.waveforms.collectAsStateCompat()
    val analyzed = waveforms.containsKey(item.uri)
    val detected = item.beatsUs.isNotEmpty()
    val count = Beats.markers(item).size

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeader("Beats", Icons.Rounded.GraphicEq, iconTint = C.Key, onClose = onClose, onDone = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Auto", style = Title.copy(color = if (detected) C.Text else C.Dim))
            if (!analyzed) CircularProgressIndicator(color = C.Key, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            if (detected) {
                Segmented(listOf("Beat", "Half", "Bar"), DENSITY.indexOf(item.beatEvery).coerceAtLeast(0), { vm.setBeatEvery(item.id, DENSITY[it]) })
            }
            Switch(item.autoBeats && detected, { vm.setAutoBeats(item.id, it) }, enabled = detected, colors = SwitchDefaults.colors(checkedTrackColor = C.Key))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(Icons.Rounded.AddLocationAlt, "Add") { vm.addBeatMarker(item.id) }
            PillButton(Icons.Rounded.WrongLocation, "Remove") { vm.removeBeatMarker(item.id) }
            if (count > 0) PillButton(Icons.Rounded.DeleteSweep, "$count") { vm.clearBeatMarkers(item.id) }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            PillButton(Icons.Rounded.ContentCut, "Sync cuts", accent = count > 0) { vm.syncCutsToBeats() }
        }
        Spacer(Modifier.height(8.dp))
    }
}
