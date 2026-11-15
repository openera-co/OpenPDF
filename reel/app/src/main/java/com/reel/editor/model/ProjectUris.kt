package com.reel.editor.model

/** Every media file a project points at, and a way to repoint them all (used to pack and unpack projects). */
object ProjectUris {
    /** Phone media, or a file packed inside a .reelproj ("media/…"). */
    private fun isMedia(u: String) = u.startsWith("content:") || u.startsWith("file:") || u.startsWith("media/")

    fun all(p: Project): Set<String> = buildSet {
        p.clips.forEach { c -> add(c.uri); c.reversedFrom?.let { add(it.uri) }; c.smoothFrom?.let { add(it.uri) }; c.cutout.imageUri?.let { add(it) } }
        p.audio.forEach { add(it.uri) }
        p.overlays.forEach { add(it.uri) }
        p.stickers.forEach { add(it.source) }
    }.filter(::isMedia).toSet()

    fun map(p: Project, f: (String) -> String): Project {
        fun m(u: String) = if (isMedia(u)) f(u) else u
        return p.copy(
            clips = p.clips.map { c ->
                c.copy(uri = m(c.uri), reversedFrom = c.reversedFrom?.let { it.copy(uri = m(it.uri)) }, smoothFrom = c.smoothFrom?.let { it.copy(uri = m(it.uri)) }, cutout = c.cutout.copy(imageUri = c.cutout.imageUri?.let(::m)))
            },
            audio = p.audio.map { it.copy(uri = m(it.uri)) },
            overlays = p.overlays.map { it.copy(uri = m(it.uri)) },
            stickers = p.stickers.map { it.copy(source = m(it.source)) },
            texts = p.texts.map { t -> t.copy(anchorUri = t.anchorUri?.let(::m)) },
        )
    }
}
