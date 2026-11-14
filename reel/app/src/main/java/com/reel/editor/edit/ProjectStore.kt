package com.reel.editor.edit

import android.content.Context
import com.reel.editor.model.Project
import kotlinx.serialization.json.Json
import java.io.File

/** Drafts on disk: one JSON file per project, written atomically. */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<Project> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { json.decodeFromString<Project>(f.readText()) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun load(id: String): Project? = runCatching { json.decodeFromString<Project>(File(dir, "$id.json").readText()) }.getOrNull()

    fun save(project: Project) {
        val tmp = File(dir, "${project.id}.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        tmp.renameTo(File(dir, "${project.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
