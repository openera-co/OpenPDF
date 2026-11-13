package com.reel.editor.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
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
    private var surface: SurfaceView? = null
    private var structure: Any? = null
    private var pending: Project? = null
    private var latest: Project? = null
    private var durationUs = 0L
    private var wantPlay = false

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
            if (state == Player.STATE_ENDED) {
                _playing.value = false
                wantPlay = false
                player?.pause()
                _positionUs.value = durationUs
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = "Preview stopped: ${error.errorCodeName}"
            _playing.value = false
        }
    }

    private fun ensurePlayer(): CompositionPlayer = player ?: CompositionPlayer.Builder(app).build().also {
        it.addListener(listener)
        surface?.let { s -> it.setVideoSurfaceView(s) }
        player = it
    }

    fun attach(view: SurfaceView) {
        surface = view
        player?.setVideoSurfaceView(view)
    }

    fun detach(view: SurfaceView) {
        if (surface === view) {
            player?.clearVideoSurfaceView(view)
            surface = null
        }
    }

    /** Call on every project change. */
    fun update(project: Project) {
        latest = project
        val s = CompositionFactory.structure(project)
        if (s != structure || player == null) {
            pending = project
            main.removeCallbacks(rebuild)
            main.postDelayed(rebuild, if (structure == null) 0 else 140)
        } else {
            val (w, h) = RenderPlans.canvasSize(project, CompositionFactory.PREVIEW_HEIGHT)
            live.set(RenderPlans.build(project, FxCatalog.get(app), w, h))
            refreshFrame()
        }
    }

    private val rebuild = Runnable {
        val p = pending ?: return@Runnable
        pending = null
        val built = CompositionFactory.build(app, p, CompositionFactory.PREVIEW_HEIGHT, live = live)
        structure = CompositionFactory.structure(p)
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
            pl.setComposition(built.composition, pos / 1000)
            pl.prepare()
            pl.playWhenReady = wantPlay
        } catch (e: Exception) {
            _error.value = "Preview could not start: ${e.message}"
        }
    }

    /** While paused, re-seek to the same frame so look changes render immediately. */
    private fun refreshFrame() {
        val p = player ?: return
        if (!p.isPlaying) {
            main.removeCallbacks(refresh)
            main.postDelayed(refresh, 30)
        }
    }

    private val refresh = Runnable { player?.let { if (!it.isPlaying) it.seekTo(_positionUs.value / 1000) } }

    private fun tick() {
        val p = player ?: return
        _positionUs.value = p.currentPosition * 1000
        if (p.isPlaying) main.postDelayed({ tick() }, 16)
    }

    fun play() {
        val p = player ?: return
        wantPlay = true
        if (_positionUs.value >= durationUs - 30_000) seekTo(0)
        p.play()
    }

    fun pause() {
        wantPlay = false
        player?.pause()
        player?.let { _positionUs.value = it.currentPosition * 1000 }
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
        structure = null
    }
}
