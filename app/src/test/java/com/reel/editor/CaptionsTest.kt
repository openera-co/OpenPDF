package com.reel.editor

import com.reel.editor.model.CaptionGrouping
import com.reel.editor.model.TextStyle
import com.reel.editor.model.TimedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionsTest {
    private fun words(vararg w: Pair<String, Long>): List<TimedWord> =
        w.map { (t, s) -> TimedWord(t, s * 1000, s * 1000 + 250_000) }

    @Test fun breaksAtSentenceEndsAndKeepsOrder() {
        val ws = words("Ask" to 0, "not" to 300, "what" to 600, "you" to 900, "can." to 1200, "Do" to 1500, "it" to 1800)
        val lines = CaptionGrouping.group(ws)
        assertEquals(listOf("Ask not what you can.", "Do it"), lines.map { it.text })
        assertTrue(lines[0].endUs <= lines[1].startUs)
        assertEquals(ws.size, lines.sumOf { it.words.size })
    }

    @Test fun respectsLengthWordCountAndPauses() {
        val long = (0 until 30).map { TimedWord("word$it", it * 200_000L, it * 200_000L + 150_000) }
        val lines = CaptionGrouping.group(long)
        assertTrue(lines.all { it.text.length <= CaptionGrouping.MAX_CHARS && it.words.size <= CaptionGrouping.MAX_WORDS })
        assertTrue(lines.all { it.endUs - it.startUs <= CaptionGrouping.MAX_US + 300_000 })
        // A long pause starts a new caption.
        val paused = listOf(TimedWord("hello", 0, 300_000), TimedWord("again", 2_000_000, 2_300_000))
        assertEquals(2, CaptionGrouping.group(paused).size)
    }

    @Test fun layerWordsAreRelativeAndMatchText() {
        val line = CaptionGrouping.group(words("Fellow" to 1000, "Americans" to 1400)).single()
        val layer = CaptionGrouping.toLayer(line, TextStyle(), 0.8f)
        assertTrue(layer.isCaption)
        assertEquals(1_000_000, layer.startUs)
        assertEquals(0L, layer.words[0].startUs)
        assertEquals(400_000L, layer.words[1].startUs)
        var offset = 0
        for (w in layer.words) {
            val at = layer.text.indexOf(w.text, offset)
            assertTrue("word ${w.text} not in text", at >= 0)
            offset = at + w.text.length
        }
        assertTrue(layer.words.all { it.endUs <= layer.durationUs })
    }

    @Test fun emptyAndBlankInput() {
        assertTrue(CaptionGrouping.group(emptyList()).isEmpty())
        assertTrue(CaptionGrouping.group(listOf(TimedWord(" ", 0, 10))).isEmpty())
    }
}

class CaptionEditTest {
    private fun cap(id: String, start: Long, text: String, wordUs: Long = 400_000): com.reel.editor.model.TextLayer {
        val words = text.split(" ").mapIndexed { i, w -> com.reel.editor.model.WordTiming(w, i * wordUs, (i + 1) * wordUs) }
        return com.reel.editor.model.TextLayer(id = id, text = text, startUs = start, durationUs = words.size * wordUs, isCaption = true, words = words)
    }

    @org.junit.Test fun mergeJoinsTextAndTimings() {
        val t = listOf(cap("a", 0, "hello there"), cap("b", 1_000_000, "general kenobi"))
        val m = com.reel.editor.model.CaptionEdit.mergeWithNext(t, "a")
        org.junit.Assert.assertEquals(1, m.size)
        org.junit.Assert.assertEquals("hello there general kenobi", m[0].text)
        org.junit.Assert.assertEquals(1_800_000L, m[0].durationUs)
        org.junit.Assert.assertEquals(1_000_000L, m[0].words[2].startUs)
    }

    @org.junit.Test fun mergeOfLastLineDoesNothing() {
        val t = listOf(cap("a", 0, "one two"))
        org.junit.Assert.assertEquals(t, com.reel.editor.model.CaptionEdit.mergeWithNext(t, "a"))
    }

    @org.junit.Test fun splitAtTheMiddleWord() {
        val t = listOf(cap("a", 2_000_000, "one two three four"))
        val s = com.reel.editor.model.CaptionEdit.split(t, "a")
        org.junit.Assert.assertEquals(listOf("one two", "three four"), s.map { it.text })
        org.junit.Assert.assertEquals(2_800_000L, s[1].startUs)
        org.junit.Assert.assertEquals(800_000L, s[0].durationUs)
        org.junit.Assert.assertEquals(0L, s[1].words[0].startUs)
        org.junit.Assert.assertNotEquals(s[0].id, s[1].id)
    }

    @org.junit.Test fun nudgeNeverGoesBeforeZero() {
        val t = listOf(cap("a", 100_000, "hi"))
        org.junit.Assert.assertEquals(0L, com.reel.editor.model.CaptionEdit.nudge(t, "a", -500_000)[0].startUs)
        org.junit.Assert.assertEquals(200_000L, com.reel.editor.model.CaptionEdit.nudge(t, "a", 100_000)[0].startUs)
    }
}
