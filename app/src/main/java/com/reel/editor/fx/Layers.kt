package com.reel.editor.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.reel.editor.model.TextAlignment
import com.reel.editor.model.TextAnim
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Text fonts: the phone's own families plus 55 bundled open-licence families (Google Fonts, SIL OFL
 * and Apache 2.0, licences in assets/fonts/licenses). Characters a bundled font lacks (for example
 * Arabic, Devanagari or CJK) fall back to the system fonts automatically.
 */
object Fonts {
    enum class Category(val label: String) { SANS("Sans"), DISPLAY("Bold"), SERIF("Serif"), SCRIPT("Script"), RETRO("Retro"), SYSTEM("Basic") }

    /**
     * [family] for system fonts, or [asset] for bundled ones. Variable fonts set [weight] (and use
     * [boldWeight] for bold); static ones may ship a separate [boldAsset].
     */
    data class Font(
        val id: String,
        val label: String,
        val category: Category,
        val family: String? = null,
        val style: Int = Typeface.NORMAL,
        val asset: String? = null,
        val boldAsset: String? = null,
        val weight: Int? = null,
        val boldWeight: Int = 800,
    )

    private fun b(id: String, label: String, cat: Category, weight: Int? = null, bold: Boolean = false, boldWeight: Int = 800) =
        Font(id, label, cat, asset = "fonts/$id.ttf", boldAsset = if (bold) "fonts/$id-bold.ttf" else null, weight = weight, boldWeight = boldWeight)

    private val system = listOf(
        Font("sans-bold", "Bold", Category.SYSTEM, "sans-serif", Typeface.BOLD),
        Font("sans", "Regular", Category.SYSTEM, "sans-serif"),
        Font("black", "Black", Category.SYSTEM, "sans-serif-black"),
        Font("medium", "Medium", Category.SYSTEM, "sans-serif-medium"),
        Font("condensed", "Condensed", Category.SYSTEM, "sans-serif-condensed", Typeface.BOLD),
        Font("light", "Light", Category.SYSTEM, "sans-serif-light"),
        Font("serif", "Serif", Category.SYSTEM, "serif", Typeface.BOLD),
        Font("serif-italic", "Serif italic", Category.SYSTEM, "serif", Typeface.BOLD_ITALIC),
        Font("mono", "Mono", Category.SYSTEM, "monospace", Typeface.BOLD),
        Font("casual", "Casual", Category.SYSTEM, "casual"),
        Font("cursive", "Script", Category.SYSTEM, "cursive", Typeface.BOLD),
        Font("smallcaps", "Small caps", Category.SYSTEM, "sans-serif-smallcaps"),
    )

