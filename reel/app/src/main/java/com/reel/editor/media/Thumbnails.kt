package com.reel.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Timeline thumbnails. One retriever per source is kept open (opening is the slow part),
 * frames are cached in memory with a size bound, and requests are snapped to a coarse time grid
 * so scrolling and zooming reuse frames.
 */
class Thumbnails(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val retrievers = ConcurrentHashMap<String, MediaMetadataRetriever>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    fun peek(uri: String, timeUs: Long, heightPx: Int): Bitmap? = cache.get(key(uri, timeUs, heightPx))

    private fun key(uri: String, timeUs: Long, h: Int) = "$uri|${timeUs / GRID_US}|$h"

    suspend fun frame(uri: String, timeUs: Long, heightPx: Int, isPhoto: Boolean): Bitmap? {
        val k = key(uri, timeUs, heightPx)
        cache.get(k)?.let { return it }
        return withContext(io) {
            locks.getOrPut(uri) { Mutex() }.withLock {
                cache.get(k)?.let { return@withLock it }
                val bmp = if (isPhoto) decodePhoto(uri, heightPx) else grab(uri, (timeUs / GRID_US) * GRID_US, heightPx)
                bmp?.also { cache.put(k, it) }
            }
        }
    }

    private fun decodePhoto(uri: String, h: Int): Bitmap? = try {
        val u = Uri.parse(uri)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outHeight / (s * 2) >= h) s *= 2
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (e: Exception) {
        null
    }

    private fun grab(uri: String, timeUs: Long, h: Int): Bitmap? = try {
        val r = retrievers.getOrPut(uri) { MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(uri)) } }
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: h
        val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (dw, dh) = if (rot % 180 != 0) vh to w else w to vh
        val tw = (h.toFloat() * dw / dh.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, tw, h)
        else r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, tw, h, true) }
    } catch (e: Exception) {
        null
    }

    fun release() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
        cache.evictAll()
    }

    companion object {
        const val GRID_US = 250_000L
    }
}
