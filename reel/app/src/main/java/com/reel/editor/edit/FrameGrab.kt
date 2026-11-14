package com.reel.editor.edit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** Exact-frame grabs for freeze frames, saved as JPEG in app storage. */
object FrameGrab {
    fun toFile(context: Context, uri: String, mediaTimeUs: Long): File? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            val bmp = r.getFrameAtTime(mediaTimeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val f = File(dir, "freeze_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bmp.recycle()
            f
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }
}
