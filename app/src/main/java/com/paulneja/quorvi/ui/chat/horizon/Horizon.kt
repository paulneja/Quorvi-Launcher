package com.paulneja.quorvi.ui.chat.horizon

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.paulneja.quorvi.ui.chat.orb.NexoOrb
import com.paulneja.quorvi.ui.chat.orb.OrbMode
import com.paulneja.quorvi.ui.chat.orb.OrbStates
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

object HorizonEasing : Easing {
    override fun transform(fraction: Float): Float =
        if (fraction < 0.5f) 0.5f * (2f * fraction).pow(2.3f)
        else 1f - 0.5f * (2f * (1f - fraction)).pow(2.3f)
}

val UltraTint = Color(0xFFFF5FA2)

val LocalHorizon = staticCompositionLocalOf<HorizonState?> { null }

@Stable
class HorizonState internal constructor(initialUltra: Boolean, val supported: Boolean) {
    internal val anim = Animatable(if (initialUltra) 1f else 0f)
    val phase: Float get() = anim.value
    val warp: Float get() = 1f - abs(2f * anim.value - 1f)
    val mode: Float get() = ((anim.value - 0.42f) / 0.16f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    var spin by mutableFloatStateOf(1f)
        internal set
    var orbCenter by mutableStateOf<Offset?>(null)
        internal set
    var orbSize by mutableFloatStateOf(0f)
        internal set
    var hostWidth by mutableFloatStateOf(0f)
        internal set
    var hostHeight by mutableFloatStateOf(0f)
        internal set
    var clock by mutableFloatStateOf(3f)
        internal set
    var animated by mutableStateOf(false)
        internal set
    internal var host: LayoutCoordinates? = null
    internal var anchor: LayoutCoordinates? = null
    internal val ripples = FloatArray(RIPPLES * 4)
    private var cursor = 0

    suspend fun go(ultra: Boolean, motion: Boolean, onSwap: () -> Unit = {}) {
        val target = if (ultra) 1f else 0f
        if (anim.targetValue == target && anim.isRunning) return
        spin = if (ultra) 1f else -1f
        if (!motion) { anim.snapTo(target); onSwap(); return }
        val ms = (abs(target - anim.value) * if (ultra) FORWARD_MS else BACK_MS).toInt().coerceAtLeast(1)
        var before = anim.value >= 0.5f
        anim.animateTo(target, tween(ms, easing = HorizonEasing)) {
            val now = value >= 0.5f
            if (now != before) { before = now; onSwap() }
        }
    }

    suspend fun snap(ultra: Boolean) = anim.snapTo(if (ultra) 1f else 0f)

    fun ripple(at: Offset, strength: Float = 1f) {
        if (!animated) return
        val i = (cursor % RIPPLES) * 4
        ripples[i] = at.x
        ripples[i + 1] = at.y
        ripples[i + 2] = clock
        ripples[i + 3] = strength
        cursor++
    }

    fun rippleAt(p: Offset): Float {
        val r = orbRadius()
        var sum = 0f
        for (k in 0 until RIPPLES) {
            val i = k * 4
            val s = ripples[i + 3]
            if (s <= 0f) continue
            val age = clock - ripples[i + 2]
            if (age < 0f || age > 2.5f) continue
            val d = hypot(p.x - ripples[i], p.y - ripples[i + 1])
            val rad = age * r * 4.2f
            val w = r * (0.25f + 0.35f * age)
            val x = (d - rad) / w
            sum += exp(-x * x) * exp(-age * 1.7f) * s
        }
        return sum
    }

    fun tint(accent: Color): Color = lerp(accent, UltraTint, mode)

    internal fun orbRadius(): Float = (if (orbSize > 0f) orbSize else DEFAULT_ORB_PX) / 2f * 0.74f

    internal fun refresh() {
        val h = host?.takeIf { it.isAttached } ?: return
        hostWidth = h.size.width.toFloat()
        hostHeight = h.size.height.toFloat()
        val a = anchor?.takeIf { it.isAttached }
        if (a == null) {
            orbCenter = null
            return
        }
        orbCenter = h.localPositionOf(a, Offset(a.size.width / 2f, a.size.height / 2f))
        orbSize = a.size.width.toFloat()
    }

    fun center(): Offset = centerIn(hostWidth, hostHeight)

    internal fun centerIn(width: Float, height: Float): Offset = orbCenter ?: Offset(width / 2f, -height * 0.03f)

    companion object {
        const val FORWARD_MS = 1150f
        const val BACK_MS = 950f
        const val RIPPLES = 4
        internal var DEFAULT_ORB_PX = 300f
    }
}

@Composable
fun rememberHorizon(ultra: Boolean): HorizonState {
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val density = LocalDensity.current
    HorizonState.DEFAULT_ORB_PX = with(density) { 104.dp.toPx() }
    return remember { HorizonState(ultra, supported) }
}

fun Modifier.horizonFrame(state: HorizonState): Modifier =
    onGloballyPositioned { state.host = it; state.refresh() }
        .pointerInput(state) {
            var last = Offset.Zero
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    for (change in event.changes) {
                        if (change.pressed && !change.previousPressed) {
                            state.ripple(change.position, 1f)
                            last = change.position
                        } else if (change.pressed && (change.position - last).getDistance() > 70.dp.toPx()) {
                            state.ripple(change.position, 0.5f)
                            last = change.position
                        }
                    }
                }
            }
        }

