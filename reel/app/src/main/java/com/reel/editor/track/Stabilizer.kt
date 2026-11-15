package com.reel.editor.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.SpeedMath
import com.reel.editor.model.Stabilize

/** Measures camera shake through a clip: one motion per pair of sampled frames. */
class Stabilizer(private val context: Context) {
    @Volatile var cancelled = false

    /** Samples every [stepUs] of the clip's timeline time; returns the sample times and the motions between them. */
    fun analyze(clip: Clip, durationUs: Long, stepUs: Long = 1_000_000L / 15, progress: (Float) -> Unit = {}): Pair<List<Long>, List<Stabilize.Motion>>? {
        if (clip.kind == ClipKind.PHOTO) return null
        val times = ArrayList<Long>()
        run { var t = 0L; while (t < durationUs) { times += t; t += stepUs } }
        if (times.size < 3) return null
        val media = times.map { clip.trimStartUs + SpeedMath.timelineToMedia(clip.speed, clip.trimmedDurationUs, it) }
        val grays = arrayOfNulls<Stabilize.Gray>(times.size)
        val ok = FrameSampler(context, clip.uri).sample(media, { cancelled }) { i, bmp ->
            grays[i] = gray(bmp, clip)
            progress(0.8f * (i + 1f) / times.size)
        }
        if (cancelled || !ok || grays.any { it == null }) return null
        val motions = (1 until times.size).map { i ->
            if (cancelled) return null
            progress(0.8f + 0.2f * i / times.size)
            Stabilize.motion(grays[i - 1]!!, grays[i]!!)
        }
        return times to motions
    }

    /** The clip's cropped picture, 160 px wide, as grey levels. */
    private fun gray(src: Bitmap, clip: Clip): Stabilize.Gray {
        val cr = clip.crop
        val x0 = (cr.x * src.width).toInt().coerceIn(0, src.width - 2)
        val y0 = (cr.y * src.height).toInt().coerceIn(0, src.height - 2)
        val cw = (cr.w * src.width).toInt().coerceIn(2, src.width - x0)
        val ch = (cr.h * src.height).toInt().coerceIn(2, src.height - y0)
        val w = 160
        val h = (w * ch / cw).coerceIn(48, 320)
        val cut = if (x0 == 0 && y0 == 0 && cw == src.width && ch == src.height) src else Bitmap.createBitmap(src, x0, y0, cw, ch)
        val small = Bitmap.createScaledBitmap(cut, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        return Stabilize.Gray(w, h, FloatArray(w * h) { i ->
            val c = px[i]
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        })
    }
}