    private val bundled = listOf(
        b("montserrat", "Montserrat", Category.SANS, 700, boldWeight = 900),
        b("poppins", "Poppins", Category.SANS, bold = true),
        b("inter", "Inter", Category.SANS, 600, boldWeight = 900),
        b("rubik", "Rubik", Category.SANS, 600, boldWeight = 900),
        b("raleway", "Raleway", Category.SANS, 700, boldWeight = 900),
        b("lato", "Lato", Category.SANS, bold = true),
        b("nunito", "Nunito", Category.SANS, 700, boldWeight = 1000),
        b("worksans", "Work Sans", Category.SANS, 600, boldWeight = 900),
        b("spacegrotesk", "Space Grotesk", Category.SANS, 600, boldWeight = 700),
        b("dmsans", "DM Sans", Category.SANS, 600, boldWeight = 1000),
        b("archivoblack", "Archivo Black", Category.DISPLAY),
        b("anton", "Anton", Category.DISPLAY),
        b("bebasneue", "Bebas Neue", Category.DISPLAY),
        b("oswald", "Oswald", Category.DISPLAY, 600, boldWeight = 700),
        b("teko", "Teko", Category.DISPLAY, 600, boldWeight = 700),
        b("russoone", "Russo One", Category.DISPLAY),
        b("bungee", "Bungee", Category.DISPLAY),
        b("righteous", "Righteous", Category.DISPLAY),
        b("fredoka", "Fredoka", Category.DISPLAY, 600, boldWeight = 700),
        b("comfortaa", "Comfortaa", Category.DISPLAY, 700, boldWeight = 700),
        b("baloo2", "Baloo", Category.DISPLAY, 700, boldWeight = 800),
        b("luckiestguy", "Luckiest Guy", Category.DISPLAY),
        b("bangers", "Bangers", Category.DISPLAY),
        b("blackopsone", "Black Ops", Category.DISPLAY),
        b("orbitron", "Orbitron", Category.DISPLAY, 700, boldWeight = 900),
        b("monoton", "Monoton", Category.DISPLAY),
        b("rubikmonoone", "Rubik Mono", Category.DISPLAY),
        b("staatliches", "Staatliches", Category.DISPLAY),
        b("titanone", "Titan One", Category.DISPLAY),
        b("playfairdisplay", "Playfair", Category.SERIF, 700, boldWeight = 900),
        b("merriweather", "Merriweather", Category.SERIF, 700, boldWeight = 900),
        b("lora", "Lora", Category.SERIF, 600, boldWeight = 700),
        b("cinzel", "Cinzel", Category.SERIF, 700, boldWeight = 900),
        b("abrilfatface", "Abril Fatface", Category.SERIF),
        b("dmserifdisplay", "DM Serif", Category.SERIF),
        b("robotoslab", "Roboto Slab", Category.SERIF, 700, boldWeight = 900),
        b("ebgaramond", "Garamond", Category.SERIF, 600, boldWeight = 800),
        b("alfaslabone", "Alfa Slab", Category.SERIF),
        b("pacifico", "Pacifico", Category.SCRIPT),
        b("lobster", "Lobster", Category.SCRIPT),
        b("dancingscript", "Dancing", Category.SCRIPT, 700, boldWeight = 700),
        b("caveat", "Caveat", Category.SCRIPT, 700, boldWeight = 700),
        b("satisfy", "Satisfy", Category.SCRIPT),
        b("greatvibes", "Great Vibes", Category.SCRIPT),
        b("permanentmarker", "Marker", Category.SCRIPT),
        b("kalam", "Kalam", Category.SCRIPT, bold = true),
        b("shadowsintolight", "Shadows", Category.SCRIPT),
        b("indieflower", "Indie Flower", Category.SCRIPT),
        b("amaticsc", "Amatic", Category.SCRIPT, bold = true),
        b("sacramento", "Sacramento", Category.SCRIPT),
        b("yellowtail", "Yellowtail", Category.SCRIPT),
        b("pressstart2p", "Press Start", Category.RETRO),
        b("vt323", "VT323", Category.RETRO),
        b("spacemono", "Space Mono", Category.RETRO, bold = true),
        b("silkscreen", "Silkscreen", Category.RETRO, bold = true),
    )

    val all = system + bundled
    private val byId = all.associateBy { it.id }

    @Volatile private var assets: android.content.res.AssetManager? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    /** Call once at startup so bundled fonts can be read. */
    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    fun font(id: String): Font = byId[id] ?: system[0]

    fun typeface(style: TextStyle): Typeface = typeface(style.font, style.bold, style.italic)

    fun typeface(id: String, bold: Boolean = false, italic: Boolean = false): Typeface {
        val f = font(id)
        return cache.getOrPut("$id|$bold|$italic") { load(f, bold, italic) }
    }

    private fun load(f: Font, boldIn: Boolean, italicIn: Boolean): Typeface {
        val am = assets
        if (f.asset == null || am == null) {
            val bold = boldIn || (f.style and Typeface.BOLD) != 0
            val italic = italicIn || (f.style and Typeface.ITALIC) != 0
            return Typeface.create(f.family ?: "sans-serif", styleOf(bold, italic))
        }
        val base = try {
            when {
                f.weight != null -> Typeface.Builder(am, f.asset)
                    .setFontVariationSettings("'wght' ${if (boldIn) f.boldWeight else f.weight}").build()
                boldIn && f.boldAsset != null -> Typeface.createFromAsset(am, f.boldAsset)
                else -> Typeface.createFromAsset(am, f.asset)
            }
        } catch (e: Exception) {
            logFx("font ${f.id} unavailable", e)
            return Typeface.create("sans-serif", styleOf(boldIn, italicIn))
        } ?: Typeface.DEFAULT
        // Bold is native where the font has it; otherwise (and for italic) Android synthesizes it.
        val synthBold = boldIn && f.weight == null && f.boldAsset == null
        return if (synthBold || italicIn) Typeface.create(base, styleOf(synthBold, italicIn)) else base
    }

