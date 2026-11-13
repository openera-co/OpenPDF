package com.reel.editor

import com.reel.editor.edit.History
import com.reel.editor.edit.ProjectStore
import com.reel.editor.fx.CurveLut
import com.reel.editor.fx.TextMotion
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Curves
import com.reel.editor.model.Easing
import com.reel.editor.model.Keyframe
import com.reel.editor.model.MIN_CLIP_US
import com.reel.editor.model.Project
import com.reel.editor.model.Pt
import com.reel.editor.model.Speed
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.TextAnim
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.Transform
import com.reel.editor.model.TransitionRef
import com.reel.editor.model.ease
import com.reel.editor.model.interpolate
import com.reel.editor.model.timelineDurationUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ModelTest {
    private fun clip(len: Long = 5_000_000, speed: Speed = Speed()) =
        Clip(uri = "file:///v$len.mp4", kind = ClipKind.VIDEO, sourceDurationUs = len, sourceWidth = 1920, sourceHeight = 1080, speed = speed)

    @Test fun constantSpeedChangesDuration() {
        assertEquals(2_500_000, clip(speed = Speed(2f)).timelineDurationUs)
        assertEquals(10_000_000, clip(speed = Speed(0.5f)).timelineDurationUs)
    }

    @Test fun speedMappingRoundTrips() {
        val sp = Speed(curve = listOf(2f, 0.3f, 0.3f, 2.5f, 1f))
        val media = 6_000_000L
        val total = SpeedMath.timelineDurationUs(sp, media)
        for (t in listOf(0L, 100_000L, 1_234_567L, total / 2, total - 1)) {
            val m = SpeedMath.timelineToMedia(sp, media, t)
            val back = SpeedMath.mediaToTimeline(sp, media, m)
            assertTrue("t=$t back=$back", abs(back - t) < 2_000)
        }
        assertEquals(media, SpeedMath.timelineToMedia(sp, media, total + 1_000_000))
    }

    @Test fun curveSegmentsCoverTheClip() {
        val segs = SpeedMath.segments(Speed(curve = listOf(1f, 4f)), 4_800_000)
        assertEquals(48, segs.size)
        assertEquals(0L, segs.first().first)
        assertTrue(segs.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue(segs.last().second > segs.first().second)
    }

    @Test fun splitKeepsTotalLengthAndMediaContinuity() {
        val p = Project(clips = listOf(clip(), clip(3_000_000)))
        val s = TimelineMath.split(p, 2_000_000)!!
        assertEquals(3, s.clips.size)
        assertEquals(TimelineMath.duration(p), TimelineMath.duration(s))
        assertEquals(s.clips[0].trimEndUs, s.clips[1].trimStartUs)
        assertNotEquals(s.clips[0].id, s.clips[1].id)
    }

    @Test fun splitWithSpeedCurveKeepsLength() {
        val p = Project(clips = listOf(clip(8_000_000, Speed(curve = listOf(1f, 3f, 0.5f, 1f)))))
        val total = TimelineMath.duration(p)
        val s = TimelineMath.split(p, total / 3)!!
        assertTrue(abs(TimelineMath.duration(s) - total) < 60_000)
    }

    @Test fun splitRefusesTinyPieces() {
        val p = Project(clips = listOf(clip()))
        assertNull(TimelineMath.split(p, MIN_CLIP_US / 2))
        assertNull(TimelineMath.split(p, 5_000_000 - MIN_CLIP_US / 2))
        assertNull(TimelineMath.split(Project(), 1_000_000))
    }

    @Test fun splitMovesKeyframesToTheRightPiece() {
        val c = clip().copy(keyframes = listOf(Keyframe(500_000, Transform()), Keyframe(4_000_000, Transform(scale = 2f))))
        val s = TimelineMath.split(Project(clips = listOf(c)), 2_000_000)!!
        assertEquals(listOf(500_000L), s.clips[0].keyframes.map { it.timeUs })
        assertEquals(listOf(2_000_000L), s.clips[1].keyframes.map { it.timeUs })
    }

    @Test fun trimClampsToSourceAndMinimum() {
        val c = clip()
        val p = Project(clips = listOf(c))
        val longer = TimelineMath.trim(p, c.id, leftEdge = false, deltaUs = 9_000_000)
        assertEquals(5_000_000, longer.clips[0].trimEndUs)
        val tiny = TimelineMath.trim(p, c.id, leftEdge = true, deltaUs = 9_000_000)
        assertEquals(MIN_CLIP_US, tiny.clips[0].trimmedDurationUs)
        val left = TimelineMath.trim(p, c.id, leftEdge = true, deltaUs = -1_000_000)
        assertEquals(0, left.clips[0].trimStartUs)
    }

    @Test fun trimAtDoubleSpeedMovesTwiceTheMedia() {
        val c = clip(speed = Speed(2f))
        val p = TimelineMath.trim(Project(clips = listOf(c)), c.id, leftEdge = true, deltaUs = 500_000)
        assertEquals(1_000_000, p.clips[0].trimStartUs)
    }

    @Test fun moveDropsTransitionFromFirstClip() {
        val a = clip()
        val b = clip(3_000_000).copy(id = "b", transitionIn = TransitionRef("glt_fade"))
        val p = TimelineMath.move(Project(clips = listOf(a, b)), 1, 0)
        assertEquals("b", p.clips[0].id)
        assertNull(p.clips[0].transitionIn)
    }

    @Test fun snapPicksNearestWithinThreshold() {
        assertEquals(1_000_000, TimelineMath.snap(1_030_000, listOf(0L, 1_000_000L, 2_000_000L), 50_000))
        assertEquals(1_300_000, TimelineMath.snap(1_300_000, listOf(0L, 1_000_000L), 50_000))
    }

    @Test fun clipAtCoversEnds() {
        val placed = TimelineMath.place(listOf(clip(), clip(3_000_000)))
        assertEquals(0, TimelineMath.clipAt(placed, 0)!!.index)
        assertEquals(1, TimelineMath.clipAt(placed, 5_000_000)!!.index)
        assertEquals(1, TimelineMath.clipAt(placed, 99_000_000)!!.index)
    }

    @Test fun easingEndpointsAndShape() {
        for (e in Easing.PRESETS) {
            assertEquals(0f, e.ease(0f), 1e-3f)
            assertEquals(1f, e.ease(1f), 1e-3f)
        }
        assertTrue(Easing.EASE_IN.ease(0.25f) < 0.25f)
        assertTrue(Easing.EASE_OUT.ease(0.25f) > 0.25f)
        assertEquals(0.5f, Easing.EASE_IN_OUT.ease(0.5f), 0.02f)
        assertTrue(Easing.BACK.ease(0.8f) > 1f)
    }

    @Test fun keyframeInterpolation() {
        val keys = listOf(Keyframe(0, Transform(x = 0f)), Keyframe(1_000_000, Transform(x = 1f), Easing.LINEAR))
        assertEquals(0.5f, interpolate(Transform(), keys, 500_000).x, 1e-3f)
        assertEquals(0f, interpolate(Transform(), keys, -10).x, 1e-6f)
        assertEquals(1f, interpolate(Transform(), keys, 5_000_000).x, 1e-6f)
        assertEquals(0.3f, interpolate(Transform(x = 0.3f), emptyList(), 100).x, 1e-6f)
    }

    @Test fun historyGroupsGestures() {
        val h = History()
        val p0 = Project(name = "0")
        val p1 = p0.copy(name = "1")
        h.record(p0)
        h.beginGesture(p1)
        h.record(p1.copy(name = "ignored during gesture"))
        h.endGesture(p1.copy(name = "3"))
        assertEquals("1", h.undo(p1.copy(name = "3"))!!.name)
        assertEquals("0", h.undo(p1)!!.name)
        assertNull(h.undo(p0))
        assertEquals("1", h.redo(p0)!!.name)
    }

    @Test fun historyIsBounded() {
        val h = History(limit = 3)
        repeat(10) { h.record(Project(name = "$it")) }
        var n = 0
        var cur = Project()
        while (true) { cur = h.undo(cur) ?: break; n++ }
        assertEquals(3, n)
    }

    @Test fun curveSamplingIdentityAndMonotone() {
        val id = CurveLut.sample(Curves.IDENTITY)
        for (i in 0 until 256) assertTrue(abs(id[i] - i) <= 1)
        val s = CurveLut.sample(listOf(Pt(0f, 0f), Pt(0.25f, 0.1f), Pt(0.75f, 0.9f), Pt(1f, 1f)))
        for (i in 1 until 256) assertTrue("non-monotone at $i", s[i] >= s[i - 1])
        assertEquals(255, s[255])
    }

    @Test fun textMotionFadesInAndOut() {
        val inA = TextAnim("fade", 1_000_000)
        assertEquals(0f, TextMotion.at(inA, null, null, 0, 4_000_000).opacity, 1e-3f)
        assertEquals(1f, TextMotion.at(inA, null, null, 2_000_000, 4_000_000).opacity, 1e-3f)
        val out = TextMotion.at(null, TextAnim("fade", 1_000_000), null, 3_999_000, 4_000_000)
        assertTrue(out.opacity < 0.01f)
        assertEquals(0.5f, TextMotion.at(TextAnim("typewriter", 1_000_000), null, null, 500_000, 4_000_000).reveal, 1e-3f)
        assertEquals(1f, TextMotion.at(null, null, null, 1, 4).opacity, 1e-6f)
    }

    @Test fun canvasSizeUsesShortSideAndEncoderFriendlySizes() {
        fun size(r: com.reel.editor.model.AspectRatio, res: Int) =
            com.reel.editor.fx.RenderPlans.canvasSize(Project(canvas = com.reel.editor.model.CanvasSettings(ratio = r)), res)
        assertEquals(1080 to 1920, size(com.reel.editor.model.AspectRatio.R9_16, 1080))
        assertEquals(1920 to 1080, size(com.reel.editor.model.AspectRatio.R16_9, 1080))
        assertEquals(720 to 720, size(com.reel.editor.model.AspectRatio.R1_1, 720))
        assertEquals(1080 to 1352, size(com.reel.editor.model.AspectRatio.R4_5, 1080))
        assertEquals(2160 to 3840, size(com.reel.editor.model.AspectRatio.R9_16, 2160))
        val wide = size(com.reel.editor.model.AspectRatio.R21_9, 2160)
        assertEquals(4096, wide.first)
        assertTrue(wide.second % 8 == 0)
        // Original ratio follows the first clip (1920x1080 landscape here).
        val orig = com.reel.editor.fx.RenderPlans.canvasSize(Project(canvas = com.reel.editor.model.CanvasSettings(ratio = com.reel.editor.model.AspectRatio.ORIGINAL), clips = listOf(clip())), 720)
        assertEquals(1280 to 720, orig)
    }

    @Test fun projectJsonRoundTrip() {
        val p = Project(name = "Trip", clips = listOf(clip().copy(transitionIn = TransitionRef("glt_fade", 300_000), keyframes = listOf(Keyframe(0, Transform(scale = 2f))))))
        val text = ProjectStore.json.encodeToString(Project.serializer(), p)
        val back = ProjectStore.json.decodeFromString(Project.serializer(), text)
        assertEquals(p, back)
        // Unknown fields from newer versions are ignored.
        assertNotNull(ProjectStore.json.decodeFromString(Project.serializer(), text.replaceFirst("{", "{\"futureField\":1,")))
    }
}
