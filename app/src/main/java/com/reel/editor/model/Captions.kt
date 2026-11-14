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

/** Keeps auto captions on the speech they transcribe when clips are trimmed, moved, sped up or deleted. */
object CaptionAnchor {
    /** Ties each unanchored caption to the clip under its start, in source media time. */
    fun anchor(p: Project, layers: List<TextLayer>): List<TextLayer> {
        val placed = TimelineMath.place(p.clips)
        return layers.map { l ->
            if (!l.isCaption || l.anchorClipId != null) return@map l
            val pc = placed.firstOrNull { l.startUs >= it.startUs && l.startUs < it.endUs } ?: return@map l
            val c = pc.clip
            fun media(t: Long) = c.trimStartUs + SpeedMath.timelineToMedia(c.speed, c.trimmedDurationUs, (t - pc.startUs).coerceIn(0, pc.durationUs))
            l.copy(anchorClipId = c.id, anchorUri = c.uri, anchorMediaStartUs = media(l.startUs), anchorMediaEndUs = media(minOf(l.startUs + l.durationUs, pc.endUs)))
        }
    }

    /** Moves anchored captions to where their speech now plays; drops ones whose speech was cut. */
    fun reflow(p: Project): Project {
        if (p.texts.none { it.anchorClipId != null }) return p
        val all = TimelineMath.place(p.clips)
        val placed = all.associateBy { it.clip.id }
        var changed = false
        val out = p.texts.mapNotNull { l ->
            val id = l.anchorClipId ?: return@mapNotNull l
            fun covers(x: Placed) = minOf(l.anchorMediaEndUs, x.clip.trimEndUs) - maxOf(l.anchorMediaStartUs, x.clip.trimStartUs) >= 200_000
            // After a split or duplicate the speech may live in another clip of the same file.
            val pc = placed[id]?.takeIf { covers(it) }
                ?: all.firstOrNull { it.clip.uri == (l.anchorUri ?: placed[id]?.clip?.uri) && covers(it) }
            if (pc == null) { changed = true; return@mapNotNull null }
            val c = pc.clip
            val trimEnd = c.trimStartUs + c.trimmedDurationUs
            // Less than a fifth of a second of the speech left: the caption goes with it.
            val visStart = maxOf(l.anchorMediaStartUs, c.trimStartUs)
            val visEnd = minOf(l.anchorMediaEndUs, trimEnd)
            if (visEnd - visStart < 200_000) { changed = true; return@mapNotNull null }
            fun tl(m: Long) = pc.startUs + SpeedMath.mediaToTimeline(c.speed, c.trimmedDurationUs, (m - c.trimStartUs).coerceIn(0, c.trimmedDurationUs))
            val s = tl(visStart)
            val e = maxOf(tl(visEnd), s + 1)
            val d = e - s
            if (s == l.startUs && d == l.durationUs && c.id == id) return@mapNotNull l
            changed = true
            val k = if (l.durationUs > 0) d.toDouble() / l.durationUs else 1.0
            l.copy(anchorClipId = c.id, startUs = s, durationUs = d, words = l.words.map { w -> w.copy(startUs = (w.startUs * k).toLong(), endUs = (w.endUs * k).toLong()) })
        }
        return if (changed) p.copy(texts = out) else p
    }
}
