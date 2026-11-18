package com.reel.editor

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Curved text: a positive curve is a smile (ends higher than the middle), negative an arch. */
@RunWith(AndroidJUnit4::class)
class CurveTextDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** Average height (y, down) of ink in the middle third versus the outer thirds. */
    private fun middleVsEnds(b: Bitmap): Pair<Double, Double> {
        fun inkY(x0: Int, x1: Int): Double {
            var s = 0.0; var n = 0
            for (x in x0 until x1) for (y in 0 until b.height) if ((b.getPixel(x, y) ushr 24) > 128) { s += y; n++ }
            return if (n == 0) 0.0 else s / n
        }
        val w = b.width
        val mid = inkY(w / 3, 2 * w / 3)
        val ends = (inkY(0, w / 6) + inkY(5 * w / 6, w)) / 2
        return mid to ends
    }

    @Test fun curveDirection() {
        com.reel.editor.fx.Fonts.init(ctx)
        for ((name, c) in listOf("smile" to 0.45f, "arch" to -0.45f)) {
            val b = LayerBitmaps.text(TextLayer(text = "CURVED TEXT", startUs = 0, durationUs = 1, style = TextStyle(sizeSp = 72f, curve = c, shadow = false)), -1, -1, 1280, 720)!!
            File(ctx.getExternalFilesDir(null), "curve_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val (mid, ends) = middleVsEnds(b)
            android.util.Log.i("CurveText", "$name ${b.width}x${b.height} middle=$mid ends=$ends")
            // y grows downward: a smile's middle sits lower (larger y) than its ends.
            if (c > 0) assertTrue("$name: middle $mid ends $ends", mid > ends) else assertTrue("$name: middle $mid ends $ends", mid < ends)
        }
    }
}
