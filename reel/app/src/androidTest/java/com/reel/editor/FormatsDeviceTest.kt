package com.reel.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.edit.ProjectArchive
import com.reel.editor.edit.ProjectStore
import com.reel.editor.engine.ExportState
import com.reel.editor.engine.Exporter
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.ExportFormat
import com.reel.editor.model.ExportSettings
import com.reel.editor.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** GIF and sound-only exports through the real exporter, and packing a project to another "phone". */
@RunWith(AndroidJUnit4::class)
class FormatsDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private fun project(): Project {
        val c = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!.let { it.copy(trimEndUs = 1_500_000) }
        return Project(name = "Formats", clips = listOf(c))
    }

    private fun export(p: Project, s: ExportSettings): ExportState.Done {
        Handler(Looper.getMainLooper()).post { Exporter.reset(); Exporter.start(ctx, p, s) }
        val until = System.currentTimeMillis() + 15 * 60_000
        while (System.currentTimeMillis() < until) {
            Thread.sleep(500)
            when (val st = Exporter.state.value) {
                is ExportState.Done -> { Handler(Looper.getMainLooper()).post { Exporter.reset() }; return st }
                is ExportState.Failed -> throw AssertionError(st.message)
                else -> {}
            }
        }
        throw AssertionError("export timed out")
    }

    /** Exports land in the phone's gallery and music: remove them so test runs leave nothing behind. */
    private fun cleanUp(d: ExportState.Done) {
        runCatching { ctx.contentResolver.delete(d.uri, null, null) }
        d.file.delete()
    }

    @Test fun exportsAGif() {
        val d = export(project(), ExportSettings(format = ExportFormat.GIF))
        try { checkGif(d) } finally { cleanUp(d) }
    }

    private fun checkGif(d: ExportState.Done) {
        val head = d.file.inputStream().use { s -> ByteArray(6).also { s.read(it) } }.decodeToString()
        val bmp = android.graphics.BitmapFactory.decodeFile(d.file.absolutePath)
        Log.i(TAG, "gif ${d.file.length()} bytes ${bmp?.width}x${bmp?.height} uri=${d.uri}")
        assertEquals("GIF89a", head)
        assertTrue(bmp != null && minOf(bmp.width, bmp.height) <= 360)
        assertTrue(ctx.contentResolver.getType(d.uri)?.startsWith("image/") == true)
    }

    @Test fun exportsSoundOnly() {
        val d = export(project(), ExportSettings(format = ExportFormat.AUDIO))
        try { checkSound(d) } finally { cleanUp(d) }
    }

    private fun checkSound(d: ExportState.Done) {
        val r = MediaMetadataRetriever().apply { setDataSource(d.file.absolutePath) }
        val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        r.release()
        Log.i(TAG, "audio ${d.file.name} ${d.file.length()} bytes video=$hasVideo audio=$hasAudio ${dur}ms")
        assertTrue(d.file.name.endsWith(".m4a"))
        assertEquals("yes", hasAudio)
        assertTrue(hasVideo != "yes")
        assertTrue(dur in 1200..1900)
    }

    @Test fun packsAndUnpacksAProject() {
        val store = ProjectStore(ctx)
        val p = project()
        val packed = ProjectArchive.export(ctx, p)
        val imported = ProjectArchive.import(ctx, Uri.fromFile(packed), store)
        Log.i(TAG, "packed ${packed.length()} bytes -> ${imported.clips[0].uri}")
        assertTrue(imported.id != p.id)
        assertTrue(imported.clips[0].uri.startsWith("file:"))
        // The copied clip plays the same.
        val c = MediaProbe.probeClip(ctx, Uri.parse(imported.clips[0].uri))!!
        assertEquals(p.clips[0].sourceDurationUs / 1000, c.sourceDurationUs / 1000)
        store.delete(imported.id)
    }

    companion object { const val TAG = "FormatsDeviceTest" }
}
