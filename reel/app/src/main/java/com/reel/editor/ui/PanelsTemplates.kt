package com.reel.editor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reel.editor.edit.EditorViewModel
import com.reel.editor.fx.LayerBitmaps
import com.reel.editor.model.ShapeLayer
import com.reel.editor.model.TextLayer
import com.reel.editor.model.TextStyle
import com.reel.editor.model.TextTemplates
import com.reel.editor.model.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text templates: a plain text tile, then ready-made designs by category. */
@Composable
fun TextTemplatesPanel(vm: EditorViewModel, onClose: () -> Unit) {
    var cat by remember { mutableStateOf(TextTemplates.categories.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeader("Text", Icons.Rounded.TextFields, iconTint = C.TextTrack, onClose = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextTemplates.categories.forEach { Chip(it, it == cat) { cat = it } }
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(min = 140.dp, max = 300.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "plain") { PlainTile { vm.addText() } }
            items(TextTemplates.all.filter { it.category == cat }, key = { it.id }) { t -> TemplateTile(t) { vm.addTextTemplate(t) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlainTile(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(12.dp)).background(C.Surface2).clickable(onClickLabel = "Plain text", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("Aa", style = Body.copy(fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp))) }
}

@Composable
private fun TemplateTile(t: TextTemplates.Template, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(TemplateThumbs.cached(t.id), t.id) {
        if (value == null) value = withContext(Dispatchers.Default) { TemplateThumbs.render(t) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(12.dp)).background(Color(t.tile.toInt()))
                .border(1.dp, C.Surface2, RoundedCornerShape(12.dp)).clickable(onClickLabel = t.name, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            img?.let { Image(it, t.name, Modifier.fillMaxWidth().aspectRatio(1.25f), contentScale = ContentScale.Crop) }
                ?: Icon(Icons.Rounded.TextFields, null, tint = C.Muted, modifier = Modifier.size(22.dp))
        }
        Text(t.name, style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Renders each template as it looks at rest, with the real text and shape renderers. */
object TemplateThumbs {
    private val cache = HashMap<String, ImageBitmap>()
    private const val H = 640
    private const val W = H * 9 / 16

    fun cached(id: String): ImageBitmap? = synchronized(cache) { cache[id] }

    fun render(t: TextTemplates.Template): ImageBitmap? = runCatching {
        val frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(frame)
        c.drawColor(t.tile.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var top = Float.MAX_VALUE
        var bottom = 0f
        fun place(bmp: Bitmap, x: Float, y: Float, w: Float, h: Float, rot: Float, alpha: Float = 1f) {
            c.save()
            c.rotate(rot, x * W, y * H)
            paint.alpha = (alpha * 255).toInt()
            c.drawBitmap(bmp, null, RectF(x * W - w / 2, y * H - h / 2, x * W + w / 2, y * H + h / 2), paint)
            c.restore()
            top = minOf(top, y * H - h / 2)
            bottom = maxOf(bottom, y * H + h / 2)
        }
        for (b in t.boxes) {
            val s = ShapeLayer(shape = b.shape, fill = b.fill, stroke = b.stroke, strokeWidth = b.strokeWidth, corner = b.corner, aspect = b.aspect, startUs = 0, durationUs = 1)
            val bmp = LayerBitmaps.shapeLayer(s, H)
            val h = b.scale * H
            place(bmp, b.x, b.y, h * bmp.width / bmp.height, h, b.rotation, b.opacity)
        }
        for (p in t.parts) {
            val l = TextLayer(text = p.text, startUs = 0, durationUs = 1, style = p.style, transform = Transform(p.x, p.y, p.scale, p.rotation))
            val bmp = LayerBitmaps.text(l, -1, -1, H, W) ?: continue
            place(bmp, p.x, p.y, bmp.width * p.scale, bmp.height * p.scale, p.rotation)
        }
        // Crop a 5:4 window around the content.
        val cropH = (W / 1.25f).toInt()
        val mid = if (bottom > top) (top + bottom) / 2 else H / 2f
        val y0 = (mid - cropH / 2).toInt().coerceIn(0, H - cropH)
        Bitmap.createBitmap(frame, 0, y0, W, cropH).asImageBitmap()
    }.getOrNull()?.also { synchronized(cache) { cache[t.id] = it } }
}

/** One-tap looks for the selected text: font, colours and outline from the templates. */
@Composable
fun TextStylePresets(current: TextStyle, onPick: (TextStyle) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextTemplates.styles.forEach { (name, st) ->
            val img by produceState<ImageBitmap?>(null, name) {
                value = withContext(Dispatchers.Default) {
                    runCatching {
                        LayerBitmaps.text(TextLayer(text = "Aa", startUs = 0, durationUs = 1, style = st.copy(sizeSp = 90f, labelColor = st.labelColor)), -1, -1, 640)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            val picked = current.font == st.font && current.color == st.color && current.strokeWidth == st.strokeWidth
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(C.Bg)
                    .border(if (picked) 2.dp else 1.dp, if (picked) C.Accent else C.Surface2, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = name) { onPick(st) },
                contentAlignment = Alignment.Center,
            ) { img?.let { Image(it, name, Modifier.size(44.dp), contentScale = ContentScale.Fit) } }
        }
    }
}
