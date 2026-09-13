package com.paulneja.quorvi.ui.canvas

import com.paulneja.quorvi.ui.design.liquidGlass
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.backdrop
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.glass

class CoordsHolder {
    var value: LayoutCoordinates? = null
}

@Composable
fun NodeHost(
    node: CanvasNode,
    geometry: CanvasGeometry,
    hidden: Boolean,
    targeted: Boolean = false,
    trackBounds: Boolean = true,
    onLongPress: (PointerId, Offset, Rect) -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClickAt: ((Rect) -> Unit)? = null,

    accepts: ((Offset) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val holder = remember { CoordsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = Quorvi.motion
    val pressable = (onClick != null || onClickAt != null) && Quorvi.look.pressScale
    val scale by animateFloatAsState(if (pressed && pressable) 0.9f else 1f, if (pressed) motion.quick() else motion.settle(), label = "press")
    Box(
        modifier
            .onGloballyPositioned {
                holder.value = it
                if (trackBounds) geometry.nodes[node.id] = it.boundsInRoot()
            }
            .graphicsLayer {
                alpha = if (hidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .longPressToArm(node.id, enabled = true, coordinates = { holder.value }, accepts = accepts, onLongPress = onLongPress)
            .then(
                when {
                    onClickAt != null -> Modifier.clickable(interactionSource = interaction, indication = null) { onClickAt(holder.value?.boundsInRoot() ?: Rect.Zero) }
                    onClick != null -> Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (targeted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(Quorvi.shapes.panel)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.5.dp, Quorvi.colors.accent, Quorvi.shapes.panel),
            )
        }
        content()
    }
}

@Composable
fun CellLayout(
    spec: GridSpec,
    nodes: List<CanvasNode>,
    highlight: CellRect?,
    modifier: Modifier = Modifier,
    showCells: Boolean = false,
    overlay: @Composable BoxWithConstraintsScope.(Dp, Dp) -> Unit = { _, _ -> },
    node: @Composable (CanvasNode) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val cellWidth = maxWidth / spec.columns
        val cellHeight = maxHeight / spec.rows
        if (showCells) {
            val taken = nodes.filter { !it.backdrop }.map { it.rect }
            for (y in 0 until spec.rows) for (x in 0 until spec.columns) {
                if (taken.any { it.overlaps(CellRect(x, y)) }) continue
                Box(
                    Modifier
                        .offset(cellWidth * x, cellHeight * y)
                        .size(cellWidth, cellHeight)
                        .padding(6.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), Quorvi.shapes.control),
                )
            }
        }
        if (highlight != null) {
            Box(
                Modifier
                    .offset(cellWidth * highlight.x, cellHeight * highlight.y)
                    .size(cellWidth * highlight.w, cellHeight * highlight.h)
                    .padding(4.dp)
                    .clip(Quorvi.shapes.panel)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), Quorvi.shapes.panel),
            )
        }

        nodes.sortedByDescending { it.backdrop }.forEach { n ->
            key(n.id) {
                Box(
                    Modifier
                        .offset(cellWidth * n.rect.x, cellHeight * n.rect.y)
                        .size(cellWidth * n.rect.w, cellHeight * n.rect.h),
                    contentAlignment = Alignment.Center,
                ) { node(n) }
            }
        }
        overlay(cellWidth, cellHeight)
    }
}

@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == current) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)),
            )
        }
    }
}

@Composable
fun SearchBar(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onMenu: (() -> Unit)? = null) {
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(rememberVectorPainter(Glyphs.Search), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)))
            Spacer(Modifier.width(12.dp))
            Text(label, style = Quorvi.type.body, color = Color.White.copy(alpha = 0.75f))
        }
        if (onMenu != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(48.dp).liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME).clickable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(Glyphs.Quorvi), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Quorvi.colors.accent))
            }
        }
    }
}

@Composable
fun DropZone(label: String, glyph: ImageVector, active: Boolean, danger: Boolean, onBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    val colors = Quorvi.colors
    val tint = if (danger) colors.danger else colors.text
    Row(
        modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .height(48.dp)
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.OVERLAY, refraction = 12.dp)
            .then(if (active) Modifier.background(tint.copy(alpha = 0.18f), Quorvi.shapes.panel) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(rememberVectorPainter(glyph), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
        Spacer(Modifier.width(8.dp))
        Text(label, style = Quorvi.type.action, color = tint)
    }
}
