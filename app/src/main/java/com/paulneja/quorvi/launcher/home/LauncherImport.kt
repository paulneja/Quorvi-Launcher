package com.paulneja.quorvi.launcher.home

data class L3Row(
    val id: Long,
    val container: Long,
    val screen: Int,
    val cellX: Int,
    val cellY: Int,
    val itemType: Int,
    val title: String?,
    val component: String?,
    val profileId: Long,
    val rank: Int,
)

sealed interface ImportEntry {
    data class App(val component: String) : ImportEntry
    data class Folder(val title: String, val components: List<String>) : ImportEntry
}

data class ImportPlan(val entries: List<ImportEntry>, val dock: List<String>) {
    val appCount: Int get() = entries.sumOf { if (it is ImportEntry.Folder) it.components.size else 1 }
}

object LauncherImport {
    const val DESKTOP = -100L
    const val HOTSEAT = -101L
    const val TYPE_APP = 0
    const val TYPE_FOLDER = 2
    const val TYPE_APP_PAIR = 10

    fun plan(rows: List<L3Row>, installed: Set<String>): ImportPlan {
        val main = rows.filter { it.profileId == 0L }
        val children = main.filter { it.itemType == TYPE_APP && it.container > 0 }.groupBy { it.container }
        fun appOf(r: L3Row) = r.component?.takeIf { it in installed }
        val entries = main
            .filter { it.container == DESKTOP && (it.itemType == TYPE_APP || it.itemType == TYPE_FOLDER) }
            .sortedWith(compareBy({ it.screen }, { it.cellY }, { it.cellX }))
            .mapNotNull { r ->
                when (r.itemType) {
                    TYPE_APP -> appOf(r)?.let { ImportEntry.App(it) }
                    else -> {
                        val inside = children[r.id].orEmpty().sortedWith(compareBy({ it.rank }, { it.cellY }, { it.cellX })).mapNotNull(::appOf).distinct()
                        when (inside.size) {
                            0 -> null
                            1 -> ImportEntry.App(inside.single())
                            else -> ImportEntry.Folder(r.title?.takeIf { it.isNotBlank() } ?: "", inside)
                        }
                    }
                }
            }
        val dock = main.filter { it.container == HOTSEAT && it.itemType == TYPE_APP }
            .sortedWith(compareBy({ it.screen }, { it.cellX }))
            .mapNotNull(::appOf)
        return ImportPlan(entries.distinctBy { if (it is ImportEntry.App) it.component else it }, dock)
    }

    fun componentOf(intentUri: String?): String? {
        intentUri ?: return null
        val match = Regex("component=([^;]+)").find(intentUri) ?: return null
        val raw = match.groupValues[1]
        val pkg = raw.substringBefore('/')
        val cls = raw.substringAfter('/').let { if (it.startsWith(".")) pkg + it else it }
        return "$pkg/$cls"
    }
}
