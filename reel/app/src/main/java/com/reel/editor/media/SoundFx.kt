package com.reel.editor.media

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Sound effects made by a small synthesizer, so there is nothing to license or download.
 * Each one renders the same way every time (seeded noise) and is written once as a WAV file.
 */
object SoundFx {
    const val RATE = 44_100

    enum class Category(val label: String) { MOVES("Moves"), HITS("Hits"), UI("Taps"), FUN("Fun") }

    class Sfx(val id: String, val name: String, val category: Category, val seconds: Double, internal val gen: Synth.() -> Unit)

    /** Bump when any sound changes, so cached files are rendered again. */
    private const val VERSION = 1

    val all: List<Sfx> = listOf(
        // ------------------------------------------------------------ moves
        Sfx("whoosh", "Whoosh", Category.MOVES, 0.8) { whoosh(0.8, 300.0, 2800.0, 0.5) },
        Sfx("swish", "Swish", Category.MOVES, 0.3) { whoosh(0.3, 900.0, 5000.0, 0.45) },
        Sfx("swooshup", "Swoosh up", Category.MOVES, 0.7) { sweepNoise(0.7, 400.0, 6000.0, rise = true); sweep(0.7, 200.0, 1400.0, 0.12, Wave.SINE, rise = true) },
        Sfx("swooshdown", "Swoosh down", Category.MOVES, 0.7) { sweepNoise(0.7, 6000.0, 400.0, rise = false); sweep(0.7, 1400.0, 200.0, 0.12, Wave.SINE, rise = false) },
        Sfx("riser", "Riser", Category.MOVES, 2.5) { riser(2.5) },
        Sfx("drop", "Drop", Category.MOVES, 1.0) { sweep(1.0, 1400.0, 50.0, 0.8, Wave.SINE, rise = false) },
        Sfx("reverse", "Reverse cymbal", Category.MOVES, 1.6) { reverseCymbal(1.6) },
        // ------------------------------------------------------------ hits
        Sfx("boom", "Boom", Category.HITS, 1.8) { boom(1.8) },
        Sfx("impact", "Impact", Category.HITS, 0.9) { kick(0.0, 1.0, 0.6); noiseBurst(0.0, 0.25, 0.7, 3000.0) },
        Sfx("thud", "Thud", Category.HITS, 0.5) { kick(0.0, 0.8, 0.35, startHz = 140.0, endHz = 55.0); noiseBurst(0.0, 0.08, 0.3, 600.0) },
        Sfx("kick", "Kick", Category.HITS, 0.5) { kick(0.0, 1.0, 0.4) },
        Sfx("snare", "Snare", Category.HITS, 0.4) { snare(0.0, 1.0) },
        Sfx("clap", "Clap", Category.HITS, 0.5) { clap(0.0, 1.0) },
        Sfx("heartbeat", "Heartbeat", Category.HITS, 1.0) { kick(0.0, 0.9, 0.18, 90.0, 45.0); kick(0.22, 0.7, 0.2, 80.0, 40.0) },
        // ------------------------------------------------------------ taps
        Sfx("pop", "Pop", Category.UI, 0.15) { sweep(0.12, 900.0, 180.0, 0.9, Wave.SINE, rise = false, decay = 30.0) },
        Sfx("click", "Click", Category.UI, 0.08) { noiseBurst(0.0, 0.004, 0.8, 8000.0); tone(0.0, 0.03, 3200.0, 0.4, decay = 160.0) },
        Sfx("ding", "Ding", Category.UI, 1.5) { bellTone(0.0, 1320.0, 0.6, 3.0, harmonic = true) },
        Sfx("notify", "Notification", Category.UI, 0.6) { tone(0.0, 0.25, 880.0, 0.5, decay = 9.0); tone(0.12, 0.45, 1320.0, 0.5, decay = 8.0) },
        Sfx("coin", "Coin", Category.UI, 0.6) { tone(0.0, 0.08, 988.0, 0.35, Wave.SQUARE, decay = 0.0); tone(0.08, 0.5, 1319.0, 0.35, Wave.SQUARE, decay = 7.0) },
        Sfx("success", "Success", Category.UI, 0.9) { listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f -> tone(i * 0.08, 0.5, f, 0.35, Wave.TRIANGLE, decay = 6.0) } },
        Sfx("error", "Error", Category.UI, 0.5) { tone(0.0, 0.18, 150.0, 0.35, Wave.SQUARE, decay = 2.0); tone(0.22, 0.22, 140.0, 0.35, Wave.SQUARE, decay = 2.0) },
        Sfx("shutter", "Shutter", Category.UI, 0.25) { noiseBurst(0.0, 0.03, 0.9, 5000.0); noiseBurst(0.07, 0.05, 0.7, 3000.0) },
        Sfx("typing", "Typing", Category.UI, 1.4) { typing(1.4) },
        Sfx("tick", "Tick tock", Category.UI, 2.0) { for (i in 0 until 4) tone(i * 0.5, 0.05, if (i % 2 == 0) 2400.0 else 1800.0, 0.6, decay = 120.0) },
        // ------------------------------------------------------------ fun
        Sfx("laser", "Laser", Category.FUN, 0.35) { sweep(0.3, 2400.0, 180.0, 0.4, Wave.SQUARE, rise = false, decay = 6.0) },
        Sfx("zap", "Zap", Category.FUN, 0.4) { zap(0.4) },
        Sfx("glitch", "Glitch", Category.FUN, 0.5) { glitch(0.5) },
        Sfx("bubble", "Bubbles", Category.FUN, 0.6) { for (i in 0 until 4) sweep(0.07, 300.0 + i * 120, 900.0 + i * 200, 0.5, Wave.SINE, rise = true, decay = 25.0, at = i * 0.13) },
        Sfx("sparkle", "Sparkle", Category.FUN, 1.2) { sparkle(1.2) },
        Sfx("scratch", "Scratch", Category.FUN, 0.6) { scratch(0.6) },
        Sfx("tada", "Ta-da", Category.FUN, 1.6) { tada() },
        Sfx("dramatic", "Dramatic", Category.FUN, 2.6) { dramatic() },
        Sfx("bell", "Bell", Category.FUN, 2.5) { bellTone(0.0, 440.0, 0.6, 1.4, harmonic = false) },
        Sfx("chime", "Chime", Category.FUN, 1.6) { listOf(1046.5, 1318.5, 1568.0).forEachIndexed { i, f -> bellTone(i * 0.11, f, 0.35, 3.5, harmonic = true) } },
        Sfx("applause", "Applause", Category.FUN, 3.0) { applause(3.0) },
        Sfx("wind", "Wind", Category.FUN, 3.0) { wind(3.0) },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Renders a sound to samples in -1..1, peak-normalised. */
    fun render(s: Sfx): FloatArray {
        val syn = Synth(s.seconds, s.id.hashCode())
        s.gen(syn)
        return syn.finish()
    }

