package com.paulneja.quorvi.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalUltra = compositionLocalOf { false }

private val UltraPink = Color(0xFFFF5FA2)
private val UltraOrange = Color(0xFFFFA24C)
private val UltraViolet = Color(0xFF9B6CFF)
private val UltraCyan = Color(0xFF4DE1FF)
private val ChatViolet = Color(0xFFB98CFF)
private val ChatCyan = Color(0xFF5CE1E6)

@Composable
fun ModeOrb(size: Dp, ultra: Boolean, busy: Boolean = false, modifier: Modifier = Modifier, instant: Boolean = false) =
    com.paulneja.quorvi.ui.chat.orb.NexoOrb(
        size, if (ultra) com.paulneja.quorvi.ui.chat.orb.OrbMode.ULTRA else com.paulneja.quorvi.ui.chat.orb.OrbMode.CHAT,
        if (busy) com.paulneja.quorvi.ui.chat.orb.OrbState.THINKING else com.paulneja.quorvi.ui.chat.orb.OrbState.IDLE, modifier = modifier, instant = instant,
    )

@Composable
internal fun LegacyOrb(size: Dp, ultra: Boolean, busy: Boolean = false, modifier: Modifier = Modifier) {
    val motion = Quorvi.motion
    val p by animateFloatAsState(if (ultra) 1f else 0f, if (motion.enabled) spring(dampingRatio = 0.7f, stiffness = 70f) else tween(0), label = "ultra")
    val energy by animateFloatAsState(if (busy) 1f else 0f, tween(800), label = "energy")
    val t = rememberInfiniteTransition(label = "orb")

    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (motion.enabled) 30_000 else Int.MAX_VALUE, easing = LinearEasing)), label = "clock")
    val accent = Quorvi.colors.accent
    val ripple = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(ultra) {
        if (ultra && motion.enabled) { ripple.snapTo(0f); ripple.animateTo(1f, tween(1400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    }
    Canvas(modifier.size(size).graphicsLayer { val s = 1f + 0.12f * p; scaleX = s; scaleY = s }) {
        val r = this.size.minDimension / 2

        val time = clock * 2f * PI.toFloat()
        drawAura(r, lerp(accent, UltraPink, p), 0.30f + 0.16f * p + 0.14f * energy)
        if (p < 1f) {
            drawChatRings(r, clock * 360f * 3f, accent, 1f - p)
            drawChatCore(r, accent, 1f - p, time)
        }
        if (p > 0f) {
            drawLiquid(r, time, p, energy)
            drawGlass(r, time, p)
            drawSparkles(r, clock, p)
        }
        if (ripple.value < 1f) drawRipple(r, ripple.value)
    }
}

private fun DrawScope.drawAura(r: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, r * 1.5f), r * 1.5f, center)
}

private fun DrawScope.drawChatRings(r: Float, a: Float, accent: Color, alpha: Float) {
    rotate(a) { drawCircle(Brush.sweepGradient(listOf(accent, ChatViolet, ChatCyan, accent), center), r * 0.98f, center, alpha, Stroke(r * 0.12f)) }
    rotate(-a * 2f) { drawCircle(Brush.sweepGradient(listOf(Color.Transparent, ChatCyan, Color.Transparent, ChatViolet, Color.Transparent), center), r * 0.76f, center, alpha, Stroke(r * 0.07f)) }
}

private fun DrawScope.drawChatCore(r: Float, accent: Color, alpha: Float, time: Float) {
    val breathe = 1f + 0.06f * sin(time * 12f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * alpha), accent.copy(alpha = alpha), accent.copy(alpha = 0.2f * alpha)), center, r * 0.5f * breathe), r * 0.48f * breathe, center)
}

private fun blob(c: Offset, base: Float, time: Float, seed: Int, wobble: Float): androidx.compose.ui.graphics.Path {
    val n = 40
    val pts = List(n) { i ->
        val th = i * 2f * PI.toFloat() / n
        val rr = base * (1f + wobble * (0.10f * sin(2 * th + time * (6 + seed) + seed) + 0.07f * sin(3 * th - time * (9 + 2 * seed)) + 0.05f * sin(5 * th + time * 13 + seed * 2f)))
        Offset(c.x + cos(th) * rr, c.y + sin(th) * rr)
    }
    val path = androidx.compose.ui.graphics.Path()
    val first = Offset((pts[n - 1].x + pts[0].x) / 2, (pts[n - 1].y + pts[0].y) / 2)
    path.moveTo(first.x, first.y)
    for (i in 0 until n) {
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        path.quadraticTo(cur.x, cur.y, (cur.x + next.x) / 2, (cur.y + next.y) / 2)
    }
    path.close()
    return path
}

private fun DrawScope.drawLiquid(r: Float, time: Float, p: Float, energy: Float) {
    val colors = listOf(UltraPink, UltraOrange, UltraViolet, UltraCyan)
    val wobble = 1f + energy * 0.8f
    colors.forEachIndexed { i, color ->

        val orbit = r * 0.30f * p
        val ang = time * (2 + i) * (if (i % 2 == 0) 1f else -1f) + i * PI.toFloat() / 2
        val c = Offset(center.x + cos(ang) * orbit, center.y + sin(time * (3 + i) + i) * orbit)
        val base = r * (0.50f - i * 0.03f) * (0.3f + 0.7f * p)
        val path = blob(c, base, time, i, wobble)
        drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = 0.75f * p), color.copy(alpha = 0.45f * p), color.copy(alpha = 0.08f * p)), c, base * 1.15f), blendMode = BlendMode.Screen)
    }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * p), Color.White.copy(alpha = 0f)), center, r * 0.2f), r * 0.2f, center, blendMode = BlendMode.Screen)
}

private fun DrawScope.drawGlass(r: Float, time: Float, p: Float) {
    drawCircle(Color.White.copy(alpha = 0.18f * p), r * 0.9f, center, style = Stroke(r * 0.018f))
    rotate(time / (2f * PI.toFloat()) * 360f * 4f) {
        drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f * p), Color.Transparent), center), -60f, 70f, false,
            topLeft = Offset(center.x - r * 0.9f, center.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.03f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSparkles(r: Float, clock: Float, p: Float) {
    repeat(7) { i ->
        val phase = (clock * (3 + i) + i / 7f) % 1f
        val ang = i * 2.4f + clock * 2f * PI.toFloat() * 2f
        val d = r * (0.95f + 0.35f * phase)
        val fade = sin(phase * PI.toFloat())
        drawCircle(if (i % 2 == 0) UltraOrange else UltraCyan, r * 0.022f * (0.6f + fade), Offset(center.x + cos(ang) * d, center.y + sin(ang) * d), alpha = 0.8f * fade * p, blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawRipple(r: Float, w: Float) {
    val fade = 1f - w
    repeat(2) { k ->
        val rr = r * (0.5f + 1.3f * (w - k * 0.12f).coerceAtLeast(0f))
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, UltraPink.copy(alpha = 0.35f * fade), UltraViolet.copy(alpha = 0.25f * fade), Color.Transparent), center, rr), rr, center, blendMode = BlendMode.Plus)
    }
}
