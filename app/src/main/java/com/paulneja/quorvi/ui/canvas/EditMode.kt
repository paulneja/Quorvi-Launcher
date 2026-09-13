package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Edge
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.ResizeMath
import com.paulneja.quorvi.launcher.home.SpanLimits
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import kotlin.math.roundToInt

data class EditAction(val label: String, val glyph: ImageVector, val run: () -> Unit)

@Composable
fun EditToolbar(actions: List<EditAction>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            Column(
                Modifier.clip(Quorvi.shapes.control).clickable(onClick = action.run).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(rememberVectorPainter(action.glyph), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Quorvi.colors.text))
                Spacer(Modifier.height(4.dp))
                Text(action.label, style = Quorvi.type.label, color = Quorvi.colors.text)
            }
        }
    }
}

fun limitsFor(node: CanvasNode, widgets: WidgetHostManager, cellWidthDp: Float, cellHeightDp: Float, density: Float): SpanLimits = when (node) {
    is CanvasNode.Clock -> SpanLimits(minW = 2, minH = 1)

    is CanvasNode.Card -> SpanLimits(minW = 1, minH = 1)

    is CanvasNode.Widget -> SpanLimits(minW = 1, minH = 1)
    else -> SpanLimits(maxW = 1, maxH = 1, horizontal = false, vertical = false)
}

@Composable
fun BoxWithConstraintsScope.ResizeFrame(
    node: CanvasNode,
    spec: GridSpec,
    occupied: List<CellRect>,
    limits: SpanLimits,
    cellWidth: Dp,
    cellHeight: Dp,
    onResize: (CellRect) -> Unit,
) {
    var preview by remember(node.id, node.rect) { mutableStateOf(node.rect) }
    val density = LocalDensity.current
    val accent = Quorvi.colors.accent
    Box(
        Modifier
            .offset(cellWidth * preview.x, cellHeight * preview.y)
            .size(cellWidth * preview.w, cellHeight * preview.h)
            .padding(2.dp)
            .border(2.dp, accent, Quorvi.shapes.panel),
    )
    Edge.entries.forEach { edge ->
        val enabled = if (edge == Edge.LEFT || edge == Edge.RIGHT) limits.horizontal else limits.vertical
        if (!enabled) return@forEach
        val cx = when (edge) {
            Edge.LEFT -> cellWidth * preview.x
            Edge.RIGHT -> cellWidth * (preview.x + preview.w)
            else -> cellWidth * (preview.x + preview.w / 2f)
        }
        val cy = when (edge) {
            Edge.TOP -> cellHeight * preview.y
            Edge.BOTTOM -> cellHeight * (preview.y + preview.h)
            else -> cellHeight * (preview.y + preview.h / 2f)
        }
        var accumulated by remember(edge, node.id) { mutableFloatStateOf(0f) }
        var origin by remember(edge, node.id) { mutableStateOf(node.rect) }
        Box(
            Modifier
                .offset(cx - 18.dp, cy - 18.dp)
                .size(36.dp)
                .pointerInput(node.id, edge) {
                    detectDragGestures(
                        onDragStart = { accumulated = 0f; origin = preview },
                        onDragEnd = { if (preview != node.rect) onResize(preview) },
                        onDragCancel = { preview = node.rect },
                    ) { change, drag ->
                        change.consume()
                        accumulated += if (edge == Edge.LEFT || edge == Edge.RIGHT) drag.x else drag.y
                        val cellPx = with(density) { if (edge == Edge.LEFT || edge == Edge.RIGHT) cellWidth.toPx() else cellHeight.toPx() }
                        val cells = (accumulated / cellPx).roundToInt()
                        preview = ResizeMath.resize(origin, edge, cells, limits, spec, occupied)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).border(2.dp, accent, CircleShape))
        }
    }
}
