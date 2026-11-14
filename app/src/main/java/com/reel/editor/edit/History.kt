package com.reel.editor.edit

import com.reel.editor.model.Project

/**
 * Undo/redo over immutable project snapshots. Continuous gestures (dragging a slider or a trim
 * handle) call [beginGesture] once and [commit] at the end, so the whole drag is one step.
 */
class History(private val limit: Int = 100) {
    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()
    private var gestureStart: Project? = null

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun record(before: Project) {
        if (gestureStart != null) return
        push(before)
    }

    fun beginGesture(before: Project) {
        if (gestureStart == null) gestureStart = before
    }

    fun endGesture(after: Project) {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != after) push(start)
    }

    private fun push(p: Project) {
        undo.addLast(p)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: Project): Project? {
        gestureStart = null
        val p = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return p
    }

    /** One step back in time: what changed in it, and how many undos reach the state before it. */
    data class Step(val label: String, val stepsBack: Int)

    /** Undoable steps, newest first, each named after what it changed. */
    fun steps(current: Project): List<Step> {
        val states = undo.toList() + current
        return (states.size - 1 downTo 1).map { i -> Step(describe(states[i - 1], states[i]), states.size - i) }
    }

    /** Undoes [n] steps at once; the skipped states stay available to redo. */
    fun undoSteps(n: Int, current: Project): Project? {
        var cur = current
        repeat(n) { cur = undo(cur) ?: return cur.takeIf { it !== current } }
        return cur
    }

    fun redo(current: Project): Project? {
        gestureStart = null
        val p = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return p
    }
}

/** Names a change for the history list. */
fun describe(a: Project, b: Project): String {
    fun <T> change(x: List<T>, y: List<T>, what: String): String? = when {
        y.size > x.size -> "$what added"
        y.size < x.size -> "$what deleted"
        x != y -> "$what changed"
        else -> null
    }
    return change(a.clips, b.clips, "Clip")
        ?: change(a.texts.filter { !it.isCaption }, b.texts.filter { !it.isCaption }, "Text")
        ?: change(a.texts.filter { it.isCaption }, b.texts.filter { it.isCaption }, "Captions")
        ?: change(a.audio, b.audio, "Audio")
        ?: change(a.stickers, b.stickers, "Sticker")
        ?: change(a.shapes, b.shapes, "Shape")
        ?: change(a.overlays, b.overlays, "Overlay")
        ?: change(a.effects, b.effects, "Effect")
        ?: change(a.filters, b.filters, "Filter")
        ?: change(a.adjustments, b.adjustments, "Adjustment layer")
        ?: (if (a.canvas != b.canvas) "Canvas changed" else null)
        ?: (if (a.tracks != b.tracks) "Track switches" else null)
        ?: (if (a.name != b.name) "Renamed" else null)
        ?: "Edit"
}
