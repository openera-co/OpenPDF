package com.reel.editor

import com.reel.editor.model.CaptionAnchor
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Easing
import com.reel.editor.model.OverlayClip
import com.reel.editor.model.Project
import com.reel.editor.model.ShapeLayer
import com.reel.editor.model.Speed
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TimelineMath
import com.reel.editor.model.TrackLayout
import com.reel.editor.model.ValueKey
import com.reel.editor.model.WordTiming
import com.reel.editor.model.interpolateValue
import com.reel.editor.model.keyAt
import com.reel.editor.model.setKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayersTest {
    private fun clip(len: Long = 10_000_000, uri: String = "file:///a.mp4") =
        Clip(uri = uri, kind = ClipKind.VIDEO, sourceDurationUs = len, sourceWidth = 1080, sourceHeight = 1920)

    private fun text(start: Long, dur: Long, s: String = "hi") = TextLayer(text = s, startUs = start, durationUs = dur)

    // ------------------------------------------------------------------ lanes

    @Test fun overlappingTextsStackIntoLanes() {
        val p = Project(clips = listOf(clip()), texts = listOf(text(0, 3_000_000), text(1_000_000, 3_000_000), text(2_000_000, 3_000_000), text(6_000_000, 1_000_000)))
        val r = TrackLayout.assign(TrackLayout.itemsOf(p))
        val rows = p.texts.map { r.rowOf["text:${it.id}"]!! }
        assertEquals(listOf(0, 1, 2, 0), rows)
        assertEquals(3, r.rows)
    }

    @Test fun groupsKeepTheirOwnLanes() {
        val p = Project(
            clips = listOf(clip()),
            texts = listOf(text(0, 1_000_000)),
            shapes = listOf(ShapeLayer(startUs = 0, durationUs = 1_000_000)),
        )
        val r = TrackLayout.assign(TrackLayout.itemsOf(p))
        assertNotEquals(r.rowOf["text:${p.texts[0].id}"], r.rowOf["shape:${p.shapes[0].id}"])
    }

    @Test fun manyLayersAllGetARow() {
        val texts = (0 until 60).map { text(it * 50_000L, 2_000_000) }
        val p = Project(clips = listOf(clip()), texts = texts)
        val r = TrackLayout.assign(TrackLayout.itemsOf(p))
        assertEquals(60, r.rowOf.size)
        // No two overlapping layers share a lane.
        for (a in texts) for (b in texts) if (a !== b && r.rowOf["text:${a.id}"] == r.rowOf["text:${b.id}"]) {
            assertTrue(a.startUs + a.durationUs <= b.startUs || b.startUs + b.durationUs <= a.startUs)
        }
    }

    // ------------------------------------------------------------------ clamping

    @Test fun layersDraggedPastTheStartOrEndStayInside() {
        val p = Project(clips = listOf(clip()), texts = listOf(text(-3_000_000, 2_000_000), text(9_500_000, 2_000_000), text(40_000_000, 1_000_000)))
        val c = TrackLayout.clampLayers(p)
        assertEquals(0L, c.texts[0].startUs)
        assertEquals(8_000_000L, c.texts[1].startUs)
        assertEquals(10_000_000L, c.texts[1].startUs + c.texts[1].durationUs)
        assertEquals(9_000_000L, c.texts[2].startUs)
    }

    @Test fun layerLongerThanTheVideoShrinksToIt() {
        val p = Project(clips = listOf(clip(2_000_000)), texts = listOf(text(0, 9_000_000)))
        assertEquals(2_000_000L, TrackLayout.clampLayers(p).texts[0].durationUs)
    }

    @Test fun overlayPastTheEndIsTrimmed() {
        val o = OverlayClip(uri = "file:///o.mp4", kind = ClipKind.VIDEO, sourceDurationUs = 8_000_000, sourceWidth = 100, sourceHeight = 100, startUs = 6_000_000)
        val c = TrackLayout.clampLayers(Project(clips = listOf(clip()), overlays = listOf(o))).overlays[0]
        assertTrue(c.endUs <= 10_000_000)
        assertEquals(0L, c.trimStartUs)
    }

    @Test fun emptyProjectIsLeftAlone() {
        val p = Project(texts = listOf(text(5, 5)))
        assertEquals(p, TrackLayout.clampLayers(p))
    }

    // ------------------------------------------------------------------ value keyframes

    @Test fun valueKeysInterpolate() {
        val keys = listOf(ValueKey(0, 0f, Easing.LINEAR), ValueKey(1_000_000, 1f, Easing.LINEAR))
        assertEquals(0.5f, interpolateValue(9f, keys, 500_000), 1e-4f)
        assertEquals(0f, interpolateValue(9f, keys, -10), 0f)
        assertEquals(1f, interpolateValue(9f, keys, 5_000_000), 0f)
        assertEquals(9f, interpolateValue(9f, emptyList(), 5), 0f)
    }

    @Test fun setKeyReplacesNearbyAndKeepsOrder() {
        var k = emptyList<ValueKey>().setKey(1_000_000, 1f).setKey(0, 0f)
        assertEquals(listOf(0L, 1_000_000L), k.map { it.timeUs })
        k = k.setKey(1_010_000, 0.3f)
        assertEquals(2, k.size)
        assertEquals(0.3f, k.keyAt(1_000_000)!!.value, 0f)
        assertNull(k.keyAt(500_000))
    }

    // ------------------------------------------------------------------ captions follow their speech

    private fun caption(start: Long, dur: Long) = TextLayer(text = "hello there", startUs = start, durationUs = dur, isCaption = true,
        words = listOf(WordTiming("hello", 0, dur / 2), WordTiming("there", dur / 2, dur)))

    @Test fun captionsMoveWithTheirClip() {
        val a = clip(4_000_000, "file:///a.mp4")
        val b = clip(6_000_000, "file:///b.mp4")
        var p = Project(clips = listOf(a, b))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(5_000_000, 1_000_000)))) // 1 s into b
        // Swap: b is now first, so its caption should start at 1 s.
        val swapped = CaptionAnchor.reflow(TimelineMath.move(p, 1, 0))
        assertEquals(1_000_000L, swapped.texts.single().startUs)
    }

    @Test fun trimmedOutSpeechDropsItsCaption() {
        var p = Project(clips = listOf(clip()))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(8_000_000, 1_000_000), caption(1_000_000, 1_000_000))))
        val trimmed = TimelineMath.trim(p, p.clips[0].id, false, -4_000_000) // clip now 6 s
        val r = CaptionAnchor.reflow(trimmed)
        assertEquals(1, r.texts.size)
        assertEquals(1_000_000L, r.texts[0].startUs)
    }

    @Test fun leftTrimShiftsCaptionsEarlier() {
        var p = Project(clips = listOf(clip()))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(5_000_000, 1_000_000))))
        val r = CaptionAnchor.reflow(TimelineMath.trim(p, p.clips[0].id, true, 2_000_000))
        assertEquals(3_000_000L, r.texts.single().startUs)
    }

    @Test fun speedChangeStretchesCaptions() {
        var p = Project(clips = listOf(clip()))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(4_000_000, 2_000_000))))
        val fast = p.copy(clips = p.clips.map { it.copy(speed = Speed(2f)) })
        val r = CaptionAnchor.reflow(fast).texts.single()
        assertEquals(2_000_000L, r.startUs)
        assertEquals(1_000_000L, r.durationUs)
        assertEquals(500_000L, r.words[1].startUs)
    }

    @Test fun splitKeepsCaptionsOnBothHalves() {
        var p = Project(clips = listOf(clip()))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(1_000_000, 1_000_000), caption(7_000_000, 1_000_000))))
        val split = TimelineMath.split(p, 5_000_000)!!
        val r = CaptionAnchor.reflow(split)
        assertEquals(listOf(1_000_000L, 7_000_000L), r.texts.map { it.startUs })
        assertNotEquals(r.texts[0].anchorClipId, r.texts[1].anchorClipId)
    }

    @Test fun deletedClipTakesItsCaptions() {
        var p = Project(clips = listOf(clip(5_000_000, "file:///a.mp4"), clip(5_000_000, "file:///b.mp4")))
        p = p.copy(texts = CaptionAnchor.anchor(p, listOf(caption(1_000_000, 1_000_000), caption(6_000_000, 1_000_000))))
        val r = CaptionAnchor.reflow(p.copy(clips = p.clips.drop(1)))
        assertEquals(1, r.texts.size)
        assertEquals(1_000_000L, r.texts[0].startUs)
    }

    @Test fun manualTextIsNeverMoved() {
        val p = Project(clips = listOf(clip()), texts = listOf(text(3_000_000, 1_000_000)))
        assertEquals(p, CaptionAnchor.reflow(p))
    }
}
