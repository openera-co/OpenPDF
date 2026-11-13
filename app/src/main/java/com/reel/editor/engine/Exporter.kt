package com.reel.editor.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.reel.editor.model.ExportSettings
import com.reel.editor.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val etaSec: Int?) : ExportState
    data class Done(val uri: Uri, val file: File, val sizeBytes: Long, val durationMs: Long) : ExportState
    data class Failed(val message: String) : ExportState
    data object Cancelled : ExportState
}

/** Runs one export at a time on the main looper (Transformer requires a looper thread). */
object Exporter {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state
    private var transformer: Transformer? = null
    private var startedAt = 0L

    fun estimateBytes(project: Project, s: ExportSettings, durationUs: Long): Long {
        val (w, h) = com.reel.editor.fx.RenderPlans.canvasSize(project, s.height)
        val video = CompositionFactory.bitrate(w, h, s.fps, s.bitrate.factor) * (if (s.hevc) 0.65 else 1.0)
        return ((video + 192_000) / 8.0 * durationUs / 1e6).toLong()
    }

    fun start(context: Context, project: Project, settings: ExportSettings) {
        if (_state.value is ExportState.Running) return
        val app = context.applicationContext
        val built = CompositionFactory.build(app, project, settings.height, settings.fps)
        if (built == null) {
            _state.value = ExportState.Failed("Nothing to export")
            return
        }
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "${project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "Reel" }}_${System.currentTimeMillis()}.mp4")
        val mime = if (settings.hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
        val bitrate = (CompositionFactory.bitrate(built.canvasWidth, built.canvasHeight, settings.fps, settings.bitrate.factor) * (if (settings.hevc) 0.65 else 1.0)).toInt()
        val encoder = DefaultEncoderFactory.Builder(app)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            .setEnableFallback(true)
            .build()
        val t = Transformer.Builder(app)
            .setVideoMimeType(mime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoder)
            .setPortraitEncodingEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    val uri = try {
                        publish(app, out)
                    } catch (e: Exception) {
                        _state.value = ExportState.Failed("Saved, but could not add to gallery: ${e.message}")
                        return
                    }
                    _state.value = ExportState.Done(uri, out, out.length(), exportResult.durationMs)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null
                    out.delete()
                    _state.value = ExportState.Failed(friendly(exportException))
                }
            })
            .build()
        transformer = t
        startedAt = System.currentTimeMillis()
        _state.value = ExportState.Running(0f, null)
        t.start(built.composition, out.absolutePath)
        poll()
        ExportService.start(app)
    }

    private fun poll() {
        val t = transformer ?: return
        val holder = ProgressHolder()
        if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            val p = holder.progress / 100f
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000f
            val eta = if (p > 0.03f) ((elapsed / p) * (1 - p)).toInt() else null
            _state.value = ExportState.Running(p, eta)
        }
        main.postDelayed({ poll() }, 250)
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
        _state.value = ExportState.Cancelled
    }

    fun reset() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }

    private fun friendly(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
            "This phone's encoder can't make this video. Try a lower resolution or turn off HEVC."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED, ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "One of the clips uses a format this phone can't decode."
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "A clip could not be read. It may have been moved or deleted."
        else -> "Export failed (${e.errorCodeName})."
    }

    /** Copies the file into the shared Movies/Reel collection and returns its content URI. */
    private fun publish(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Reel")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error("insert failed")
        resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 20) } } ?: error("open failed")
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