    private fun styleOf(bold: Boolean, italic: Boolean) = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
}

/** CPU rendering of text and sticker layers into premultiplied bitmaps. */
object LayerBitmaps {
    /** Pixel size of 1 style unit, relative to canvas height so text keeps its look at any export size. */
    fun pxPerUnit(canvasHeight: Int) = canvasHeight / 1280f

    fun text(layer: TextLayer, visibleChars: Int, highlightWord: Int, canvasHeight: Int): Bitmap? {
        val st = layer.style
        val raw = layer.text.ifEmpty { " " }
        val shown = if (visibleChars in 0 until raw.length) raw.substring(0, visibleChars) else raw
        val unit = pxPerUnit(canvasHeight)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Fonts.typeface(st)
            textSize = st.sizeSp * unit
            letterSpacing = st.letterSpacing
            color = st.color.toInt()
        }
        val maxWidth = (canvasHeight * 0.95f * 9f / 16f * 1.6f).toInt().coerceAtLeast(64)
        val measureWidth = min(maxWidth, ceil(StaticLayout.getDesiredWidth(raw, paint)).toInt() + 2)
        val align = when (st.align) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        // Lay out the full text so typewriter reveal never reflows.
        val fullLayout = StaticLayout.Builder.obtain(raw, 0, raw.length, paint, measureWidth.coerceAtLeast(1))
            .setAlignment(align).setLineSpacing(0f, st.lineSpacing).setIncludePad(true).build()
        val pad = (st.strokeWidth * unit * 2 + (if (st.shadow) 10 * unit else 0f) + (if (st.labelColor != null) 18 * unit else 0f)).toInt() + 4
        val w = fullLayout.width + pad * 2
        val h = fullLayout.height + pad * 2
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        st.labelColor?.let { lc ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lc.toInt() }
            val r = 14 * unit
            canvas.drawRoundRect(RectF(pad / 3f, pad / 3f, w - pad / 3f, h - pad / 3f), r, r, p)
        }
        canvas.translate(pad.toFloat(), pad.toFloat())
        val layout = if (shown.length == raw.length) fullLayout else
            StaticLayout.Builder.obtain(shown, 0, shown.length, paint, fullLayout.width).setAlignment(align)
                .setLineSpacing(0f, st.lineSpacing).setIncludePad(true).build()
        if (st.strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = st.strokeWidth * unit * 2
            paint.color = st.strokeColor.toInt()
            layout.draw(canvas)
            paint.style = Paint.Style.FILL
        }
        paint.color = st.color.toInt()
        if (st.shadow) paint.setShadowLayer(8 * unit, 0f, 3 * unit, Color.argb(140, 0, 0, 0))
        layout.draw(canvas)
        paint.clearShadowLayer()
        if (highlightWord >= 0 && layer.words.isNotEmpty()) {
            // Karaoke highlight: redraw the active word in the accent color over itself.
            var offset = 0
            for ((i, wt) in layer.words.withIndex()) {
                val at = raw.indexOf(wt.text, offset)
                if (at < 0) continue
                offset = at + wt.text.length
                if (i != highlightWord || offset > shown.length) continue
                val line = layout.getLineForOffset(at)
                val x = layout.getPrimaryHorizontal(at)
                val y = layout.getLineBaseline(line).toFloat()
                paint.color = 0xFFFFD36B.toInt()
                canvas.drawText(raw, at, offset, x, y, paint)
            }
        }
        return bmp
    }

    val stickerShapes = listOf("heart", "star", "sparkle", "arrow", "circle", "bubble", "sun", "bolt", "check", "fire", "music", "smile")

    fun sticker(context: Context, source: String, canvasHeight: Int): Bitmap? {
        if (source.startsWith("shape:")) return shape(source.removePrefix("shape:"), (canvasHeight * 0.5f).toInt().coerceIn(64, 1024))
        return try {
            val uri = Uri.parse(source)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / sample > 1536) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            logFx("sticker decode failed", e)
            null
        }
    }

    fun shape(name: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.05f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE }
        val path = Path()
        when (name) {
            "heart" -> {
                fill.color = 0xFFFF4D6D.toInt()
                path.moveTo(s * 0.5f, s * 0.85f)
                path.cubicTo(s * 0.05f, s * 0.55f, s * 0.15f, s * 0.12f, s * 0.5f, s * 0.32f)
                path.cubicTo(s * 0.85f, s * 0.12f, s * 0.95f, s * 0.55f, s * 0.5f, s * 0.85f)
                c.drawPath(path, fill)
            }
            "star", "sparkle" -> {
                fill.color = if (name == "star") 0xFFFFD36B.toInt() else Color.WHITE
                val points = if (name == "star") 5 else 4
                val inner = if (name == "star") 0.2f else 0.08f
                for (i in 0 until points * 2) {
                    val r = if (i % 2 == 0) s * 0.46f else s * inner
                    val a = -PI / 2 + i * PI / points
                    val x = s / 2 + (r * cos(a)).toFloat()
                    val y = s / 2 + (r * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, fill)
            }
            "arrow" -> {
                stroke.strokeWidth = s * 0.09f
                path.moveTo(s * 0.12f, s * 0.5f); path.lineTo(s * 0.85f, s * 0.5f)
                path.moveTo(s * 0.6f, s * 0.25f); path.lineTo(s * 0.86f, s * 0.5f); path.lineTo(s * 0.6f, s * 0.75f)
                c.drawPath(path, stroke)
            }
            "circle" -> { stroke.strokeWidth = s * 0.07f; c.drawCircle(s / 2, s / 2, s * 0.4f, stroke) }
            "bubble" -> {
                fill.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.08f, s * 0.12f, s * 0.92f, s * 0.68f), s * 0.16f, s * 0.16f, fill)
                path.moveTo(s * 0.3f, s * 0.66f); path.lineTo(s * 0.24f, s * 0.9f); path.lineTo(s * 0.48f, s * 0.66f); path.close()
                c.drawPath(path, fill)
            }
            "sun" -> {
                fill.color = 0xFFFFB547.toInt()
                c.drawCircle(s / 2, s / 2, s * 0.2f, fill)
                stroke.color = 0xFFFFB547.toInt()
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    c.drawLine(s / 2 + (s * 0.3f * cos(a)).toFloat(), s / 2 + (s * 0.3f * sin(a)).toFloat(), s / 2 + (s * 0.44f * cos(a)).toFloat(), s / 2 + (s * 0.44f * sin(a)).toFloat(), stroke)
                }
            }
            "bolt" -> {
                fill.color = 0xFFFFD36B.toInt()
                path.moveTo(s * 0.56f, s * 0.06f); path.lineTo(s * 0.2f, s * 0.56f); path.lineTo(s * 0.48f, s * 0.56f)
                path.lineTo(s * 0.4f, s * 0.94f); path.lineTo(s * 0.8f, s * 0.42f); path.lineTo(s * 0.52f, s * 0.42f); path.close()
                c.drawPath(path, fill)
            }
            "check" -> {
                fill.color = 0xFF3DDC84.toInt()
                c.drawCircle(s / 2, s / 2, s * 0.44f, fill)
                stroke.strokeWidth = s * 0.08f
                path.moveTo(s * 0.3f, s * 0.52f); path.lineTo(s * 0.45f, s * 0.67f); path.lineTo(s * 0.72f, s * 0.36f)
                c.drawPath(path, stroke)
            }
            "fire" -> {
                fill.color = 0xFFFF7A2B.toInt()
                path.moveTo(s * 0.5f, s * 0.05f)
                path.cubicTo(s * 0.8f, s * 0.35f, s * 0.86f, s * 0.6f, s * 0.78f, s * 0.75f)
                path.cubicTo(s * 0.7f, s * 0.95f, s * 0.3f, s * 0.95f, s * 0.22f, s * 0.75f)
                path.cubicTo(s * 0.14f, s * 0.55f, s * 0.3f, s * 0.4f, s * 0.36f, s * 0.3f)
                path.cubicTo(s * 0.42f, s * 0.45f, s * 0.5f, s * 0.45f, s * 0.5f, s * 0.05f)
                c.drawPath(path, fill)
                fill.color = 0xFFFFD36B.toInt()
                c.drawOval(RectF(s * 0.38f, s * 0.55f, s * 0.62f, s * 0.88f), fill)
            }
            "music" -> {
                fill.color = Color.WHITE
                c.drawOval(RectF(s * 0.18f, s * 0.62f, s * 0.42f, s * 0.82f), fill)
                c.drawOval(RectF(s * 0.58f, s * 0.52f, s * 0.82f, s * 0.72f), fill)
                stroke.strokeWidth = s * 0.06f
                path.moveTo(s * 0.39f, s * 0.72f); path.lineTo(s * 0.39f, s * 0.22f); path.lineTo(s * 0.79f, s * 0.14f); path.lineTo(s * 0.79f, s * 0.62f)
                c.drawPath(path, stroke)
            }
            else -> {
                fill.color = 0xFFFFD36B.toInt()
                c.drawCircle(s / 2, s / 2, s * 0.44f, fill)
                fill.color = 0xFF1E1A2E.toInt()
                c.drawCircle(s * 0.36f, s * 0.42f, s * 0.05f, fill)
                c.drawCircle(s * 0.64f, s * 0.42f, s * 0.05f, fill)
                stroke.color = 0xFF1E1A2E.toInt()
                stroke.strokeWidth = s * 0.05f
                c.drawArc(RectF(s * 0.3f, s * 0.4f, s * 0.7f, s * 0.72f), 20f, 140f, false, stroke)
            }
        }
        return bmp
    }
}

