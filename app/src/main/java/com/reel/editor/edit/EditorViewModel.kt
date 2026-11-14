package com.reel.editor.edit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reel.editor.ReelApp
import com.reel.editor.engine.PreviewController
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.AudioItem
import com.reel.editor.model.AudioKind
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Keyframe
import com.reel.editor.model.MIN_CLIP_US
import com.reel.editor.model.Project
import com.reel.editor.model.StickerLayer
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TimedFilter
import com.reel.editor.model.TimedFx
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.Transform
import com.reel.editor.model.interpolate
import com.reel.editor.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Selection {
    data object None : Selection
    data class ClipSel(val id: String) : Selection
    data class TextSel(val id: String) : Selection
    data class StickerSel(val id: String) : Selection
    data class AudioSel(val id: String) : Selection
    data class EffectSel(val id: String) : Selection
    data class FilterSel(val id: String) : Selection
    data class ShapeSel(val id: String) : Selection
    data class AdjustSel(val id: String) : Selection
    data class OverlaySel(val id: String) : Selection
}

enum class Panel {
    SPEED, VOLUME, ANIMATION, FILTERS, ADJUST, EFFECTS, TRANSITION, TEXT_EDIT, TEXT_STYLE, TEXT_ANIM,
    STICKERS, AUDIO, CANVAS, KEYFRAMES, CHROMA, CROP, EXPORT, CAPTIONS, CUTOUT, BEATS, VOICE, SHAPE, ADJUST_LAYER, OVERLAY, MASK, ADD_LAYER
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val reel = app as ReelApp
    val store = reel.store
    val thumbnails = reel.thumbnails
    val audioAnalyzer = reel.audio
    val preview = PreviewController(app)
    private val history = History()

    private val _project = MutableStateFlow(Project())
    val project: StateFlow<Project> = _project
    private val _selection = MutableStateFlow<Selection>(Selection.None)
    val selection: StateFlow<Selection> = _selection
    private val _panel = MutableStateFlow<Panel?>(null)
    val panel: StateFlow<Panel?> = _panel
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    /** Waveform peaks keyed by URI, filled in the background. */
    private val _waveforms = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val waveforms: StateFlow<Map<String, FloatArray>> = _waveforms

    private var saveJob: Job? = null
    private var loaded = false

    fun open(projectId: String?) {
        if (loaded && (projectId == null || projectId == _project.value.id)) return
        loaded = true
        val p = projectId?.let { store.load(it) } ?: Project(name = "Project ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())}")
        _project.value = p
        preview.update(p)
        preview.seekTo(p.playheadUs)
        analyzeAudio(p)
    }

    // ------------------------------------------------------------------ edits

    /** Applies an edit as one undo step. */
    fun edit(change: (Project) -> Project) {
        val before = _project.value
        val after = change(before)
        if (after == before) return
        history.record(before)
        apply(after)
    }

    /** For continuous drags: call [beginGesture], then [live] repeatedly, then [endGesture]. */
    fun beginGesture() {
        history.beginGesture(_project.value)
        preview.setScrubbing(false)
    }

    fun live(change: (Project) -> Project) {
        val after = change(_project.value)
        if (after != _project.value) apply(after)
    }

    fun endGesture() {
        history.endGesture(_project.value)
        refreshHistoryFlags()
    }

    private fun apply(p: Project) {
        val fixed = com.reel.editor.model.TrackLayout.clampLayers(com.reel.editor.model.CaptionAnchor.reflow(p)).copy(updatedAt = System.currentTimeMillis())
        _project.value = fixed
        preview.update(fixed)
        refreshHistoryFlags()
        scheduleSave()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    fun undo() {
        history.undo(_project.value)?.let { _project.value = it; preview.update(it); scheduleSave() }
        refreshHistoryFlags()
        validateSelection()
    }

    fun redo() {
        history.redo(_project.value)?.let { _project.value = it; preview.update(it); scheduleSave() }
        refreshHistoryFlags()
        validateSelection()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            store.save(_project.value.copy(playheadUs = preview.positionUs.value))
        }
    }

