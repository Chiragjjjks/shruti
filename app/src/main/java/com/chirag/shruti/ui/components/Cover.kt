package com.chirag.shruti.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.chirag.shruti.data.Book
import com.chirag.shruti.data.CoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** The book's cover: your own image if you set one, otherwise a drawn cover. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(modifier.clip(RoundedCornerShape(corner))) {
        val path = book.coverPath
        val custom by produceState<ImageBitmap?>(null, path) {
            value = path?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(it.substringBefore('#'))
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 700) sample *= 2
                        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                    }.getOrNull()
                }
            }
        }
        val img = custom
        if (img != null) {
            Image(img, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else when (book.art) {
            CoverArt.IKIGAI -> IkigaiCover()
            CoverArt.SHIVA -> ShivaCover()
        }
    }
}

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

@Composable
private fun IkigaiCover() {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE3EEF5), Color(0xFFB9D3E4)))),
    ) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            // A rising sun, a branch and a few blossoms.
            drawCircle(Color(0xFFE0574B).copy(alpha = 0.85f), radius = s * 0.075f, center = Offset(s * 0.8f, s * 0.27f))
            val ink = Color(0xFF2A2523)
            val branch = Path().apply {
                moveTo(-s * 0.02f, s * 0.1f)
                cubicTo(s * 0.2f, s * 0.03f, s * 0.42f, s * 0.15f, s * 0.68f, s * 0.06f)
            }
            drawPath(branch, ink, style = Stroke(width = s * 0.014f, cap = StrokeCap.Round))
            val twig = Path().apply {
                moveTo(s * 0.3f, s * 0.08f)
                quadraticTo(s * 0.36f, s * 0.2f, s * 0.5f, s * 0.24f)
            }
            drawPath(twig, ink, style = Stroke(width = s * 0.008f, cap = StrokeCap.Round))
            val blossoms = listOf(
                0.12f to 0.08f, 0.22f to 0.13f, 0.33f to 0.05f, 0.4f to 0.17f, 0.5f to 0.24f,
                0.55f to 0.09f, 0.63f to 0.04f, 0.47f to 0.06f, 0.18f to 0.04f,
            )
            blossoms.forEachIndexed { i, (bx, by) ->
                val c = Offset(s * bx, s * by)
                val r = s * (0.018f + (i % 3) * 0.005f)
                for (k in 0 until 5) {
                    val a = Math.toRadians((k * 72.0) + i * 17)
                    drawCircle(Color(0xFFFBE9EE), radius = r, center = c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()))
                }
                drawCircle(Color(0xFFD9879D), radius = r * 0.45f, center = c)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = w * 0.07f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(w * 0.42f))
            Text("IKIGAI", fontSize = (w * 0.15f).sp(), fontWeight = FontWeight.Bold, letterSpacing = (w * 0.045f).sp(), color = Color(0xFF15171C), maxLines = 1)
            Text("生き甲斐", fontSize = (w * 0.065f).sp(), color = Color(0xFF7193AE), maxLines = 1)
            Spacer(Modifier.height(w * 0.04f))
            Text(
                "The Japanese Secret to\na Long and Happy Life",
                fontSize = (w * 0.045f).sp(), lineHeight = (w * 0.065f).sp(), fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center, color = Color(0xFF34404B),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "HÉCTOR GARCÍA AND FRANCESC MIRALLES",
                fontSize = (w * 0.028f).sp(), letterSpacing = (w * 0.004f).sp(), fontFamily = FontFamily.Serif,
                color = Color(0xFF4C5A66), maxLines = 1,
            )
            Spacer(Modifier.height(w * 0.05f))
        }
    }
}

@Composable
private fun ShivaCover() {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF41B7B2), Color(0xFF167378)))),
    ) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            val c = Offset(s * 0.5f, s * 0.6f)
            val white = Color.White
            drawCircle(white.copy(alpha = 0.18f), radius = s * 0.21f, center = c)
            drawCircle(white.copy(alpha = 0.75f), radius = s * 0.21f, center = c, style = Stroke(s * 0.006f))
            drawCircle(white.copy(alpha = 0.55f), radius = s * 0.18f, center = c, style = Stroke(s * 0.004f))
            for (k in 0 until 16) {
                rotate(k * 22.5f, pivot = c) {
                    drawOval(
                        white.copy(alpha = 0.7f),
                        topLeft = Offset(c.x - s * 0.02f, c.y - s * 0.175f),
                        size = Size(s * 0.04f, s * 0.095f),
                        style = Stroke(s * 0.004f),
                    )
                }
            }
            drawCircle(white.copy(alpha = 0.9f), radius = s * 0.075f, center = c)
            // Trident at the centre.
            val ink = Color(0xFF1B6E72)
            val stroke = Stroke(width = s * 0.008f, cap = StrokeCap.Round)
            drawLine(ink, Offset(c.x, c.y - s * 0.05f), Offset(c.x, c.y + s * 0.055f), s * 0.008f, StrokeCap.Round)
            val prongs = Path().apply {
                moveTo(c.x - s * 0.035f, c.y - s * 0.045f)
                quadraticTo(c.x - s * 0.035f, c.y - s * 0.005f, c.x, c.y - s * 0.005f)
                quadraticTo(c.x + s * 0.035f, c.y - s * 0.005f, c.x + s * 0.035f, c.y - s * 0.045f)
            }
            drawPath(prongs, ink, style = stroke)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = w * 0.06f), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(w * 0.07f))
            Text("7 SECRETS OF", fontSize = (w * 0.05f).sp(), letterSpacing = (w * 0.012f).sp(), fontFamily = FontFamily.Serif, color = Color.White, maxLines = 1)
            Text("SHIVA", fontSize = (w * 0.14f).sp(), letterSpacing = (w * 0.025f).sp(), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("DEVDUTT PATTANAIK", fontSize = (w * 0.05f).sp(), letterSpacing = (w * 0.01f).sp(), fontFamily = FontFamily.Serif, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(w * 0.06f))
        }
    }
}
