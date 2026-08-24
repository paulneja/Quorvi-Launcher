package com.paulneja.quorvi.launcher.home

import kotlin.math.floor

data class CellRect(val x: Int, val y: Int, val w: Int = 1, val h: Int = 1) {
    fun overlaps(o: CellRect): Boolean = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
}

data class GridSpec(val columns: Int, val rows: Int, val dockSize: Int = 5, val maxPages: Int = 12) {
    fun contains(r: CellRect): Boolean = r.x >= 0 && r.y >= 0 && r.w >= 1 && r.h >= 1 && r.x + r.w <= columns && r.y + r.h <= rows

    companion object {
        const val MIN_CELL_WIDTH_DP = 88f
        const val MIN_CELL_HEIGHT_DP = 104f
        const val CHROME_HEIGHT_DP = 256f

        fun auto(widthDp: Float, heightDp: Float): GridSpec {
            val portraitWidth = minOf(widthDp, heightDp)
            val portraitHeight = maxOf(widthDp, heightDp) - CHROME_HEIGHT_DP
            val columns = floor(portraitWidth / MIN_CELL_WIDTH_DP).toInt().coerceIn(4, 6)
            val rows = floor(portraitHeight / MIN_CELL_HEIGHT_DP).toInt().coerceIn(4, 8)
            return GridSpec(columns, rows, dockSize = columns.coerceAtMost(5))
        }

        const val BENTO_CHROME_DP = 292f

        fun bento(widthDp: Float, heightDp: Float): GridSpec {
            val portraitWidth = minOf(widthDp, heightDp)
            val portraitHeight = maxOf(widthDp, heightDp)
            val cell = (portraitWidth - 16f) / 4f
            val rows = floor((portraitHeight - BENTO_CHROME_DP) / cell).toInt().coerceIn(4, 8)
            return GridSpec(4, rows, dockSize = 4)
        }
    }
}

data class Placed(val id: Long, val page: Int, val rect: CellRect)

object CellGrid {
    fun fits(spec: GridSpec, occupied: Collection<CellRect>, target: CellRect): Boolean =
        spec.contains(target) && occupied.none { it.overlaps(target) }

    fun findFree(spec: GridSpec, occupied: Collection<CellRect>, w: Int, h: Int): CellRect? {
        if (w > spec.columns || h > spec.rows) return null
        for (y in 0..spec.rows - h) {
            for (x in 0..spec.columns - w) {
                val candidate = CellRect(x, y, w, h)
                if (occupied.none { it.overlaps(candidate) }) return candidate
            }
        }
        return null
    }

    fun findFreeOnPages(spec: GridSpec, byPage: Map<Int, List<CellRect>>, w: Int, h: Int, startPage: Int = 0): Pair<Int, CellRect>? {
        for (page in startPage until spec.maxPages) {
            findFree(spec, byPage[page].orEmpty(), w, h)?.let { return page to it }
        }
        return null
    }

    fun relayout(spec: GridSpec, items: List<Placed>): List<Placed> {
        val taken = mutableMapOf<Int, MutableList<CellRect>>()
        val sorted = items.sortedWith(compareBy({ it.page }, { it.rect.y }, { it.rect.x }))
        val kept = mutableListOf<Placed>()
        val homeless = mutableListOf<Placed>()
        for (item in sorted) {
            val clamped = item.rect.copy(w = item.rect.w.coerceAtMost(spec.columns), h = item.rect.h.coerceAtMost(spec.rows))
            val onPage = taken.getOrPut(item.page) { mutableListOf() }
            if (fits(spec, onPage, clamped)) {
                onPage += clamped
                kept += item.copy(rect = clamped)
            } else {
                homeless += item.copy(rect = clamped)
            }
        }
        for (item in homeless) {
            val spot = findFreeOnPages(spec, taken, item.rect.w, item.rect.h, item.page) ?: findFreeOnPages(spec, taken, item.rect.w, item.rect.h) ?: continue
            taken.getOrPut(spot.first) { mutableListOf() } += spot.second
            kept += item.copy(page = spot.first, rect = spot.second)
        }
        return kept
    }
}