    fun saveNow() {
        val p = _project.value.copy(playheadUs = preview.positionUs.value)
        if (p.clips.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { store.save(p) }
    }

    fun toast(msg: String) {
        _message.value = msg
        viewModelScope.launch { delay(2400); if (_message.value == msg) _message.value = null }
    }

    // ------------------------------------------------------------------ selection and panels

    fun select(s: Selection) {
        _selection.value = s
        if (s == Selection.None) _panel.value = null
    }

    fun openPanel(p: Panel?) {
        _panel.value = p
        if (p != null) preview.pause()
    }

    private fun validateSelection() {
        val p = _project.value
        val ok = when (val s = _selection.value) {
            is Selection.ClipSel -> p.clips.any { it.id == s.id }
            is Selection.TextSel -> p.texts.any { it.id == s.id }
            is Selection.StickerSel -> p.stickers.any { it.id == s.id }
            is Selection.AudioSel -> p.audio.any { it.id == s.id }
            is Selection.EffectSel -> p.effects.any { it.id == s.id }
            is Selection.FilterSel -> p.filters.any { it.id == s.id }
            is Selection.ShapeSel -> p.shapes.any { it.id == s.id }
            is Selection.AdjustSel -> p.adjustments.any { it.id == s.id }
            is Selection.OverlaySel -> p.overlays.any { it.id == s.id }
            Selection.None -> true
        }
        if (!ok) select(Selection.None)
    }

    val selectedClip: Clip? get() = (_selection.value as? Selection.ClipSel)?.let { s -> _project.value.clips.firstOrNull { it.id == s.id } }

    /** The clip under the playhead, used by tools when nothing is selected. */
    fun clipAtPlayhead(): Clip? = TimelineMath.clipAt(TimelineMath.place(_project.value.clips), preview.positionUs.value)?.clip

    fun targetClip(): Clip? = selectedClip ?: clipAtPlayhead()

    fun updateClip(id: String, gesture: Boolean = false, change: (Clip) -> Clip) {
        val f: (Project) -> Project = { p -> p.copy(clips = p.clips.map { if (it.id == id) change(it) else it }) }
        if (gesture) live(f) else edit(f)
    }

    // ------------------------------------------------------------------ import

    fun addMedia(uris: List<Uri>, atEnd: Boolean = true) {
        if (uris.isEmpty()) return
        _busy.value = true
        viewModelScope.launch {
            val clips = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    MediaProbe.probeClip(getApplication(), u)
                }
            }
            _busy.value = false
            if (clips.isEmpty()) { toast("Couldn't open those files"); return@launch }
            if (clips.size < uris.size) toast("${uris.size - clips.size} file(s) couldn't be opened")
            edit { p ->
                val list = p.clips.toMutableList()
                val insertAt = if (atEnd || list.isEmpty()) list.size else {
                    val pl = TimelineMath.place(list)
                    val c = TimelineMath.clipAt(pl, preview.positionUs.value)
                    if (c == null) list.size else c.index + 1
                }
                list.addAll(insertAt, clips)
                p.copy(clips = list)
            }
            clips.filter { it.kind == ClipKind.VIDEO && it.hasAudio }.forEach { analyzeUri(it.uri) }
        }
    }

    fun addAudio(uri: Uri, kind: AudioKind, title: String? = null) {
        viewModelScope.launch {
            runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val item = withContext(Dispatchers.IO) { MediaProbe.probeAudio(getApplication(), uri, title, kind, preview.positionUs.value) }
            if (item == null) { toast("Couldn't open that audio"); return@launch }
            edit { it.copy(audio = it.audio + item) }
            select(Selection.AudioSel(item.id))
            analyzeUri(item.uri, item.id)
        }
    }

    private fun analyzeAudio(p: Project) {
        p.clips.filter { it.kind == ClipKind.VIDEO && it.hasAudio }.forEach { analyzeUri(it.uri) }
        p.audio.forEach { analyzeUri(it.uri, it.id) }
    }

    private fun analyzeUri(uri: String, audioItemId: String? = null) {
        if (_waveforms.value.containsKey(uri) && audioItemId == null) return
        viewModelScope.launch(Dispatchers.Default) {
            val a = audioAnalyzer.analyze(uri) ?: return@launch
            _waveforms.value = _waveforms.value + (uri to a.peaks)
            // Auto-duck depends on these levels: refresh the preview once they are known.
            if (_project.value.audio.any { it.duck }) withContext(Dispatchers.Main) { preview.update(_project.value) }
            if (audioItemId != null && a.beatsUs.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    // Beats are analysis results, not an edit: no undo step.
                    val p = _project.value
                    _project.value = p.copy(audio = p.audio.map { if (it.id == audioItemId && it.beatsUs.isEmpty()) it.copy(beatsUs = a.beatsUs) else it })
                    preview.update(_project.value)
                }
            }
        }
    }

    // ------------------------------------------------------------------ clip operations

    fun split() {
        val t = preview.positionUs.value
        val p = _project.value
        val target = when (val s = _selection.value) {
            is Selection.TextSel -> return splitText(s.id, t)
            is Selection.AudioSel -> return splitAudio(s.id, t)
            else -> null
        }
        val result = TimelineMath.split(p, t)
        if (result == null) { toast("Move the playhead inside a clip"); return }
        edit { result }
        target ?: Unit
    }

    private fun splitText(id: String, t: Long) = edit { p ->
        val l = p.texts.firstOrNull { it.id == id } ?: return@edit p
        val off = t - l.startUs
        if (off < MIN_CLIP_US || l.durationUs - off < MIN_CLIP_US) return@edit p
        p.copy(texts = p.texts.flatMap { if (it.id == id) listOf(it.copy(durationUs = off), it.copy(id = newId(), startUs = t, durationUs = it.durationUs - off)) else listOf(it) })
    }

    private fun splitAudio(id: String, t: Long) = edit { p ->
        val a = p.audio.firstOrNull { it.id == id } ?: return@edit p
        val off = t - a.startUs
        if (off < MIN_CLIP_US || a.durationUs - off < MIN_CLIP_US) return@edit p
        p.copy(audio = p.audio.flatMap { if (it.id == id) listOf(it.copy(trimEndUs = it.trimStartUs + off, fadeOutUs = 0), it.copy(id = newId(), startUs = t, trimStartUs = it.trimStartUs + off, fadeInUs = 0)) else listOf(it) })
    }

    fun deleteSelected() {
        when (val s = _selection.value) {
            is Selection.ClipSel -> edit { p ->
                val clips = p.clips.filter { it.id != s.id }.toMutableList()
                if (clips.isNotEmpty() && clips[0].transitionIn != null) clips[0] = clips[0].copy(transitionIn = null)
                p.copy(clips = clips)
            }
            is Selection.TextSel -> edit { p -> p.copy(texts = p.texts.filter { it.id != s.id }) }
            is Selection.StickerSel -> edit { p -> p.copy(stickers = p.stickers.filter { it.id != s.id }) }
            is Selection.AudioSel -> edit { p -> p.copy(audio = p.audio.filter { it.id != s.id }) }
            is Selection.EffectSel -> edit { p -> p.copy(effects = p.effects.filter { it.id != s.id }) }
            is Selection.FilterSel -> edit { p -> p.copy(filters = p.filters.filter { it.id != s.id }) }
            is Selection.ShapeSel -> edit { p -> p.copy(shapes = p.shapes.filter { it.id != s.id }) }
            is Selection.AdjustSel -> edit { p -> p.copy(adjustments = p.adjustments.filter { it.id != s.id }) }
            is Selection.OverlaySel -> edit { p -> p.copy(overlays = p.overlays.filter { it.id != s.id }) }
            Selection.None -> return
        }
        select(Selection.None)
    }

    fun duplicateSelected() {
        when (val s = _selection.value) {
            is Selection.ClipSel -> edit { p ->
                val i = p.clips.indexOfFirst { it.id == s.id }
                if (i < 0) p else p.copy(clips = p.clips.toMutableList().apply { add(i + 1, this[i].copy(id = newId(), transitionIn = null)) })
            }
            is Selection.TextSel -> edit { p ->
                val l = p.texts.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(texts = p.texts + l.copy(id = newId(), startUs = l.startUs + l.durationUs))
            }
            is Selection.StickerSel -> edit { p ->
                val l = p.stickers.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(stickers = p.stickers + l.copy(id = newId(), transform = l.transform.copy(x = l.transform.x + 0.05f, y = l.transform.y + 0.05f)))
            }
            is Selection.EffectSel -> edit { p ->
                val e = p.effects.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(effects = p.effects + e.copy(id = newId(), startUs = e.startUs + e.durationUs))
            }
            is Selection.ShapeSel -> edit { p ->
                val l = p.shapes.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(shapes = p.shapes + l.copy(id = newId(), transform = l.transform.copy(x = l.transform.x + 0.05f, y = l.transform.y + 0.05f)))
            }
            is Selection.AdjustSel -> edit { p ->
                val l = p.adjustments.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(adjustments = p.adjustments + l.copy(id = newId(), startUs = l.startUs + l.durationUs))
            }
            is Selection.OverlaySel -> edit { p ->
                val l = p.overlays.firstOrNull { it.id == s.id } ?: return@edit p
                p.copy(overlays = p.overlays + l.copy(id = newId(), transform = l.transform.copy(x = l.transform.x + 0.05f, y = l.transform.y + 0.05f)))
            }
            else -> Unit
        }
    }

    fun replaceClip(uri: Uri) {
        val c = selectedClip ?: return
        viewModelScope.launch {
            val probed = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                MediaProbe.probeClip(getApplication(), uri)
            } ?: run { toast("Couldn't open that file"); return@launch }
            val keep = c.trimmedDurationUs
            updateClip(c.id) {
                it.copy(
                    uri = probed.uri, kind = probed.kind, sourceDurationUs = maxOf(probed.sourceDurationUs, if (probed.kind == ClipKind.PHOTO) keep else 0),
                    sourceWidth = probed.sourceWidth, sourceHeight = probed.sourceHeight, rotationDegrees = probed.rotationDegrees,
                    hasAudio = probed.hasAudio, trimStartUs = 0, trimEndUs = minOf(keep, if (probed.kind == ClipKind.PHOTO) keep else probed.sourceDurationUs),
                )
            }
        }
    }

    /** Freeze frame: grabs the frame under the playhead into a 2 s photo inserted after a split. */
    fun freezeFrame() {
        val p = _project.value
        val placed = TimelineMath.place(p.clips)
        val t = preview.positionUs.value
        val at = TimelineMath.clipAt(placed, t) ?: return
        if (at.clip.kind != ClipKind.VIDEO) { toast("Freeze works on video clips"); return }
        viewModelScope.launch {
            val local = t - at.startUs
            val media = at.clip.trimStartUs + com.reel.editor.model.SpeedMath.timelineToMedia(at.clip.speed, at.clip.trimmedDurationUs, local)
            val file = withContext(Dispatchers.IO) { FrameGrab.toFile(getApplication(), at.clip.uri, media) } ?: run { toast("Couldn't grab that frame"); return@launch }
            val still = at.clip.copy(
                id = newId(), uri = Uri.fromFile(file).toString(), kind = ClipKind.PHOTO, sourceDurationUs = 2_000_000,
                trimStartUs = 0, trimEndUs = 2_000_000, speed = com.reel.editor.model.Speed(), hasAudio = false,
                transitionIn = null, animIn = null, animOut = null, keyframes = emptyList(), rotationDegrees = 0,
                sourceWidth = if (at.clip.rotationDegrees % 180 != 0) at.clip.sourceHeight else at.clip.sourceWidth,
                sourceHeight = if (at.clip.rotationDegrees % 180 != 0) at.clip.sourceWidth else at.clip.sourceHeight,
            )
            edit { proj ->
                val splitP = TimelineMath.split(proj, t) ?: proj
                val placedNow = TimelineMath.place(splitP.clips)
                val idx = (TimelineMath.clipAt(placedNow, t - 1)?.index ?: (placedNow.size - 1)) + 1
                splitP.copy(clips = splitP.clips.toMutableList().apply { add(idx.coerceIn(0, size), still) })
            }
        }
    }

    // ------------------------------------------------------------------ layers

    fun addText(text: String = "Your text") {
        val t = preview.positionUs.value
        val total = TimelineMath.duration(_project.value)
        val l = TextLayer(text = text, startUs = t.coerceAtMost((total - 500_000).coerceAtLeast(0)), durationUs = 3_000_000L.coerceAtMost((total - t).coerceAtLeast(1_000_000)))
        edit { it.copy(texts = it.texts + l) }
        select(Selection.TextSel(l.id))
        openPanel(Panel.TEXT_EDIT)
    }

    fun addSticker(source: String) {
        val t = preview.positionUs.value
        val total = TimelineMath.duration(_project.value)
        val s = StickerLayer(source = source, startUs = t, durationUs = 3_000_000L.coerceAtMost((total - t).coerceAtLeast(1_000_000)))
        edit { it.copy(stickers = it.stickers + s) }
        select(Selection.StickerSel(s.id))
    }

    fun addTimedEffect(fxId: String) {
        val t = preview.positionUs.value
        val total = TimelineMath.duration(_project.value)
        val e = TimedFx(fx = com.reel.editor.model.FxRef(fxId), startUs = t.coerceAtMost((total - 300_000).coerceAtLeast(0)), durationUs = 2_000_000L.coerceAtMost((total - t).coerceAtLeast(500_000)))
        edit { it.copy(effects = it.effects + e) }
        select(Selection.EffectSel(e.id))
    }

    fun addTimedFilter(lookId: String?, shaderId: String?) {
        val t = preview.positionUs.value
        val total = TimelineMath.duration(_project.value)
        val f = TimedFilter(lookId = lookId, shaderFx = shaderId?.let { com.reel.editor.model.FxRef(it) }, startUs = t, durationUs = 3_000_000L.coerceAtMost((total - t).coerceAtLeast(500_000)))
        edit { it.copy(filters = it.filters + f) }
        select(Selection.FilterSel(f.id))
    }

    // ------------------------------------------------------------------ keyframes

    /** Adds or removes a keyframe on the selected clip/text/sticker at the playhead. */
    fun toggleKeyframe() {
        val t = preview.positionUs.value
        val p = _project.value
        when (val s = _selection.value) {
            is Selection.ClipSel -> {
                val placed = TimelineMath.place(p.clips).firstOrNull { it.clip.id == s.id } ?: return
                val local = (t - placed.startUs).coerceIn(0, placed.durationUs)
                val c = placed.clip
                val existing = c.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
                updateClip(c.id) {
                    if (existing != null) it.copy(keyframes = it.keyframes - existing)
                    else it.copy(keyframes = (it.keyframes + Keyframe(local, interpolate(it.transform, it.keyframes, local))).sortedBy { k -> k.timeUs })
                }
            }
            is Selection.TextSel -> edit { pp ->
                pp.copy(texts = pp.texts.map { l ->
                    if (l.id != s.id) l else {
                        val local = (t - l.startUs).coerceIn(0, l.durationUs)
                        val ex = l.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
                        if (ex != null) l.copy(keyframes = l.keyframes - ex)
                        else l.copy(keyframes = (l.keyframes + Keyframe(local, interpolate(l.transform, l.keyframes, local))).sortedBy { it.timeUs })
                    }
                })
            }
            is Selection.StickerSel -> edit { pp ->
                pp.copy(stickers = pp.stickers.map { l ->
                    if (l.id != s.id) l else {
                        val local = (t - l.startUs).coerceIn(0, l.durationUs)
                        val ex = l.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
                        if (ex != null) l.copy(keyframes = l.keyframes - ex)
                        else l.copy(keyframes = (l.keyframes + Keyframe(local, interpolate(l.transform, l.keyframes, local))).sortedBy { it.timeUs })
                    }
                })
            }
            is Selection.ShapeSel -> edit { pp ->
                pp.copy(shapes = pp.shapes.map { l ->
                    if (l.id != s.id) l else {
                        val local = (t - l.startUs).coerceIn(0, l.durationUs)
                        val ex = l.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
                        if (ex != null) l.copy(keyframes = l.keyframes - ex)
                        else l.copy(keyframes = (l.keyframes + Keyframe(local, interpolate(l.transform, l.keyframes, local))).sortedBy { it.timeUs })
                    }
                })
            }
            is Selection.OverlaySel -> edit { pp ->
                pp.copy(overlays = pp.overlays.map { l ->
                    if (l.id != s.id) l else {
                        val local = (t - l.startUs).coerceIn(0, l.timelineDurationUs)
                        val ex = l.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
                        if (ex != null) l.copy(keyframes = l.keyframes - ex)
                        else l.copy(keyframes = (l.keyframes + Keyframe(local, interpolate(l.transform, l.keyframes, local))).sortedBy { it.timeUs })
                    }
                })
            }
            else -> toast("Select a layer first")
        }
    }

    /**
     * Sets the transform of the selected item at the playhead. If it has keyframes, the change
     * lands on (or creates) the keyframe under the playhead, like desktop editors do.
     */
    fun setTransformAtPlayhead(gesture: Boolean, change: (Transform) -> Transform) {
        val t = preview.positionUs.value
        val p = _project.value
        fun apply(base: Transform, keys: List<Keyframe>, local: Long): Pair<Transform, List<Keyframe>> {
            if (keys.isEmpty()) return change(base) to keys
            val ex = keys.firstOrNull { kotlin.math.abs(it.timeUs - local) < 40_000 }
            val cur = interpolate(base, keys, local)
            val nk = if (ex != null) keys.map { if (it === ex) it.copy(transform = change(it.transform)) else it }
            else (keys + Keyframe(local, change(cur))).sortedBy { it.timeUs }
            return base to nk
        }
        val f: (Project) -> Project = when (val s = _selection.value) {
            is Selection.ClipSel -> { pp ->
                val start = TimelineMath.place(pp.clips).firstOrNull { it.clip.id == s.id }?.startUs ?: 0
                pp.copy(clips = pp.clips.map { if (it.id == s.id) apply(it.transform, it.keyframes, t - start).let { (b, k) -> it.copy(transform = b, keyframes = k) } else it })
            }
            is Selection.TextSel -> { pp -> pp.copy(texts = pp.texts.map { if (it.id == s.id) apply(it.transform, it.keyframes, t - it.startUs).let { (b, k) -> it.copy(transform = b, keyframes = k) } else it }) }
            is Selection.StickerSel -> { pp -> pp.copy(stickers = pp.stickers.map { if (it.id == s.id) apply(it.transform, it.keyframes, t - it.startUs).let { (b, k) -> it.copy(transform = b, keyframes = k) } else it }) }
            is Selection.ShapeSel -> { pp -> pp.copy(shapes = pp.shapes.map { if (it.id == s.id) apply(it.transform, it.keyframes, t - it.startUs).let { (b, k) -> it.copy(transform = b, keyframes = k) } else it }) }
            is Selection.OverlaySel -> { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == s.id) apply(it.transform, it.keyframes, t - it.startUs).let { (b, k) -> it.copy(transform = b, keyframes = k) } else it }) }
            else -> return
        }
        if (gesture) live(f) else edit(f)
        p.hashCode()
    }

    // ------------------------------------------------------------------ shape, adjustment and overlay layers

    private fun layerSpan(defaultUs: Long = 3_000_000): Pair<Long, Long> {
        val total = TimelineMath.duration(_project.value)
        val start = preview.positionUs.value.coerceIn(0, (total - 200_000).coerceAtLeast(0))
        return start to minOf(defaultUs, (total - start).coerceAtLeast(200_000))
    }

    fun addShape(kind: String) {
        if (_project.value.clips.isEmpty()) { toast("Add a clip first"); return }
        val (s, d) = layerSpan()
        val l = com.reel.editor.model.ShapeLayer(shape = kind, startUs = s, durationUs = d, aspect = if (kind == "line" || kind == "arrow") 3f else 1f)
        edit { p -> p.copy(shapes = p.shapes + l) }
        select(Selection.ShapeSel(l.id))
    }

    fun addAdjustmentLayer() {
        if (_project.value.clips.isEmpty()) { toast("Add a clip first"); return }
        val (s, d) = layerSpan()
        val l = com.reel.editor.model.AdjustmentLayer(startUs = s, durationUs = d)
        edit { p -> p.copy(adjustments = p.adjustments + l) }
        select(Selection.AdjustSel(l.id))
        openPanel(Panel.ADJUST_LAYER)
    }

    fun addOverlays(uris: List<Uri>) {
        if (_project.value.clips.isEmpty()) { toast("Add a clip first"); return }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            var start = layerSpan().first
            val added = ArrayList<com.reel.editor.model.OverlayClip>()
            val total = TimelineMath.duration(_project.value)
            for (u in uris) {
                val c = withContext(Dispatchers.IO) { MediaProbe.probeClip(ctx, u) } ?: continue
                runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val len = minOf(c.sourceDurationUs, (total - start).coerceAtLeast(200_000))
                added += com.reel.editor.model.OverlayClip(
                    uri = c.uri, kind = c.kind, sourceDurationUs = c.sourceDurationUs, sourceWidth = c.sourceWidth, sourceHeight = c.sourceHeight,
                    rotationDegrees = c.rotationDegrees, hasAudio = c.hasAudio, startUs = start, trimEndUs = len,
                    transform = com.reel.editor.model.Transform(x = 0.5f + 0.06f * added.size, y = 0.42f + 0.06f * added.size, scale = 0.45f),
                )
            }
            if (added.isEmpty()) { toast("Couldn't open that file"); return@launch }
            edit { p -> p.copy(overlays = p.overlays + added) }
            select(Selection.OverlaySel(added.last().id))
        }
    }

    fun updateShape(id: String, gesture: Boolean = false, change: (com.reel.editor.model.ShapeLayer) -> com.reel.editor.model.ShapeLayer) {
        val f: (Project) -> Project = { p -> p.copy(shapes = p.shapes.map { if (it.id == id) change(it) else it }) }
        if (gesture) live(f) else edit(f)
    }

    fun updateAdjustLayer(id: String, gesture: Boolean = false, change: (com.reel.editor.model.AdjustmentLayer) -> com.reel.editor.model.AdjustmentLayer) {
        val f: (Project) -> Project = { p -> p.copy(adjustments = p.adjustments.map { if (it.id == id) change(it) else it }) }
        if (gesture) live(f) else edit(f)
    }

    fun updateOverlay(id: String, gesture: Boolean = false, change: (com.reel.editor.model.OverlayClip) -> com.reel.editor.model.OverlayClip) {
        val f: (Project) -> Project = { p -> p.copy(overlays = p.overlays.map { if (it.id == id) change(it) else it }) }
        if (gesture) live(f) else edit(f)
    }

    // ------------------------------------------------------------------ voice

    /** Applies voice settings to the selected clip or audio item. */
    fun setVoice(target: Selection, gesture: Boolean = false, change: (com.reel.editor.model.VoiceFx) -> com.reel.editor.model.VoiceFx) {
        when (target) {
            is Selection.ClipSel -> updateClip(target.id, gesture) { it.copy(voice = change(it.voice)) }
            is Selection.AudioSel -> {
                val f: (Project) -> Project = { p -> p.copy(audio = p.audio.map { if (it.id == target.id) it.copy(voice = change(it.voice)) else it }) }
                if (gesture) live(f) else edit(f)
            }
            else -> Unit
        }
    }

    fun setDuck(id: String, on: Boolean) = updateAudio(id) { it.copy(duck = on) }

    // ------------------------------------------------------------------ beat markers

    private fun updateAudio(id: String, change: (com.reel.editor.model.AudioItem) -> com.reel.editor.model.AudioItem) =
        edit { p -> p.copy(audio = p.audio.map { if (it.id == id) change(it) else it }) }

    fun setAutoBeats(id: String, on: Boolean) = updateAudio(id) { it.copy(autoBeats = on) }

    fun setBeatEvery(id: String, every: Int) = updateAudio(id) { it.copy(beatEvery = every, autoBeats = true) }

    fun addBeatMarker(id: String) {
        val t = preview.positionUs.value
        val a = _project.value.audio.firstOrNull { it.id == id } ?: return
        if (t < a.startUs || t > a.endUs) { toast("Move the playhead over the music"); return }
        updateAudio(id) { com.reel.editor.model.Beats.add(it, t) }
    }

    fun removeBeatMarker(id: String) {
        val a = _project.value.audio.firstOrNull { it.id == id } ?: return
        val b = com.reel.editor.model.Beats.removeNear(a, preview.positionUs.value)
        if (b == a) toast("No marker at the playhead") else updateAudio(id) { b }
    }

    fun clearBeatMarkers(id: String) = updateAudio(id) { com.reel.editor.model.Beats.clear(it) }

    /** Moves every cut to the nearest beat marker. */
    fun syncCutsToBeats() {
        val p = _project.value
        val markers = com.reel.editor.model.Beats.markers(p)
        if (markers.isEmpty()) { toast("Add beat markers first"); return }
        val synced = com.reel.editor.model.Beats.syncCuts(p, markers)
        if (synced == p) { toast("Cuts already on beats"); return }
        edit { synced }
        toast("Cuts synced to beats")
    }

    // ------------------------------------------------------------------ captions

    data class CaptionStatus(val stage: com.reel.editor.captions.CaptionEngine.Stage, val fraction: Float)

    private val captionEngine by lazy { com.reel.editor.captions.CaptionEngine(getApplication()) }
    private var captionRun: kotlinx.coroutines.Job? = null
    private val _captionStatus = MutableStateFlow<CaptionStatus?>(null)
    val captionStatus: StateFlow<CaptionStatus?> = _captionStatus

    /** Transcribes the project's speech and replaces any earlier auto captions. */
    fun generateCaptions(language: String, translate: Boolean, style: com.reel.editor.model.TextStyle) {
        if (captionRun?.isActive == true) return
        if (_project.value.clips.isEmpty()) { toast("Add a clip first"); return }
        preview.pause()
        _captionStatus.value = CaptionStatus(com.reel.editor.captions.CaptionEngine.Stage.LISTENING, 0f)
        captionRun = viewModelScope.launch {
            try {
                var lastPct = -1
                val res = captionEngine.run(_project.value, language, translate) { p ->
                    val pct = (p.fraction * 100).toInt()
                    if (pct != lastPct || p.stage != _captionStatus.value?.stage) {
                        lastPct = pct
                        _captionStatus.value = CaptionStatus(p.stage, p.fraction)
                    }
                }
                if (res.lines.isEmpty()) {
                    toast("No speech found")
                } else {
                    val y = _project.value.texts.firstOrNull { it.isCaption }?.transform?.y ?: 0.78f
                    edit { p -> p.copy(texts = p.texts.filterNot { it.isCaption } + com.reel.editor.model.CaptionAnchor.anchor(p, res.lines.map { com.reel.editor.model.CaptionGrouping.toLayer(it, style, y) })) }
                    toast("${res.lines.size} captions added")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("captions", e)
                toast(e.message ?: "Captions failed")
            } finally {
                _captionStatus.value = null
            }
        }
    }

    fun cancelCaptions() {
        captionEngine.cancel()
        captionRun?.cancel()
        _captionStatus.value = null
    }

    fun deleteCaptions() = edit { p -> p.copy(texts = p.texts.filterNot { it.isCaption }) }

    /** Applies a look to every auto caption, keeping each one's size. */
    fun styleCaptions(style: com.reel.editor.model.TextStyle) = edit { p ->
        p.copy(texts = p.texts.map { if (it.isCaption) it.copy(style = style.copy(sizeSp = it.style.sizeSp)) else it })
    }

    override fun onCleared() {
        captionEngine.cancel()
        saveNow()
        preview.release()
        super.onCleared()
    }
}
