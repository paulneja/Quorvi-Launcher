package com.paulneja.quorvi.ui.chat.horizon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.launch

fun Modifier.orbLit(corner: Dp, strength: Float = 1f): Modifier = composed {
    val h = LocalHorizon.current ?: return@composed this
    var origin by remember { mutableStateOf<Offset?>(null) }
    val accent = Quorvi.colors.accent
    onGloballyPositioned { c -> h.host?.takeIf { it.isAttached && c.isAttached }?.let { origin = it.localPositionOf(c, Offset.Zero) } }
        .drawWithContent {
            drawContent()
            val o = origin ?: return@drawWithContent
            val mid = Offset(size.width / 2f, size.height / 2f)
            val dir = h.center() - (o + mid)
            val dist = max(dir.getDistance(), 1f)
            val n = dir / dist
            val reach = max(h.hostHeight, 1f)
            val breathe = 0.88f + 0.12f * sin(h.clock * 1.4f + o.y * 0.004f)
            val wave = h.rippleAt(o + mid).coerceAtMost(1.5f)
            val a = ((0.34f + 0.62f * exp(-dist / (reach * 0.5f))) * breathe + 0.9f * wave) * strength
            val tint = h.tint(accent)
            val start = mid + Offset(n.x * size.width / 2f, n.y * size.height / 2f)
            val end = mid - Offset(n.x * size.width / 2f, n.y * size.height / 2f)
            val r = CornerRadius(corner.toPx())
            drawRoundRect(
                Brush.linearGradient(listOf(tint.copy(alpha = a.coerceIn(0f, 1f)), tint.copy(alpha = (a * 0.18f).coerceIn(0f, 1f)), Color.Transparent), start, end),
                cornerRadius = r, style = Stroke(1.6.dp.toPx()),
            )
            drawRoundRect(
                Brush.linearGradient(listOf(tint.copy(alpha = (a * 0.22f).coerceIn(0f, 1f)), Color.Transparent), start, mid),
                cornerRadius = r,
            )
        }
}

fun Modifier.orbTouch(onTap: () -> Unit = {}): Modifier = composed {
    val h = LocalHorizon.current
    val squish = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    val motion = Quorvi.motion.enabled
    pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                if (motion) scope.launch { squish.animateTo(0.84f, tween(110)) }
                val released = tryAwaitRelease()
                if (motion) scope.launch { squish.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 260f)) }
                if (released) {
                    haptics.snap()
                    h?.ripple(h.center(), 1.8f)
                    onTap()
                }
            },
        )
    }.graphicsLayer {
        val s = squish.value
        scaleX = s + (1f - s) * 0.35f
        scaleY = s - (1f - s) * 0.25f
    }
}

fun Modifier.launchIn(animate: Boolean): Modifier = composed {
    val motion = Quorvi.motion.enabled
    val p = remember { Animatable(if (animate && motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 230f)) }
    graphicsLayer {
        val v = p.value
        val rest = 1f - v
        alpha = (v * 3f).coerceIn(0f, 1f)
        translationY = rest * 190.dp.toPx()
        translationX = rest * 18.dp.toPx()
        scaleY = 1f + 0.30f * rest.coerceAtLeast(-0.3f)
        scaleX = 1f - 0.18f * rest.coerceAtLeast(-0.3f)
        transformOrigin = TransformOrigin(1f, 1f)
    }
}

fun Modifier.lightSweep(trigger: Any?, run: Boolean): Modifier = composed {
    val motion = Quorvi.motion.enabled
    val sweep = remember { Animatable(2f) }
    LaunchedEffect(trigger) {
        if (run && motion) {
            sweep.snapTo(-0.4f)
            sweep.animateTo(1.4f, tween(1000, easing = LinearEasing))
        }
    }
    val h = LocalHorizon.current
    val accent = Quorvi.colors.accent
    graphicsLayer { compositingStrategy = if (sweep.value < 1.4f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val x = sweep.value
            if (x >= 1.4f) return@drawWithContent
            val tint = h?.tint(accent) ?: accent
            val cx = x * size.width
            val band = size.width * 0.35f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, tint.copy(alpha = 0.55f), Color.White.copy(alpha = 0.85f), tint.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(cx - band, 0f), end = Offset(cx + band, size.height * 0.4f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}
