package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.ui.canvas.DrawerState
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerStateTest {
    private fun drawer() = DrawerState(TestScope()).apply { heightPx = 1000f }

    @Test
    fun slowShortDragUpStillOpens() {
        val d = drawer()
        d.dragBy(-200f)
        assertEquals(1f, d.targetFor(0f))
    }

    @Test
    fun tinyDragUpSnapsBack() {
        val d = drawer()
        d.dragBy(-100f)
        assertEquals(0f, d.targetFor(0f))
    }

    @Test
    fun dragDownFromOpenCloses() {
        val d = drawer()
        d.dragBy(-1000f)
        d.dragBy(200f)
        assertEquals(0f, d.targetFor(0f))
    }

    @Test
    fun smallDragDownFromOpenStaysOpen() {
        val d = drawer()
        d.dragBy(-1000f)
        d.dragBy(100f)
        assertEquals(1f, d.targetFor(0f))
    }

    @Test
    fun flingWinsOverPosition() {
        val d = drawer()
        d.dragBy(-50f)
        assertEquals(1f, d.targetFor(-3000f))
        d.dragBy(-900f)
        assertEquals(0f, d.targetFor(3000f))
    }
}
