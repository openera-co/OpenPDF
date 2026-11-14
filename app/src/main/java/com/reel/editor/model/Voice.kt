package com.reel.editor.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A voice effect: an optional pitch shift (semitones) plus a native character effect id. */
data class VoiceEffectDef(val id: String, val name: String, val semitones: Float, val nativeId: Int)

object VoiceEffects {
    val all = listOf(
        VoiceEffectDef("chipmunk", "Chipmunk", 7f, 0),
        VoiceEffectDef("helium", "Helium", 12f, 0),
        VoiceEffectDef("deep", "Deep", -5f, 0),
        VoiceEffectDef("monster", "Monster", -7f, 13),
        VoiceEffectDef("robot", "Robot", 0f, 1),
        VoiceEffectDef("alien", "Alien", 4f, 2),
        VoiceEffectDef("phone", "Phone", 0f, 3),
        VoiceEffectDef("megaphone", "Megaphone", 0f, 4),
        VoiceEffectDef("radio", "Radio", 0f, 5),
        VoiceEffectDef("echo", "Echo", 0f, 6),
        VoiceEffectDef("cave", "Cave", 0f, 7),
        VoiceEffectDef("hall", "Hall", 0f, 8),
        VoiceEffectDef("room", "Room", 0f, 9),
        VoiceEffectDef("underwater", "Underwater", 0f, 10),
        VoiceEffectDef("lofi", "Lo-fi", 0f, 11),
        VoiceEffectDef("vibrato", "Vibrato", 0f, 12),
    )
    private val byId = all.associateBy { it.id }
    fun get(id: String?): VoiceEffectDef? = id?.let { byId[it] }

    /** Pitch factor for Sonic, or 1 when the effect does not shift pitch. */
    fun pitchFactor(id: String?): Float = get(id)?.semitones?.let { 2f.pow(it / 12f) } ?: 1f
}

/**
 * Auto-duck: where speech is, in timeline time, and the music gain around it.
 * Levels come from the waveform analysis (peak per bucket, 0..1).
 */
object Ducking {
    const val LEAD_US = 120_000L     // start lowering a little before speech
    const val ATTACK_US = 150_000L
    const val RELEASE_US = 450_000L
    const val BRIDGE_US = 600_000L   // pauses shorter than this stay ducked

    /** Speech stretches in source time from a level track sampled [perSec] times per second. */
    fun activeInSource(levels: FloatArray, perSec: Int): List<LongRange> {
        if (levels.isEmpty()) return emptyList()
        val sorted = levels.sorted()
        val loud = sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.size - 1)]
        val threshold = max(0.03f, loud * 0.22f)
        val stepUs = 1_000_000L / perSec
        val out = ArrayList<LongRange>()
        var start = -1L
        for ((i, v) in levels.withIndex()) {
            val t = i * stepUs
            if (v >= threshold) { if (start < 0) start = t }
            else if (start >= 0) { out += start until t; start = -1 }
        }
        if (start >= 0) out += start until levels.size * stepUs
        return out
    }

    /** Joins stretches separated by short pauses and sorts them. */
    fun merge(ranges: List<LongRange>, bridgeUs: Long = BRIDGE_US): List<LongRange> {
        val s = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
        val out = ArrayList<LongRange>()
        for (r in s) {
            val last = out.lastOrNull()
            if (last != null && r.first - last.last <= bridgeUs) out[out.lastIndex] = last.first..max(last.last, r.last)
            else out += r
        }
        return out
    }

    /** Speech on the timeline from clips (with speed and trim) and voiceovers. */
    fun speechOnTimeline(project: Project, levels: (String) -> FloatArray?, perSec: Int): List<LongRange> {
        val ranges = ArrayList<LongRange>()
        for (pc in TimelineMath.place(project.clips)) {
            val c = pc.clip
            if (c.kind != ClipKind.VIDEO || !c.hasAudio || c.muted || c.volume <= 0f) continue
            val lv = levels(c.uri) ?: continue
            for (r in activeInSource(lv, perSec)) {
                val a = max(r.first, c.trimStartUs)
                val b = min(r.last, c.trimEndUs)
                if (b <= a) continue
                val ta = pc.startUs + SpeedMath.mediaToTimeline(c.speed, c.trimmedDurationUs, a - c.trimStartUs)
                val tb = pc.startUs + SpeedMath.mediaToTimeline(c.speed, c.trimmedDurationUs, b - c.trimStartUs)
                ranges += ta..tb
            }
        }
        for (a in project.audio) {
            if (a.kind != AudioKind.VOICEOVER || a.volume <= 0f) continue
            val lv = levels(a.uri) ?: continue
            for (r in activeInSource(lv, perSec)) {
                val x = max(r.first, a.trimStartUs)
                val y = min(r.last, a.trimEndUs)
                if (y > x) ranges += (a.startUs + x - a.trimStartUs)..(a.startUs + y - a.trimStartUs)
            }
        }
        return merge(ranges)
    }

    /** Music gain at timeline time [t]: [depthDb] under speech, smooth ramps around it. */
    fun gainAt(speech: List<LongRange>, t: Long, depthDb: Float): Float {
        val low = 10f.pow(depthDb / 20f)
        var g = 1f
        for (r in speech) {
            val start = r.first - LEAD_US
            val end = r.last
            val v = when {
                t in start..end -> low
                t < start && start - t < ATTACK_US -> low + (1f - low) * ((start - t).toFloat() / ATTACK_US)
                t > end && t - end < RELEASE_US -> low + (1f - low) * ((t - end).toFloat() / RELEASE_US)
                else -> 1f
            }
            g = min(g, v)
            if (r.first - LEAD_US - ATTACK_US > t) break
        }
        return g
    }
}
