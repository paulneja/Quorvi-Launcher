package com.paulneja.quorvi.launcher.home

enum class BlockSize(val w: Int, val h: Int) { S(2, 1), M(2, 2), L(4, 2), XL(4, 4), W(4, 1), T(2, 4) }

data class BlockKind(val id: String, val sizes: List<BlockSize>, val default: BlockSize = sizes.first())

object BlockKinds {
    private val panelSizes = listOf(BlockSize.S, BlockSize.M)

    val panels = listOf("agent", "github", "network", "system", "shell", "media", "ha", "servers", "capsules", "notifications", "today").map { "panel:$it" }

    val all: List<BlockKind> = panels.map { BlockKind(it, panelSizes) } + listOf(
        BlockKind("clock", listOf(BlockSize.L, BlockSize.W)),
        BlockKind("today", listOf(BlockSize.L, BlockSize.W, BlockSize.XL)),
        BlockKind("media", listOf(BlockSize.M, BlockSize.L, BlockSize.W)),
        BlockKind("toggles", listOf(BlockSize.L, BlockSize.M, BlockSize.W)),
        BlockKind("notifications", listOf(BlockSize.M, BlockSize.L, BlockSize.T)),
        BlockKind("apps", listOf(BlockSize.L, BlockSize.W, BlockSize.XL)),
        BlockKind("system", listOf(BlockSize.M, BlockSize.S, BlockSize.L)),
        BlockKind("capsules", listOf(BlockSize.W, BlockSize.M, BlockSize.L)),
        BlockKind("contacts", listOf(BlockSize.S, BlockSize.W, BlockSize.M)),
        BlockKind("photos", listOf(BlockSize.M, BlockSize.L)),
        BlockKind("note", listOf(BlockSize.S, BlockSize.M, BlockSize.L)),
        BlockKind("timer", listOf(BlockSize.M, BlockSize.S)),
        BlockKind("agents", listOf(BlockSize.L, BlockSize.M, BlockSize.XL)),
        BlockKind("hosts", listOf(BlockSize.W, BlockSize.L, BlockSize.M)),
        BlockKind("http", listOf(BlockSize.W, BlockSize.M, BlockSize.L)),
        BlockKind("ci", listOf(BlockSize.M, BlockSize.L)),
        BlockKind("ghpending", listOf(BlockSize.L, BlockSize.M, BlockSize.T)),
        BlockKind("ha", listOf(BlockSize.M, BlockSize.L)),
        BlockKind("host", listOf(BlockSize.L, BlockSize.M)),
        BlockKind("docker", listOf(BlockSize.L, BlockSize.M)),
        BlockKind("favlist", listOf(BlockSize.XL, BlockSize.L)),
        BlockKind(GLASS_KIND, listOf(BlockSize.L, BlockSize.W, BlockSize.M, BlockSize.XL)),
    )

    private val byId = all.associateBy { it.id }

    fun of(kind: String): BlockKind? = byId[kind] ?: byId[kind.substringBefore(':')]?.takeIf { kind.contains(':') }
}

enum class PageTemplate { HOME, TODAY, CONTROL, NEXO, APPS, EMPTY, START, MINIMAL, TERMINAL }

data class SeedBlock(val kind: String, val rect: CellRect)

object PageTemplates {
    fun blocks(template: PageTemplate, spec: GridSpec, ai: Boolean = true): List<SeedBlock> = when (template) {
        PageTemplate.HOME -> home(spec, ai)
        PageTemplate.TODAY -> flow(spec, listOf("today" to listOf(BlockSize.L), "media" to listOf(BlockSize.M), "notifications" to listOf(BlockSize.M), "apps" to listOf(BlockSize.L, BlockSize.W), "photos" to listOf(BlockSize.L)))
        PageTemplate.CONTROL -> flow(spec, listOf("toggles" to listOf(BlockSize.L), "system" to listOf(BlockSize.M), "timer" to listOf(BlockSize.M), "capsules" to listOf(BlockSize.W), "contacts" to listOf(BlockSize.S), "note" to listOf(BlockSize.S)))
        PageTemplate.NEXO -> flow(spec, listOf("agents" to listOf(BlockSize.L), "ci" to listOf(BlockSize.M), "ha" to listOf(BlockSize.M), "hosts" to listOf(BlockSize.W), "http" to listOf(BlockSize.W)))
        PageTemplate.APPS, PageTemplate.EMPTY, PageTemplate.TERMINAL -> emptyList()
        PageTemplate.START -> listOf(SeedBlock("clock", CellRect(0, 0, spec.columns, 2)))
        PageTemplate.MINIMAL -> listOf(SeedBlock("clock", CellRect(0, 0, spec.columns, 2)), SeedBlock("favlist", CellRect(0, 2, spec.columns, spec.rows - 2)))
    }

    private fun home(spec: GridSpec, ai: Boolean): List<SeedBlock> {
        val half = spec.columns / 2
        val out = mutableListOf(SeedBlock("clock", CellRect(0, 0, spec.columns, 2)))
        val top = spec.rows - 2
        if (top < 2) return out
        listOf(if (ai) "panel:agent" else "panel:shell", "panel:github", "panel:network", "panel:system").forEachIndexed { i, kind ->
            out += SeedBlock(kind, CellRect((i % 2) * half, top + i / 2, half, 1))
        }
        return out
    }

    private fun flow(spec: GridSpec, wanted: List<Pair<String, List<BlockSize>>>): List<SeedBlock> {
        val placed = mutableListOf<SeedBlock>()
        for ((kind, sizes) in wanted) {
            for (size in sizes) {
                val w = if (size.w == 4) spec.columns else (spec.columns / 2).coerceAtLeast(1)
                val spot = CellGrid.findFree(spec, placed.map { it.rect }, w, size.h) ?: continue
                placed += SeedBlock(kind, spot)
                break
            }
        }
        return placed
    }
}

object BentoSeed {
    val defaultPages get() = pagesFor(com.paulneja.quorvi.core.settings.HomePreset.CLEAN)

    fun pagesFor(preset: com.paulneja.quorvi.core.settings.HomePreset): List<PageTemplate> = when (preset) {
        com.paulneja.quorvi.core.settings.HomePreset.CLEAN -> listOf(PageTemplate.HOME, PageTemplate.APPS)
        com.paulneja.quorvi.core.settings.HomePreset.FULL -> listOf(PageTemplate.HOME, PageTemplate.TODAY, PageTemplate.APPS)
        com.paulneja.quorvi.core.settings.HomePreset.BLANK -> listOf(PageTemplate.START)
        com.paulneja.quorvi.core.settings.HomePreset.MINIMAL -> listOf(PageTemplate.MINIMAL)
    }

    fun isTrivial(items: List<HomeItemEntity>): Boolean =
        items.filter { it.container == Container.PAGE }.all { it.type == ItemType.CLOCK }
}
