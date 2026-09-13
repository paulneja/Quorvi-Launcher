package com.paulneja.quorvi.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderTargetTest {
    @Test
    fun movesOneSlotAfterHalfASlot() {
        assertNull(reorderTarget(dragX = 40f, slot = 100f, from = 1, count = 3))
        assertEquals(2, reorderTarget(dragX = 60f, slot = 100f, from = 1, count = 3))
        assertEquals(0, reorderTarget(dragX = -60f, slot = 100f, from = 1, count = 3))
    }

    @Test
    fun neverPastTheEnds() {
        assertNull(reorderTarget(dragX = -300f, slot = 100f, from = 0, count = 3))
        assertNull(reorderTarget(dragX = 300f, slot = 100f, from = 2, count = 3))
    }
}
