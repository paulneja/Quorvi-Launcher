package com.paulneja.quorvi.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class GlassLevel { CHROME, PANEL, OVERLAY, TOP }

val LocalWallpaperTint = staticCompositionLocalOf { Color(0xFF2A3140) }
val LocalGlassIntensity = staticCompositionLocalOf { 0.7f }

private val grain: ImageBitmap by lazy {
    val size = 64
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun Modifier.glass(shape: Shape, level: GlassLevel, tint: Color, intensity: Float, solid: Boolean = false, withGrain: Boolean = true, edge: Float = 0.75f): Modifier {
    if (LocalLook.current.glass.material) return materialSurface(shape, level)

    val base = when {
        solid -> 0.94f
        level == GlassLevel.OVERLAY -> 0.93f
        level == GlassLevel.TOP -> 0.95f
        level == GlassLevel.PANEL -> 0.74f * (0.75f + 0.35f * (1f - intensity.coerceIn(0f, 1f)))
        else -> 0.70f * (0.75f + 0.35f * (1f - intensity.coerceIn(0f, 1f)))
    }
    val fill = tint.copy(alpha = 0.22f).compositeOver(Color(0xFF0C0F14)).copy(alpha = base.coerceIn(0.3f, 0.94f))
    val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
    return this
        .clip(shape)
        .drawBehind {
            drawRect(fill)
            if (withGrain) drawRect(grainBrush, alpha = 0.035f)
            val half = size.height * 0.5f
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), startY = 0f, endY = half), size = size.copy(height = half), topLeft = Offset.Zero)
        }
        .glassRim(shape, edge)
}

@Composable
fun Modifier.materialSurface(shape: Shape, level: GlassLevel): Modifier {
    val s = MaterialTheme.colorScheme
    val fill = when (level) {
        GlassLevel.CHROME -> s.surfaceContainerHigh
        GlassLevel.PANEL -> s.surfaceContainer
        GlassLevel.OVERLAY -> s.surfaceContainerHigh
        GlassLevel.TOP -> s.surfaceContainerHighest
    }
    return clip(shape).background(fill)
}

fun Modifier.glassRim(shape: Shape, strength: Float = 0.75f): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = 1.2.dp.toPx()
    val angle = Math.toRadians(140.0 - 90.0)
    val dx = cos(angle).toFloat() * size.width
    val dy = sin(angle).toFloat() * size.height
    val brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.85f * strength),
        0.30f to Color.White.copy(alpha = 0.10f * strength),
        0.55f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.35f * strength),
        start = Offset(size.width / 2 - dx / 2, size.height / 2 - dy / 2),
        end = Offset(size.width / 2 + dx / 2, size.height / 2 + dy / 2),
    )
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = Stroke(stroke))
    }
}
