package com.reel.editor.model

import kotlin.math.abs

/** Beat markers on music: automatic (detected) and custom (placed by hand), plus cut syncing. */
object Beats {
    const val REMOVE_TOLERANCE_US = 150_000L

    /** Version of the native beat tracker; bump when detection changes so saved beats refresh. */
    const val DETECTOR_VERSION = 2

    /** Source time of one marker to timeline time for this item, or null if it is trimmed away. */
    private fun AudioItem.toTimeline(sourceUs: Long): Long? =
        if (sourceUs < trimStartUs || sourceUs > trimEndUs) null else startUs + (sourceUs - trimStartUs)

    /** Detected beats that are shown, in source time. */
    fun AudioItem.visibleAutoBeats(): List<Long> =
        if (!autoBeats) emptyList() else beatsUs.filterIndexed { i, _ -> i % beatEvery.coerceAtLeast(1) == 0 }

    /** All markers of one item on the timeline, sorted and without duplicates. */
    fun markers(a: AudioItem): List<Long> =
        (a.visibleAutoBeats() + a.markersUs).mapNotNull { a.toTimeline(it) }.distinct().sorted()

    /** Every marker in the project on the timeline. */
    fun markers(p: Project): List<Long> = p.audio.flatMap { markers(it) }.distinct().sorted()

    /** Adds a custom marker at a timeline time, if it falls inside the item. */
    fun add(a: AudioItem, timelineUs: Long): AudioItem {
        if (timelineUs < a.startUs || timelineUs > a.endUs) return a
        val src = a.trimStartUs + (timelineUs - a.startUs)
        if (a.markersUs.any { abs(it - src) < 20_000 }) return a
        return a.copy(markersUs = (a.markersUs + src).sorted())
    }

    /**
     * Removes the marker nearest a timeline time (within [REMOVE_TOLERANCE_US]). Removing a
     * detected beat turns the shown beats into custom markers first, so every point can be edited.
     */
    fun removeNear(a: AudioItem, timelineUs: Long): AudioItem {
        val src = a.trimStartUs + (timelineUs - a.startUs)
        val customHit = a.markersUs.minByOrNull { abs(it - src) }?.takeIf { abs(it - src) <= REMOVE_TOLERANCE_US }
        val autoHit = a.visibleAutoBeats().minByOrNull { abs(it - src) }?.takeIf { abs(it - src) <= REMOVE_TOLERANCE_US }
        return when {
            customHit != null && (autoHit == null || abs(customHit - src) <= abs(autoHit - src)) -> a.copy(markersUs = a.markersUs - customHit)
            autoHit != null -> a.copy(autoBeats = false, markersUs = (a.markersUs + a.visibleAutoBeats() - autoHit).distinct().sorted())
            else -> a
        }
    }

    fun clear(a: AudioItem): AudioItem = a.copy(autoBeats = false, markersUs = emptyList())

    /** Source media a clip needs to fill [timelineUs] at its speed (binary search for speed curves). */
    private fun mediaFor(c: Clip, timelineUs: Long, maxMediaUs: Long): Long {
        if (c.speed.curve.isNullOrEmpty()) return (timelineUs * c.speed.constant.toDouble()).toLong().coerceAtMost(maxMediaUs)
        var lo = 0L
        var hi = maxMediaUs
        repeat(40) {
            val mid = (lo + hi) / 2
            if (SpeedMath.timelineDurationUs(c.speed, mid) < timelineUs) lo = mid else hi = mid
        }
        return hi
    }

    /**
     * Moves each cut to the marker nearest its current position that the clip has footage for.
     * Clips keep their start in the source; only their ends change. Returns the project unchanged
     * when there are no markers.
     */
    fun syncCuts(p: Project, markers: List<Long> = markers(p)): Project {
        if (markers.isEmpty() || p.clips.isEmpty()) return p
        var pos = 0L
        val out = ArrayList<Clip>(p.clips.size)
        for (c in p.clips) {
            val cur = c.timelineDurationUs
            val maxDur = if (c.kind == ClipKind.PHOTO) Long.MAX_VALUE / 4 else SpeedMath.timelineDurationUs(c.speed, c.sourceDurationUs - c.trimStartUs)
            val target = markers.filter { it - pos >= MIN_CLIP_US && it - pos <= maxDur }.minByOrNull { abs(it - (pos + cur)) }
            if (target == null) {
                out += c
                pos += cur
                continue
            }
            val dur = target - pos
            val synced = if (c.kind == ClipKind.PHOTO) {
                val end = c.trimStartUs + dur
                c.copy(trimEndUs = end, sourceDurationUs = maxOf(c.sourceDurationUs, end))
            } else {
                c.copy(trimEndUs = c.trimStartUs + mediaFor(c, dur, c.sourceDurationUs - c.trimStartUs))
            }
            out += synced
            pos += synced.timelineDurationUs
        }
        return p.copy(clips = out)
    }
}
