package com.reel.editor.edit

import android.content.Context
import android.net.Uri
import com.reel.editor.model.Project
import com.reel.editor.model.ProjectUris
import com.reel.editor.model.newId
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A whole project in one .reelproj file: the project plus copies of every video, photo and sound
 * it uses, so it can be backed up or opened on another phone.
 */
object ProjectArchive {
    /** Packs [p] into a file in the cache (shared through the app's FileProvider). */
    fun export(context: Context, p: Project, progress: (Float) -> Unit = {}): File {
        val dir = File(context.cacheDir, "projects").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "${p.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "Reel" }}.reelproj")
        val uris = ProjectUris.all(p).toList()
        val names = HashMap<String, String>()
        ZipOutputStream(out.outputStream().buffered(1 shl 16)).use { zip ->
            for ((i, u) in uris.withIndex()) {
                val ext = extensionOf(context, u)
                val name = "media/$i$ext"
                val input = runCatching { context.contentResolver.openInputStream(Uri.parse(u)) }.getOrNull() ?: continue
                // Videos and sounds are already compressed: store them as they are.
                zip.putNextEntry(ZipEntry(name))
                input.use { it.copyTo(zip, 1 shl 16) }
                zip.closeEntry()
                names[u] = name
                progress((i + 1f) / (uris.size + 1))
            }
            val packed = ProjectUris.map(p) { names[it] ?: it }
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(ProjectStore.json.encodeToString(Project.serializer(), packed).toByteArray())
            zip.closeEntry()
        }
        progress(1f)
        return out
    }

    /** Unpacks a .reelproj into the app and returns the project (a new id, so nothing is overwritten). */
    fun import(context: Context, uri: Uri, store: ProjectStore): Project {
        val id = newId()
        val mediaDir = File(context.filesDir, "media/$id").apply { mkdirs() }
        var json: String? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered(1 shl 16)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "project.json" -> json = zip.readBytes().decodeToString()
                        e.name.startsWith("media/") && !e.name.contains("..") -> File(mediaDir, e.name.removePrefix("media/")).outputStream().use { zip.copyTo(it, 1 shl 16) }
                    }
                }
            }
        } ?: throw IllegalArgumentException("Can't open that file")
        val packed = ProjectStore.json.decodeFromString(Project.serializer(), json ?: throw IllegalArgumentException("Not a Reel project"))
        val p = ProjectUris.map(packed) { u ->
            if (u.startsWith("media/")) Uri.fromFile(File(mediaDir, u.removePrefix("media/"))).toString() else u
        }.copy(id = id, name = packed.name + " (imported)", updatedAt = System.currentTimeMillis())
        store.save(p)
        return p
    }

    private fun extensionOf(context: Context, u: String): String {
        val mime = runCatching { context.contentResolver.getType(Uri.parse(u)) }.getOrNull()
        return when {
            mime != null -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
            else -> u.substringAfterLast('/').substringAfterLast('.', "").let { if (it.length in 1..5) ".$it" else "" }
        }
    }
}
