package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.apps.AppKey
import com.paulneja.quorvi.launcher.home.CellGrid
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.Placed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridTest {
    private val spec = GridSpec(columns = 4, rows = 5)

    @Test
    fun spansOverlap() {
        assertTrue(CellRect(0, 0, 2, 2).overlaps(CellRect(1, 1)))
        assertFalse(CellRect(0, 0, 2, 2).overlaps(CellRect(2, 0)))
    }

    @Test
    fun fitsRespectsBoundsAndNeighbours() {
        val clock = CellRect(0, 0, 4, 2)
        assertTrue(CellGrid.fits(spec, listOf(clock), CellRect(0, 2, 2, 2)))
        assertFalse(CellGrid.fits(spec, listOf(clock), CellRect(3, 1)))
        assertFalse(CellGrid.fits(spec, emptyList(), CellRect(3, 0, 2, 1)))
    }

    @Test
    fun findFreeSkipsTakenAreasInReadingOrder() {
        assertEquals(CellRect(0, 2), CellGrid.findFree(spec, listOf(CellRect(0, 0, 4, 2)), 1, 1))
        assertEquals(CellRect(2, 2, 2, 2), CellGrid.findFree(spec, listOf(CellRect(0, 0, 4, 2), CellRect(0, 2, 2, 3)), 2, 2))
        assertNull(CellGrid.findFree(spec, emptyList(), 5, 1))
    }

    @Test
    fun findFreeOnPagesSpillsToNextPage() {
        val full = (0 until 5).flatMap { y -> (0 until 4).map { x -> CellRect(x, y) } }
        assertEquals(1 to CellRect(0, 0), CellGrid.findFreeOnPages(spec, mapOf(0 to full), 1, 1))
    }

    @Test
    fun relayoutKeepsWhatFitsAndMovesTheRest() {
        val smaller = GridSpec(columns = 4, rows = 4)
        val items = listOf(Placed(1, 0, CellRect(0, 0, 4, 2)), Placed(2, 0, CellRect(1, 4)), Placed(3, 0, CellRect(0, 2)))
        val result = CellGrid.relayout(smaller, items).associateBy { it.id }
        assertEquals(CellRect(0, 0, 4, 2), result.getValue(1).rect)
        assertEquals(CellRect(0, 2), result.getValue(3).rect)
        assertEquals(CellRect(1, 2), result.getValue(2).rect)
        assertEquals(3, result.size)
    }

    @Test
    fun relayoutClampsSpansWiderThanTheGrid() {
        val narrow = GridSpec(columns = 4, rows = 5)
        val result = CellGrid.relayout(narrow, listOf(Placed(1, 0, CellRect(0, 0, 6, 2))))
        assertEquals(CellRect(0, 0, 4, 2), result.single().rect)
    }

    @Test
    fun autoGridFollowsScreenSize() {
        assertEquals(GridSpec(4, 5, dockSize = 4), GridSpec.auto(360f, 800f))
        assertEquals(GridSpec(4, 5, dockSize = 4), GridSpec.auto(800f, 360f))
        assertEquals(6, GridSpec.auto(600f, 1000f).columns)
    }

    @Test
    fun appKeyRoundTrips() {
        val key = AppKey("com.example", "com.example.ui.Main\$Inner", 10)
        assertEquals(key, AppKey.decode(key.encode()))
        assertNull(AppKey.decode("garbage"))
    }
}
