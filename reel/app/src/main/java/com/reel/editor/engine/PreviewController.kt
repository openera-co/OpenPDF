package com.reel.editor.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.util.Size
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import com.reel.editor.fx.FxCatalog
import com.reel.editor.fx.LivePlans
import com.reel.editor.fx.RenderPlans
import com.reel.editor.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the preview player. Structural edits rebuild the composition (debounced); look edits are
 * pushed into [LivePlans] and refresh the current frame without re-preparing.
 */
class PreviewController(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val live = LivePlans()
    private var player: CompositionPlayer? = null
    private var structure: Any? = null
    private var pending: Project? = null
    private var latest: Project? = null
    private var durationUs = 0L
    private var wantPlay = false
    private var needsFirstFrame = false
    private var recoveries = 0

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
            if (isPlaying) tick()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) finishAtEnd()
            if (state == Player.STATE_READY) recoveries = 0
            // Make sure a paused, freshly prepared player shows its first frame.
            if (state == Player.STATE_READY && needsFirstFrame) {
                needsFirstFrame = false
                if (!wantPlay) refreshFrame()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playing.value = false
            // Usually transient (a slow release or decoder hiccup): rebuild quietly and carry on.
            // Only a failure that survives the retries is reported.
            if (recoveries < 3) {
                recoveries++
                com.reel.editor.CrashReports.note("preview", "recovering from ${error.errorCodeName}")
                main.post { recreate() }
            } else {
                com.reel.editor.CrashReports.error("preview", error)
                _error.value = "Preview paused. Tap play to try again."
            }
        }
    }

    private fun ensurePlayer(): CompositionPlayer = player ?: CompositionPlayer.Builder(app)
        // Keeps the last decoded frame so look and layer edits redraw instantly while paused.
        .experimentalSetEnableReplayableCache(true)
        .build().also {
        it.addListener(listener)
        outSurface?.let { s -> it.setVideoSurface(s, outSize) }
        player = it
    }

    private var outSurface: Surface? = null
    private var outSize = Size(1, 1)

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
            outSurface?.release()
            val surface = Surface(st)
            outSurface = surface
            outSize = Size(width, height)
            player?.setVideoSurface(surface, outSize)
            refreshFrame()
        }

        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
            outSize = Size(width, height)
            // Media3 ignores a new size for the same Surface, so detach and reattach it.
            outSurface?.let { surf -> player?.let { it.clearVideoSurface(surf); it.setVideoSurface(surf, outSize) } }
            refreshFrame()
        }

        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            outSurface?.let { surf -> runCatching { player?.clearVideoSurface(surf) } }
            outSurface?.release()
            outSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    /**
     * The preview draws into a TextureView: it is composited with the rest of the UI, so it resizes
     * and animates with panels without the stale buffers a SurfaceView can show.
     */
    private var attached: java.lang.ref.WeakReference<TextureView>? = null

    /** The frame on screen, effects and layers included, at the preview's own resolution. */
    fun snapshot(): android.graphics.Bitmap? = attached?.get()?.takeIf { it.isAvailable }?.bitmap

    fun attach(view: TextureView) {
        attached = java.lang.ref.WeakReference(view)
        view.surfaceTextureListener = textureListener
        view.surfaceTexture?.let { textureListener.onSurfaceTextureAvailable(it, view.width, view.height) }
    }

    fun detach(view: TextureView) {
        if (view.surfaceTextureListener === textureListener) view.surfaceTextureListener = null
        outSurface?.let { surf -> runCatching { player?.clearVideoSurface(surf) } }
        outSurface?.release()
        outSurface = null
    }

    private fun analyzed(p: Project): Set<String> {
        val a = (app as? com.reel.editor.ReelApp)?.audio ?: return emptySet()
        return (p.clips.map { it.uri } + p.audio.map { it.uri }).filter { a.cached(it) != null }.toSet()
    }

    /** Call on every project change. */
    fun update(project: Project) {
        latest = project
        val s = CompositionFactory.structure(project, analyzed(project))
        if (s != structure || player == null) {
            pending = project
            main.removeCallbacks(rebuild)
            main.postDelayed(rebuild, if (structure == null) 0 else 140)
        } else {
            val (w, h) = RenderPlans.canvasSize(project, CompositionFactory.PREVIEW_HEIGHT)
            live.set(RenderPlans.build(project, FxCatalog.get(app), w, h))
            redraw()
        }
    }

    private val rebuild = Runnable {
        val p = pending ?: return@Runnable
        pending = null
        val built = CompositionFactory.build(app, p, CompositionFactory.PREVIEW_HEIGHT, live = live)
        structure = CompositionFactory.structure(p, analyzed(p))
        if (built == null) {
            player?.let { it.stop(); it.clearMediaItems() }
            durationUs = 0
            return@Runnable
        }
        durationUs = built.durationUs
        val pl = ensurePlayer()
        val pos = _positionUs.value.coerceIn(0, (durationUs - 1).coerceAtLeast(0))
        _error.value = null
        try {
            needsFirstFrame = true
            pl.setComposition(built.composition, pos / 1000)
            pl.prepare()
            pl.playWhenReady = wantPlay
        } catch (e: Exception) {
            _error.value = "Preview could not start: ${e.message}"
        }
    }

    /** While paused, re-run the effect pipeline on the cached frame; no decoder work needed. */
    /** Shows the clips without looks, effects and layers while held. */
    fun showOriginal(on: Boolean) {
        if (com.reel.editor.fx.PreviewOriginal.on == on) return
        com.reel.editor.fx.PreviewOriginal.on = on
        redraw()
    }

    private fun redraw() {
        val p = player ?: return
        if (p.isPlaying) return
        main.removeCallbacks(redrawRunnable)
        main.postDelayed(redrawRunnable, 16)
    }

    private val redrawRunnable = Runnable {
        val p = player ?: return@Runnable
        try {
            p.experimentalRedrawLastFrame()
        } catch (e: Exception) {
            refreshFrame()
        }
    }

    /** While paused, re-seek to the current frame (used when the output surface changes). */
    private fun refreshFrame() {
        val p = player ?: return
        if (!p.isPlaying) {
            main.removeCallbacks(refresh)
            main.postDelayed(refresh, 40)
        }
    }

    private val refresh = Runnable { player?.let { if (!it.isPlaying) it.seekTo(_positionUs.value.coerceAtMost((durationUs - 34_000).coerceAtLeast(0)) / 1000) } }

    private val tickRunnable = Runnable { tick() }

    private fun tick() {
        val p = player ?: return
        main.removeCallbacks(tickRunnable)
        val pos = p.currentPosition * 1000
        if (durationUs > 0 && pos >= durationUs) {
            // Some audio sinks keep the clock running past the last frame; stop at the end ourselves.
            finishAtEnd()
            return
        }
        _positionUs.value = pos.coerceIn(0, durationUs.coerceAtLeast(0))
        if (p.isPlaying) main.postDelayed(tickRunnable, 16)
    }

    private fun finishAtEnd() {
        wantPlay = false
        player?.pause()
        _playing.value = false
        _positionUs.value = durationUs
        // Show the true last frame even if decoding fell behind the clock.
        player?.seekTo(((durationUs - 34_000).coerceAtLeast(0)) / 1000)
    }

    /** Throws away the player and builds a fresh one at the same position. */
    private fun recreate() {
        val p = latest ?: return
        val wasPlaying = wantPlay
        player?.let { pl ->
            pl.removeListener(listener)
            outSurface?.let { runCatching { pl.clearVideoSurface(it) } }
            runCatching { pl.release() }
        }
        player = null
        structure = null
        wantPlay = wasPlaying
        pending = p
        main.removeCallbacks(rebuild)
        rebuild.run()
    }

    fun play() {
        if (_error.value != null) {
            _error.value = null
            recoveries = 0
            wantPlay = true
            recreate()
            return
        }
        val p = player ?: return
        wantPlay = true
        if (_positionUs.value >= durationUs - 30_000) seekTo(0)
        p.play()
    }

    fun pause() {
        wantPlay = false
        player?.pause()
        player?.let { _positionUs.value = (it.currentPosition * 1000).coerceIn(0, durationUs.coerceAtLeast(0)) }
    }

    fun toggle() = if (_playing.value) pause() else play()

    fun seekTo(us: Long) {
        val t = us.coerceIn(0, durationUs.coerceAtLeast(0))
        _positionUs.value = t
        player?.seekTo(t / 1000)
    }

    fun setVolumeMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
    }

    fun setScrubbing(on: Boolean) {
        if (on) pause()
        player?.setScrubbingModeEnabled(on)
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        player?.removeListener(listener)
        player?.release()
        player = null
        outSurface?.release()
        outSurface = null
        structure = null
    }
}
