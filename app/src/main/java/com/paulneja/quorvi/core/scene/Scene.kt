package com.paulneja.quorvi.core.scene

import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.ItemType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Scene(
    val schemaVersion: Int,
    val exportedAt: Long,
    val grid: SceneGrid,
    val appearance: SceneAppearance,
    val items: List<SceneItem>,
)

@Serializable
data class SceneGrid(val mode: String, val columns: Int, val rows: Int)

@Serializable
data class SceneAppearance(
    val accent: Long,
    val glass: Float,
    val motion: String,
    val iconScale: Float,
    val showLabels: Boolean,
    val iconPack: String? = null,
)

@Serializable
data class SceneItem(
    val type: String,
    val container: String,
    val page: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    val app: String? = null,
    val shortcut: String? = null,
    val title: String? = null,
    val locked: Boolean = false,
    val children: List<String> = emptyList(),
    @SerialName("widgetProvider") val widgetProvider: String? = null,
)

object SceneCodec {
    const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false; encodeDefaults = false }
    private val types = mapOf(ItemType.APP to "app", ItemType.WIDGET to "widget", ItemType.FOLDER to "folder", ItemType.SHORTCUT to "shortcut", ItemType.CLOCK to "clock")
    private val containers = mapOf(Container.PAGE to "page", Container.DOCK to "dock")

    fun typeName(type: Int): String? = types[type]
    fun typeOf(name: String): Int? = types.entries.firstOrNull { it.value == name }?.key
    fun containerName(container: Int): String? = containers[container]
    fun containerOf(name: String): Int? = containers.entries.firstOrNull { it.value == name }?.key

    fun encode(scene: Scene): String = json.encodeToString(Scene.serializer(), scene)

    fun decode(text: String): Result<Scene> = runCatching { json.decodeFromString(Scene.serializer(), text) }.mapCatching { scene ->
        validate(scene)
        scene
    }

    fun validate(scene: Scene) {
        require(scene.schemaVersion == VERSION) { "Unsupported scene version ${scene.schemaVersion}" }
        require(scene.grid.mode in setOf("AUTO", "MANUAL")) { "Unknown grid mode" }
        require(scene.grid.columns in 3..8 && scene.grid.rows in 3..10) { "Grid out of range" }
        require(scene.appearance.glass in 0f..1f && scene.appearance.iconScale in 0.5f..1.6f) { "Appearance out of range" }
        require(scene.items.size <= 1000) { "Too many items" }
        scene.items.forEach { item ->
            require(typeOf(item.type) != null) { "Unknown item type ${item.type}" }
            require(containerOf(item.container) != null) { "Unknown container ${item.container}" }
            require(item.page in 0..32 && item.x in 0..16 && item.y in 0..16 && item.w in 1..8 && item.h in 1..10) { "Item position out of range" }
            when (item.type) {
                "app" -> require(!item.app.isNullOrBlank()) { "App item without component" }
                "shortcut" -> require(!item.app.isNullOrBlank() && !item.shortcut.isNullOrBlank()) { "Shortcut item incomplete" }
                "folder" -> require(item.children.isNotEmpty()) { "Empty folder" }
            }
        }
    }
}
