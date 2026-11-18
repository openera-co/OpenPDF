package com.reel.editor

import com.reel.editor.update.Updater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class UpdaterTest {
    private fun le(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()

    @Test fun appliesCopyAndDataOps() {
        val old = File.createTempFile("old", ".bin").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        val ops = ByteArrayOutputStream()
        ops.write('C'.code); ops.write(le(10)); ops.write(le(5))
        ops.write('D'.code); ops.write(le(3)); ops.write(byteArrayOf(7, 8, 9))
        ops.write('C'.code); ops.write(le(0)); ops.write(le(2))
        ops.write('E'.code)
        val patch = File.createTempFile("patch", ".rpatch")
        patch.outputStream().use { o -> o.write("RPT1".toByteArray()); DeflaterOutputStream(o).use { it.write(ops.toByteArray()) } }
        val out = File.createTempFile("new", ".bin")
        Updater.applyPatch(old, patch, out)
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 14, 7, 8, 9, 0, 1), out.readBytes())
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsCopiesOutsideTheOldFile() {
        val old = File.createTempFile("old", ".bin").apply { writeBytes(ByteArray(10)) }
        val ops = ByteArrayOutputStream().apply { write('C'.code); write(le(5)); write(le(50)); write('E'.code) }
        val patch = File.createTempFile("patch", ".rpatch")
        patch.outputStream().use { o -> o.write("RPT1".toByteArray()); DeflaterOutputStream(o).use { it.write(ops.toByteArray()) } }
        Updater.applyPatch(old, patch, File.createTempFile("next", ".bin"))
    }

    /** The patch maker in tools/update and the app's applier agree, on real zip files. */
    @Test fun pythonPatchRebuildsTheNewZip() {
        val script = listOf(File("../tools/update/make_update.py"), File("tools/update/make_update.py")).firstOrNull { it.exists() }
        assumeTrue(script != null && runCatching { ProcessBuilder("python3", "--version").start().waitFor() == 0 }.getOrDefault(false))
        val rnd = Random(3)
        val big = ByteArray(300_000).also { rnd.nextBytes(it) }
        fun zip(f: File, extra: ByteArray) = ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("model.bin").apply { method = ZipEntry.STORED; size = big.size.toLong(); compressedSize = big.size.toLong(); crc = java.util.zip.CRC32().apply { update(big) }.value })
            z.write(big); z.closeEntry()
            z.putNextEntry(ZipEntry("classes.dex")); z.write(extra); z.closeEntry()
        }
        val old = File.createTempFile("old", ".zip").also { zip(it, ByteArray(20_000).also { b -> rnd.nextBytes(b) }) }
        val new = File.createTempFile("new", ".zip").also { zip(it, ByteArray(30_000).also { b -> rnd.nextBytes(b) }) }
        val patch = File.createTempFile("patch", ".rpatch")
        val code = """
import importlib.util, sys
spec = importlib.util.spec_from_file_location('mu', sys.argv[1]); mu = importlib.util.module_from_spec(spec); spec.loader.exec_module(mu)
print(mu.make_patch(sys.argv[2], sys.argv[3], sys.argv[4]))
""".trimIndent()
        val p = ProcessBuilder("python3", "-c", code, script!!.absolutePath, old.absolutePath, new.absolutePath, patch.absolutePath).redirectErrorStream(true).start()
        val log = p.inputStream.readBytes().decodeToString()
        assertEquals(log, 0, p.waitFor())
        val out = File.createTempFile("out", ".zip")
        Updater.applyPatch(old, patch, out)
        assertArrayEquals(new.readBytes(), out.readBytes())
        // The big unchanged entry was copied, not shipped.
        assert(patch.length() < 60_000) { "patch ${patch.length()} bytes" }
    }
}
