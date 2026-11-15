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
    /** Several items picked on the timeline; moved, deleted or styled together. */
    data class MultiSel(val items: List<Selection>) : Selection
}

/** What "Copy style" holds: the look of one kind of item, pasted onto items of the same kind. */
sealed interface StyleClip {
    data class OfClip(val c: com.reel.editor.model.Clip) : StyleClip
    data class OfText(val t: com.reel.editor.model.TextLayer) : StyleClip
    data class OfShape(val s: com.reel.editor.model.ShapeLayer) : StyleClip
    data class OfOverlay(val o: com.reel.editor.model.OverlayClip) : StyleClip
}

/** Which timeline track a selection lives on, for the eye, speaker and lock switches. */
fun trackKeyOf(s: Selection): String? = when (s) {
    is Selection.ClipSel -> com.reel.editor.model.TrackFlags.VIDEO
    is Selection.TextSel -> com.reel.editor.model.TrackFlags.TEXT
    is Selection.StickerSel, is Selection.ShapeSel -> com.reel.editor.model.TrackFlags.STICKERS
    is Selection.EffectSel, is Selection.FilterSel, is Selection.AdjustSel -> com.reel.editor.model.TrackFlags.EFFECTS
    is Selection.OverlaySel -> com.reel.editor.model.TrackFlags.OVERLAYS
    is Selection.AudioSel -> com.reel.editor.model.TrackFlags.AUDIO
    else -> null
}

enum class Panel {
    SPEED, VOLUME, ANIMATION, FILTERS, ADJUST, EFFECTS, TRANSITION, TEXT_EDIT, TEXT_STYLE, TEXT_ANIM,
    STICKERS, AUDIO, CANVAS, KEYFRAMES, CHROMA, CROP, EXPORT, CAPTIONS, CUTOUT, BEATS, VOICE, SHAPE, ADJUST_LAYER, OVERLAY, MASK, ADD_LAYER, TRACK, PRIVACY, HISTORY, TEXT_TEMPLATES, STABILIZE
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
        val p = projectId?.let { store.load(it) } ?: Project(name = "Project ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())}", canvas = com.reel.editor.model.CanvasSettings(ratio = com.reel.editor.Settings.defaultRatio(getApplication())))
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

    fun historySteps(): List<History.Step> = history.steps(_project.value)

