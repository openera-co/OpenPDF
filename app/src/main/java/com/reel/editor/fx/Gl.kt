package com.reel.editor.fx

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal GLES 2 program wrapper. Unlike Media3's GlProgram it tolerates uniforms the
 * driver optimized away and supports bool/int vector uniforms (used by third-party transitions).
 */
class Prog(vertexSrc: String, fragmentSrc: String) {
    val id: Int
    private val uniforms = HashMap<String, Pair<Int, Int>>() // name -> (location, type)
    private val positionLoc: Int

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            throw GlFxException("link failed: $log")
        }
        val count = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_ACTIVE_UNIFORMS, count, 0)
        val len = IntArray(1)
        val size = IntArray(1)
        val type = IntArray(1)
        val name = ByteArray(256)
        for (i in 0 until count[0]) {
            GLES20.glGetActiveUniform(id, i, name.size, len, 0, size, 0, type, 0, name, 0)
            var n = String(name, 0, len[0])
            if (n.endsWith("[0]")) n = n.dropLast(3)
            uniforms[n] = GLES20.glGetUniformLocation(id, n) to type[0]
        }
        positionLoc = GLES20.glGetAttribLocation(id, "aFramePosition")
    }

    fun has(name: String) = uniforms.containsKey(name)

    fun use() = GLES20.glUseProgram(id)

    fun set(name: String, vararg v: Float) {
        val (loc, type) = uniforms[name] ?: return
        when (type) {
            GLES20.GL_FLOAT -> GLES20.glUniform1f(loc, v.getOrElse(0) { 0f })
            GLES20.GL_FLOAT_VEC2 -> GLES20.glUniform2f(loc, v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f })
            GLES20.GL_FLOAT_VEC3 -> GLES20.glUniform3f(loc, v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f })
            GLES20.GL_FLOAT_VEC4 -> GLES20.glUniform4f(loc, v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f }, v.getOrElse(3) { 0f })
            GLES20.GL_INT, GLES20.GL_BOOL -> GLES20.glUniform1i(loc, v.getOrElse(0) { 0f }.toInt())
            GLES20.GL_INT_VEC2, GLES20.GL_BOOL_VEC2 -> GLES20.glUniform2i(loc, v.getOrElse(0) { 0f }.toInt(), v.getOrElse(1) { 0f }.toInt())
            GLES20.GL_INT_VEC3, GLES20.GL_BOOL_VEC3 -> GLES20.glUniform3i(loc, v.getOrElse(0) { 0f }.toInt(), v.getOrElse(1) { 0f }.toInt(), v.getOrElse(2) { 0f }.toInt())
            GLES20.GL_INT_VEC4, GLES20.GL_BOOL_VEC4 -> GLES20.glUniform4i(loc, v.getOrElse(0) { 0f }.toInt(), v.getOrElse(1) { 0f }.toInt(), v.getOrElse(2) { 0f }.toInt(), v.getOrElse(3) { 0f }.toInt())
        }
    }

    fun texture(name: String, texId: Int, unit: Int) {
        val (loc, _) = uniforms[name] ?: return
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(loc, unit)
    }

    /** Draws the full-screen quad. */
    fun draw() {
        if (positionLoc >= 0) {
            GLES20.glEnableVertexAttribArray(positionLoc)
            GLES20.glVertexAttribPointer(positionLoc, 4, GLES20.GL_FLOAT, false, 0, QUAD)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (positionLoc >= 0) GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    fun release() = GLES20.glDeleteProgram(id)

    companion object {
        private val QUAD: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f))
            position(0)
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw GlFxException("compile failed: $log")
            }
            return s
        }
    }
}

class GlFxException(msg: String) : RuntimeException(msg)

object GlTex {
    /**
     * Creating a texture binds it on the active unit. Callers often create textures lazily while
     * other samplers are already bound, so creation always restores the previous binding.
     */
    private inline fun preservingBinding(block: () -> Int): Int {
        val prev = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, prev, 0)
        val id = block()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prev[0])
        return id
    }

    fun fromBitmap(bitmap: Bitmap, linear: Boolean = true): Int = preservingBinding {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val filter = if (linear) GLES20.GL_LINEAR else GLES20.GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        ids[0]
    }

    fun empty(width: Int, height: Int): Int = preservingBinding {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        ids[0]
    }

    fun delete(id: Int) {
        if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun framebuffer(texId: Int): Int {
        val fb = IntArray(1)
        GLES20.glGenFramebuffers(1, fb, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0)
        return fb[0]
    }
}

internal fun logFx(msg: String, t: Throwable? = null) {
    if (t != null) Log.w("ReelFx", msg, t) else Log.d("ReelFx", msg)
}
