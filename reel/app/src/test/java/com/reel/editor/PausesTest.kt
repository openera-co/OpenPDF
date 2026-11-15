package com.reel.editor

import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Keyframe
import com.reel.editor.model.Pauses
import com.reel.editor.model.Project
import com.reel.editor.model.Speed
import com.reel.editor.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PausesTest {
    private val perSec = 40

    /** Levels (40 per second) loud in the given seconds, quiet hiss elsewhere. */
    private fun levels(seconds: Double, vararg talk: Pair<Double, Double>) = FloatArray((seconds * perSec).toInt()) { i ->
        val t = i.toDouble() / perSec
        if (talk.any { t >= it.first && t < it.second }) 0.6f else 0.01f
    }

    private fun clip(durUs: Long) = Clip(uri = "x", kind = ClipKind.VIDEO, sourceDurationUs = durUs, sourceWidth = 1920, sourceHeight = 1080)

    @Test
    fun cutsLongPausesAndKeepsShortOnes() {
        val c = clip(10_000_000)
        // Talk 0.5-2.0, a short 0.3 s breath, 2.3-4.0, a 2 s pause, 6.0-8.0, then silence.
        val lv = levels(10.0, 0.5 to 2.0, 2.3 to 4.0, 6.0 to 8.0)
        val r = Pauses.remove(Project(clips = listOf(c)), c.id, lv, perSec)!!
        val pieces = r.project.clips
        assertEquals(2, pieces.size)
        assertEquals(c.id, pieces[0].id)
        // Padded by 110 ms around the talking.
        assertEquals(390_000.0, pieces[0].trimStartUs.toDouble(), 30_000.0)
        assertEquals(4_110_000L.toDouble(), pieces[0].trimEndUs.toDouble(), 30_000.0)
        assertEquals(5_890_000L.toDouble(), pieces[1].trimStartUs.toDouble(), 30_000.0)
        assertEquals(8_110_000L.toDouble(), pieces[1].trimEndUs.toDouble(), 30_000.0)
        assertEquals(3, r.removed)
        assertEquals(10_000_000L - pieces.sumOf { it.trimmedDurationUs }, r.removedUs)
    }

    @Test
    fun keyframesFollowTheirPiece() {
        val c = clip(6_000_000).copy(keyframes = listOf(Keyframe(500_000, Transform(x = 0.2f)), Keyframe(5_000_000, Transform(x = 0.8f))))
        val lv = levels(6.0, 0.0 to 1.0, 4.5 to 6.0)
        val pieces = Pauses.remove(Project(clips = listOf(c)), c.id, lv, perSec)!!.project.clips
        assertEquals(2, pieces.size)
        assertEquals(listOf(500_000L), pieces[0].keyframes.map { it.timeUs })
        val start = pieces[1].trimStartUs
        assertEquals(listOf(5_000_000L - start), pieces[1].keyframes.map { it.timeUs })
    }

    @Test
    fun leavesSteadyTalkAndCurvesAlone() {
        val c = clip(4_000_000)
        assertNull(Pauses.remove(Project(clips = listOf(c)), c.id, levels(4.0, 0.0 to 4.0), perSec))
        val curved = c.copy(speed = Speed(curve = listOf(1f, 2f, 1f)))
        assertNull(Pauses.remove(Project(clips = listOf(curved)), curved.id, levels(4.0, 0.0 to 1.0, 3.0 to 4.0), perSec))
    }

    @Test
    fun onlyTheTrimmedPartCounts() {
        val c = clip(10_000_000).copy(trimStartUs = 2_000_000, trimEndUs = 7_000_000)
        val pieces = Pauses.remove(Project(clips = listOf(c)), c.id, levels(10.0, 0.0 to 3.0, 6.0 to 10.0), perSec)!!.project.clips
        assertTrue(pieces.all { it.trimStartUs >= 2_000_000 && it.trimEndUs <= 7_000_000 })
        assertEquals(2_000_000L, pieces.first().trimStartUs)
        assertEquals(7_000_000L, pieces.last().trimEndUs)
    }
}