    /** The sound as a WAV file in the app's files, written the first time it is asked for. */
    fun file(filesDir: File, s: Sfx): File {
        val dir = File(filesDir, "sfx").apply { mkdirs() }
        val f = File(dir, "${s.id}-v$VERSION.wav")
        if (!f.exists() || f.length() < 44) {
            val tmp = File(dir, "${s.id}.tmp")
            writeWav(tmp, render(s))
            tmp.renameTo(f)
        }
        return f
    }

    fun writeWav(f: File, samples: FloatArray, rate: Int = RATE) {
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            data[i * 2] = (v and 0xff).toByte()
            data[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        RandomAccessFile(f, "rw").use { out ->
            out.setLength(0)
            fun i32(v: Int) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte())) }
            fun i16(v: Int) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())) }
            out.write("RIFF".toByteArray()); i32(36 + data.size); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
            out.write("data".toByteArray()); i32(data.size); out.write(data)
        }
    }

    enum class Wave { SINE, SQUARE, SAW, TRIANGLE }

    class Synth(seconds: Double, seed: Int) {
        val buf = FloatArray((seconds * RATE).toInt().coerceAtLeast(1))
        private val rnd = Random(seed)
        val n get() = buf.size

        fun noise() = rnd.nextFloat() * 2f - 1f
        fun rand() = rnd.nextDouble()

        private fun osc(w: Wave, phase: Double): Double {
            val f = phase - kotlin.math.floor(phase)
            return when (w) {
                Wave.SINE -> sin(2 * PI * f)
                Wave.SQUARE -> if (f < 0.5) 0.8 else -0.8
                Wave.SAW -> 2 * f - 1
                Wave.TRIANGLE -> 1 - 4 * abs(f - 0.5)
            }
        }

        private fun idx(t: Double) = (t * RATE).toInt()

        fun add(i: Int, v: Double) { if (i in buf.indices) buf[i] += v.toFloat() }

        /** A note with a short attack and an exponential decay (per second); decay 0 keeps it flat. */
        fun tone(at: Double, len: Double, hz: Double, amp: Double, wave: Wave = Wave.SINE, decay: Double = 4.0) {
            val s = idx(at); val m = idx(len)
            var ph = 0.0
            for (k in 0 until m) {
                val t = k.toDouble() / RATE
                val env = min(1.0, t / 0.004) * exp(-decay * t) * min(1.0, (len - t) / 0.01)
                add(s + k, osc(wave, ph) * amp * env)
                ph += hz / RATE
            }
        }

        /** A pitch glide between two frequencies (exponential), with a swell or a decay. */
        fun sweep(len: Double, fromHz: Double, toHz: Double, amp: Double, wave: Wave, rise: Boolean, decay: Double = 0.0, at: Double = 0.0) {
            val s = idx(at); val m = idx(len)
            var ph = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val hz = fromHz * (toHz / fromHz).pow(x)
                val t = k.toDouble() / RATE
                val shape = if (decay > 0) exp(-decay * t) else if (rise) x * x else 1 - x
                val env = min(1.0, t / 0.005) * shape * min(1.0, (len - t) / 0.01)
                add(s + k, osc(wave, ph) * amp * env)
                ph += hz / RATE
            }
        }

        /** Noise through a resonant band-pass whose centre moves; [bell] shapes loudness as a hump. */
        fun whoosh(len: Double, lowHz: Double, highHz: Double, amp: Double) {
            val m = idx(len)
            var lp = 0.0; var bp = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val hump = sin(PI * x).pow(1.6)
                val fc = lowHz + (highHz - lowHz) * hump
                val f = 2 * sin(PI * fc / RATE)
                val q = 0.35
                val hp = noise() - lp - q * bp
                bp += f * hp; lp += f * bp
                add(k, bp * amp * hump * 2.2)
            }
        }

        fun sweepNoise(len: Double, fromHz: Double, toHz: Double, rise: Boolean) {
            val m = idx(len)
            var lp = 0.0; var bp = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val fc = fromHz * (toHz / fromHz).pow(x)
                val f = 2 * sin(PI * min(fc, RATE / 6.0) / RATE)
                val hp = noise() - lp - 0.4 * bp
                bp += f * hp; lp += f * bp
                val env = (if (rise) x.pow(1.5) else (1 - x).pow(1.5)) * min(1.0, x * 20) * min(1.0, (1 - x) * 30)
                add(k, bp * env * 1.6)
            }
        }

        fun noiseBurst(at: Double, len: Double, amp: Double, cutoffHz: Double) {
            val s = idx(at); val m = idx(len)
            val a = 1 - exp(-2 * PI * cutoffHz / RATE)
            var lp = 0.0
            for (k in 0 until m) {
                lp += a * (noise() - lp)
                val env = exp(-5.0 * k / m) * min(1.0, (m - k).toDouble() / (RATE * 0.004))
                add(s + k, lp * amp * env * 2)
            }
        }

        fun kick(at: Double, amp: Double, len: Double, startHz: Double = 160.0, endHz: Double = 45.0) {
            val s = idx(at); val m = idx(len)
            var ph = 0.0
            for (k in 0 until m) {
                val t = k.toDouble() / RATE
                val hz = endHz + (startHz - endHz) * exp(-t * 30)
                val env = exp(-t * 6.0 / len) * min(1.0, t / 0.002) * min(1.0, (len - t) / 0.02)
                add(s + k, tanh(sin(2 * PI * ph) * 1.8) * amp * env)
                ph += hz / RATE
            }
        }

        fun snare(at: Double, amp: Double) {
            tone(at, 0.15, 190.0, 0.5 * amp, decay = 25.0)
            val s = idx(at); val m = idx(0.25)
            var prev = 0.0
            for (k in 0 until m) {
                val x = noise().toDouble()
                val hp = x - prev; prev = x
                add(s + k, hp * 0.5 * amp * exp(-k.toDouble() / RATE * 18))
            }
        }

        fun clap(at: Double, amp: Double) {
            for (j in 0 until 4) {
                val s = idx(at + j * 0.011)
                val m = idx(if (j == 3) 0.25 else 0.012)
                var lp = 0.0; var bp = 0.0
                val f = 2 * sin(PI * 1400.0 / RATE)
                for (k in 0 until m) {
                    val hp = noise() - lp - 0.5 * bp
                    bp += f * hp; lp += f * bp
                    add(s + k, bp * amp * exp(-k.toDouble() / RATE * (if (j == 3) 16 else 200)) * 1.5)
                }
            }
        }

        /** Struck metal: harmonic partials for a ding, inharmonic ones for a church-like bell. */
        fun bellTone(at: Double, hz: Double, amp: Double, decay: Double, harmonic: Boolean) {
            val ratios = if (harmonic) doubleArrayOf(1.0, 2.0, 3.0, 4.2) else doubleArrayOf(0.5, 1.0, 1.19, 1.56, 2.0, 2.74, 3.0)
            val gains = if (harmonic) doubleArrayOf(1.0, 0.35, 0.12, 0.06) else doubleArrayOf(0.4, 1.0, 0.5, 0.35, 0.3, 0.2, 0.12)
            val len = (n.toDouble() / RATE) - at
            for (i in ratios.indices) tone(at, len, hz * ratios[i], amp * gains[i], decay = decay * (1 + i * 0.35))
        }

        fun boom(len: Double) {
            kick(0.0, 1.0, len, startHz = 110.0, endHz = 32.0)
            val m = idx(len)
            val a = 1 - exp(-2 * PI * 180.0 / RATE)
            var lp = 0.0
            for (k in 0 until m) {
                lp += a * (noise() - lp)
                add(k, lp * 3.0 * exp(-k.toDouble() / RATE * 2.5))
            }
        }

        fun riser(len: Double) {
            sweepNoise(len, 300.0, 9000.0, rise = true)
            var ph1 = 0.0; var ph2 = 0.0
            val m = idx(len)
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val hz = 120 * (16.0).pow(x)
                val env = x.pow(2.0) * min(1.0, (1 - x) * 60)
                add(k, (osc(Wave.SAW, ph1) + osc(Wave.SAW, ph2)) * 0.12 * env)
                ph1 += hz / RATE; ph2 += hz * 1.007 / RATE
            }
        }

        fun reverseCymbal(len: Double) {
            val m = idx(len)
            var prev = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val v = noise().toDouble()
                val hp = v - prev; prev = v
                add(k, hp * 0.6 * x.pow(3.0) * min(1.0, (1 - x) * 80))
            }
        }

        fun zap(len: Double) {
            val m = idx(len)
            var ph = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val mod = sin(2 * PI * k * 55.0 / RATE) * 600 * (1 - x)
                val hz = 1800 * (1 - x) + 200 + mod
                add(k, osc(Wave.SQUARE, ph) * 0.35 * (1 - x) + noise() * 0.1 * (1 - x))
                ph += hz / RATE
            }
        }

        fun glitch(len: Double) {
            var t = 0.0
            while (t < len - 0.02) {
                val seg = 0.02 + rand() * 0.06
                when ((rand() * 3).toInt()) {
                    0 -> tone(t, seg, 200 + rand() * 1800, 0.35, Wave.SQUARE, decay = 0.0)
                    1 -> noiseBurst(t, seg, 0.6, 2000 + rand() * 6000)
                    else -> {}
                }
                t += seg
            }
        }

        fun typing(len: Double) {
            var t = 0.02
            while (t < len - 0.08) {
                noiseBurst(t, 0.006, 0.7, 6000.0)
                tone(t, 0.03, 2000 + rand() * 600, 0.15, decay = 150.0)
                t += 0.07 + rand() * 0.12
            }
        }

        fun sparkle(len: Double) {
            for (i in 0 until 26) {
                val at = rand() * (len - 0.3)
                tone(at, 0.25, 2500 + rand() * 3500, 0.12, decay = 18.0)
            }
        }

        fun scratch(len: Double) {
            val m = idx(len)
            var ph = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val wob = sin(2 * PI * x * 2.5)
                val hz = 300 + 500 * wob
                val v = osc(Wave.SAW, ph) * 0.4 + noise() * 0.15
                add(k, v * abs(wob) * min(1.0, (1 - x) * 20))
                ph += hz / RATE
            }
        }

        fun tada() {
            tone(0.0, 0.12, 523.25, 0.3, Wave.SAW, decay = 3.0)
            for (f in doubleArrayOf(523.25, 659.25, 783.99, 1046.5)) tone(0.16, 1.4, f, 0.18, Wave.SAW, decay = 1.6)
            lowpass(3500.0)
        }

        fun dramatic() {
            val notes = doubleArrayOf(146.83, 146.83, 155.56)
            val starts = doubleArrayOf(0.0, 0.35, 0.7)
            val lens = doubleArrayOf(0.28, 0.28, 1.8)
            for (i in 0 until 3) for (d in doubleArrayOf(1.0, 1.006, 2.0)) tone(starts[i], lens[i], notes[i] * d, 0.18, Wave.SAW, decay = if (i == 2) 0.9 else 2.0)
            lowpass(1800.0)
        }

        fun applause(len: Double) {
            val claps = (len * 60).toInt()
            for (i in 0 until claps) {
                val x = rand()
                val at = x * (len - 0.3)
                val swell = sin(PI * (at / len)).pow(0.5)
                if (rand() < swell) clap(at, 0.25 + rand() * 0.2)
            }
        }

        fun wind(len: Double) {
            val m = idx(len)
            var lp = 0.0; var bp = 0.0
            for (k in 0 until m) {
                val x = k.toDouble() / m
                val gust = 0.5 + 0.5 * sin(2 * PI * x * 1.3 + sin(2 * PI * x * 0.7) * 2)
                val fc = 300 + 900 * gust
                val f = 2 * sin(PI * fc / RATE)
                val hp = noise() - lp - 0.15 * bp
                bp += f * hp; lp += f * bp
                add(k, bp * 0.5 * (0.3 + 0.7 * gust) * min(1.0, x * 6) * min(1.0, (1 - x) * 6))
            }
        }

        fun lowpass(hz: Double) {
            val a = 1 - exp(-2 * PI * hz / RATE)
            var y = 0.0
            for (i in buf.indices) { y += a * (buf[i] - y); buf[i] = y.toFloat() }
        }

        fun finish(): FloatArray {
            // short fade at both ends so no sound starts or stops with a click
            val fin = min(buf.size / 4, RATE / 1000)
            for (i in 0 until fin) buf[i] *= i.toFloat() / fin
            val fout = min(buf.size / 4, RATE / 200)
            for (i in 0 until fout) buf[buf.size - 1 - i] *= i.toFloat() / fout
            var peak = 0f
            for (v in buf) peak = max(peak, abs(v))
            if (peak > 1e-6f) { val g = 0.9f / peak; for (i in buf.indices) buf[i] *= g }
            return buf
        }
    }
}
