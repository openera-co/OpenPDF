package com.reel.editor

import com.reel.editor.media.SoundFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class SoundFxTest {
    @Test
    fun everySoundRendersCleanly() {
        val ids = HashSet<String>()
        for (s in SoundFx.all) {
            assertTrue("duplicate ${s.id}", ids.add(s.id))
            val x = SoundFx.render(s)
            assertEquals(s.id, (s.seconds * SoundFx.RATE).toInt(), x.size)
            assertTrue("${s.id} has NaN", x.none { it.isNaN() })
            val peak = x.maxOf { abs(it) }
            assertTrue("${s.id} peak $peak", peak in 0.85f..0.91f)
            // Not silent: real energy across the sound, not one spike.
            val rms = sqrt(x.sumOf { (it * it).toDouble() } / x.size)
            assertTrue("${s.id} rms $rms", rms > 0.02)
            // Starts and ends at silence: no click.
            assertTrue("${s.id} starts at ${x[0]}", abs(x[0]) < 0.01f)
            assertTrue("${s.id} ends at ${x.last()}", abs(x.last()) < 0.01f)
        }
        assertTrue(SoundFx.all.size >= 30)
        assertTrue(SoundFx.Category.entries.all { c -> SoundFx.all.count { it.category == c } >= 5 })
    }

    @Test
    fun renderingIsRepeatable() {
        val s = SoundFx.byId("whoosh")!!
        assertTrue(SoundFx.render(s).contentEquals(SoundFx.render(s)))
    }

    @Test
    fun writesAValidWav() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sfxtest").apply { deleteRecursively(); mkdirs() }
        val s = SoundFx.byId("pop")!!
        val f = SoundFx.file(dir, s)
        val b = f.readBytes()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        fun i32(o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        assertEquals(SoundFx.RATE, i32(24))
        assertEquals(b.size - 44, i32(40))
        assertEquals((s.seconds * SoundFx.RATE).toInt() * 2, i32(40))
        // A second call reuses the file.
        val t = f.lastModified()
        assertEquals(t, SoundFx.file(dir, s).lastModified())
    }

    /** Writes every sound to build/sfx for listening and spectrogram checks. */
    @Test
    fun dumpAll() {
        val dir = File("build/sfx").apply { mkdirs() }
        for (s in SoundFx.all) SoundFx.writeWav(File(dir, "${s.id}.wav"), SoundFx.render(s))
    }
}
