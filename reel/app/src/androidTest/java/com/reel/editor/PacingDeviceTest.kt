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
 * An exported video has every frame across cuts between clips, and the sound keeps the music's
 * sample rate. Seen in the editor: a two-clip export froze 194 ms at the cut (Media3's frame
 * dropper restarting at each clip) and came out at the voice clip's 22 kHz.
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

    private val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.PRODUCT.startsWith("sdk")

    /**
     * Every frame is there. On the emulator its old OMX decoder drops up to 4 frames at each clip
     * start (in the first 250 ms) while the first frame renders (the renderer never receives them; HANDOFF
     * section 7), so gaps of up to 5 frames right at a clip start are allowed there only.
     */
    private fun assertEveryFrame(label: String, pts: List<Long>, clipStartsUs: List<Long>) {
        val frame = 33_334L
        val bad = pts.zipWithNext().filter { (x, y) ->
            val gap = y - x
            val atStart = clipStartsUs.any { x < it + 250_000 && y > it }
            gap > frame * 3 / 2 && !(emulator && atStart && gap <= frame * 6)
        }
        Log.i(TAG, "$label frames=${pts.size} first=${pts.take(6)} gaps=${pts.zipWithNext { x, y -> y - x }.withIndex().filter { it.value > 50_000 }.map { pts[it.index] to it.value }}")
        assertTrue("$label: frames missing at ${bad.map { it.first }}", bad.isEmpty())
        assertEquals("$label: first frame at 0", 0L, pts.first())
    }

    private fun framePts(file: File): List<Long> {
        val (vx, _) = track(file, "video/")
        val pts = ArrayList<Long>()
        while (vx.sampleTime >= 0) { pts += vx.sampleTime; vx.advance() }
        vx.release()
        return pts.sorted()
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
            val pts = framePts(d.file)
            assertEveryFrame("A-first", pts, listOf(0L, 2_000_000L))
            val (ax, af) = track(d.file, "audio/")
            ax.release()
            val rate = af.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            Log.i(TAG, "audio rate=$rate")
            assertEquals(44_100, rate)
        } finally {
            runCatching { ctx.contentResolver.delete(d.uri, null, null) }
            d.file.delete()
        }
    }

    @Test
    fun firstClipStartsOnItsFirstFrames() {
        // Same check with the colour-bar clip first: tells a clip's own start apart from the
        // export's start.
        val a = MediaProbe.probeClip(ctx, asset("a.mp4"))!!
        val b = MediaProbe.probeClip(ctx, asset("talk.mp4"))!!.copy(trimEndUs = 1_000_000)
        val d = export(Project(name = "Pacing2", clips = listOf(a, b)), ExportSettings(height = 360, fps = 30))
        try {
            val pts = framePts(d.file)
            assertEveryFrame("B-first", pts, listOf(0L, 2_000_000L))
        } finally {
            runCatching { ctx.contentResolver.delete(d.uri, null, null) }
            d.file.delete()
        }
    }

    @Test
    fun clipsWithoutBFramesKeepEveryFrame() {
        // nob.mp4 is a.mp4 encoded without B-frames (like most phone camera recordings).
        val a = MediaProbe.probeClip(ctx, asset("nob.mp4"))!!
        val b = MediaProbe.probeClip(ctx, asset("nob.mp4"))!!
        val d = export(Project(name = "Pacing3", clips = listOf(a, b)), ExportSettings(height = 360, fps = 30))
        try {
            val pts = framePts(d.file)
            assertEveryFrame("no-B", pts, listOf(0L, 2_000_000L))
        } finally {
            runCatching { ctx.contentResolver.delete(d.uri, null, null) }
            d.file.delete()
        }
    }

    @Test
    fun portraitExportKeepsItsResolution() {
        // A 9:16 project at 1080p must come out 1080x1920 as shown (stored turned plus a rotation
        // tag where the encoder stops at 1920x1088), not shrunk to fit the encoder upright.
        val a = MediaProbe.probeClip(ctx, asset("a.mp4"))!!.copy(trimEndUs = 1_000_000)
        val p = Project(name = "Portrait", clips = listOf(a)).let { it.copy(canvas = it.canvas.copy(ratio = com.reel.editor.model.AspectRatio.R9_16)) }
        val d = export(p, ExportSettings(height = 1080, fps = 30))
        try {
            val r = android.media.MediaMetadataRetriever().apply { setDataSource(d.file.absolutePath) }
            val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            r.release()
            val (sw, sh) = if (rot % 180 == 0) w to h else h to w
            Log.i(TAG, "portrait stored ${w}x$h rotation $rot -> shown ${sw}x$sh")
            assertEquals(1080, sw)
            assertEquals(1920, sh)
        } finally {
            runCatching { ctx.contentResolver.delete(d.uri, null, null) }
            d.file.delete()
        }
    }

    companion object { const val TAG = "ReelPacingTest" }
}
