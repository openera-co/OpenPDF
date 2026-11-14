package com.reel.editor.model

/** A transcribed word placed on the timeline. */
data class TimedWord(val text: String, val startUs: Long, val endUs: Long)

/** One caption on screen: its words, with times on the timeline. */
data class CaptionLine(val startUs: Long, val endUs: Long, val words: List<TimedWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Splits a word stream into readable caption lines. */
object CaptionGrouping {
    const val MAX_CHARS = 32
    const val MAX_WORDS = 7
    const val MAX_US = 3_200_000L
    const val GAP_US = 700_000L
    private const val TAIL_US = 250_000L
    private const val MIN_US = 400_000L

    fun group(words: List<TimedWord>, maxChars: Int = MAX_CHARS): List<CaptionLine> {
        val sorted = words.filter { it.text.isNotBlank() }.sortedBy { it.startUs }
        val lines = ArrayList<MutableList<TimedWord>>()
        var cur = mutableListOf<TimedWord>()
        fun chars(l: List<TimedWord>) = l.sumOf { it.text.length } + (l.size - 1).coerceAtLeast(0)
        for (w in sorted) {
            if (cur.isNotEmpty()) {
                val last = cur.last()
                val gap = w.startUs - last.endUs
                val wouldChars = chars(cur) + 1 + w.text.length
                val span = w.endUs - cur.first().startUs
                val endsSentence = last.text.last() in ".?!…"
                val softBreak = last.text.last() in ",;:" && chars(cur) >= maxChars / 2
                if (gap > GAP_US || wouldChars > maxChars || cur.size >= MAX_WORDS || span > MAX_US || endsSentence || softBreak) {
                    lines += cur
                    cur = mutableListOf()
                }
            }
            cur += w
        }
        if (cur.isNotEmpty()) lines += cur
        return lines.mapIndexed { i, l ->
            val start = l.first().startUs
            val nextStart = lines.getOrNull(i + 1)?.first()?.startUs ?: Long.MAX_VALUE
            // Hold each line a moment after its last word, but never into the next line.
            val end = maxOf(l.last().endUs + TAIL_US, start + MIN_US).coerceAtMost(nextStart).coerceAtLeast(start + 1)
            CaptionLine(start, end, l)
        }
    }

    /** The caption as a text layer with karaoke word timings relative to its start. */
    fun toLayer(line: CaptionLine, style: TextStyle, y: Float): TextLayer = TextLayer(
        text = line.text,
        startUs = line.startUs,
        durationUs = line.endUs - line.startUs,
        style = style,
        transform = Transform(y = y),
        animIn = null,
        animOut = null,
        isCaption = true,
        words = line.words.map { WordTiming(it.text, it.startUs - line.startUs, it.endUs - line.startUs) },
    )
}
