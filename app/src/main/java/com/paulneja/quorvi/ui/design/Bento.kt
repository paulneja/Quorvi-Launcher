package com.paulneja.quorvi.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidPress(interaction: androidx.compose.foundation.interaction.MutableInteractionSource, shape: androidx.compose.ui.graphics.Shape = Quorvi.shapes.panel): Modifier {
    val motion = Quorvi.motion
    val enabled = LocalLook.current.pressScale && motion.enabled
    var at by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Unspecified) }
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(interaction, enabled) {
        if (!enabled) return@LaunchedEffect
        interaction.interactions.collect { i ->
            when (i) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> { at = i.pressPosition; glow.animateTo(1f, androidx.compose.animation.core.tween(90)) }
                is androidx.compose.foundation.interaction.PressInteraction.Release, is androidx.compose.foundation.interaction.PressInteraction.Cancel -> glow.animateTo(0f, motion.settle())
            }
        }
    }
    if (!enabled) return this
    return this
        .pressScale { 1f - 0.03f * glow.value }
        .pressGlow(shape) { if (at == androidx.compose.ui.geometry.Offset.Unspecified) null else at to glow.value }
}

private fun Modifier.pressScale(scale: () -> Float): Modifier = this.then(
    Modifier.graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
)

private fun Modifier.pressGlow(shape: androidx.compose.ui.graphics.Shape, state: () -> Pair<androidx.compose.ui.geometry.Offset, Float>?): Modifier = this.then(
    Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            val (center, amount) = state() ?: return@onDrawWithContent
            if (amount <= 0.01f) return@onDrawWithContent
            val radius = maxOf(size.width, size.height) * 0.75f
            val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
            clipPath(path) {
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.22f * amount),
                        0.45f to Color.White.copy(alpha = 0.07f * amount),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    },
)

@Immutable
sealed interface BlockState {
    data object Ready : BlockState
    data object Loading : BlockState
    data class Empty(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null) : BlockState
    data class NeedsPermission(val message: String, val action: String, val onGrant: () -> Unit) : BlockState
    data class Error(val message: String, val action: String? = null, val onRetry: (() -> Unit)? = null) : BlockState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoBlock(
    modifier: Modifier = Modifier,
    state: BlockState = BlockState.Ready,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    title: String? = null,
    glyph: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val clickable = onClick != null || onLongClick != null
    Column(
        modifier
            .fillMaxSize()
            .padding(5.dp)
            .then(if (clickable) Modifier.liquidPress(interaction) else Modifier)
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .then(if (clickable) Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier)
            .padding(padding),
    ) {
        if (state != BlockState.Ready && title != null && glyph != null) {
            BlockHeader(glyph, title)
            Spacer(Modifier.height(6.dp))
        }
        when (state) {
            BlockState.Ready -> content()
            BlockState.Loading -> Box(Modifier.fillMaxSize())
            is BlockState.Empty -> Placeholder(state.message, state.action, state.onAction)
            is BlockState.NeedsPermission -> Placeholder(state.message, state.action, state.onGrant)
            is BlockState.Error -> Placeholder(state.message, state.action, state.onRetry, danger = true)
        }
    }
}

@Composable
private fun ColumnScope.Placeholder(message: String, action: String?, onAction: (() -> Unit)?, danger: Boolean = false) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(message, style = Quorvi.type.caption, color = if (danger) Quorvi.colors.danger else Quorvi.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                action,
                style = Quorvi.type.action,
                color = Quorvi.colors.accent,
                modifier = Modifier.clip(Quorvi.shapes.control).clickable(onClick = onAction).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun BlockHeader(glyph: ImageVector, title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TileIcon(glyph)
        Spacer(Modifier.width(9.dp))
        Text(title, style = Quorvi.type.action.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun TileIcon(glyph: ImageVector, size: Dp = 28.dp, tint: Color = Quorvi.colors.accent) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(glyph), null, Modifier.size(size * 0.56f), colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.lerp(tint, Color.White, 0.25f)))
    }
}

@Composable
fun Glyph(glyph: ImageVector, size: Dp = 20.dp, tint: Color = Quorvi.colors.text, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(glyph), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
fun Dot(color: Color, size: Dp = 7.dp, halo: Boolean = true) {
    Box(
        Modifier
            .size(size + if (halo) 6.dp else 0.dp)
            .then(if (halo) Modifier.clip(CircleShape).background(color.copy(alpha = 0.22f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(size).clip(CircleShape).background(color)) }
}

@Composable
fun ProgressLine(fraction: Float, color: Color = Quorvi.colors.accent, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.13f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 56.dp, stroke: Dp = 6.dp, content: @Composable () -> Unit = {}) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun QuorviSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val motion = Quorvi.motion
    val knob by animateDpAsState(if (checked) 19.dp else 3.dp, motion.quick(), label = "knob")
    val track by animateColorAsState(if (checked) Quorvi.colors.accent else Color.White.copy(alpha = 0.16f), motion.quick(), label = "track")
    Box(
        Modifier
            .size(42.dp, 26.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.35f))
            .then(if (onChange != null && enabled) Modifier.clickable { onChange(!checked) } else Modifier),
    ) {
        Box(Modifier.offset(x = knob, y = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun QuorviSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    glyph: ImageVector? = null,
    height: Dp = 36.dp,
    onDone: (Float) -> Unit = {},
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val span = range.endInclusive - range.start
    val fraction = if (span == 0f) 0f else ((local - range.start) / span).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 3))
            .background(Color.White.copy(alpha = 0.09f))
            .pointerInput(range) {
                fun at(x: Float) = (range.start + (x / size.width).coerceIn(0f, 1f) * span)
                detectTapGestures { local = at(it.x); onChange(local); onDone(local) }
            }
            .pointerInput(range) {
                fun at(x: Float) = (range.start + (x / size.width).coerceIn(0f, 1f) * span)
                detectHorizontalDragGestures(
                    onDragEnd = { onDone(local) },
                    onHorizontalDrag = { change, _ -> local = at(change.position.x); onChange(local) },
                )
            },
    ) {
        Box(Modifier.fillMaxHeight().width(maxWidth * fraction).clip(RoundedCornerShape(height / 3)).background(Color.White.copy(alpha = 0.86f)))
        if (glyph != null) Glyph(glyph, 18.dp, if (fraction > 0.12f) Color(0xFF0E1116) else Quorvi.colors.text, Modifier.align(Alignment.CenterStart).offset(x = 11.dp))
    }
}

@Composable
fun <T> QuorviSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.07f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Quorvi.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (active) Quorvi.colors.text else Quorvi.colors.textSecondary) }
        }
    }
}

@Composable
fun ChipButton(label: String, glyph: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier
            .liquidPress(interaction, Quorvi.shapes.control)
            .clip(Quorvi.shapes.control)
            .background(if (active) Quorvi.colors.accent else Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, 14.dp, if (active) Quorvi.colors.onAccent else Quorvi.colors.text)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Quorvi.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = if (active) Quorvi.colors.onAccent else Quorvi.colors.text, maxLines = 1)
    }
}

@Composable
fun GlassGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).padding(vertical = 6.dp)) {
        if (title != null) Text(title.uppercase(), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
        content()
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    glyph: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(11.dp))
        } else if (glyph != null) {
            Glyph(glyph, 20.dp, Quorvi.colors.text.copy(alpha = 0.82f))
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Quorvi.type.body, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
