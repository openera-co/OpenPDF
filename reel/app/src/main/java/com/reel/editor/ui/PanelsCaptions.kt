package com.reel.editor.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reel.editor.captions.CaptionEngine
import com.reel.editor.edit.EditorViewModel

/** Caption text size; looks come from the text presets. */
private const val CAPTION_SIZE = 52f

@Composable
fun CaptionsPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val project by vm.project.collectAsStateCompat()
    val status by vm.captionStatus.collectAsStateCompat()
    var lang by rememberSaveable { mutableStateOf("auto") }
    var translate by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableIntStateOf(1) }
    val count = project.texts.count { it.isCaption }
    val style = TEXT_PRESETS[preset].second.copy(sizeSp = CAPTION_SIZE)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Captions", Icons.Rounded.ClosedCaption, iconTint = C.TextTrack, onClose = onClose, onDone = onClose)
        val st = status
        if (st != null) {
            val fraction by animateFloatAsState(st.fraction, label = "captions")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = C.TextTrack, modifier = Modifier.size(20.dp))
                    Text(
                        when (st.stage) {
                            CaptionEngine.Stage.LISTENING -> "Listening"
                            CaptionEngine.Stage.WRITING -> "Writing captions"
                        },
                        style = Title.copy(fontSize = 15.sp), modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    Text("${(st.fraction * 100).toInt()}%", style = Body.copy(color = C.Muted))
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = C.TextTrack, trackColor = C.Line)
                Row { PillButton(Icons.Rounded.Delete, "Cancel") { vm.cancelCaptions() } }
            }
            Spacer(Modifier.height(10.dp))
            return@Column
        }

        var tab by rememberSaveable { mutableIntStateOf(0) }
        if (count > 0) Segmented(listOf("Make", "Lines"), tab, { tab = it }, Modifier.padding(horizontal = 12.dp))
        if (count > 0 && tab == 1) {
            CaptionLines(vm, project)
            Spacer(Modifier.height(8.dp))
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CaptionEngine.LANGUAGES.forEach { (code, name) -> Chip(name, code == lang) { lang = code } }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lang != "en") {
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (translate) C.Accent else C.Surface2)
                        .clickable(role = Role.Switch) { translate = !translate }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Translate, "Translate to English", tint = if (translate) C.Bg else C.Text, modifier = Modifier.size(18.dp))
                    Text("EN", style = Label.copy(color = if (translate) C.Bg else C.Text, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEXT_PRESETS.forEachIndexed { i, (name, s) ->
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(C.Bg)
                        .then(if (i == preset) Modifier.border(2.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { preset = i; if (count > 0) vm.styleCaptions(TEXT_PRESETS[i].second.copy(sizeSp = CAPTION_SIZE)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, style = Body.copy(fontSize = 18.sp, color = Color(s.color), fontWeight = FontWeight.Black, background = s.labelColor?.let { Color(it) } ?: Color.Transparent))
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(Icons.Rounded.AutoAwesome, if (count > 0) "Redo captions" else "Auto captions", accent = true) {
                vm.generateCaptions(lang, translate && lang != "en", style)
            }
            if (count > 0) {
                PillButton(Icons.Rounded.Delete, "$count") { vm.deleteCaptions() }
            }
        }
        Spacer(Modifier.width(1.dp).height(8.dp))
    }
}


/** Every caption line with its time: jump to it, merge, split, nudge or delete. */
@Composable
private fun CaptionLines(vm: EditorViewModel, project: com.reel.editor.model.Project) {
    val caps = project.texts.filter { it.isCaption }.sortedBy { it.startUs }
    val pos by vm.preview.positionUs.collectAsStateCompat()
    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
        items(caps.size, key = { caps[it].id }) { i ->
            val c = caps[i]
            val on = pos >= c.startUs && pos < c.startUs + c.durationUs
            Column(Modifier.fillMaxWidth().background(if (on) C.Surface2 else Color.Transparent).clickable { vm.preview.seekTo(c.startUs) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%d:%04.1f".format(c.startUs / 60_000_000, (c.startUs % 60_000_000) / 1e6), style = Mono.copy(color = C.TextTrack, fontSize = 12.sp), modifier = Modifier.width(56.dp))
                    Text(c.text, style = Body, maxLines = 2, modifier = Modifier.weight(1f))
                }
                if (on) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconBtn(Icons.Rounded.FastRewind, "Earlier", size = 36.dp, iconSize = 18.dp) { vm.nudgeCaption(c.id, -100_000) }
                    IconBtn(Icons.Rounded.FastForward, "Later", size = 36.dp, iconSize = 18.dp) { vm.nudgeCaption(c.id, 100_000) }
                    IconBtn(Icons.Rounded.Edit, "Edit text", size = 36.dp, iconSize = 18.dp) { vm.select(com.reel.editor.edit.Selection.TextSel(c.id)); vm.openPanel(com.reel.editor.edit.Panel.TEXT_EDIT) }
                    IconBtn(Icons.Rounded.CallSplit, "Split", size = 36.dp, iconSize = 18.dp) { vm.splitCaption(c.id) }
                    if (i < caps.lastIndex) IconBtn(Icons.Rounded.CallMerge, "Merge with next", size = 36.dp, iconSize = 18.dp) { vm.mergeCaption(c.id) }
                    IconBtn(Icons.Rounded.Delete, "Delete", size = 36.dp, iconSize = 18.dp) { vm.deleteCaption(c.id) }
                }
            }
        }
    }
}
