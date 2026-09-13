package com.paulneja.quorvi.launcher.home

enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

data class SpanLimits(val minW: Int = 1, val minH: Int = 1, val maxW: Int = Int.MAX_VALUE, val maxH: Int = Int.MAX_VALUE, val horizontal: Boolean = true, val vertical: Boolean = true)

object ResizeMath {
    fun resize(start: CellRect, edge: Edge, cells: Int, limits: SpanLimits, spec: GridSpec, occupied: Collection<CellRect>): CellRect {
        val proposed = when (edge) {
            Edge.LEFT -> if (!limits.horizontal) start else start.copy(x = start.x + cells, w = start.w - cells)
            Edge.RIGHT -> if (!limits.horizontal) start else start.copy(w = start.w + cells)
            Edge.TOP -> if (!limits.vertical) start else start.copy(y = start.y + cells, h = start.h - cells)
            Edge.BOTTOM -> if (!limits.vertical) start else start.copy(h = start.h + cells)
        }
        val maxW = minOf(limits.maxW, spec.columns)
        val maxH = minOf(limits.maxH, spec.rows)
        if (proposed.w < limits.minW || proposed.h < limits.minH || proposed.w > maxW || proposed.h > maxH) return start
        return if (CellGrid.fits(spec, occupied, proposed)) proposed else start
    }
}
