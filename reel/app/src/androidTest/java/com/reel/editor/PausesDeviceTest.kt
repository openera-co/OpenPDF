package com.reel.editor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.CompositionFactory
import com.reel.editor.media.AudioAnalyzer
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.Pauses
import com.reel.editor.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Remove pauses on real speech: the silent gap goes, the words stay, and the export is shorter. */
@RunWith(AndroidJUnit4::class)
class PausesDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext

    @Test
    fun cutsTheGapOutOfRealSpeech() {
        val f = File(ctx.cacheDir, "pauses.mp4")
        inst.context.assets.open("pauses.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val clip = MediaProbe.probeClip(ctx, Uri.fromFile(f))!!
        val lv = AudioAnalyzer(ctx).analyze(clip.uri)!!.peaks
        val r = Pauses.remove(Project(clips = listOf(clip)), clip.id, lv, CompositionFactory.LEVELS_PER_SEC)!!
        val pieces = r.project.clips
        Log.i(TAG, "PAUSES pieces=${pieces.map { "${it.trimStartUs / 1000}-${it.trimEndUs / 1000}" }} removed=${r.removedUs / 1000} ms")
        assertEquals(2, pieces.size)
        // The words (0.3-1.6 s and 3.0-3.5 s) are kept whole.
        assertTrue(pieces[0].trimStartUs <= 300_000 && pieces[0].trimEndUs >= 1_600_000)
        assertTrue(pieces[1].trimStartUs <= 3_000_000 && pieces[1].trimEndUs >= 3_500_000)
        // The 1.4 s silence in between is gone.
        assertTrue(pieces[0].trimEndUs < 1_900_000 && pieces[1].trimStartUs > 2_700_000)

        val out = File(ctx.cacheDir, "pauses_out.mp4").apply { delete() }
        val built = CompositionFactory.build(ctx, r.project, 360, 30)!!
        val done = CountDownLatch(1)
        var error: ExportException? = null
        Handler(Looper.getMainLooper()).post {
            Transformer.Builder(ctx).setMaxDelayBetweenMuxerSamplesMs(120_000).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { error = exportException; done.countDown() }
            }).build().start(built.composition, out.absolutePath)
        }
        assertTrue("export timed out", done.await(20, TimeUnit.MINUTES))
        error?.let { throw AssertionError("export failed: ${it.errorCodeName}", it) }
        val mr = MediaMetadataRetriever().apply { setDataSource(out.absolutePath) }
        val ms = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        mr.release()
        val expected = pieces.sumOf { it.trimmedDurationUs } / 1000
        Log.i(TAG, "PAUSES export $ms ms, expected $expected ms")
        assertTrue("export is $ms ms, expected about $expected", kotlin.math.abs(ms - expected) < 150)
    }

    companion object {
        const val TAG = "ReelPausesTest"
    }
}
