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

    fun redo(current: Project): Project? {
        gestureStart = null
        val p = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return p
    }
}
