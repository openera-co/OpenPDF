package com.reel.editor.model

/**
 * Lanes for layers on the timeline. Each kind of layer has its own group of lanes above the video
 * track; overlapping items of one kind stack into extra lanes instead of drawing over each other.
 * Items keep their lane by creation order, so dragging one does not reshuffle the others.
 */
object TrackLayout {
    /** Top to bottom above the video track. */
    enum class Group { EFFECTS, TEXT, STICKERS, OVERLAYS }

    data class Item(val key: String, val group: Group, val startUs: Long, val endUs: Long)

    data class Result(val rowOf: Map<String, Int>, val rows: Int, val groupRows: Map<Group, IntRange>)

    fun assign(items: List<Item>): Result {
        val rowOf = HashMap<String, Int>()
        val groupRows = LinkedHashMap<Group, IntRange>()
        var base = 0
        for (g in Group.entries) {
            val lanes = ArrayList<MutableList<Item>>()
            for (it in items.filter { it.group == g }) {
                val lane = lanes.indexOfFirst { l -> l.none { o -> it.startUs < o.endUs && o.startUs < it.endUs } }
                val idx = if (lane >= 0) lane else { lanes.add(mutableListOf()); lanes.lastIndex }
                lanes[idx] += it
                rowOf[it.key] = base + idx
            }
            if (lanes.isNotEmpty()) groupRows[g] = base until base + lanes.size
            base += lanes.size
        }
        return Result(rowOf, base, groupRows)
    }

    /** Layers and their timeline spans, in creation order. */
    fun itemsOf(p: Project): List<Item> = buildList {
        p.effects.forEach { add(Item("fx:${it.id}", Group.EFFECTS, it.startUs, it.startUs + it.durationUs)) }
        p.filters.forEach { add(Item("filter:${it.id}", Group.EFFECTS, it.startUs, it.startUs + it.durationUs)) }
        p.adjustments.forEach { add(Item("adjust:${it.id}", Group.EFFECTS, it.startUs, it.startUs + it.durationUs)) }
        p.texts.forEach { add(Item("text:${it.id}", Group.TEXT, it.startUs, it.startUs + it.durationUs)) }
        p.stickers.forEach { add(Item("sticker:${it.id}", Group.STICKERS, it.startUs, it.startUs + it.durationUs)) }
        p.shapes.forEach { add(Item("shape:${it.id}", Group.STICKERS, it.startUs, it.startUs + it.durationUs)) }
        p.overlays.forEach { add(Item("overlay:${it.id}", Group.OVERLAYS, it.startUs, it.startUs + it.timelineDurationUs)) }
    }

    /**
     * Keeps every layer inside the video: a layer can't start after the end or run past it, and is
     * never shorter than [minUs]. Layers wholly past the end are pulled back so they stay visible.
     */
    fun clampLayers(p: Project, minUs: Long = 200_000): Project {
        val total = TimelineMath.duration(p)
        if (total <= 0) return p
        fun fit(start: Long, dur: Long): Pair<Long, Long> {
            val d = dur.coerceIn(minOf(minUs, total), total)
            val s = start.coerceIn(0, (total - d).coerceAtLeast(0))
            return s to d
        }
        fun <T> List<T>.fix(get: (T) -> Pair<Long, Long>, set: (T, Long, Long) -> T): List<T> {
            var changed = false
            val out = map { item ->
                val (s, d) = get(item)
                val (ns, nd) = fit(s, d)
                if (ns != s || nd != d) { changed = true; set(item, ns, nd) } else item
            }
            return if (changed) out else this
        }
        return p.copy(
            texts = p.texts.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            stickers = p.stickers.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            shapes = p.shapes.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            effects = p.effects.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            filters = p.filters.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            adjustments = p.adjustments.fix({ it.startUs to it.durationUs }) { t, s, d -> t.copy(startUs = s, durationUs = d) },
            // Overlays keep their source trim: shorten from the end, then slide.
            overlays = p.overlays.fix({ it.startUs to it.timelineDurationUs }) { o, s, d -> o.copy(startUs = s, trimEndUs = o.trimStartUs + d) },
        )
    }
}
