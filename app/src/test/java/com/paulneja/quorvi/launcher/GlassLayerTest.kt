package com.paulneja.quorvi.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CanvasState
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.GLASS_KIND
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.HomeItemEntity
import com.paulneja.quorvi.launcher.home.ItemType
import com.paulneja.quorvi.ui.canvas.CanvasGeometry
import com.paulneja.quorvi.ui.canvas.DragPayload
import com.paulneja.quorvi.ui.canvas.DropResolver
import com.paulneja.quorvi.ui.canvas.DropTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassLayerTest {
    private fun card(id: Long, kind: String, rect: CellRect) = CanvasNode.Card(id, Container.PAGE, 0, rect, false, kind)

    private val glass = card(1, GLASS_KIND, CellRect(0, 0, 4, 2))
    private val note = card(2, "note", CellRect(0, 3, 2, 1))
    private val state = CanvasState(GridSpec(4, 5), listOf(listOf(glass, note)), emptyList())
    private val geometry = CanvasGeometry().apply { grid = Rect(0f, 0f, 400f, 500f) }

    private fun drop(node: CanvasNode, x: Float, y: Float) = DropResolver.resolve(state, DragPayload.Existing(node), Offset(x, y), Offset.Zero, geometry, 0)

    @Test
    fun blocksGoOnTopOfAGlassSpace() {
        assertEquals(DropTarget.Cell(0, CellRect(0, 0, 2, 1)), drop(note, 5f, 5f))
    }

    @Test
    fun glassSpacesOnlyCollideWithEachOther() {
        val second = card(3, GLASS_KIND, CellRect(0, 4, 4, 1))
        assertNull(drop(second, 5f, 105f))
        assertEquals(DropTarget.Cell(0, CellRect(0, 3, 4, 1)), drop(second, 5f, 305f))
    }

    @Test
    fun onlyGlassCardsAreBackdrops() {
        assertTrue(HomeItemEntity(type = ItemType.NEXO_CARD, container = Container.PAGE, title = GLASS_KIND).backdrop)
        assertFalse(HomeItemEntity(type = ItemType.NEXO_CARD, container = Container.PAGE, title = "note").backdrop)
        assertFalse(HomeItemEntity(type = ItemType.APP, container = Container.PAGE, title = GLASS_KIND).backdrop)
    }
}
