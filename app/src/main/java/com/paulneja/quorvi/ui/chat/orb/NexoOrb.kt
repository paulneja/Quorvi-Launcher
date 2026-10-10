package com.paulneja.quorvi.ui.chat.orb

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.paulneja.quorvi.ui.chat.LegacyOrb
import com.paulneja.quorvi.ui.design.Quorvi

@Composable
fun NexoOrb(size: Dp, mode: OrbMode, state: OrbState = OrbState.IDLE, level: Float = 0f, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) ShaderOrb(size, mode, state, level, modifier, animate)
    else LegacyOrb(size, mode == OrbMode.ULTRA, state == OrbState.THINKING || state == OrbState.WORKING, modifier)
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderOrb(size: Dp, mode: OrbMode, state: OrbState, level: Float, modifier: Modifier, animate: Boolean) {
    val pearl = remember { OrbShaders.pearl() }
    val star = remember { OrbShaders.singularity() }
    val nova = remember { OrbShaders.nova() }
    if (pearl == null || star == null) {
        LegacyOrb(size, mode == OrbMode.ULTRA, state == OrbState.THINKING || state == OrbState.WORKING, modifier)
        return
    }
    val motion = Quorvi.motion.enabled
    val live = motion && animate
    val soft = spring<Float>(dampingRatio = 0.85f, stiffness = 120f)

    val target = if (mode == OrbMode.ULTRA) 1f else 0f
    val progress = remember { androidx.compose.animation.core.Animatable(target) }
    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    LaunchedEffect(target, motion) {
        if (!motion) { progress.snapTo(target); return@LaunchedEffect }
        if (target == 1f) {
            var fired = progress.value >= 0.5f
            progress.animateTo(1f, tween(((1f - progress.value) * FORWARD_MS).toInt(), easing = androidx.compose.animation.core.LinearEasing)) {
                if (!fired && value >= 0.5f) { fired = true; haptics.pickup() }
            }
        } else {
            progress.animateTo(0f, tween((progress.value * BACK_MS).toInt(), easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    val p = progress.value
    val listen by animateFloatAsState(if (state == OrbState.LISTENING) 1f else 0f, soft, label = "listen")
    val think by animateFloatAsState(if (state == OrbState.THINKING) 1f else 0f, soft, label = "think")
    val speak by animateFloatAsState(if (state == OrbState.SPEAKING) 1f else 0f, soft, label = "speak")
    val work by animateFloatAsState(if (state == OrbState.WORKING) 1f else 0f, soft, label = "work")
    val lvl by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "level")
    val time = remember { mutableFloatStateOf(7f) }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        val offset = time.floatValue
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = offset + OrbStates.clock(it, start) }
    }
    val tilt by rememberTilt(enabled = live && size.value >= 64f)
    val accent = Quorvi.colors.accent
    val detail = OrbStates.detail(size.value)
    val transitioning = p > 0.005f && p < 0.995f
    val time2 = time
    val density = androidx.compose.ui.platform.LocalDensity.current
    Canvas(
        modifier.size(size).graphicsLayer {
            if (!transitioning) return@graphicsLayer

            val amp = with(density) { 3.dp.toPx() } * bell(p, 0.12f, 0.50f).let { it * it }
            val tt = time2.floatValue
            translationX = kotlin.math.sin(tt * 97f) * amp
            translationY = kotlin.math.cos(tt * 83f) * amp
            val s = 1f + 0.16f * bell(p, 0.50f, 0.72f)
            scaleX = s; scaleY = s
        },
    ) {
        val t = time.floatValue
        fun RuntimeShader.feed(intro: Float, agitation: Float = 0f) {
            setFloatUniform("size", this@Canvas.size.width, this@Canvas.size.height)
            setFloatUniform("time", t)
            setFloatUniform("level", lvl)
            setFloatUniform("listen", listen)
            setFloatUniform("think", maxOf(think, agitation))
            setFloatUniform("speak", speak)
            setFloatUniform("work", work)
            setFloatUniform("tilt", tilt.x, tilt.y)
            setFloatUniform("accent", accent.red, accent.green, accent.blue)
            setFloatUniform("detail", detail)
            setFloatUniform("intro", intro)
        }

        val pearlIntro = 1f - smooth(0.14f, 0.50f, p)
        val starIntro = smooth(0.52f, 0.97f, p)
        if (pearlIntro > 0.001f) { pearl.feed(pearlIntro, agitation = smooth(0f, 0.14f, p)); drawRect(ShaderBrush(pearl)) }
        if (starIntro > 0.001f) { star.feed(starIntro); drawRect(ShaderBrush(star)) }
        if (transitioning && nova != null) {
            val ext = minOf(this.size.width * 1.7f, ((with(density) { 900.dp.toPx() }) - this.size.width) / 2f).coerceAtLeast(0f)
            val w = this.size.width + 2 * ext
            val h = this.size.height + 2 * ext
            nova.setFloatUniform("size", w, h)
            nova.setFloatUniform("orbR", this.size.minDimension / 2f * 0.74f)
            nova.setFloatUniform("p", p)
            nova.setFloatUniform("time", t)
            // Shaders read canvas coordinates: move the canvas, not just the rectangle, so the burst is centred on the orb.
            translate(-ext, -ext) {
                drawRect(ShaderBrush(nova), size = androidx.compose.ui.geometry.Size(w, h), blendMode = BlendMode.Plus)
            }
        }
    }
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun bell(x: Float, a: Float, b: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return kotlin.math.sin(t * Math.PI.toFloat())
}

private const val FORWARD_MS = 2600f
private const val BACK_MS = 1500f
