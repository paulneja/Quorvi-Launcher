package com.paulneja.quorvi.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CanvasState
import com.paulneja.quorvi.launcher.home.CellGrid
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.backdrop
import kotlin.math.abs
import kotlin.math.roundToInt

class CanvasGeometry {
    var grid: Rect = Rect.Zero
    var dock: Rect = Rect.Zero
    var remove: Rect = Rect.Zero
    var uninstall: Rect = Rect.Zero
    val nodes = mutableMapOf<Long, Rect>()
}

object DropResolver {
    private const val ONTO_REGION = 0.32f

    fun resolve(state: CanvasState, payload: DragPayload, pointer: Offset, grab: Offset, geometry: CanvasGeometry, page: Int): DropTarget? {
        val dragged = (payload as? DragPayload.Existing)?.node
        val appLike = payload is DragPayload.NewApp || dragged is CanvasNode.App
        if (geometry.remove.contains(pointer)) return if (dragged != null) DropTarget.Remove else null
        if (geometry.uninstall.contains(pointer)) return if (appLike) DropTarget.Uninstall else null

        val w = dragged?.rect?.w ?: 1
        val h = dragged?.rect?.h ?: 1
        val spec = state.spec

        if (geometry.dock.contains(pointer)) {
            if (w != 1 || h != 1 || dragged is CanvasNode.Widget || dragged is CanvasNode.Clock || dragged?.backdrop == true) return null
            val slotWidth = geometry.dock.width / spec.dockSize
            val slot = ((pointer.x - geometry.dock.left) / slotWidth).toInt().coerceIn(0, spec.dockSize - 1)
            val occupant = state.dock.firstOrNull { it.rect.x == slot && it.id != dragged?.id }
            return when {
                occupant == null -> DropTarget.Dock(slot)
                appLike && (occupant is CanvasNode.App || occupant is CanvasNode.Folder) -> DropTarget.Onto(occupant.id)
                else -> null
            }
        }

        if (!geometry.grid.contains(pointer)) return null
        val cellWidth = geometry.grid.width / spec.columns
        val cellHeight = geometry.grid.height / spec.rows
        val cellX = ((pointer.x - geometry.grid.left) / cellWidth).toInt().coerceIn(0, spec.columns - 1)
        val cellY = ((pointer.y - geometry.grid.top) / cellHeight).toInt().coerceIn(0, spec.rows - 1)
        val layer = dragged?.backdrop == true
        val nodes = state.pages.getOrNull(page).orEmpty().filter { it.id != dragged?.id && it.backdrop == layer }

        val occupant = nodes.firstOrNull { it.rect.overlaps(CellRect(cellX, cellY)) }
        if (occupant != null && appLike && (occupant is CanvasNode.App || occupant is CanvasNode.Folder)) {
            val centerX = geometry.grid.left + (occupant.rect.x + 0.5f) * cellWidth
            val centerY = geometry.grid.top + (occupant.rect.y + 0.5f) * cellHeight
            if (abs(pointer.x - centerX) < cellWidth * ONTO_REGION && abs(pointer.y - centerY) < cellHeight * ONTO_REGION) {
                return DropTarget.Onto(occupant.id)
            }
        }

        val x = if (w == 1) cellX else ((pointer.x - grab.x - geometry.grid.left) / cellWidth).roundToInt()
        val y = if (h == 1) cellY else ((pointer.y - grab.y - geometry.grid.top) / cellHeight).roundToInt()
        val rect = CellRect(x.coerceIn(0, spec.columns - w), y.coerceIn(0, spec.rows - h), w, h)
        return if (CellGrid.fits(spec, nodes.map { it.rect }, rect)) DropTarget.Cell(page, rect) else null
    }
}
