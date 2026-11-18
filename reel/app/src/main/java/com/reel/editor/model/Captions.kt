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

/** Line-by-line caption edits. Pure: they take and return the project's text layers. */
object CaptionEdit {
    private fun sorted(texts: List<TextLayer>) = texts.filter { it.isCaption }.sortedBy { it.startUs }

    /** Joins a caption with the one after it. */
    fun mergeWithNext(texts: List<TextLayer>, id: String): List<TextLayer> {
        val caps = sorted(texts)
        val i = caps.indexOfFirst { it.id == id }
        if (i < 0 || i == caps.lastIndex) return texts
        val a = caps[i]; val b = caps[i + 1]
        val shift = b.startUs - a.startUs
        val sameSource = a.anchorClipId != null && a.anchorUri == b.anchorUri
        val merged = a.copy(
            text = (a.text.trimEnd() + " " + b.text.trimStart()).trim(),
            durationUs = (b.startUs + b.durationUs) - a.startUs,
            words = a.words + b.words.map { it.copy(startUs = it.startUs + shift, endUs = it.endUs + shift) },
            anchorClipId = if (sameSource) a.anchorClipId else null,
            anchorMediaEndUs = if (sameSource) b.anchorMediaEndUs else a.anchorMediaEndUs,
        )
        return texts.filter { it.id != b.id }.map { if (it.id == a.id) merged else it }
    }

    /** Splits a caption into two halves at the word nearest its middle. */
    fun split(texts: List<TextLayer>, id: String): List<TextLayer> {
        val c = texts.firstOrNull { it.id == id && it.isCaption } ?: return texts
        val parts = c.text.trim().split(Regex("\\s+"))
        if (parts.size < 2) return texts
        val cut = parts.size / 2
        // Where the second half starts, from word timings when there are any.
        val atUs = c.words.getOrNull(cut)?.startUs?.takeIf { c.words.size == parts.size } ?: (c.durationUs * cut / parts.size)
        if (atUs <= 0 || atUs >= c.durationUs) return texts
        val mediaCut = c.anchorMediaStartUs + atUs
        val first = c.copy(
            text = parts.take(cut).joinToString(" "), durationUs = atUs,
            words = c.words.filter { it.startUs < atUs }.map { it.copy(endUs = minOf(it.endUs, atUs)) },
            anchorMediaEndUs = if (c.anchorClipId != null) mediaCut else c.anchorMediaEndUs,
        )
        val second = c.copy(
            id = newId(), text = parts.drop(cut).joinToString(" "), startUs = c.startUs + atUs, durationUs = c.durationUs - atUs,
            words = c.words.filter { it.startUs >= atUs }.map { it.copy(startUs = it.startUs - atUs, endUs = it.endUs - atUs) },
            anchorMediaStartUs = if (c.anchorClipId != null) mediaCut else c.anchorMediaStartUs,
        )
        return texts.flatMap { if (it.id == id) listOf(first, second) else listOf(it) }
    }

    /** Moves a caption earlier or later; it keeps following its speech, shifted by the same amount. */
    fun nudge(texts: List<TextLayer>, id: String, deltaUs: Long): List<TextLayer> = texts.map { c ->
        if (c.id != id) c else {
            val d = maxOf(deltaUs, -c.startUs)
            c.copy(startUs = c.startUs + d, anchorMediaStartUs = c.anchorMediaStartUs + d, anchorMediaEndUs = c.anchorMediaEndUs + d)
        }
    }
}
