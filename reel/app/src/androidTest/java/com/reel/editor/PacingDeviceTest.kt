package com.reel.editor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.engine.ExportState
import com.reel.editor.engine.Exporter
import com.reel.editor.media.MediaProbe
import com.reel.editor.model.AudioItem
import com.reel.editor.model.AudioKind
import com.reel.editor.model.ExportSettings
import com.reel.editor.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An exported video has every frame: no frozen frames at the start or at cuts between clips, and
 * the sound keeps the music's sample rate. Seen in the editor: a two-clip export started with a
 * 167 ms freeze and froze 194 ms at the cut, and came out at the voice clip's 22 kHz.
 */
@RunWith(AndroidJUnit4::class)
class PacingDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    private fun asset(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private fun export(p: Project, s: ExportSettings): ExportState.Done {
        Handler(Looper.getMainLooper()).post { Exporter.reset(); Exporter.start(ctx, p, s) }
        val until = System.currentTimeMillis() + 20 * 60_000
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

    private fun track(file: File, mimePrefix: String): Pair<MediaExtractor, MediaFormat> {
        val ex = MediaExtractor().apply { setDataSource(file.absolutePath) }
        val i = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(mimePrefix) }
        ex.selectTrack(i)
        return ex to ex.getTrackFormat(i)
    }

    @Test
    fun everyFrameIsThereAcrossACut() {
        // talk.mp4 has 22.05 kHz sound, the drums 44.1 kHz.
        val a = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!.copy(trimEndUs = 2_000_000)
        val b = MediaProbe.probeClip(ctx, asset("a.mp4"))!!
        val drums = MediaProbe.probeAudio(ctx, asset("drums124.m4a"), "Drums", AudioKind.MUSIC, 0)!!
        val p = Project(name = "Pacing", clips = listOf(a, b), audio = listOf<AudioItem>(drums.copy(trimEndUs = 3_500_000)))
        val d = export(p, ExportSettings(height = 360, fps = 30))
        try {
            val (vx, _) = track(d.file, "video/")
            val pts = ArrayList<Long>()
            while (vx.sampleTime >= 0) { pts += vx.sampleTime; vx.advance() }
            vx.release()
            pts.sort()
            val gaps = pts.zipWithNext { x, y -> y - x }
            val worst = gaps.maxOrNull() ?: 0
            Log.i(TAG, "frames=${pts.size} first=${pts.take(6)} worstGapUs=$worst atUs=${pts.getOrNull(gaps.indexOf(worst))}")
            val (ax, af) = track(d.file, "audio/")
            ax.release()
            val rate = af.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            Log.i(TAG, "audio rate=$rate")
            // 30 fps: frames 33 ms apart; allow timestamp rounding, not a dropped frame.
            assertTrue("a frame is missing: ${worst / 1000} ms gap at ${pts.getOrNull(gaps.indexOf(worst))}", worst < 50_000)
            assertEquals("first frame at 0", 0L, pts.first())
            assertEquals(44_100, rate)
        } finally {
            runCatching { ctx.contentResolver.delete(d.uri, null, null) }
            d.file.delete()
        }
    }

    companion object { const val TAG = "ReelPacingTest" }
}
