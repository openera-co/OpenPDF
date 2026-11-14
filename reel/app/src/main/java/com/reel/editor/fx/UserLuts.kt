package com.reel.editor.fx

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Colour looks the user imports as .cube files (the format most LUT packs ship in). They show up
 * next to the built-in looks, with ids "user:<name>", and work anywhere a look does.
 */
object UserLuts {
    class Cube(val size: Int, val data: FloatArray, val min: FloatArray, val max: FloatArray)

    private lateinit var dir: File
    private val parsed = HashMap<String, Cube>()

    fun init(context: Context) { dir = File(context.filesDir, "luts").apply { mkdirs() } }

    fun list(): List<LookDef> = if (!::dir.isInitialized) emptyList() else
        dir.listFiles { f -> f.extension == "cube" }.orEmpty().sortedBy { it.lastModified() }.map { def(it.nameWithoutExtension) }

    private fun def(name: String) = LookDef(-1, "user:$name", name, "Mine")

    fun find(id: String): LookDef? = if (!id.startsWith("user:") || !::dir.isInitialized) null
        else id.removePrefix("user:").let { n -> if (File(dir, "$n.cube").exists()) def(n) else null }

    /** Copies a .cube into the app after checking it parses. Returns the new look. */
    fun import(context: Context, uri: Uri, displayName: String?): LookDef {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IllegalArgumentException("Can't open that file")
        val cube = parse(text)
        val base = (displayName ?: "Look").substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Look" }.take(24)
        var name = base
        var i = 2
        while (File(dir, "$name.cube").exists()) name = "$base ${i++}"
        File(dir, "$name.cube").writeText(text)
        synchronized(parsed) { parsed[name] = cube }
        return def(name)
    }

    fun delete(id: String) {
        val n = id.removePrefix("user:")
        File(dir, "$n.cube").delete()
        synchronized(parsed) { parsed.remove(n) }
    }

    /** Parses a 3D .cube LUT (red changes fastest). */
    fun parse(text: String): Cube {
        var size = 0
        val min = floatArrayOf(0f, 0f, 0f)
        val max = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0].uppercase()) {
                "TITLE" -> {}
                "LUT_1D_SIZE" -> throw IllegalArgumentException("Only 3D .cube looks are supported")
                "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull() ?: 0
                "DOMAIN_MIN" -> for (k in 0..2) min[k] = parts.getOrNull(k + 1)?.toFloatOrNull() ?: 0f
                "DOMAIN_MAX" -> for (k in 0..2) max[k] = parts.getOrNull(k + 1)?.toFloatOrNull() ?: 1f
                else -> {
                    if (parts.size < 3) continue
                    val r = parts[0].toFloatOrNull() ?: continue
                    values += r; values += parts[1].toFloat(); values += parts[2].toFloat()
                }
            }
        }
        require(size in 2..256) { "Not a .cube look" }
        require(values.size >= size * size * size * 3) { "This .cube file is incomplete" }
        return Cube(size, values.toFloatArray(), min, max)
    }

    private fun cube(id: String): Cube? {
        val n = id.removePrefix("user:")
        synchronized(parsed) { parsed[n]?.let { return it } }
        val f = File(dir, "$n.cube")
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()?.also { synchronized(parsed) { parsed[n] = it } }
    }

    /**
     * The look as Reel's LUT image: [n]x(n*n) ARGB pixels, row = r*n + g, column = b, resampled
     * from the file's own size with trilinear interpolation.
     */
    fun pixels(id: String, n: Int): IntArray? {
        val c = cube(id) ?: return null
        return toPixels(c, n)
    }

    fun toPixels(c: Cube, n: Int): IntArray {
        val s = c.size
        val out = IntArray(n * n * n)
        fun at(r: Int, g: Int, b: Int, ch: Int) = c.data[((b * s + g) * s + r) * 3 + ch]
        for (ri in 0 until n) for (gi in 0 until n) for (bi in 0 until n) {
            // Input colour in the LUT's own domain, then position in its grid.
            val pos = FloatArray(3)
            val inp = floatArrayOf(ri / (n - 1f), gi / (n - 1f), bi / (n - 1f))
            for (k in 0..2) pos[k] = ((inp[k] - c.min[k]) / (c.max[k] - c.min[k]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f) * (s - 1)
            val r0 = pos[0].toInt().coerceAtMost(s - 2); val g0 = pos[1].toInt().coerceAtMost(s - 2); val b0 = pos[2].toInt().coerceAtMost(s - 2)
            val fr = pos[0] - r0; val fg = pos[1] - g0; val fb = pos[2] - b0
            var px = 0xFF shl 24
            for (ch in 0..2) {
                fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
                val c00 = l(at(r0, g0, b0, ch), at(r0 + 1, g0, b0, ch), fr)
                val c10 = l(at(r0, g0 + 1, b0, ch), at(r0 + 1, g0 + 1, b0, ch), fr)
                val c01 = l(at(r0, g0, b0 + 1, ch), at(r0 + 1, g0, b0 + 1, ch), fr)
                val c11 = l(at(r0, g0 + 1, b0 + 1, ch), at(r0 + 1, g0 + 1, b0 + 1, ch), fr)
                val v = l(l(c00, c10, fg), l(c01, c11, fg), fb)
                px = px or ((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl (16 - 8 * ch))
            }
            out[(ri * n + gi) * n + bi] = px
        }
        return out
    }
}
