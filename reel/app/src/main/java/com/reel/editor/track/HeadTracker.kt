package com.reel.editor.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.reel.editor.fx.Segmenter
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind
import com.reel.editor.model.HeadPoint
import com.reel.editor.model.SpeedMath
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Finds a head in a clip over time. Faces come from Android's face detector; when it finds none
 * (profile, small or covered faces) the person mask gives the top of the body instead.
 */
class HeadTracker(private val context: Context) {
    @Volatile var cancelled = false

    /** Samples the clip every [stepUs] of timeline time, from [fromUs] to [toUs] (offsets in the clip). */
    fun track(clip: Clip, fromUs: Long, toUs: Long, stepUs: Long = 150_000, progress: (Float) -> Unit = {}): List<HeadPoint> {
        val out = ArrayList<HeadPoint>()
        if (clip.kind == ClipKind.PHOTO) {
            val bmp = context.contentResolver.openInputStream(Uri.parse(clip.uri))?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return out
            val p = detect(bmp, 0)
            var t = fromUs
            while (t <= toUs) { out += p.copy(timeUs = t); t += stepUs }
            return out
        }
        // Fast path: one decode pass over the range.
        val times = ArrayList<Long>()
        run { var t = fromUs; while (t <= toUs) { times += t; t += stepUs } }
        val media = times.map { clip.trimStartUs + SpeedMath.timelineToMedia(clip.speed, clip.trimmedDurationUs, it.coerceAtLeast(0)) }
        val found = arrayOfNulls<HeadPoint>(times.size)
        val ok = FrameSampler(context, clip.uri).sample(media, { cancelled }) { i, bmp ->
            found[i] = detect(bmp, times[i])
            progress((i + 1f) / times.size)
        }
        if (ok && found.none { it == null }) return found.map { it!! }
        // Fallback: ask for frames one by one.
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(clip.uri))
            for ((i, t) in times.withIndex()) {
                if (cancelled) break
                found[i]?.let { out += it; continue }
                val frame = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(media[i], MediaMetadataRetriever.OPTION_CLOSEST, 360, 360)
                else r.getFrameAtTime(media[i], MediaMetadataRetriever.OPTION_CLOSEST)
                out += if (frame != null) detect(frame, t) else HeadPoint(t, 0.5f, 0.5f, 0f, false)
                progress((i + 1f) / times.size)
            }
        } finally {
            r.release()
        }
        return out
    }

    private fun detect(frame: Bitmap, t: Long): HeadPoint = face(frame, t) ?: person(frame, t) ?: HeadPoint(t, 0.5f, 0.5f, 0f, false)

    private fun face(src: Bitmap, t: Long): HeadPoint? {
        // FaceDetector wants RGB_565 with an even width.
        val w = (src.width / 2) * 2
        val h = src.height
        if (w < 8 || h < 8) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(bmp).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val faces = arrayOfNulls<FaceDetector.Face>(3)
        val n = try { FaceDetector(w, h, faces.size).findFaces(bmp, faces) } catch (e: Exception) { 0 }
        val best = faces.take(n).filterNotNull().filter { it.confidence() >= 0.3f }.maxByOrNull { it.eyesDistance() } ?: return null
        val mid = android.graphics.PointF()
        best.getMidPoint(mid)
        val eyes = best.eyesDistance()
        // The point between the eyes sits a little above the head's middle.
        return HeadPoint(t, mid.x / w, (mid.y + eyes * 0.35f) / h, (eyes * 2.8f) / h, true)
    }

    private fun person(src: Bitmap, t: Long): HeadPoint? {
        val seg = try { Segmenter.forThread(context, src.width > src.height) } catch (e: Exception) { return null }
        val sw = seg.width
        val sh = seg.height
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        scaled.getPixels(px, 0, sw, 0, 0, sw, sh)
        val buf = ByteBuffer.allocateDirect(sw * sh * 4).order(ByteOrder.nativeOrder())
        // Segmenter takes GL-style bottom-up rows.
        for (y in sh - 1 downTo 0) for (x in 0 until sw) {
            val c = px[y * sw + x]
            buf.put(((c shr 16) and 255).toByte()); buf.put(((c shr 8) and 255).toByte()); buf.put((c and 255).toByte()); buf.put(255.toByte())
        }
        buf.rewind()
        seg.reset()
        val mask = ByteArray(sw * sh)
        seg.run(buf, "track", t, mask)
        // mask rows are bottom-up; walk top-down.
        fun on(x: Int, yTop: Int) = (mask[(sh - 1 - yTop) * sw + x].toInt() and 255) > 128
        var top = -1
        var bottom = -1
        for (y in 0 until sh) { val any = (0 until sw).any { on(it, y) }; if (any) { if (top < 0) top = y; bottom = y } }
        if (top < 0 || bottom - top < sh / 10) return null
        // The head is the top fifth of the person (at most a third of the frame).
        val band = ((bottom - top) * 0.2f).toInt().coerceIn(2, sh / 3)
        var sx = 0L; var cnt = 0; var minX = sw; var maxX = 0
        for (y in top until (top + band).coerceAtMost(sh)) for (x in 0 until sw) if (on(x, y)) { sx += x; cnt++; minX = minOf(minX, x); maxX = maxOf(maxX, x) }
        if (cnt < 4) return null
        return HeadPoint(t, (sx.toFloat() / cnt + 0.5f) / sw, (top + band / 2f) / sh, (maxX - minX + 1f) / sw * sw / sh, true)
    }
}
