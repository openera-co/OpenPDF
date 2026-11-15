package com.reel.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.CompositionPlayer
import com.reel.editor.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Renders one frame of the project at full export size, with every look, layer and effect, by
 * running a second player off screen into an [ImageReader].
 */
object StillRenderer {
    suspend fun render(context: Context, project: Project, positionUs: Long, height: Int = 1080): Bitmap? = withContext(Dispatchers.Main) {
        val built = CompositionFactory.build(context, project, height) ?: return@withContext null
        val w = built.canvasWidth; val h = built.canvasHeight
        val thread = HandlerThread("still").apply { start() }
        val bg = Handler(thread.looper)
        val main = Handler(Looper.getMainLooper())
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        val lock = Any()
        var latest: Image? = null
        // Keep only the newest frame so the player never waits on us.
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            synchronized(lock) { latest?.close(); latest = img }
        }, bg)
        val player = CompositionPlayer.Builder(context).build()
        try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine { cont ->
                    var seeking = false
                    fun grab() {
                        // Copy on the reader's thread, then hand the bitmap back.
                        bg.post {
                            val bmp = synchronized(lock) {
                                val img = latest ?: return@synchronized null
                                try {
                                    val plane = img.planes[0]
                                    val rowPx = plane.rowStride / plane.pixelStride
                                    val full = Bitmap.createBitmap(rowPx, h, Bitmap.Config.ARGB_8888)
                                    full.copyPixelsFromBuffer(plane.buffer)
                                    if (rowPx == w) full else Bitmap.createBitmap(full, 0, 0, w, h).also { full.recycle() }
                                } catch (e: Exception) { null }
                            }
                            main.post { if (cont.isActive) cont.resume(bmp) }
                        }
                    }
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state != Player.STATE_READY) return
                            if (!seeking) {
                                seeking = true
                                player.seekTo(positionUs.coerceIn(0, (built.durationUs - 34_000).coerceAtLeast(0)) / 1000)
                                // A seek to where the player already is may not pass through loading again.
                                main.postDelayed({ if (cont.isActive) grab() }, 2_500)
                            } else {
                                // Ready at the position: give the frame a moment to reach the reader.
                                main.postDelayed({ if (cont.isActive) grab() }, 400)
                            }
                        }
                    })
                    player.setVideoSurface(reader.surface, Size(w, h))
                    player.setComposition(built.composition)
                    player.prepare()
                }
            }
        } finally {
            player.release()
            synchronized(lock) { latest?.close(); latest = null }
            reader.close()
            thread.quitSafely()
        }
    }
}
