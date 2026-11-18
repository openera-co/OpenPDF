package com.reel.editor.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.reel.editor.model.AudioItem
import com.reel.editor.model.AudioKind
import com.reel.editor.model.Clip
import com.reel.editor.model.ClipKind

/** Reads what the editor needs to know about a picked file. */
object MediaProbe {
    const val PHOTO_DEFAULT_US = 3_000_000L

    fun probeClip(context: Context, uri: Uri): Clip? {
        val type = context.contentResolver.getType(uri).orEmpty()
        return if (type.startsWith("image/")) probePhoto(context, uri) else probeVideo(context, uri)
    }

    private fun probeVideo(context: Context, uri: Uri): Clip? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            // Prefer the video track's own length: containers often report the longer audio track,
            // and Media3 places clips by video length during export.
            val videoDur = videoTrackDurationUs(context, uri)
            val d = if (videoDur != null && videoDur > 0) videoDur else dur
            if (d <= 0 || w <= 0 || h <= 0) return null
            Clip(uri = uri.toString(), kind = ClipKind.VIDEO, sourceDurationUs = d, sourceWidth = w, sourceHeight = h, rotationDegrees = rot, hasAudio = hasAudio)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    private fun probePhoto(context: Context, uri: Uri): Clip? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var w = o.outWidth
        var h = o.outHeight
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) { val t = w; w = h; h = t }
        if (w <= 0 || h <= 0) null else Clip(
            uri = uri.toString(), kind = ClipKind.PHOTO, sourceDurationUs = PHOTO_DEFAULT_US,
            sourceWidth = w, sourceHeight = h, hasAudio = false,
        )
    } catch (e: Exception) {
        null
    }

    fun probeAudio(context: Context, uri: Uri, title: String?, kind: AudioKind, startUs: Long): AudioItem? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            if (dur <= 0) return null
            // A MediaStore uri's last segment is a row id ("23"): prefer the file's display name.
            val name = title ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: displayName(context, uri) ?: uri.lastPathSegment ?: "Audio"
            AudioItem(uri = uri.toString(), title = name, kind = kind, sourceDurationUs = dur, startUs = startUs)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    /** The file name a content provider shows for [uri], without its extension. */
    fun displayName(context: Context, uri: Uri): String? = try {
        if (uri.scheme == "file") uri.lastPathSegment?.substringBeforeLast('.')
        else context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun videoTrackDurationUs(context: Context, uri: Uri): Long? {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION)
        } catch (e: Exception) {
            null
        } finally {
            ex.release()
        }
    }

    /** Frame rate of the first video track, for choosing export defaults. */
    fun frameRate(context: Context, uri: Uri): Int? {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_FRAME_RATE) }?.getInteger(MediaFormat.KEY_FRAME_RATE)
        } catch (e: Exception) {
            null
        } finally {
            ex.release()
        }
    }
}
