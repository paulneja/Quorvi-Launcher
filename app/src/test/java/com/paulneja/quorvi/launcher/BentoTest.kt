package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.home.BlockKinds
import com.paulneja.quorvi.launcher.home.BlockSize
import com.paulneja.quorvi.launcher.home.BentoSeed
import com.paulneja.quorvi.launcher.home.CellGrid
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.HomeItemEntity
import com.paulneja.quorvi.launcher.home.ItemType
import com.paulneja.quorvi.launcher.home.PageTemplate
import com.paulneja.quorvi.launcher.home.PageTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BentoTest {
    @Test
    fun bentoGridUsesFourColumnsAndFitsRowsToHeight() {
        val armor = GridSpec.bento(411f, 914f)
        assertEquals(4, armor.columns)
        assertEquals(6, armor.rows)
        assertEquals(4, GridSpec.bento(360f, 640f).rows)
        assertTrue(GridSpec.bento(411f, 1400f).rows <= 8)
        assertEquals(4, GridSpec.bento(914f, 411f).columns)
    }

    @Test
    fun blockKindsResolveFamiliesAndSizes() {
        val agent = BlockKinds.of("panel:agent")
        assertNotNull(agent)
        assertEquals(BlockSize.S, agent!!.default)
        assertTrue(BlockSize.M in agent.sizes)
        assertEquals("host", BlockKinds.of("host:42")?.id)
        assertNull(BlockKinds.of("nope"))
        assertTrue(BlockKinds.of("today")!!.sizes.all { it.w == 4 })
    }

    @Test
    fun everyTemplateFitsWithoutOverlapOnAnyHeight() {
        for (rows in 4..8) {
            val spec = GridSpec(4, rows)
            for (template in PageTemplate.entries) {
                val blocks = PageTemplates.blocks(template, spec)
                val placed = mutableListOf<CellRect>()
                blocks.forEach { b ->
                    assertTrue("$template $rows ${b.kind}", CellGrid.fits(spec, placed, b.rect))
                    placed += b.rect
                }
            }
        }
    }

    @Test
    fun homeKeepsClockOnTopAndFourPanelTilesWithinThumbReach() {
        val spec = GridSpec(4, 6)
        val blocks = PageTemplates.blocks(PageTemplate.HOME, spec)
        val clock = blocks.single { it.kind == "clock" }
        assertEquals(0, clock.rect.y)
        val tiles = blocks.filter { it.kind.startsWith("panel:") }
        assertEquals(listOf("panel:agent", "panel:github", "panel:network", "panel:system"), tiles.map { it.kind })
        assertTrue(tiles.all { it.rect.y >= spec.rows - 2 })
    }

    @Test
    fun smallScreensDropLowPriorityBlocksFirst() {
        val small = PageTemplates.blocks(PageTemplate.TODAY, GridSpec(4, 4)).map { it.kind }
        val tall = PageTemplates.blocks(PageTemplate.TODAY, GridSpec(4, 6)).map { it.kind }
        assertTrue("today" in small)
        assertTrue(tall.containsAll(listOf("today", "media", "notifications", "apps")))
        assertTrue(small.size < tall.size)
    }

    @Test
    fun onlyClockAndEmptyPagesCountAsTrivial() {
        val clock = HomeItemEntity(type = ItemType.CLOCK, container = Container.PAGE, spanX = 4, spanY = 2)
        val dockApp = HomeItemEntity(type = ItemType.APP, container = Container.DOCK, appKey = "a/b#0")
        val pageApp = HomeItemEntity(type = ItemType.APP, container = Container.PAGE, cellY = 3, appKey = "a/b#0")
        assertTrue(BentoSeed.isTrivial(emptyList()))
        assertTrue(BentoSeed.isTrivial(listOf(clock, dockApp)))
        assertFalse(BentoSeed.isTrivial(listOf(clock, pageApp)))
    }

    @Test
    fun presetsStayLeanAndDistinct() {
        assertEquals(listOf(PageTemplate.HOME, PageTemplate.APPS), BentoSeed.pagesFor(com.paulneja.quorvi.core.settings.HomePreset.CLEAN))
        assertEquals(listOf(PageTemplate.HOME, PageTemplate.TODAY, PageTemplate.APPS), BentoSeed.pagesFor(com.paulneja.quorvi.core.settings.HomePreset.FULL))
        assertEquals(listOf(PageTemplate.START), BentoSeed.pagesFor(com.paulneja.quorvi.core.settings.HomePreset.BLANK))
        assertEquals(listOf(PageTemplate.MINIMAL), BentoSeed.pagesFor(com.paulneja.quorvi.core.settings.HomePreset.MINIMAL))
        assertEquals(BentoSeed.pagesFor(com.paulneja.quorvi.core.settings.HomePreset.CLEAN), BentoSeed.defaultPages)
        val minimal = PageTemplates.blocks(PageTemplate.MINIMAL, GridSpec(4, 6)).map { it.kind }
        assertEquals(listOf("clock", "favlist"), minimal)
        assertEquals(listOf("clock"), PageTemplates.blocks(PageTemplate.START, GridSpec(4, 6)).map { it.kind })
    }
}