/** Per-frame motion of a text or sticker layer for its in/out/loop animations. */
data class LayerMotion(
    val opacity: Float = 1f,
    val scale: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotationDeg: Float = 0f,
    val reveal: Float = 1f,
)

object TextMotion {
    val inAnims = listOf("none", "fade", "pop", "slideup", "slidedown", "slideleft", "slideright", "typewriter", "bounce", "zoom", "spin", "drop", "glitch", "wipe")
    val outAnims = listOf("none", "fade", "pop", "slideup", "slidedown", "slideleft", "slideright", "zoom", "spin", "drop", "glitch")
    val loopAnims = listOf("none", "bob", "pulse", "swing", "shake", "blink", "float")

    fun label(id: String) = when (id) {
        "none" -> "None"; "fade" -> "Fade"; "pop" -> "Pop"; "slideup" -> "Slide up"; "slidedown" -> "Slide down"
        "slideleft" -> "Slide left"; "slideright" -> "Slide right"; "typewriter" -> "Typewriter"; "bounce" -> "Bounce"
        "zoom" -> "Zoom"; "spin" -> "Spin"; "drop" -> "Drop"; "glitch" -> "Glitch"; "wipe" -> "Wipe"; "bob" -> "Bob"
        "pulse" -> "Pulse"; "swing" -> "Swing"; "shake" -> "Shake"; "blink" -> "Blink"; "float" -> "Float"
        else -> id
    }

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    /** [p] goes 0 to 1 as the layer becomes fully visible (for out animations the caller passes 1 to 0). */
    fun appear(id: String, p: Float, timeSec: Float): LayerMotion {
        val t = p.coerceIn(0f, 1f)
        val e = easeOut(t)
        return when (id) {
            "fade" -> LayerMotion(opacity = t)
            "pop" -> {
                val s = if (t < 0.7f) 1.15f * easeOut(t / 0.7f) else 1.15f - 0.15f * ((t - 0.7f) / 0.3f)
                LayerMotion(opacity = min(1f, t * 3f), scale = max(0.01f, s))
            }
            "slideup" -> LayerMotion(opacity = t, dy = -(1f - e) * 0.12f)
            "slidedown" -> LayerMotion(opacity = t, dy = (1f - e) * 0.12f)
            "slideleft" -> LayerMotion(opacity = t, dx = (1f - e) * 0.3f)
            "slideright" -> LayerMotion(opacity = t, dx = -(1f - e) * 0.3f)
            "typewriter" -> LayerMotion(reveal = t)
            "bounce" -> {
                val y = if (t < 1f) abs(cos(t * PI.toFloat() * 2.5f)) * (1f - t) * 0.15f else 0f
                LayerMotion(opacity = min(1f, t * 4f), dy = y)
            }
            "zoom" -> LayerMotion(opacity = t, scale = 0.3f + 0.7f * e)
            "spin" -> LayerMotion(opacity = t, scale = 0.4f + 0.6f * e, rotationDeg = -(1f - e) * 180f)
            "drop" -> LayerMotion(opacity = min(1f, t * 2f), dy = (1f - e) * 0.3f, scale = 1f + (1f - e) * 0.3f)
            "glitch" -> {
                val k = 1f - t
                val j = ((timeSec * 37f) % 1f - 0.5f) * 0.06f * k
                LayerMotion(opacity = if (k > 0.05f && ((timeSec * 20).toInt() % 3 == 0)) 0.4f else 1f, dx = j)
            }
            "wipe" -> LayerMotion(reveal = t)
            else -> LayerMotion()
        }
    }

