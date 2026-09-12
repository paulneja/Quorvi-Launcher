package com.paulneja.quorvi.launcher.home

import com.paulneja.quorvi.launcher.apps.AppEntry

sealed interface CanvasNode {
    val id: Long
    val container: Int
    val page: Int
    val rect: CellRect
    val locked: Boolean

    data class App(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean, val app: AppEntry) : CanvasNode

    data class Folder(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean, val title: String, val apps: List<App>) : CanvasNode

    data class Widget(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean, val widgetId: Int) : CanvasNode

    data class Shortcut(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean, val packageName: String, val shortcutId: String, val user: android.os.UserHandle) : CanvasNode

    data class Card(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean, val kind: String, val config: String? = null) : CanvasNode

    data class Clock(override val id: Long, override val container: Int, override val page: Int, override val rect: CellRect, override val locked: Boolean) : CanvasNode
}

val CanvasNode.backdrop: Boolean get() = this is CanvasNode.Card && kind == GLASS_KIND

data class PageMeta(val template: PageTemplate, val title: String? = null)

data class CanvasState(
    val spec: GridSpec,
    val pages: List<List<CanvasNode>>,
    val dock: List<CanvasNode>,
    val pageMeta: List<PageMeta> = List(pages.size) { PageMeta(PageTemplate.EMPTY) },
) {
    val pageCount: Int get() = pages.size

    fun node(id: Long): CanvasNode? = (pages.flatten() + dock).firstOrNull { it.id == id }
        ?: (pages.flatten() + dock).filterIsInstance<CanvasNode.Folder>().flatMap { it.apps }.firstOrNull { it.id == id }
}