    /** Goes back [n] steps in one go (from the history list). */
    fun undoSteps(n: Int) {
        history.undoSteps(n, _project.value)?.let { _project.value = it; preview.update(it); scheduleSave() }
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

    /** Timeline taps: while several items are picked, a tap adds or removes one. */
    fun tapSelect(s: Selection) {
        val cur = _selection.value
        if (cur is Selection.MultiSel && s != Selection.None && s !is Selection.MultiSel) {
            val items = if (s in cur.items) cur.items - s else cur.items + s
            select(if (items.isEmpty()) Selection.None else Selection.MultiSel(items))
        } else select(s)
    }

    /** Starts picking several items, beginning with the selected one. */
    fun startMultiSelect() {
        val cur = _selection.value
        if (cur == Selection.None || cur is Selection.MultiSel) return
        select(Selection.MultiSel(listOf(cur)))
        toast("Tap more items")
    }

    // ------------------------------------------------------------------ styles

    private var clipboard: StyleClip? = null
    private val _canPaste = MutableStateFlow(false)
    val canPaste: StateFlow<Boolean> = _canPaste

    fun copyStyle() {
        val p = _project.value
        clipboard = when (val s = _selection.value) {
            is Selection.ClipSel -> p.clips.firstOrNull { it.id == s.id }?.let { StyleClip.OfClip(it) }
            is Selection.TextSel -> p.texts.firstOrNull { it.id == s.id }?.let { StyleClip.OfText(it) }
            is Selection.ShapeSel -> p.shapes.firstOrNull { it.id == s.id }?.let { StyleClip.OfShape(it) }
            is Selection.OverlaySel -> p.overlays.firstOrNull { it.id == s.id }?.let { StyleClip.OfOverlay(it) }
            else -> null
        }
        _canPaste.value = clipboard != null
        toast(if (clipboard != null) "Style copied" else "Nothing to copy here")
    }

    /** Pastes the copied style onto the selected item(s) of the same kind. */
    fun pasteStyle() {
        val cb = clipboard ?: return
        val targets = when (val s = _selection.value) { is Selection.MultiSel -> s.items; Selection.None -> emptyList(); else -> listOf(s) }
        val ids = targets.mapNotNull { t -> when (t) { is Selection.ClipSel -> t.id; is Selection.TextSel -> t.id; is Selection.ShapeSel -> t.id; is Selection.OverlaySel -> t.id; else -> null } }.toSet()
        var n = 0
        edit { p ->
            when (cb) {
                is StyleClip.OfClip -> p.copy(clips = p.clips.map { c ->
                    if (c.id !in ids || c.id == cb.c.id) c else { n++; c.copy(adjust = cb.c.adjust, lookId = cb.c.lookId, lookAmount = cb.c.lookAmount, filter = cb.c.filter, clipEffects = cb.c.clipEffects, animIn = cb.c.animIn, animOut = cb.c.animOut, chroma = cb.c.chroma, fit = cb.c.fit, voice = cb.c.voice) }
                })
                is StyleClip.OfText -> p.copy(texts = p.texts.map { t ->
                    if (t.id !in ids || t.id == cb.t.id) t else { n++; t.copy(style = cb.t.style, animIn = cb.t.animIn, animOut = cb.t.animOut, loop = cb.t.loop) }
                })
                is StyleClip.OfShape -> p.copy(shapes = p.shapes.map { sh ->
                    if (sh.id !in ids || sh.id == cb.s.id) sh else { n++; sh.copy(fill = cb.s.fill, stroke = cb.s.stroke, strokeWidth = cb.s.strokeWidth, corner = cb.s.corner, animIn = cb.s.animIn, animOut = cb.s.animOut) }
                })
                is StyleClip.OfOverlay -> p.copy(overlays = p.overlays.map { o ->
                    if (o.id !in ids || o.id == cb.o.id) o else { n++; o.copy(blend = cb.o.blend, mask = cb.o.mask, chroma = cb.o.chroma, adjust = cb.o.adjust, lookId = cb.o.lookId, animIn = cb.o.animIn, animOut = cb.o.animOut) }
                })
            }
        }
        toast(if (n == 0) "Pick items of the same kind" else "Style pasted on $n")
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
            is Selection.MultiSel -> s.items.isNotEmpty()
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

    /** Cuts the quiet stretches out of the selected clip, leaving its spoken parts back to back. */
    fun removePauses() {
        val sel = _selection.value as? Selection.ClipSel ?: return
        val c = _project.value.clips.firstOrNull { it.id == sel.id } ?: return
        if (c.kind != com.reel.editor.model.ClipKind.VIDEO || !c.hasAudio) { toast("This clip has no sound"); return }
        if (!c.speed.curve.isNullOrEmpty()) { toast("Works on clips at one speed"); return }
        preview.pause()
        viewModelScope.launch {
            _busy.value = true
            val lv = withContext(Dispatchers.Default) { (audioAnalyzer.cached(c.uri) ?: audioAnalyzer.analyze(c.uri))?.peaks }
            _busy.value = false
            if (lv == null) { toast("Couldn't read the sound"); return@launch }
            val r = com.reel.editor.model.Pauses.remove(_project.value, c.id, lv, com.reel.editor.engine.CompositionFactory.LEVELS_PER_SEC)
            if (r == null) { toast("No pauses to cut"); return@launch }
            edit { r.project }
            toast("Cut ${r.removed} pause${if (r.removed == 1) "" else "s"}, %.1f s".format(r.removedUs / 1e6))
        }
    }

    /** Adds a built-in sound effect at the playhead. */
    fun addSound(s: com.reel.editor.media.SoundFx.Sfx) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val f = withContext(Dispatchers.IO) { runCatching { com.reel.editor.media.SoundFx.file(ctx.filesDir, s) }.getOrNull() }
            if (f == null) { toast("Couldn't make that sound"); return@launch }
            val dur = (f.length() - 44) / 2 * 1_000_000L / com.reel.editor.media.SoundFx.RATE
            val item = com.reel.editor.model.AudioItem(
                uri = Uri.fromFile(f).toString(), title = s.name, kind = com.reel.editor.model.AudioKind.SOUND,
                sourceDurationUs = dur, startUs = preview.positionUs.value,
            )
            edit { it.copy(audio = it.audio + item) }
            select(Selection.AudioSel(item.id))
            // Waveform only: beats mean nothing on a sound effect.
            analyzeUri(item.uri)
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
                    _project.value = p.copy(audio = p.audio.map {
                        if (it.id == audioItemId && (it.beatsUs.isEmpty() || it.beatsVersion < com.reel.editor.model.Beats.DETECTOR_VERSION))
                            it.copy(beatsUs = a.beatsUs, beatsVersion = com.reel.editor.model.Beats.DETECTOR_VERSION, bpm = a.bpm.toFloat())
                        else it
                    })
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
            is Selection.MultiSel -> {
                val ids = s.items.map { it.toString() }.toSet()
                fun gone(sel: Selection) = sel.toString() in ids
                if (s.items.any { it is Selection.ClipSel } && _project.value.clips.all { gone(Selection.ClipSel(it.id)) }) { toast("Keep at least one clip"); return }
                edit { p ->
                    p.copy(
                        clips = p.clips.filterNot { gone(Selection.ClipSel(it.id)) },
                        texts = p.texts.filterNot { gone(Selection.TextSel(it.id)) },
                        stickers = p.stickers.filterNot { gone(Selection.StickerSel(it.id)) },
                        audio = p.audio.filterNot { gone(Selection.AudioSel(it.id)) },
                        effects = p.effects.filterNot { gone(Selection.EffectSel(it.id)) },
                        filters = p.filters.filterNot { gone(Selection.FilterSel(it.id)) },
                        shapes = p.shapes.filterNot { gone(Selection.ShapeSel(it.id)) },
                        adjustments = p.adjustments.filterNot { gone(Selection.AdjustSel(it.id)) },
                        overlays = p.overlays.filterNot { gone(Selection.OverlaySel(it.id)) },
                    )
                }
                toast("${s.items.size} deleted")
            }
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

    /** Adds a ready-made text design at the playhead and selects its main text. */
    fun addTextTemplate(t: com.reel.editor.model.TextTemplates.Template) {
        val p = _project.value
        val total = TimelineMath.duration(p)
        val start = preview.positionUs.value.coerceAtMost((total - 500_000).coerceAtLeast(0))
        val (next, id) = com.reel.editor.model.TextTemplates.apply(p, t, start, (total - start).coerceAtLeast(1_000_000))
        edit { next }
        select(Selection.TextSel(id))
        openPanel(null)
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
    fun isLocked(s: Selection): Boolean = trackKeyOf(s)?.let { _project.value.tracks.isLocked(it) } == true

    fun toggleTrack(kind: com.reel.editor.model.TrackFlags.Kind, key: String) {
        edit { p -> p.copy(tracks = p.tracks.toggle(kind, key)) }
        // Hidden items stay on screen (faintly) and selectable; only a lock drops the selection.
        if (kind == com.reel.editor.model.TrackFlags.Kind.LOCK && trackKeyOf(_selection.value) == key) select(Selection.None)
    }

    fun muteAudio(ids: List<String>, muted: Boolean) = edit { p -> p.copy(audio = p.audio.map { if (it.id in ids) it.copy(muted = muted) else it }) }

    fun setTransformAtPlayhead(gesture: Boolean, change: (Transform) -> Transform) {
        if (isLocked(_selection.value)) { if (!gesture) toast("Track is locked"); return }
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

    // ------------------------------------------------------------------ clip tools

    /** Moves the selected clip's sound to its own audio track (and mutes the clip). */
    fun extractAudio() {
        val sel = _selection.value as? Selection.ClipSel ?: return
        val pc = TimelineMath.place(_project.value.clips).firstOrNull { it.clip.id == sel.id } ?: return
        val c = pc.clip
        if (c.kind == com.reel.editor.model.ClipKind.PHOTO || !c.hasAudio) { toast("This clip has no sound"); return }
        if (!(c.speed.curve.isNullOrEmpty() && c.speed.constant == 1f)) { toast("Set speed to 1x first"); return }
        val a = com.reel.editor.model.AudioItem(
            uri = c.uri, title = "Clip sound", kind = com.reel.editor.model.AudioKind.VOICEOVER, sourceDurationUs = c.sourceDurationUs,
            startUs = pc.startUs, trimStartUs = c.trimStartUs, trimEndUs = c.trimEndUs, volume = c.volume,
            fadeInUs = c.fadeInUs, fadeOutUs = c.fadeOutUs, voice = c.voice, volumeKeys = c.volumeKeys,
        )
        edit { p -> p.copy(clips = p.clips.map { if (it.id == c.id) it.copy(muted = true) else it }, audio = p.audio + a) }
        select(Selection.AudioSel(a.id))
        toast("Sound moved to its own track")
    }

    /** Reads the selected text aloud into a voiceover that starts with it. */
    fun speakText() {
        val sel = _selection.value as? Selection.TextSel ?: return
        val layer = _project.value.texts.firstOrNull { it.id == sel.id } ?: return
        if (layer.text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val ctx = getApplication<Application>()
                val f = java.io.File(ctx.filesDir, "voice/tts-${newIdLocal()}.wav")
                com.reel.editor.media.Speech.synthesize(ctx, layer.text.replace('\n', ' '), f)
                val dur = withContext(Dispatchers.IO) {
                    val r = android.media.MediaMetadataRetriever()
                    try { r.setDataSource(f.absolutePath); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000) } finally { r.release() }
                } ?: 0L
                if (dur <= 0) throw IllegalStateException("Couldn't read that text")
                val a = com.reel.editor.model.AudioItem(uri = Uri.fromFile(f).toString(), title = layer.text.take(24), kind = com.reel.editor.model.AudioKind.VOICEOVER, sourceDurationUs = dur, startUs = layer.startUs)
                edit { p -> p.copy(audio = p.audio + a) }
                toast("Voice added")
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't read that text")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun newIdLocal() = java.util.UUID.randomUUID().toString().take(8)

    private val _work = MutableStateFlow<Pair<String, Float>?>(null)
    /** A long job on the selected clip (label, 0..1), shown over the preview. */
    val work: StateFlow<Pair<String, Float>?> = _work
    private var reverser: com.reel.editor.media.Reverser? = null

    fun cancelWork() { reverser?.cancelled = true }

    /** Plays the selected clip backwards (a reversed copy is made once), or back forwards. */
    fun reverseClip() {
        val sel = _selection.value as? Selection.ClipSel ?: return
        val c = _project.value.clips.firstOrNull { it.id == sel.id } ?: return
        if (c.kind == com.reel.editor.model.ClipKind.PHOTO) { toast("Photos look the same backwards"); return }
        c.reversedFrom?.let { src ->
            updateClip(c.id) { it.copy(uri = src.uri, sourceDurationUs = src.sourceDurationUs, trimStartUs = src.trimStartUs, trimEndUs = src.trimEndUs, hasAudio = src.hasAudio, reversedFrom = null) }
            toast("Playing forwards")
            return
        }
        if (_work.value != null) return
        preview.pause()
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val dir = java.io.File(ctx.filesDir, "reversed").apply { mkdirs() }
            val key = "${c.uri}|${c.trimStartUs}|${c.trimEndUs}".hashCode().toUInt().toString(16)
            val out = java.io.File(dir, "rev-$key.mp4")
            val rv = com.reel.editor.media.Reverser(ctx).also { reverser = it }
            _work.value = "Reverse" to 0f
            try {
                val ok = out.exists() || withContext(Dispatchers.IO) {
                    // Needs room for the new file; a rough guess from the source's bitrate.
                    if (ctx.filesDir.usableSpace < 200L shl 20) throw IllegalStateException("Not enough free space")
                    rv.reverse(c.uri, c.trimStartUs, c.trimEndUs, c.hasAudio, out) { _work.value = "Reverse" to it }
                }
                if (!ok) return@launch
                val dur = withContext(Dispatchers.IO) { MediaProbe.probeClip(ctx, Uri.fromFile(out))?.sourceDurationUs } ?: throw IllegalStateException("Reversed clip can't be read")
                updateClip(c.id) {
                    it.copy(
                        uri = Uri.fromFile(out).toString(), sourceDurationUs = dur, trimStartUs = 0, trimEndUs = dur,
                        reversedFrom = com.reel.editor.model.ReverseSource(it.uri, it.sourceDurationUs, it.trimStartUs, it.trimEndUs, it.hasAudio),
                    )
                }
                toast("Playing backwards")
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("reverse", e)
                toast(e.message ?: "Couldn't reverse this clip")
            } finally {
                reverser = null
                _work.value = null
            }
        }
    }

    /** Saves the frame under the playhead to the gallery (Pictures/Reel) at 1080p, as it will export. */
    fun saveFrame() {
        preview.pause()
        com.reel.editor.fx.PreviewOriginal.on = false
        val shot = preview.snapshot()
        val pos = preview.positionUs.value
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _busy.value = true
            // Full HD with everything on it; the on-screen picture if that can't be made.
            val full = runCatching { com.reel.editor.engine.StillRenderer.render(ctx, _project.value, pos, 1080) }.getOrNull()
            _busy.value = false
            val bmp = full ?: shot ?: run { toast("Preview isn't ready"); return@launch }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val name = "Reel_${System.currentTimeMillis()}.jpg"
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Reel")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val uri = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    } else {
                        // Older Android: write the file ourselves, then index it.
                        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Reel").apply { mkdirs() }
                        val f = java.io.File(dir, name)
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                        null
                    }
                    if (uri != null) {
                        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                        if (android.os.Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    }
                    true
                }.getOrDefault(false)
            }
            toast(if (ok) "Frame saved to Pictures" else "Couldn't save the frame")
        }
    }

    /** Turns the selected clip a quarter turn clockwise. */
    fun rotateClip() {
        val sel = _selection.value as? Selection.ClipSel ?: return
        setTransformAtPlayhead(false) { t -> t.copy(rotation = ((t.rotation + 90f) % 360f).let { if (it > 180f) it - 360f else it }) }
        sel.hashCode()
    }

    // ------------------------------------------------------------------ head tracking

    private val _trackProgress = MutableStateFlow<Float?>(null)
    /** 0..1 while a head is being tracked, else null. */
    val trackProgress: StateFlow<Float?> = _trackProgress
    private var tracker: com.reel.editor.track.HeadTracker? = null

    fun cancelTracking() { tracker?.cancelled = true; stabilizer?.cancelled = true }
    private var stabilizer: com.reel.editor.track.Stabilizer? = null

    /**
     * Makes the selected layer follow the head in the video under it: one keyframe every 150 ms,
     * keeping the layer's offset from the head. With [followSize] it also grows and shrinks with it.
     */
    fun trackHead(followSize: Boolean) {
        val sel = _selection.value
        if (_trackProgress.value != null) return
        val p0 = _project.value
        val span: Pair<Long, Long> = when (sel) {
            is Selection.TextSel -> p0.texts.firstOrNull { it.id == sel.id }?.let { it.startUs to it.startUs + it.durationUs }
            is Selection.StickerSel -> p0.stickers.firstOrNull { it.id == sel.id }?.let { it.startUs to it.startUs + it.durationUs }
            is Selection.ShapeSel -> p0.shapes.firstOrNull { it.id == sel.id }?.let { it.startUs to it.startUs + it.durationUs }
            is Selection.OverlaySel -> p0.overlays.firstOrNull { it.id == sel.id }?.let { it.startUs to it.endUs }
            else -> null
        } ?: return
        preview.pause()
        _trackProgress.value = 0f
        viewModelScope.launch {
            val t = com.reel.editor.track.HeadTracker(getApplication()).also { tracker = it }
            try {
                val (cw, chh) = com.reel.editor.fx.RenderPlans.canvasSize(p0, 1280)
                val canvasAspect = cw.toFloat() / chh
                // Head positions on the canvas, in timeline time, across every clip the layer spans.
                val canvasPts = withContext(Dispatchers.Default) {
                    val placed = TimelineMath.place(p0.clips).filter { it.startUs < span.second && it.endUs > span.first }
                    val total = (span.second - span.first).coerceAtLeast(1)
                    val list = ArrayList<Triple<Long, Pair<Float, Float>, Float>>()
                    for (pc in placed) {
                        val from = (span.first - pc.startUs).coerceAtLeast(0)
                        val to = (span.second - pc.startUs).coerceAtMost(pc.durationUs - 1)
                        val raw = t.track(pc.clip, from, to) { f -> _trackProgress.value = ((pc.startUs + from - span.first) + f * (to - from)) / total }
                        val pts = com.reel.editor.model.HeadPath.smooth(raw)
                        val (dw, dh) = com.reel.editor.fx.RenderPlans.displaySize(pc.clip)
                        val cr = pc.clip.crop
                        for (h in pts) {
                            val ct = interpolate(pc.clip.transform, pc.clip.keyframes, h.timeUs)
                            // Into the cropped picture's coordinates first.
                            val pos = com.reel.editor.model.HeadPath.toCanvas((h.x - cr.x) / cr.w, (h.y - cr.y) / cr.h, ct, dw * cr.w / (dh.coerceAtLeast(1) * cr.h), canvasAspect, pc.clip.fit == com.reel.editor.model.Fit.FILL)
                            list += Triple(pc.startUs + h.timeUs, pos, h.size * ct.scale)
                        }
                    }
                    list
                }
                if (t.cancelled) return@launch
                if (canvasPts.isEmpty()) { toast("No one found to follow"); return@launch }
                fun keys(base: Transform, old: List<Keyframe>, start: Long): List<Keyframe> {
                    val first = canvasPts.first()
                    val at0 = interpolate(base, old, (first.first - start).coerceAtLeast(0))
                    val dx = at0.x - first.second.first
                    val dy = at0.y - first.second.second
                    val s0 = first.third.coerceAtLeast(1e-3f)
                    return com.reel.editor.model.HeadPath.simplify(canvasPts.map { (time, pos, size) ->
                        val local = time - start
                        val cur = interpolate(base, old, local)
                        Keyframe(local, cur.copy(x = pos.first + dx, y = pos.second + dy, scale = if (followSize) (at0.scale * size / s0).coerceIn(0.05f, 8f) else cur.scale), com.reel.editor.model.Easing.LINEAR)
                    })
                }
                edit { p ->
                    when (sel) {
                        is Selection.TextSel -> p.copy(texts = p.texts.map { if (it.id == sel.id) it.copy(keyframes = keys(it.transform, it.keyframes, it.startUs)) else it })
                        is Selection.StickerSel -> p.copy(stickers = p.stickers.map { if (it.id == sel.id) it.copy(keyframes = keys(it.transform, it.keyframes, it.startUs)) else it })
                        is Selection.ShapeSel -> p.copy(shapes = p.shapes.map { if (it.id == sel.id) it.copy(keyframes = keys(it.transform, it.keyframes, it.startUs)) else it })
                        is Selection.OverlaySel -> p.copy(overlays = p.overlays.map { if (it.id == sel.id) it.copy(keyframes = keys(it.transform, it.keyframes, it.startUs)) else it })
                        else -> p
                    }
                }
                toast("Following the head")
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("track", e)
                toast("Tracking failed")
            } finally {
                tracker = null
                _trackProgress.value = null
            }
        }
    }

    /** Sets or changes the selected clip's hidden region. */
    fun updatePrivacy(gesture: Boolean = false, change: (com.reel.editor.model.Privacy) -> com.reel.editor.model.Privacy?) {
        val sel = _selection.value as? Selection.ClipSel ?: return
        updateClip(sel.id, gesture) { it.copy(privacy = change(it.privacy ?: com.reel.editor.model.Privacy())) }
    }

    /** Finds the face in the selected clip and makes its blur follow it. */
    fun trackPrivacy() {
        val sel = _selection.value as? Selection.ClipSel ?: return
        if (_trackProgress.value != null) return
        val p0 = _project.value
        val pc = TimelineMath.place(p0.clips).firstOrNull { it.clip.id == sel.id } ?: return
        preview.pause()
        _trackProgress.value = 0f
        viewModelScope.launch {
            val t = com.reel.editor.track.HeadTracker(getApplication()).also { tracker = it }
            try {
                val raw = withContext(Dispatchers.Default) { t.track(pc.clip, 0, pc.durationUs - 1, 150_000) { _trackProgress.value = it } }
                if (t.cancelled) return@launch
                val cr = pc.clip.crop
                val pts = com.reel.editor.model.HeadPath.smooth(raw, radius = 1)
                if (pts.isEmpty()) { toast("No face found"); return@launch }
                val (cw, chh) = com.reel.editor.fx.RenderPlans.canvasSize(p0, 1280)
                val canvasAspect = cw.toFloat() / chh
                val (dw, dh) = com.reel.editor.fx.RenderPlans.displaySize(pc.clip)
                val contentAspect = dw * cr.w / (dh.coerceAtLeast(1) * cr.h)
                val fill = pc.clip.fit == com.reel.editor.model.Fit.FILL
                val (_, ch) = com.reel.editor.model.HeadPath.contentSize(contentAspect, canvasAspect, fill)
                val keys = pts.map { h ->
                    val ct = interpolate(pc.clip.transform, pc.clip.keyframes, h.timeUs)
                    val (x, y) = com.reel.editor.model.HeadPath.toCanvas((h.x - cr.x) / cr.w, (h.y - cr.y) / cr.h, ct, contentAspect, canvasAspect, fill)
                    Keyframe(h.timeUs, Transform(x = x, y = y, scale = (h.size / cr.h * ch * ct.scale).coerceIn(0.03f, 1.5f)), com.reel.editor.model.Easing.LINEAR)
                }
                updateClip(pc.clip.id) { it.copy(privacy = (it.privacy ?: com.reel.editor.model.Privacy()).copy(keys = com.reel.editor.model.HeadPath.simplify(keys, 0.004f))) }
                toast("Blur follows the face")
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("privacy", e)
                toast("Tracking failed")
            } finally {
                tracker = null
                _trackProgress.value = null
            }
        }
    }

    /** Pans the selected clip so the head stays in the middle of the frame (auto reframe). */
    fun reframeClip(targetX: Float = 0.5f) {
        val sel = _selection.value as? Selection.ClipSel ?: return
        if (_trackProgress.value != null) return
        val p0 = _project.value
        val pc = TimelineMath.place(p0.clips).firstOrNull { it.clip.id == sel.id } ?: return
        preview.pause()
        _trackProgress.value = 0f
        viewModelScope.launch {
            val t = com.reel.editor.track.HeadTracker(getApplication()).also { tracker = it }
            try {
                val raw = withContext(Dispatchers.Default) { t.track(pc.clip, 0, pc.durationUs - 1, 200_000) { _trackProgress.value = it } }
                if (t.cancelled) return@launch
                val cr = pc.clip.crop
                val pts = com.reel.editor.model.HeadPath.smooth(raw, radius = 3).map { it.copy(x = (it.x - cr.x) / cr.w, y = (it.y - cr.y) / cr.h) }
                if (pts.isEmpty()) { toast("No one found to follow"); return@launch }
                val (cw, chh) = com.reel.editor.fx.RenderPlans.canvasSize(p0, 1280)
                val (dw, dh) = com.reel.editor.fx.RenderPlans.displaySize(pc.clip)
                val keys = com.reel.editor.model.HeadPath.reframe(pts, pc.clip.transform, dw * cr.w / (dh.coerceAtLeast(1) * cr.h), cw.toFloat() / chh, pc.clip.fit == com.reel.editor.model.Fit.FILL, targetX)
                updateClip(pc.clip.id) { it.copy(keyframes = com.reel.editor.model.HeadPath.simplify(keys)) }
                toast("Reframed")
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("reframe", e)
                toast("Reframe failed")
            } finally {
                tracker = null
                _trackProgress.value = null
            }
        }
    }

    /**
     * Smooths out camera shake in the selected clip: [strength] 0 light, 1 normal, 2 strong. The
     * result is the clip's keyframes (small moves, turns and a slight zoom), so it can be undone.
     */
    fun stabilizeClip(strength: Int) {
        val sel = _selection.value as? Selection.ClipSel ?: return
        if (_trackProgress.value != null) return
        val p0 = _project.value
        val pc = TimelineMath.place(p0.clips).firstOrNull { it.clip.id == sel.id } ?: return
        if (pc.clip.kind == com.reel.editor.model.ClipKind.PHOTO) { toast("Photos don't shake"); return }
        preview.pause()
        _trackProgress.value = 0f
        val sigma = floatArrayOf(5f, 10f, 18f)[strength.coerceIn(0, 2)]
        val maxZoom = floatArrayOf(1.08f, 1.14f, 1.22f)[strength.coerceIn(0, 2)]
        viewModelScope.launch {
            val st = com.reel.editor.track.Stabilizer(getApplication()).also { stabilizer = it }
            try {
                val res = withContext(Dispatchers.Default) { st.analyze(pc.clip, pc.durationUs) { _trackProgress.value = it } }
                if (st.cancelled) return@launch
                if (res == null) { toast("Couldn't read this clip"); return@launch }
                val (times, motions) = res
                val corr = com.reel.editor.model.Stabilize.corrections(motions, sigma)
                val (cw, chh) = com.reel.editor.fx.RenderPlans.canvasSize(p0, 1280)
                val (dw, dh) = com.reel.editor.fx.RenderPlans.displaySize(pc.clip)
                val cr = pc.clip.crop
                val keys = com.reel.editor.model.Stabilize.keyframes(
                    times, corr, pc.clip.transform, dw * cr.w / (dh.coerceAtLeast(1) * cr.h), cw.toFloat() / chh,
                    pc.clip.fit == com.reel.editor.model.Fit.FILL, maxZoom,
                )
                updateClip(pc.clip.id) { it.copy(keyframes = com.reel.editor.model.HeadPath.simplify(keys, 0.0015f)) }
                toast("Stabilized")
            } catch (e: Exception) {
                com.reel.editor.CrashReports.error("stabilize", e)
                toast("Stabilize failed")
            } finally {
                stabilizer = null
                _trackProgress.value = null
            }
        }
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

    fun mergeCaption(id: String) = edit { p -> p.copy(texts = com.reel.editor.model.CaptionEdit.mergeWithNext(p.texts, id)) }
    fun splitCaption(id: String) = edit { p -> p.copy(texts = com.reel.editor.model.CaptionEdit.split(p.texts, id)) }
    fun nudgeCaption(id: String, deltaUs: Long) = edit { p -> p.copy(texts = com.reel.editor.model.CaptionEdit.nudge(p.texts, id, deltaUs)) }
    fun deleteCaption(id: String) = edit { p -> p.copy(texts = p.texts.filter { it.id != id }) }

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
