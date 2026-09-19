package com.paulneja.quorvi.ui.panels

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.paulneja.quorvi.ui.design.GlassBackdrops
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassBackdrops
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.TileIcon
import com.paulneja.quorvi.ui.design.liquidGlass
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun PanelOverlay(
    kind: String,
    origin: Rect,
    blurredWallpaper: ImageBitmap?,
    progress: Animatable<Float, *>,
    onClosed: () -> Unit,
    grow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = Quorvi.motion
    LaunchedEffect(kind) { progress.animateTo(1f, motion.settle()) }
    fun close() = scope.launch {
        progress.animateTo(0f, motion.settle())
        onClosed()
    }
    PredictiveBackHandler { events ->
        try {
            events.collect { e -> progress.snapTo(1f - e.progress * 0.35f) }
            close()
        } catch (e: CancellationException) {
            progress.animateTo(1f, motion.quick())
            throw e
        }
    }
    val density = LocalDensity.current
    val tint = LocalWallpaperTint.current
    val panelLayer = rememberLayerBackdrop()
    val liquid = blurredWallpaper != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val screenW = maxWidth
        val screenH = maxHeight
        val full = with(density) { Rect(0f, 0f, screenW.toPx(), screenH.toPx()) }
        val start = if (origin.isEmpty) full.deflate(full.width * 0.3f) else origin
        val p = progress.value.coerceIn(0f, 1.1f)
        val rect = if (grow) lerp(start, full, p.coerceAtMost(1f)) else full
        val radius = if (p >= 0.999f) 0.dp else lerp(24.dp, 30.dp, p)
        Box(
            Modifier
                .graphicsLayer { if (!grow) alpha = p.coerceIn(0f, 1f) }
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .clip(RoundedCornerShape(radius)),
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(screenW, screenH)
                    .offset { IntOffset(-rect.left.roundToInt(), -rect.top.roundToInt()) },
            ) {
                Box(Modifier.fillMaxSize().then(if (liquid) Modifier.layerBackdrop(panelLayer) else Modifier)) {
                    if (blurredWallpaper != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val scale = maxOf(size.height / blurredWallpaper.height, size.width / blurredWallpaper.width)
                            val w = blurredWallpaper.width * scale
                            val h = blurredWallpaper.height * scale
                            drawImage(
                                blurredWallpaper,
                                dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                                filterQuality = FilterQuality.Low,
                            )
                            drawRect(Color(0xFF05080C).copy(alpha = 0.42f))
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f).compositeOver(Color(0xFF0C0F14)), Color(0xFF0C0F14)))))
                    }
                }
                CompositionLocalProvider(LocalGlassBackdrops provides if (liquid) GlassBackdrops(chrome = panelLayer, overlay = panelLayer, top = panelLayer) else GlassBackdrops()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (grow) ((p - 0.35f) / 0.5f).coerceIn(0f, 1f) else p.coerceIn(0f, 1f) }
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PanelHeader(kind, onBack = { close() })
                        content()
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(kind: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).liquidGlass(CircleShape, GlassLevel.CHROME).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Glyph(Glyphs.Back, 18.dp) }
        Spacer(Modifier.width(10.dp))
        TileIcon(panelGlyph(kind), size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(panelTitle(kind), style = Quorvi.type.title, color = Quorvi.colors.text)
    }
}
