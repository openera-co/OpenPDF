package com.reel.editor.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone's own videos, photos and audio, read from MediaStore for the in-app picker. */
object MediaLibrary {
    enum class Kind { VIDEO, PHOTO, AUDIO }

    data class Entry(
        val uri: Uri,
        val id: Long,
        val kind: Kind,
        val name: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val album: String,
        val dateAdded: Long,
        val artist: String? = null,
        val mime: String? = null,
    )

    // ------------------------------------------------------------------ permissions

    /** Permissions to ask for, by Android version. */
    fun permissionsFor(kinds: Set<Kind>): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> buildList {
            if (Kind.VIDEO in kinds) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Kind.PHOTO in kinds) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Kind.AUDIO in kinds) add(Manifest.permission.READ_MEDIA_AUDIO)
            // Android 14+: the user may grant only some photos and videos.
            if (Build.VERSION.SDK_INT >= 34 && (Kind.VIDEO in kinds || Kind.PHOTO in kinds)) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }.toTypedArray()
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    enum class Access { FULL, PARTIAL, NONE }

    fun access(context: Context, kinds: Set<Kind>): Access {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT < 33) return if (has(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
        val needed = buildList {
            if (Kind.VIDEO in kinds) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Kind.PHOTO in kinds) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Kind.AUDIO in kinds) add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (needed.all { has(it) }) return Access.FULL
        if (Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) return Access.PARTIAL
        return Access.NONE
    }

    // ------------------------------------------------------------------ queries

    suspend fun load(context: Context, kinds: Set<Kind>): List<Entry> = withContext(Dispatchers.IO) {
        val out = ArrayList<Entry>()
        if (Kind.VIDEO in kinds) out += query(context, Kind.VIDEO)
        if (Kind.PHOTO in kinds) out += query(context, Kind.PHOTO)
        if (Kind.AUDIO in kinds) out += query(context, Kind.AUDIO)
        out.sortedByDescending { it.dateAdded }
    }

    private fun collection(kind: Kind): Uri = when (kind) {
        Kind.VIDEO -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Kind.PHOTO -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        Kind.AUDIO -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    private fun query(context: Context, kind: Kind): List<Entry> {
        val base = collection(kind)
        val cols = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (kind != Kind.PHOTO) add(MediaStore.MediaColumns.DURATION)
            if (kind != Kind.AUDIO) add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (kind == Kind.AUDIO) { add(MediaStore.Audio.AudioColumns.ARTIST); add(MediaStore.Audio.AudioColumns.ALBUM); add(MediaStore.Audio.AudioColumns.TITLE) }
        }
        val out = ArrayList<Entry>()
        val selection = if (kind == Kind.AUDIO) "${MediaStore.MediaColumns.DURATION} > 0" else null
        try {
            context.contentResolver.query(base, cols.toTypedArray(), selection, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                fun idx(n: String) = c.getColumnIndex(n)
                val iId = idx(MediaStore.MediaColumns._ID)
                val iName = idx(MediaStore.MediaColumns.DISPLAY_NAME)
                val iDate = idx(MediaStore.MediaColumns.DATE_ADDED)
                val iMime = idx(MediaStore.MediaColumns.MIME_TYPE)
                val iW = idx(MediaStore.MediaColumns.WIDTH)
                val iH = idx(MediaStore.MediaColumns.HEIGHT)
                val iDur = idx(MediaStore.MediaColumns.DURATION)
                val iBucket = idx(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iArtist = idx(MediaStore.Audio.AudioColumns.ARTIST)
                val iAlbum = idx(MediaStore.Audio.AudioColumns.ALBUM)
                val iTitle = idx(MediaStore.Audio.AudioColumns.TITLE)
                while (c.moveToNext() && out.size < 5000) {
                    val id = c.getLong(iId)
                    val title = if (iTitle >= 0) c.getString(iTitle) else null
                    val artist = if (iArtist >= 0) c.getString(iArtist)?.takeIf { it != "<unknown>" } else null
                    out += Entry(
                        uri = ContentUris.withAppendedId(base, id),
                        id = id,
                        kind = kind,
                        name = title ?: c.getString(iName) ?: "",
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        width = if (iW >= 0) c.getInt(iW) else 0,
                        height = if (iH >= 0) c.getInt(iH) else 0,
                        album = (if (iBucket >= 0) c.getString(iBucket) else if (iAlbum >= 0) c.getString(iAlbum) else null) ?: "",
                        dateAdded = c.getLong(iDate),
                        artist = artist,
                        mime = if (iMime >= 0) c.getString(iMime) else null,
                    )
                }
            }
        } catch (e: SecurityException) {
            // No permission for this kind: nothing to show.
        }
        return out
    }

    // ------------------------------------------------------------------ thumbnails

    private val thumbs = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    suspend fun thumbnail(context: Context, e: Entry, px: Int = 256): Bitmap? {
        val key = e.id * 4 + e.kind.ordinal
        thumbs.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(e.uri, Size(px, px), null)
                } else when (e.kind) {
                    @Suppress("DEPRECATION")
                    Kind.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, e.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    Kind.PHOTO -> decodeSampled(context, e.uri, px)
                    Kind.AUDIO -> null
                }
            } catch (ex: Exception) {
                null
            }
            bmp?.also { thumbs.put(key, it) }
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }
}