fun Modifier.horizonHost(state: HorizonState): Modifier = composed {
    val warpShader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) HorizonShaders.warp() else null }
    val accent = Quorvi.colors.accent
    horizonFrame(state)
        .graphicsLayer {
            val w = state.warp
            if (warpShader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                alpha = 1f - w
                val s = 1f - 0.06f * w
                scaleX = s; scaleY = s
                return@graphicsLayer
            }
            if (w <= 0.001f) { renderEffect = null; return@graphicsLayer }
            val c = state.centerIn(size.width, size.height)
            val reach = maxOf(hypot(c.x, c.y), hypot(size.width - c.x, c.y), hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y))
            val tint = if (state.phase < 0.5f) accent else UltraTint
            warpShader.setFloatUniform("size", size.width, size.height)
            warpShader.setFloatUniform("center", c.x, c.y)
            warpShader.setFloatUniform("w", w)
            warpShader.setFloatUniform("reach", reach)
            warpShader.setFloatUniform("orbR", state.orbRadius())
            warpShader.setFloatUniform("spin", state.spin)
            warpShader.setFloatUniform("tint", tint.red, tint.green, tint.blue)
            renderEffect = RenderEffect.createRuntimeShaderEffect(warpShader, "content").asComposeRenderEffect()
        }
}

fun Modifier.horizonAnchor(state: HorizonState): Modifier = composed {
    DisposableEffect(state) { onDispose { state.anchor = null; state.refresh() } }
    onGloballyPositioned { state.anchor = it; state.refresh() }
        .graphicsLayer { alpha = if (state.supported && state.warp > 0.001f) 0f else 1f }
}

@Composable
fun HorizonOrb(state: HorizonState) {
    if (!state.supported) return
    val active by remember(state) { derivedStateOf { state.warp > 0.001f } }
    val ultraSide by remember(state) { derivedStateOf { state.phase >= 0.5f } }
    val center = state.orbCenter
    if (!active || center == null || state.orbSize <= 0f) return
    val density = LocalDensity.current
    val sizeDp = with(density) { state.orbSize.toDp() }
    val half = state.orbSize / 2f
    Box(Modifier.offset { IntOffset((center.x - half).toInt(), (center.y - half).toInt()) }.size(sizeDp)) {
        NexoOrb(sizeDp, if (ultraSide) OrbMode.ULTRA else OrbMode.CHAT, phase = { state.phase })
    }
}

@Composable
fun NexoField(
    state: HorizonState,
    busy: Boolean,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    listening: Boolean = false,
    speaking: Boolean = false,
    top: Color? = null,
    bottom: Color? = null,
) {
    val colors = Quorvi.colors
    val topColor = top ?: colors.sunken
    val bottomColor = bottom ?: colors.content
    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) HorizonShaders.field() else null }
    if (shader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Box(modifier.background(Brush.verticalGradient(listOf(topColor, bottomColor))))
        return
    }
    val motion = Quorvi.motion.enabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(motion, lifecycle) {
        state.animated = motion
        if (!motion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val offset = state.clock
            val start = withFrameNanos { it }
            while (true) withFrameNanos { state.clock = offset + OrbStates.clock(it, start) }
        }
    }
    val ultraSide by remember(state) { derivedStateOf { state.phase >= 0.5f } }
    val tilt by com.paulneja.quorvi.ui.chat.orb.rememberTilt(enabled = motion && ultraSide)
    val think by animateFloatAsState(if (busy) 1f else 0f, tween(900), label = "think")
    val listen by animateFloatAsState(if (listening) 1f else 0f, tween(500), label = "listen")
    val speak by animateFloatAsState(if (speaking) 1f else 0f, tween(500), label = "speak")
    val lvl by animateFloatAsState(level.coerceIn(0f, 1f), tween(90), label = "level")
    val dark = if (bottomColor.luminance() < 0.5f) 1f else 0f
    Canvas(modifier) {
        val c = state.centerIn(size.width, size.height)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("center", c.x, c.y)
        shader.setFloatUniform("orbR", max(state.orbRadius(), 1f))
        shader.setFloatUniform("time", state.clock)
        shader.setFloatUniform("mode", state.mode)
        shader.setFloatUniform("w", state.warp)
        shader.setFloatUniform("spin", state.spin)
        shader.setFloatUniform("tilt", tilt.x, tilt.y)
        shader.setFloatUniform("think", think)
        shader.setFloatUniform("energy", maxOf(think, listen * 0.6f, speak * 0.6f))
        shader.setFloatUniform("voice", lvl * maxOf(listen, speak))
        shader.setFloatUniform("accent", colors.accent.red, colors.accent.green, colors.accent.blue)
        shader.setFloatUniform("top", topColor.red, topColor.green, topColor.blue)
        shader.setFloatUniform("bottom", bottomColor.red, bottomColor.green, bottomColor.blue)
        shader.setFloatUniform("dark", dark)
        shader.setFloatUniform("presence", if (state.orbCenter != null) 1f else 0.55f)
        for (k in 0 until HorizonState.RIPPLES) {
            val i = k * 4
            shader.setFloatUniform("rp$k", state.ripples[i], state.ripples[i + 1], state.ripples[i + 2], state.ripples[i + 3])
        }
        drawRect(ShaderBrush(shader))
    }
}
