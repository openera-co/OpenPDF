package com.reel.editor

import android.content.Context
import com.reel.editor.model.AspectRatio

/** App-wide choices kept between sessions. */
object Settings {
    private const val NAME = "settings"
    private fun prefs(c: Context) = c.getSharedPreferences(NAME, 0)

    fun defaultRatio(c: Context): AspectRatio = runCatching { AspectRatio.valueOf(prefs(c).getString("ratio", null) ?: "R9_16") }.getOrDefault(AspectRatio.R9_16)
    fun setDefaultRatio(c: Context, r: AspectRatio) = prefs(c).edit().putString("ratio", r.name).apply()

    fun exportHeight(c: Context): Int = prefs(c).getInt("exportHeight", 1080)
    fun setExportHeight(c: Context, h: Int) = prefs(c).edit().putInt("exportHeight", h).apply()

    fun exportFps(c: Context): Int = prefs(c).getInt("exportFps", 30)
    fun setExportFps(c: Context, f: Int) = prefs(c).edit().putInt("exportFps", f).apply()
}
