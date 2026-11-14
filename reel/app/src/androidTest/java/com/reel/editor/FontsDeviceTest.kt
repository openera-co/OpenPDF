package com.reel.editor

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.fx.Fonts
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Every bundled font loads, really differs from the default, has a bold, and renders text. */
@RunWith(AndroidJUnit4::class)
class FontsDeviceTest {
    @Test
    fun allFontsLoadAndRender() {
        Fonts.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val sample = "Reel Video 2026"
        fun width(tf: Typeface) = Paint().apply { typeface = tf; textSize = 64f }.measureText(sample)
        val defaultW = width(Typeface.DEFAULT)
        val same = ArrayList<String>()
        for (f in Fonts.all) {
            val tf = Fonts.typeface(f.id)
            val bold = Fonts.typeface(f.id, bold = true)
            val w = width(tf)
            val bw = width(bold)
            if (f.asset != null && kotlin.math.abs(w - defaultW) < 0.5f) same += f.id
            Log.i(TAG, "FONT ${f.id} w=${"%.0f".format(w)} bold=${"%.0f".format(bw)}")
            val bmp = LayerBitmaps.text(TextLayer(text = sample, startUs = 0, durationUs = 1, style = TextStyle(font = f.id, bold = true, italic = true)), -1, -1, 1280)
            assertNotNull("${f.id} did not render", bmp)
            assertTrue(bmp!!.width > 50 && bmp.height > 20)
        }
        Log.i(TAG, "FONTS ${Fonts.all.size} checked; identical to default: $same")
        assertTrue("fonts that fell back to the default: $same", same.isEmpty())
        assertTrue(Fonts.all.size >= 60)
    }

    companion object {
        const val TAG = "ReelFontsTest"
    }
}
