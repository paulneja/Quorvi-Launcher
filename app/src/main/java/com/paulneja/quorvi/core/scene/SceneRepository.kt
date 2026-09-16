package com.paulneja.quorvi.core.scene

import com.paulneja.quorvi.core.settings.Appearance
import com.paulneja.quorvi.core.settings.GridMode
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppKey
import com.paulneja.quorvi.launcher.apps.Profile
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.HomeDao
import com.paulneja.quorvi.launcher.home.HomeItemEntity
import com.paulneja.quorvi.launcher.home.ItemType
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.MotionProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

data class ImportReport(val placed: Int, val missingApps: Int, val widgets: Int)

@Singleton
class SceneRepository @Inject constructor(
    private val dao: HomeDao,
    private val settings: QuorviSettings,
    private val catalog: AppCatalog,
    private val widgets: WidgetHostManager,
) {
    suspend fun export(now: Long = System.currentTimeMillis()): String {
        val items = dao.all()
        val layout = settings.currentLayout()
        val appearance = settings.appearance.first()
        fun component(key: String?) = key?.let(AppKey::decode)?.let { "${it.packageName}/${it.className}" }
        val children = items.filter { it.container == Container.FOLDER }.groupBy { it.parentId }
        val sceneItems = items.filter { it.container != Container.FOLDER }.mapNotNull { item ->
            val type = SceneCodec.typeName(item.type) ?: return@mapNotNull null
            val container = SceneCodec.containerName(item.container) ?: return@mapNotNull null
            SceneItem(
                type = type,
                container = container,
                page = item.page, x = item.cellX, y = item.cellY, w = item.spanX, h = item.spanY,
                app = if (item.type == ItemType.SHORTCUT) item.appKey?.let(AppKey::decode)?.packageName?.let { "$it/shortcut" } else component(item.appKey),
                shortcut = item.shortcutId,
                title = item.title,
                locked = item.locked,
                children = children[item.id].orEmpty().sortedBy { it.rank }.mapNotNull { component(it.appKey) },
                widgetProvider = item.widgetId?.let { widgets.info(it)?.provider?.flattenToString() },
            )
        }
        return SceneCodec.encode(
            Scene(
                schemaVersion = SceneCodec.VERSION,
                exportedAt = now,
                grid = SceneGrid(layout.gridMode.name, layout.columns, layout.rows),
                appearance = SceneAppearance(appearance.accent, appearance.glass, appearance.motion.name, appearance.iconScale, appearance.showLabels, appearance.iconPack),
                items = sceneItems,
            ),
        )
    }

    suspend fun import(text: String): Result<ImportReport> = SceneCodec.decode(text).mapCatching { scene ->
        val apps = catalog.apps.filterNotNull().first().filter { it.profile == Profile.MAIN }
        val byComponent = apps.associateBy { "${it.key.packageName}/${it.key.className}" }
        val mainSerial = apps.firstOrNull()?.key?.userSerial ?: 0L
        var missing = 0
        var widgetsSkipped = 0
        val entities = mutableListOf<Pair<HomeItemEntity, List<String>>>()
        scene.items.forEach { item ->
            val type = SceneCodec.typeOf(item.type)!!
            val container = SceneCodec.containerOf(item.container)!!
            val base = HomeItemEntity(type = type, container = container, page = item.page, cellX = item.x, cellY = item.y, spanX = item.w, spanY = item.h, title = item.title, locked = item.locked)
            when (type) {
                ItemType.APP -> byComponent[item.app]?.let { entities += base.copy(appKey = it.key.encode()) to emptyList() } ?: missing++
                ItemType.SHORTCUT -> {
                    val pkg = item.app!!.substringBefore('/')
                    if (apps.any { it.key.packageName == pkg }) entities += base.copy(appKey = AppKey(pkg, "shortcut", mainSerial).encode(), shortcutId = item.shortcut) to emptyList()
                    else missing++
                }
                ItemType.FOLDER -> {
                    val inside = item.children.mapNotNull { byComponent[it]?.key?.encode() }
                    missing += item.children.size - inside.size
                    if (inside.isNotEmpty()) entities += base to inside
                }
                ItemType.WIDGET -> widgetsSkipped++
                else -> entities += base to emptyList()
            }
        }
        dao.all().mapNotNull { it.widgetId }.forEach(widgets::release)
        dao.clear()
        entities.forEach { (entity, children) ->
            val id = dao.insert(entity)
            children.forEachIndexed { rank, key ->
                dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.FOLDER, parentId = id, rank = rank, appKey = key))
            }
        }
        settings.setGrid(GridMode.valueOf(scene.grid.mode), scene.grid.columns, scene.grid.rows)
        settings.setAppearance(
            Appearance(
                accent = scene.appearance.accent,
                glass = scene.appearance.glass,
                motion = runCatching { MotionProfile.valueOf(scene.appearance.motion) }.getOrDefault(MotionProfile.CINEMATIC),
                iconScale = scene.appearance.iconScale,
                showLabels = scene.appearance.showLabels,
                iconPack = scene.appearance.iconPack,
            ),
        )
        ImportReport(entities.size, missing, widgetsSkipped)
    }
}
