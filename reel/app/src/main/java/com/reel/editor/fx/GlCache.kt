package com.reel.editor.fx

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLContext
import java.util.concurrent.ConcurrentHashMap

/**
 * GL objects live in the context that created them. Media3 keeps one context per playback or
 * export session, while it creates a fresh shader program for every media item. Caching compiled
 * programs, uploaded layers and transition frames per context removes the compile hitch at clip
 * boundaries.
 */
object GlCache {
    private class Ctx(val handle: EGLContext) {
        val programs = HashMap<String, Prog?>()
        val textures = HashMap<String, Tex>()
        val frames = LinkedHashMap<String, Tex>()
    }

    data class Tex(val id: Int, val width: Int, val height: Int)

    private val contexts = ConcurrentHashMap<Long, Ctx>()

    private fun ctx(): Ctx {
        val c = EGL14.eglGetCurrentContext()
        val key = c.nativeHandle
        return contexts.getOrPut(key) {
            // A new context means older ones are gone; drop their bookkeeping (objects died with them).
            if (contexts.size > 3) contexts.clear()
            Ctx(c)
        }
    }

    /** Returns a compiled program, or null if this source failed before (never retried). */
    fun program(key: String, vertex: String, fragment: () -> String): Prog? {
        val c = ctx()
        if (c.programs.containsKey(key)) return c.programs[key]
        val p = try {
            Prog(vertex, fragment())
        } catch (e: Exception) {
            logFx("shader $key unavailable", e)
            null
        }
        c.programs[key] = p
        return p
    }

    fun texture(key: String, make: () -> Bitmap?): Tex? {
        val c = ctx()
        c.textures[key]?.let { return it }
        val bmp = make() ?: return null
        val t = Tex(GlTex.fromBitmap(bmp), bmp.width, bmp.height)
        c.textures[key] = t
        // Bound the number of resident layer textures.
        if (c.textures.size > 96) {
            val oldest = c.textures.keys.first()
            c.textures.remove(oldest)?.let { GlTex.delete(it.id) }
        }
        return t
    }

    /** Transition source frames, keyed by the clip that produced them. Keeps the two most recent. */
    fun frame(key: String): Tex? = ctx().frames[key]

    fun frameTarget(key: String, width: Int, height: Int): Tex {
        val c = ctx()
        c.frames[key]?.let { if (it.width == width && it.height == height) return it else { GlTex.delete(it.id); c.frames.remove(key) } }
        val t = Tex(GlTex.empty(width, height), width, height)
        c.frames[key] = t
        while (c.frames.size > 2) {
            val oldest = c.frames.keys.first()
            c.frames.remove(oldest)?.let { GlTex.delete(it.id) }
        }
        return t
    }
}
