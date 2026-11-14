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
