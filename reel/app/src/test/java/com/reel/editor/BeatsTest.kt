package com.reel.editor

import com.reel.editor.model.AudioItem
import com.reel.editor.model.Beats
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.MIN_CLIP_US
import com.reel.editor.model.Project
import com.reel.editor.model.Speed
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.timelineDurationUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeatsTest {
    // 120 bpm: a beat every 0.5 s of the song.
    private val song = AudioItem(
        uri = "file:///song.mp3", title = "Song", sourceDurationUs = 20_000_000, startUs = 1_000_000,
        trimStartUs = 2_000_000, trimEndUs = 12_000_000, beatsUs = (0 until 40).map { it * 500_000L },
    )

    @Test fun autoBeatsAreHiddenUntilEnabledAndFollowTrimAndPlacement() {
        assertTrue(Beats.markers(song).isEmpty())
        val on = song.copy(autoBeats = true)
        val m = Beats.markers(on)
        // Source 2.0 s (trim start) lands at timeline 1.0 s (item start).
        assertEquals(1_000_000L, m.first())
        assertEquals(on.endUs, m.last())
        assertTrue(m.zipWithNext().all { (a, b) -> b - a == 500_000L })
    }

    @Test fun densityKeepsEveryNthBeat() {
        val every2 = Beats.markers(song.copy(autoBeats = true, beatEvery = 2))
        assertTrue(every2.zipWithNext().all { (a, b) -> b - a == 1_000_000L })
        val bar = Beats.markers(song.copy(autoBeats = true, beatEvery = 4))
        assertTrue(bar.zipWithNext().all { (a, b) -> b - a == 2_000_000L })
    }

    @Test fun customMarkersAddAndRemove() {
        var a = Beats.add(song, 3_300_000)
        assertEquals(listOf(3_300_000L), Beats.markers(a))
        // Same spot again does nothing; outside the item does nothing.
        assertEquals(a, Beats.add(a, 3_310_000))
        assertEquals(a, Beats.add(a, 50_000_000))
        a = Beats.removeNear(a, 3_380_000)
        assertTrue(Beats.markers(a).isEmpty())
    }

    @Test fun removingADetectedBeatMakesAllOfThemEditable() {
        val on = song.copy(autoBeats = true, beatEvery = 2)
        val before = Beats.markers(on)
        val after = Beats.removeNear(on, before[3] + 40_000)
        assertFalse(after.autoBeats)
        assertEquals(before - before[3], Beats.markers(after))
        // Too far from any marker: unchanged.
        assertEquals(on, Beats.removeNear(on, before[3] + 400_000))
    }

    @Test fun markersMoveWithTheMusic() {
        val a = Beats.add(song, 3_000_000)
        val moved = a.copy(startUs = a.startUs + 2_000_000)
        assertEquals(listOf(5_000_000L), Beats.markers(moved))
    }

    @Test fun snappingIncludesMarkers() {
        val p = Project(audio = listOf(Beats.add(song, 4_200_000)))
        assertTrue(4_200_000L in TimelineMath.snapPoints(p))
    }

    private fun clip(len: Long, speed: Float = 1f) =
        Clip(uri = "file:///v$len.mp4", kind = ClipKind.VIDEO, sourceDurationUs = len, sourceWidth = 1080, sourceHeight = 1920, trimEndUs = len, speed = Speed(speed))

    @Test fun syncMovesCutsToNearestMarkers() {
        // Clips of 2.3 s and 1.8 s; markers every 0.5 s.
        val p = Project(clips = listOf(clip(5_000_000).copy(trimEndUs = 2_300_000), clip(5_000_000).copy(trimEndUs = 1_800_000), clip(5_000_000)))
        val markers = (1..20).map { it * 500_000L }
        val s = Beats.syncCuts(p, markers)
        val ends = TimelineMath.place(s.clips).map { it.endUs }
        assertTrue("cuts $ends", ends.all { e -> markers.any { abs(it - e) < 2_000 } })
        assertEquals(2_500_000L, ends[0])
        // 2.5 + 1.8 = 4.3 s: 4.5 is the nearest marker.
        assertEquals(4_500_000L, ends[1])
        assertTrue(s.clips.all { it.timelineDurationUs >= MIN_CLIP_US })
    }

    @Test fun syncRespectsAvailableFootageAndSpeed() {
        // Only 1.2 s of footage at 2x = 0.6 s on the timeline; a 1 s marker is out of reach.
        val short = clip(1_200_000, 2f)
        val s = Beats.syncCuts(Project(clips = listOf(short)), listOf(1_000_000L, 3_000_000L))
        assertEquals(short, s.clips[0])
        // At 2x with plenty of footage, a 1.5 s marker needs 3 s of media.
        val fast = clip(10_000_000, 2f).copy(trimEndUs = 2_000_000)
        val f = Beats.syncCuts(Project(clips = listOf(fast)), listOf(1_500_000L))
        assertEquals(3_000_000L, f.clips[0].trimEndUs)
        assertEquals(1_500_000L, f.clips[0].timelineDurationUs)
    }

    @Test fun syncPhotosAndNoMarkers() {
        val photo = Clip(uri = "p", kind = ClipKind.PHOTO, sourceDurationUs = 3_000_000, sourceWidth = 1, sourceHeight = 1, hasAudio = false)
        val s = Beats.syncCuts(Project(clips = listOf(photo)), listOf(4_000_000L))
        assertEquals(4_000_000L, s.clips[0].timelineDurationUs)
        val p = Project(clips = listOf(photo))
        assertEquals(p, Beats.syncCuts(p, emptyList()))
    }
}
