package com.reel.editor

import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Project
import com.reel.editor.model.TextTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextTemplatesTest {
    private val system = setOf("sans-bold", "sans", "black", "medium", "condensed", "light", "serif", "serif-italic", "mono", "casual", "cursive", "smallcaps")
    private val bundled = File("src/main/assets/fonts").list()!!.filter { it.endsWith(".ttf") }.map { it.removeSuffix(".ttf") }.toSet()
    private val layers = File("src/main/java/com/reel/editor/fx/Layers.kt").readText()

    private fun animList(name: String): Set<String> =
        Regex("val $name = listOf\\(([^)]*)\\)").find(layers)!!.groupValues[1].split(",").map { it.trim().trim('"') }.toSet()

    @Test fun everyTemplateUsesRealFontsShapesAndAnimations() {
        val inAnims = animList("inAnims"); val outAnims = animList("outAnims"); val loops = animList("loopAnims")
        val shapes = Regex("\"([a-z]+)\"(?:, \"([a-z]+)\")? ->").findAll(layers.substringAfter("private fun shapePath")).flatMap { it.groupValues.drop(1) }.filter { it.isNotEmpty() }.toSet()
        assertTrue(TextTemplates.all.size >= 30)
        assertEquals(TextTemplates.all.size, TextTemplates.all.map { it.id }.toSet().size)
        for (t in TextTemplates.all) {
            assertTrue(t.id, t.parts.isNotEmpty())
            for (p in t.parts) {
                assertTrue("${t.id} font ${p.style.font}", p.style.font in system || p.style.font in bundled)
                p.animIn?.let { assertTrue("${t.id} in ${it.id}", it.id in inAnims) }
                p.animOut?.let { assertTrue("${t.id} out ${it.id}", it.id in outAnims) }
                p.loop?.let { assertTrue("${t.id} loop ${it.id}", it.id in loops) }
                assertTrue(p.x in 0f..1f && p.y in 0f..1f)
            }
            for (b in t.boxes) {
                assertTrue("${t.id} shape ${b.shape}", b.shape in shapes)
                b.animIn?.let { assertTrue("${t.id} box in ${it.id}", it.id in inAnims) }
            }
        }
    }

    @Test fun applyAddsEditableLayersAtThePlayhead() {
        val clip = Clip(uri = "file:///a.mp4", kind = ClipKind.VIDEO, sourceDurationUs = 10_000_000, sourceWidth = 1080, sourceHeight = 1920)
        val t = TextTemplates.all.first { it.id == "l_name" }
        val (p, id) = TextTemplates.apply(Project(clips = listOf(clip)), t, 2_000_000, 8_000_000)
        assertEquals(2, p.texts.size); assertEquals(2, p.shapes.size)
        assertEquals(id, p.texts.first().id)
        assertEquals(2_000_000L, p.texts.first().startUs - 150_000)
        // Every layer ends together.
        val ends = p.texts.map { it.startUs + it.durationUs } + p.shapes.map { it.startUs + it.durationUs }
        assertEquals(1, ends.toSet().size)
        // A short timeline shortens the template.
        val (q, _) = TextTemplates.apply(Project(clips = listOf(clip)), t, 0, 1_500_000)
        assertTrue(q.texts.all { it.startUs + it.durationUs <= 1_500_000 })
    }
}