    fun loop(id: String, timeSec: Float): LayerMotion = when (id) {
        "bob" -> LayerMotion(dy = sin(timeSec * 3f) * 0.012f)
        "pulse" -> LayerMotion(scale = 1f + 0.06f * sin(timeSec * 5f))
        "swing" -> LayerMotion(rotationDeg = sin(timeSec * 3f) * 8f)
        "shake" -> LayerMotion(dx = sin(timeSec * 60f) * 0.006f, dy = cos(timeSec * 47f) * 0.004f)
        "blink" -> LayerMotion(opacity = if ((timeSec * 2f) % 1f < 0.75f) 1f else 0.15f)
        "float" -> LayerMotion(dy = sin(timeSec * 1.6f) * 0.02f, rotationDeg = sin(timeSec * 1.1f) * 3f)
        else -> LayerMotion()
    }

    fun combine(a: LayerMotion, b: LayerMotion) = LayerMotion(
        opacity = a.opacity * b.opacity,
        scale = a.scale * b.scale,
        dx = a.dx + b.dx,
        dy = a.dy + b.dy,
        rotationDeg = a.rotationDeg + b.rotationDeg,
        reveal = min(a.reveal, b.reveal),
    )

    /** Motion of a layer at [localUs] from its start, for a layer lasting [durationUs]. */
    fun at(inAnim: TextAnim?, outAnim: TextAnim?, loopAnim: TextAnim?, localUs: Long, durationUs: Long): LayerMotion {
        val tSec = localUs / 1e6f
        var m = LayerMotion()
        if (inAnim != null && inAnim.id != "none" && inAnim.durationUs > 0) {
            val d = min(inAnim.durationUs, durationUs / 2).coerceAtLeast(1)
            if (localUs < d) m = combine(m, appear(inAnim.id, localUs.toFloat() / d, tSec))
        }
        if (outAnim != null && outAnim.id != "none" && outAnim.durationUs > 0) {
            val d = min(outAnim.durationUs, durationUs / 2).coerceAtLeast(1)
            val left = durationUs - localUs
            if (left < d) m = combine(m, appear(outAnim.id, left.toFloat() / d, tSec))
        }
        if (loopAnim != null && loopAnim.id != "none") m = combine(m, loop(loopAnim.id, tSec))
        return m.copy(opacity = m.opacity.coerceIn(0f, 1f))
    }
}
