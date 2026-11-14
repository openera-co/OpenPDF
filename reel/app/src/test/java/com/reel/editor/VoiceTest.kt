package com.reel.editor

import com.reel.editor.model.AudioItem
import com.reel.editor.model.AudioKind
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.Ducking
import com.reel.editor.model.Project
import com.reel.editor.model.Speed
import com.reel.editor.model.VoiceEffects
import com.reel.editor.model.VoiceFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VoiceTest {
    @Test fun effectCatalogIsConsistent() {
        assertEquals(16, VoiceEffects.all.size)
        assertEquals(VoiceEffects.all.size, VoiceEffects.all.map { it.id }.toSet().size)
        assertEquals(1f, VoiceEffects.pitchFactor(null), 0f)
        assertEquals(1f, VoiceEffects.pitchFactor("robot"), 0f)
        assertEquals(2f, VoiceEffects.pitchFactor("helium"), 1e-4f)
        assertTrue(VoiceEffects.pitchFactor("deep") < 1f)
        assertTrue(VoiceEffects.all.all { it.nativeId in 0..13 })
    }

    @Test fun identityMeansUntouched() {
        assertTrue(VoiceFx().isIdentity)
        assertFalse(VoiceFx(enhance = 0.5f).isIdentity)
        assertFalse(VoiceFx(effect = "echo").isIdentity)
        assertFalse(VoiceFx(normalize = true).isIdentity)
    }

    /** 40 levels per second: silence, then speech from 1.0 s to 2.0 s. */
    private fun levels(seconds: Int, speech: List<Pair<Float, Float>>): FloatArray =
        FloatArray(seconds * 40) { i -> val t = i / 40f; if (speech.any { (a, b) -> t >= a && t < b }) 0.5f else 0.01f }

    @Test fun findsSpeechAndBridgesShortPauses() {
        val r = Ducking.merge(Ducking.activeInSource(levels(5, listOf(1f to 2f, 2.3f to 3f)), 40))
        assertEquals(1, r.size)
        assertEquals(1_000_000L, r[0].first)
        assertTrue(abs(r[0].last - 3_000_000L) <= 25_000)
        val apart = Ducking.merge(Ducking.activeInSource(levels(6, listOf(1f to 2f, 4f to 5f)), 40))
        assertEquals(2, apart.size)
    }

    @Test fun gainDipsUnderSpeechWithSmoothRamps() {
        val speech = listOf(2_000_000L..4_000_000L)
        val low = Ducking.gainAt(speech, 3_000_000, -14f)
        assertEquals(0.1995f, low, 1e-3f)
        assertEquals(1f, Ducking.gainAt(speech, 0, -14f), 0f)
        assertEquals(1f, Ducking.gainAt(speech, 6_000_000, -14f), 0f)
        // Ramps are monotonic and between the two levels.
        val before = (0..20).map { Ducking.gainAt(speech, 2_000_000 - Ducking.LEAD_US - Ducking.ATTACK_US + it * 10_000L, -14f) }
        assertTrue(before.zipWithNext().all { (a, b) -> b <= a + 1e-6f })
        val after = (0..50).map { Ducking.gainAt(speech, 4_000_000 + it * 10_000L, -14f) }
        assertTrue(after.zipWithNext().all { (a, b) -> b >= a - 1e-6f })
        assertTrue(after.all { it in low..1f })
    }

    @Test fun speechMapsThroughClipTrimAndSpeedAndVoiceovers() {
        val clip = Clip(uri = "v", kind = ClipKind.VIDEO, sourceDurationUs = 10_000_000, sourceWidth = 1, sourceHeight = 1,
            trimStartUs = 2_000_000, trimEndUs = 10_000_000, speed = Speed(2f))
        val vo = AudioItem(uri = "vo", title = "VO", kind = AudioKind.VOICEOVER, sourceDurationUs = 3_000_000, startUs = 6_000_000)
        val music = AudioItem(uri = "m", title = "Song", kind = AudioKind.MUSIC, sourceDurationUs = 30_000_000, startUs = 0, duck = true)
        val levelsBy = mapOf(
            "v" to levels(10, listOf(4f to 6f)),   // source 4-6 s -> timeline (4-2)/2 .. (6-2)/2 = 1-2 s
            "vo" to levels(3, listOf(0.5f to 1.5f)), // timeline 6.5-7.5 s
            "m" to levels(30, listOf(0f to 30f)),  // music is never treated as speech
        )
        val s = Ducking.speechOnTimeline(Project(clips = listOf(clip), audio = listOf(vo, music)), { levelsBy[it] }, 40)
        assertEquals(2, s.size)
        assertTrue(abs(s[0].first - 1_000_000) < 30_000 && abs(s[0].last - 2_000_000) < 30_000)
        assertTrue(abs(s[1].first - 6_500_000) < 30_000 && abs(s[1].last - 7_500_000) < 30_000)
        // A muted clip is ignored.
        val muted = Ducking.speechOnTimeline(Project(clips = listOf(clip.copy(muted = true))), { levelsBy[it] }, 40)
        assertTrue(muted.isEmpty())
    }
}
