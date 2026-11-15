package com.reel.editor

import com.reel.editor.model.HeadPath
import com.reel.editor.model.HeadPoint
import com.reel.editor.model.Project
import com.reel.editor.model.TrackFlags
import com.reel.editor.model.Transform
import com.reel.editor.model.TextLayer
import com.reel.editor.fx.RenderPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadPathTest {
    @Test fun gapsAreFilledAndEndsHeld() {
        val pts = listOf(HeadPoint(0, 0f, 0f, 0f, false), HeadPoint(1, 0.2f, 0.5f, 0.1f, true), HeadPoint(2, 0f, 0f, 0f, false), HeadPoint(3, 0.6f, 0.5f, 0.1f, true))
        val s = HeadPath.smooth(pts, radius = 0)
        assertEquals(0.2f, s[0].x, 1e-5f)
        assertEquals(0.4f, s[2].x, 1e-5f)
        assertTrue(s.all { it.found })
    }

    @Test fun nothingFoundGivesNothing() {
        assertTrue(HeadPath.smooth(listOf(HeadPoint(0, 0f, 0f, 0f, false))).isEmpty())
    }

    @Test fun centreMapsToClipCentre() {
        val (x, y) = HeadPath.toCanvas(0.5f, 0.5f, Transform(x = 0.3f, y = 0.6f), 16 / 9f, 9 / 16f, false)
        assertEquals(0.3f, x, 1e-5f); assertEquals(0.6f, y, 1e-5f)
    }

    @Test fun landscapeFitInPortraitSpansTheWidth() {
        // A 16:9 picture fitted in 9:16 is full width: its right edge is the canvas's right edge.
        val (x, _) = HeadPath.toCanvas(1f, 0.5f, Transform(), 16 / 9f, 9 / 16f, false)
        assertEquals(1f, x, 1e-5f)
    }

    @Test fun reframeKeepsHeadCentredWithoutShowingEdges() {
        val pts = listOf(HeadPoint(0, 0.3f, 0.4f, 0.2f, true), HeadPoint(1, 0.98f, 0.4f, 0.2f, true))
        val keys = HeadPath.reframe(pts, Transform(), 16 / 9f, 9 / 16f, fill = false)
        // Scale is raised so the picture fills the canvas.
        assertTrue(keys[0].transform.scale >= 3.16f - 0.01f)
        for ((k, p) in keys.zip(pts)) {
            val (cw, _) = HeadPath.contentSize(16 / 9f, 9 / 16f, false)
            val half = cw * k.transform.scale / 2
            assertTrue(k.transform.x - half <= 1e-4f && k.transform.x + half >= 1f - 1e-4f)
            val (hx, _) = HeadPath.toCanvas(p.x, p.y, k.transform, 16 / 9f, 9 / 16f, false)
            if (p.x < 0.9f) assertEquals(0.5f, hx, 1e-3f)
        }
    }

    @Test fun hiddenTracksAreLeftOut() {
        val p = Project(texts = listOf(TextLayer(text = "a", startUs = 0, durationUs = 1)), tracks = TrackFlags(hidden = setOf(TrackFlags.TEXT)))
        assertTrue(RenderPlans.visible(p).texts.isEmpty())
        assertEquals(1, RenderPlans.visible(p.copy(tracks = TrackFlags())).texts.size)
        assertTrue(TrackFlags().toggle(TrackFlags.Kind.LOCK, "text").toggle(TrackFlags.Kind.LOCK, "text").locked.isEmpty())
    }

    @Test fun hiddenEffectsShowFaintlyWhileEditing() {
        val fx = com.reel.editor.model.TimedFx(fx = com.reel.editor.model.FxRef("vhs", intensity = 0.8f), startUs = 0, durationUs = 1)
        val p = Project(effects = listOf(fx), tracks = TrackFlags(hidden = setOf(TrackFlags.EFFECTS)))
        assertTrue(RenderPlans.visible(p).effects.isEmpty())
        assertEquals(0.8f * RenderPlans.GHOST, RenderPlans.ghosted(p).effects.single().fx.intensity, 1e-5f)
        assertEquals(p.copy(tracks = TrackFlags()), RenderPlans.ghosted(p.copy(tracks = TrackFlags())))
    }

    @Test fun simplifyKeepsShapeWithFewerKeys() {
        val keys = (0..100).map { i -> com.reel.editor.model.Keyframe(i * 100_000L, Transform(x = if (i < 50) 0.3f + i * 0.004f else 0.5f, y = 0.5f)) }
        val s = HeadPath.simplify(keys)
        assertTrue("kept ${s.size}", s.size in 3..6)
        assertEquals(0L, s.first().timeUs); assertEquals(10_000_000L, s.last().timeUs)
        // The corner at 5 s survives.
        assertTrue(s.any { kotlin.math.abs(it.timeUs - 5_000_000L) <= 100_000L })
    }
}
