package com.paulneja.quorvi.launcher.home

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object HomeBackup {
    @Serializable
    private data class Item(
        val id: Long, val type: Int, val container: Int, val page: Int, val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int,
        val appKey: String? = null, val widgetId: Int? = null, val shortcutId: String? = null, val parentId: Long? = null,
        val title: String? = null, val locked: Boolean = false, val rank: Int = 0, val config: String? = null,
    )

    @Serializable
    private data class Page(val page: Int, val template: String, val title: String? = null)

    @Serializable
    private data class Snapshot(val items: List<Item>, val pages: List<Page>? = null)

    data class Restored(val items: List<HomeItemEntity>, val pages: List<HomePageEntity>?)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(items: List<HomeItemEntity>, pages: List<HomePageEntity>): String = json.encodeToString(
        Snapshot.serializer(),
        Snapshot(
            items.map { Item(it.id, it.type, it.container, it.page, it.cellX, it.cellY, it.spanX, it.spanY, it.appKey, it.widgetId, it.shortcutId, it.parentId, it.title, it.locked, it.rank, it.config) },
            pages.map { Page(it.page, it.template, it.title) },
        ),
    )

    fun decode(text: String): Restored? {
        val trimmed = text.trimStart()
        val snapshot = runCatching {
            if (trimmed.startsWith("[")) Snapshot(json.decodeFromString(ListSerializer(Item.serializer()), trimmed))
            else json.decodeFromString(Snapshot.serializer(), trimmed)
        }.getOrNull() ?: return null
        return Restored(
            snapshot.items.map { HomeItemEntity(it.id, it.type, it.container, it.page, it.cellX, it.cellY, it.spanX, it.spanY, it.appKey, it.widgetId, it.shortcutId, it.parentId, it.title, it.locked, it.rank, it.config) },
            snapshot.pages?.map { HomePageEntity(it.page, it.template, it.title) },
        )
    }
}
