package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Edge
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.ResizeMath
import com.paulneja.quorvi.launcher.home.SpanLimits
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeMathTest {
    private val spec = GridSpec(4, 5)
    private val start = CellRect(1, 1, 2, 2)

    @Test
    fun growsTowardTheDraggedEdge() {
        assertEquals(CellRect(1, 1, 3, 2), ResizeMath.resize(start, Edge.RIGHT, 1, SpanLimits(), spec, emptyList()))
        assertEquals(CellRect(0, 1, 3, 2), ResizeMath.resize(start, Edge.LEFT, -1, SpanLimits(), spec, emptyList()))
        assertEquals(CellRect(1, 0, 2, 3), ResizeMath.resize(start, Edge.TOP, -1, SpanLimits(), spec, emptyList()))
    }

    @Test
    fun respectsMinimumsAndGridBounds() {
        assertEquals(start, ResizeMath.resize(start, Edge.RIGHT, -1, SpanLimits(minW = 2), spec, emptyList()))
        assertEquals(start, ResizeMath.resize(start, Edge.RIGHT, 2, SpanLimits(), spec, emptyList()))
    }

    @Test
    fun respectsResizeModeAndNeighbours() {
        assertEquals(start, ResizeMath.resize(start, Edge.BOTTOM, 1, SpanLimits(vertical = false), spec, emptyList()))
        assertEquals(start, ResizeMath.resize(start, Edge.BOTTOM, 1, SpanLimits(), spec, listOf(CellRect(1, 3))))
    }
}
