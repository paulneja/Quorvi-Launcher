package com.paulneja.quorvi.launcher.home

import android.content.Context
import com.paulneja.quorvi.core.settings.GridMode
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.AppKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PlaceResult {
    data object Done : PlaceResult
    data object AlreadyThere : PlaceResult
    data object NoSpace : PlaceResult
    data object Rejected : PlaceResult
}

@Singleton
class HomeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HomeDao,
    private val catalog: AppCatalog,
    private val settings: QuorviSettings,
    private val defaults: DefaultApps,
    private val scope: CoroutineScope,
) {
    private val writes = Mutex()
    private val autoSpec: GridSpec = context.resources.displayMetrics.let { m ->
        GridSpec.bento(m.widthPixels / m.density, m.heightPixels / m.density)
    }

    val spec: StateFlow<GridSpec> = settings.layout
        .map { if (it.gridMode == GridMode.MANUAL) GridSpec(it.columns, it.rows, dockSize = it.columns.coerceAtMost(5)) else autoSpec }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, autoSpec)

    init {
        scope.launch { catalog.removed.collect { removePackage(it.packageName, it.userSerial) } }
        scope.launch { spec.collect { applySpec(it) } }
        scope.launch {
            val apps = catalog.apps.filterNotNull().first()
            seedIfNeeded(apps)
            seedBentoIfNeeded(apps)
        }
    }

    val canvas: Flow<CanvasState> = combine(dao.observe(), dao.observePages(), catalog.apps, spec) { items, pages, apps, grid ->
        build(items, pages, apps.orEmpty().associateBy { it.key }, grid)
    }

    private fun build(items: List<HomeItemEntity>, pages: List<HomePageEntity>, apps: Map<AppKey, AppEntry>, grid: GridSpec): CanvasState {
        val children = items.filter { it.container == Container.FOLDER }.groupBy { it.parentId }
        fun appNode(item: HomeItemEntity): CanvasNode.App? {
            val key = item.appKey?.let(AppKey::decode) ?: return null
            val app = apps[key] ?: return null
            return CanvasNode.App(item.id, item.container, item.page, item.rect, item.locked, app)
        }
        fun node(item: HomeItemEntity): CanvasNode? = when (item.type) {
            ItemType.APP -> appNode(item)
            ItemType.FOLDER -> {
                val inside = children[item.id].orEmpty().sortedBy { it.rank }.mapNotNull(::appNode)
                if (inside.isEmpty()) null else CanvasNode.Folder(item.id, item.container, item.page, item.rect, item.locked, item.title.orEmpty(), inside)
            }
            ItemType.WIDGET -> item.widgetId?.let { CanvasNode.Widget(item.id, item.container, item.page, item.rect, item.locked, it) }
            ItemType.CLOCK -> CanvasNode.Clock(item.id, item.container, item.page, item.rect, item.locked)
            ItemType.NEXO_CARD -> CanvasNode.Card(item.id, item.container, item.page, item.rect, item.locked, item.title.orEmpty(), item.config)
            ItemType.SHORTCUT -> {
                val key = item.appKey?.let(AppKey::decode)
                val user = key?.let { catalog.user(it.userSerial) }
                val id = item.shortcutId
                if (key == null || user == null || id == null) null
                else CanvasNode.Shortcut(item.id, item.container, item.page, item.rect, item.locked, key.packageName, id, user)
            }
            else -> null
        }
        val top = items.filter { it.container != Container.FOLDER }.mapNotNull(::node)
        val onPages = top.filter { it.container == Container.PAGE }
        val pageCount = maxOf(onPages.maxOfOrNull { it.page } ?: 0, pages.maxOfOrNull { it.page } ?: 0) + 1
        val meta = pages.associateBy { it.page }
        return CanvasState(
            spec = grid,
            pages = List(pageCount) { page -> onPages.filter { it.page == page } },
            dock = top.filter { it.container == Container.DOCK }.sortedBy { it.rect.x },
            pageMeta = List(pageCount) { page ->
                meta[page]?.let { PageMeta(runCatching { PageTemplate.valueOf(it.template) }.getOrDefault(PageTemplate.EMPTY), it.title) } ?: PageMeta(PageTemplate.EMPTY)
            },
        )
    }

    private suspend fun pageRects(exclude: Long? = null, backdrop: Boolean = false): Map<Int, List<CellRect>> =
        withClosedPages(dao.all().filter { it.container == Container.PAGE && it.id != exclude && it.backdrop == backdrop }.groupBy({ it.page }, { it.rect }))

    private suspend fun closedPages(): Set<Int> = dao.pages().filter { it.template == PageTemplate.TERMINAL.name }.map { it.page }.toSet()

    private suspend fun withClosedPages(byPage: Map<Int, List<CellRect>>): Map<Int, List<CellRect>> {
        val closed = closedPages()
        if (closed.isEmpty()) return byPage
        val full = CellRect(0, 0, spec.value.columns, spec.value.rows)
        return byPage + closed.associateWith { listOf(full) }
    }

    suspend fun addApp(app: AppEntry, container: Int = Container.PAGE): PlaceResult = writes.withLock {
        val key = app.key.encode()
        val items = dao.all()
        if (items.any { it.appKey == key && it.container == container }) return PlaceResult.AlreadyThere
        val slot = freeSlot(items, container, 1, 1) ?: return PlaceResult.NoSpace
        dao.insert(HomeItemEntity(type = ItemType.APP, container = container, page = slot.first, cellX = slot.second.x, cellY = slot.second.y, appKey = key))
        PlaceResult.Done
    }

    suspend fun addAppAt(app: AppEntry, container: Int, page: Int, rect: CellRect): PlaceResult = writes.withLock {
        if (!canPlace(dao.all(), null, container, page, rect)) return PlaceResult.Rejected
        dao.insert(HomeItemEntity(type = ItemType.APP, container = container, page = page, cellX = rect.x, cellY = rect.y, appKey = app.key.encode()))
        PlaceResult.Done
    }

    suspend fun addShortcut(packageName: String, shortcutId: String, userSerial: Long, preferredPage: Int = 0): PlaceResult = writes.withLock {
        val items = dao.all()
        val key = AppKey(packageName, "shortcut", userSerial).encode()
        if (items.any { it.type == ItemType.SHORTCUT && it.appKey == key && it.shortcutId == shortcutId }) return PlaceResult.AlreadyThere
        val slot = freeSlot(items, Container.PAGE, 1, 1, preferredPage) ?: return PlaceResult.NoSpace
        dao.insert(HomeItemEntity(type = ItemType.SHORTCUT, container = Container.PAGE, page = slot.first, cellX = slot.second.x, cellY = slot.second.y, appKey = key, shortcutId = shortcutId))
        PlaceResult.Done
    }

    suspend fun pinnedShortcutIds(packageName: String, userSerial: Long): List<String> =
        dao.shortcutsOf(packageName).filter { AppKey.decode(it.appKey.orEmpty())?.userSerial == userSerial }.mapNotNull { it.shortcutId }

    suspend fun addWidget(widgetId: Int, spanX: Int, spanY: Int, preferredPage: Int): Long? = writes.withLock {
        val grid = spec.value
        val w = spanX.coerceIn(1, grid.columns)
        val h = spanY.coerceIn(1, grid.rows)
        val spot = CellGrid.findFreeOnPages(grid, pageRects(), w, h, preferredPage) ?: CellGrid.findFreeOnPages(grid, pageRects(), w, h) ?: return null
        dao.insert(HomeItemEntity(type = ItemType.WIDGET, container = Container.PAGE, page = spot.first, cellX = spot.second.x, cellY = spot.second.y, spanX = w, spanY = h, widgetId = widgetId))
    }

    suspend fun move(id: Long, container: Int, page: Int, rect: CellRect): PlaceResult = writes.withLock {
        val items = dao.all()
        val item = items.firstOrNull { it.id == id } ?: return PlaceResult.Rejected
        if (item.locked) return PlaceResult.Rejected
        if (container == Container.DOCK && (item.type == ItemType.WIDGET || item.type == ItemType.CLOCK || item.backdrop)) return PlaceResult.Rejected
        val target = rect.copy(w = item.spanX, h = item.spanY).let { if (container == Container.DOCK) it.copy(y = 0, w = 1, h = 1) else it }
        if (!canPlace(items, id, container, page, target, item.backdrop)) return PlaceResult.Rejected
        dao.update(item.copy(container = container, page = page, cellX = target.x, cellY = target.y, parentId = null))
        dissolveIfNeeded(item.parentId)
        PlaceResult.Done
    }

    suspend fun resize(id: Long, rect: CellRect): PlaceResult = writes.withLock {
        val items = dao.all()
        val item = items.firstOrNull { it.id == id } ?: return PlaceResult.Rejected
        if (item.container != Container.PAGE || !canPlace(items, id, Container.PAGE, item.page, rect, item.backdrop)) return PlaceResult.Rejected
        dao.update(item.copy(cellX = rect.x, cellY = rect.y, spanX = rect.w, spanY = rect.h))
        PlaceResult.Done
    }

    suspend fun dropOnto(targetId: Long, draggedId: Long?, newApp: AppEntry?): PlaceResult = writes.withLock {
        val items = dao.all()
        val target = items.firstOrNull { it.id == targetId } ?: return PlaceResult.Rejected
        val dragged = draggedId?.let { id -> items.firstOrNull { it.id == id } }
        if (dragged != null && (dragged.type != ItemType.APP || dragged.id == target.id)) return PlaceResult.Rejected
        val appKey = dragged?.appKey ?: newApp?.key?.encode() ?: return PlaceResult.Rejected
        when (target.type) {
            ItemType.FOLDER -> {
                val rank = items.count { it.parentId == target.id }
                if (dragged != null) dao.update(dragged.copy(container = Container.FOLDER, parentId = target.id, rank = rank, page = 0, cellX = 0, cellY = 0))
                else dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.FOLDER, parentId = target.id, rank = rank, appKey = appKey))
            }
            ItemType.APP -> {
                val folderId = dao.insert(
                    HomeItemEntity(type = ItemType.FOLDER, container = target.container, page = target.page, cellX = target.cellX, cellY = target.cellY ),
                )
                dao.update(target.copy(container = Container.FOLDER, parentId = folderId, rank = 0, page = 0, cellX = 0, cellY = 0))
                if (dragged != null) dao.update(dragged.copy(container = Container.FOLDER, parentId = folderId, rank = 1, page = 0, cellX = 0, cellY = 0))
                else dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.FOLDER, parentId = folderId, rank = 1, appKey = appKey))
            }
            else -> return PlaceResult.Rejected
        }
        dissolveIfNeeded(dragged?.parentId)
        PlaceResult.Done
    }

    suspend fun renameFolder(id: Long, title: String) = writes.withLock {
        dao.get(id)?.takeIf { it.type == ItemType.FOLDER }?.let { dao.update(it.copy(title = title.trim().take(40))) }
    }

    suspend fun removeFromFolder(childId: Long): PlaceResult = writes.withLock {
        val items = dao.all()
        val child = items.firstOrNull { it.id == childId } ?: return PlaceResult.Rejected
        val folder = items.firstOrNull { it.id == child.parentId } ?: return PlaceResult.Rejected
        val slot = freeSlot(items.filter { it.id != child.id }, Container.PAGE, 1, 1, startPage = folder.page) ?: return PlaceResult.NoSpace
        dao.update(child.copy(container = Container.PAGE, parentId = null, page = slot.first, cellX = slot.second.x, cellY = slot.second.y))
        dissolveIfNeeded(folder.id)
        PlaceResult.Done
    }

    suspend fun setLocked(id: Long, locked: Boolean) = writes.withLock {
        dao.get(id)?.let { dao.update(it.copy(locked = locked)) }
    }

    suspend fun remove(id: Long): HomeItemEntity? = writes.withLock {
        val item = dao.get(id) ?: return null
        if (item.type == ItemType.FOLDER) dao.deleteChildren(item.id)
        dao.delete(id)
        dissolveIfNeeded(item.parentId)
        item
    }

    suspend fun clear() = writes.withLock { dao.clear() }

    suspend fun removePage(page: Int): List<Int> = writes.withLock {
        val items = dao.all()
        val onPage = items.filter { it.container == Container.PAGE && it.page == page }
        val released = mutableListOf<Int>()
        onPage.forEach { item ->
            if (item.type == ItemType.FOLDER) dao.deleteChildren(item.id)
            item.widgetId?.let(released::add)
            dao.delete(item.id)
        }
        dao.updateAll(items.filter { it.container == Container.PAGE && it.page > page }.map { it.copy(page = it.page - 1) })
        val pages = dao.pages()
        dao.clearPages()
        dao.upsertPages(pages.filter { it.page != page }.map { if (it.page > page) it.copy(page = it.page - 1) else it })
        released
    }

    private val terminalOffer = kotlinx.coroutines.sync.Mutex()

    private suspend fun rescueFrom(page: Int) = writes.withLock {
        val stuck = dao.all().filter { it.container == Container.PAGE && it.page == page }
        for (item in stuck) {
            val spot = CellGrid.findFreeOnPages(spec.value, pageRects(exclude = item.id, backdrop = item.backdrop), item.spanX, item.spanY)
            if (spot != null) dao.update(item.copy(page = spot.first, cellX = spot.second.x, cellY = spot.second.y))
            else dao.update(item.copy(page = addPageLocked()))
        }
    }

    private suspend fun addPageLocked(): Int {
        val items = dao.all()
        val pages = dao.pages()
        val index = maxOf(items.filter { it.container == Container.PAGE }.maxOfOrNull { it.page } ?: -1, pages.maxOfOrNull { it.page } ?: -1) + 1
        dao.upsertPages(listOf(HomePageEntity(index, PageTemplate.EMPTY.name, null)))
        return index
    }

    suspend fun offerTerminalPage() = terminalOffer.withLock {
        val terminals = dao.pages().filter { it.template == PageTemplate.TERMINAL.name }.map { it.page }.sorted()
        terminals.drop(1).asReversed().forEach { removePage(it) }
        terminals.firstOrNull()?.let { rescueFrom(it) }
        if (settings.terminalPageOffered()) return@withLock
        if (dao.pages().isEmpty() && dao.all().none { it.container == Container.PAGE }) return@withLock
        settings.markTerminalPageOffered()
        if (terminals.isEmpty()) addPage(PageTemplate.TERMINAL)
    }

    suspend fun addPage(template: PageTemplate, title: String? = null): Int = writes.withLock {
        val items = dao.all()
        val pages = dao.pages()
        val index = maxOf(items.filter { it.container == Container.PAGE }.maxOfOrNull { it.page } ?: -1, pages.maxOfOrNull { it.page } ?: -1) + 1
        dao.upsertPages(listOf(HomePageEntity(index, template.name, title)))
        insertTemplate(template, index, emptyList())
        index
    }

    suspend fun movePage(from: Int, to: Int) = writes.withLock {
        if (from == to) return@withLock
        val remap: (Int) -> Int = { p ->
            when {
                p == from -> to
                from < to && p in (from + 1)..to -> p - 1
                from > to && p in to until from -> p + 1
                else -> p
            }
        }
        val items = dao.all().filter { it.container == Container.PAGE }
        dao.updateAll(items.map { it.copy(page = remap(it.page)) })
        val pages = dao.pages()
        dao.clearPages()
        dao.upsertPages(pages.map { it.copy(page = remap(it.page)) })
    }

    suspend fun setConfig(id: Long, config: String?) = writes.withLock { dao.setConfig(id, config) }

    suspend fun addCard(kind: String, spanX: Int, spanY: Int, preferredPage: Int): PlaceResult = writes.withLock {
        val grid = spec.value
        val w = spanX.coerceAtMost(grid.columns)
        val h = spanY.coerceAtMost(grid.rows)
        val rects = pageRects(backdrop = kind == GLASS_KIND)
        val spot = CellGrid.findFreeOnPages(grid, rects, w, h, preferredPage) ?: CellGrid.findFreeOnPages(grid, rects, w, h) ?: return PlaceResult.NoSpace
        dao.insert(HomeItemEntity(type = ItemType.NEXO_CARD, container = Container.PAGE, page = spot.first, cellX = spot.second.x, cellY = spot.second.y, spanX = w, spanY = h, title = kind))
        PlaceResult.Done
    }

    suspend fun addClock(): PlaceResult = writes.withLock {
        val grid = spec.value
        val spot = CellGrid.findFreeOnPages(grid, pageRects(), grid.columns, 2) ?: return PlaceResult.NoSpace
        dao.insert(HomeItemEntity(type = ItemType.CLOCK, container = Container.PAGE, page = spot.first, cellX = spot.second.x, cellY = spot.second.y, spanX = grid.columns, spanY = 2))
        PlaceResult.Done
    }

    private suspend fun removePackage(packageName: String, userSerial: Long) = writes.withLock {
        dao.byPackage(packageName, userSerial).forEach { item ->
            dao.delete(item.id)
            dissolveIfNeeded(item.parentId)
        }
    }

    private suspend fun dissolveIfNeeded(folderId: Long?) {
        folderId ?: return
        val folder = dao.get(folderId) ?: return
        val inside = dao.all().filter { it.parentId == folderId }
        when (inside.size) {
            0 -> dao.delete(folderId)
            1 -> {
                dao.update(inside[0].copy(container = folder.container, page = folder.page, cellX = folder.cellX, cellY = folder.cellY, parentId = null, rank = 0))
                dao.delete(folderId)
            }
        }
    }

    private fun canPlace(items: List<HomeItemEntity>, movingId: Long?, container: Int, page: Int, rect: CellRect, backdrop: Boolean = false): Boolean {
        val grid = spec.value
        return when (container) {
            Container.DOCK -> rect.x in 0 until grid.dockSize && items.none { it.container == Container.DOCK && it.id != movingId && it.cellX == rect.x }
            Container.PAGE -> page in 0 until grid.maxPages &&
                CellGrid.fits(grid, items.filter { it.container == Container.PAGE && it.page == page && it.id != movingId && it.backdrop == backdrop }.map { it.rect }, rect)
            else -> false
        }
    }

    private suspend fun freeSlot(items: List<HomeItemEntity>, container: Int, w: Int, h: Int, startPage: Int = 0): Pair<Int, CellRect>? {
        val grid = spec.value
        return when (container) {
            Container.DOCK -> {
                val taken = items.filter { it.container == Container.DOCK }.map { it.cellX }.toSet()
                (0 until grid.dockSize).firstOrNull { it !in taken }?.let { 0 to CellRect(it, 0) }
            }
            else -> {
                val byPage = withClosedPages(items.filter { it.container == Container.PAGE && !it.backdrop }.groupBy({ it.page }, { it.rect }))
                CellGrid.findFreeOnPages(grid, byPage, w, h, startPage) ?: CellGrid.findFreeOnPages(grid, byPage, w, h)
            }
        }
    }

    suspend fun relayoutNow() = applySpec(spec.value, force = true)

    private suspend fun applySpec(grid: GridSpec, force: Boolean = false) = writes.withLock {
        val prefs = settings.currentLayout()
        if (!force && prefs.appliedColumns == grid.columns && prefs.appliedRows == grid.rows) return@withLock
        val onPages = dao.all().filter { it.container == Container.PAGE }
        if (onPages.isNotEmpty()) {
            val placed = onPages.partition { it.backdrop }.toList().flatMap { layer -> CellGrid.relayout(grid, layer.map { Placed(it.id, it.page, it.rect) }) }.associateBy { it.id }
            dao.updateAll(onPages.mapNotNull { item ->
                placed[item.id]?.let { item.copy(page = it.page, cellX = it.rect.x, cellY = it.rect.y, spanX = it.rect.w, spanY = it.rect.h) }
            })
        }
        settings.markApplied(grid)
    }

    private suspend fun insertTemplate(template: PageTemplate, page: Int, apps: List<AppEntry>, ai: Boolean = true) {
        val grid = spec.value
        PageTemplates.blocks(template, grid, ai).forEach { b ->
            val type = if (b.kind == "clock") ItemType.CLOCK else ItemType.NEXO_CARD
            dao.insert(HomeItemEntity(type = type, container = Container.PAGE, page = page, cellX = b.rect.x, cellY = b.rect.y, spanX = b.rect.w, spanY = b.rect.h, title = b.kind.takeIf { type == ItemType.NEXO_CARD }))
        }
        if (template == PageTemplate.APPS) {
            apps.take(grid.columns * grid.rows.coerceAtMost(5)).forEachIndexed { i, app ->
                dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.PAGE, page = page, cellX = i % grid.columns, cellY = i / grid.columns, appKey = app.key.encode()))
            }
        }
    }

    private suspend fun seedBentoIfNeeded(apps: List<AppEntry>) = writes.withLock {
        if (settings.currentLayout().bentoSeeded) return@withLock
        val items = dao.all()
        val offset = BentoSeed.defaultPages.size
        if (BentoSeed.isTrivial(items)) {
            items.filter { it.container == Container.PAGE }.forEach { dao.delete(it.id) }
        } else {
            backup(items)
            dao.updateAll(items.filter { it.container == Container.PAGE }.map { it.copy(page = it.page + offset) })
        }
        val existing = dao.pages()
        dao.clearPages()
        dao.upsertPages(BentoSeed.defaultPages.mapIndexed { i, t -> HomePageEntity(i, t.name) } + existing.map { it.copy(page = it.page + offset) })
        val inDock = dao.all().filter { it.container == Container.DOCK }.mapNotNull { it.appKey }.toSet()
        val forApps = apps.filter { it.profile == com.paulneja.quorvi.launcher.apps.Profile.MAIN && it.key.encode() !in inDock }
        BentoSeed.defaultPages.forEachIndexed { page, template -> insertTemplate(template, page, forApps) }
        settings.markBentoSeeded()
    }

    suspend fun applyPreset(preset: com.paulneja.quorvi.core.settings.HomePreset, apps: List<AppEntry>, ai: Boolean = true) = writes.withLock {
        val items = dao.all()
        if (!BentoSeed.isTrivial(items)) backup(items)
        items.filter { it.container == Container.PAGE }.forEach { item ->
            if (item.type == ItemType.FOLDER) dao.deleteChildren(item.id)
            dao.delete(item.id)
        }
        dao.clearPages()
        val pages = BentoSeed.pagesFor(preset)
        dao.upsertPages(pages.mapIndexed { i, t -> HomePageEntity(i, t.name) })
        val inDock = dao.all().filter { it.container == Container.DOCK }.mapNotNull { it.appKey }.toSet()
        val forApps = apps.filter { it.profile == com.paulneja.quorvi.launcher.apps.Profile.MAIN && it.key.encode() !in inDock }
        pages.forEachIndexed { page, template -> insertTemplate(template, page, forApps, ai) }
        settings.setPreset(preset)
        settings.markBentoSeeded()
    }

    suspend fun currentApps(): List<AppEntry> = catalog.apps.filterNotNull().first()

    suspend fun importApps(plan: ImportPlan, apps: List<AppEntry>, replaceDock: Boolean): Int = writes.withLock {
        val grid = spec.value
        val byComponent = apps.filter { it.profile == com.paulneja.quorvi.launcher.apps.Profile.MAIN }.associateBy { it.key.packageName + "/" + it.key.className }
        val items = dao.all()
        val pages = dao.pages()
        val appPages = pages.filter { it.template == PageTemplate.APPS.name }.map { it.page }.toMutableList()
        if (appPages.isEmpty()) {
            val next = maxOf(items.filter { it.container == Container.PAGE }.maxOfOrNull { it.page } ?: -1, pages.maxOfOrNull { it.page } ?: -1) + 1
            dao.upsertPages(listOf(HomePageEntity(next, PageTemplate.APPS.name)))
            appPages += next
        }
        items.filter { it.container == Container.PAGE && it.page in appPages }.forEach { item ->
            if (item.type == ItemType.FOLDER) dao.deleteChildren(item.id)
            dao.delete(item.id)
        }
        val perPage = grid.columns * grid.rows
        var placed = 0
        plan.entries.forEachIndexed { index, entry ->
            val pageIndex = index / perPage
            while (pageIndex >= appPages.size) {
                val next = (dao.pages().maxOfOrNull { it.page } ?: appPages.last()) + 1
                dao.upsertPages(listOf(HomePageEntity(next, PageTemplate.APPS.name)))
                appPages += next
            }
            val page = appPages[pageIndex]
            val slot = index % perPage
            val x = slot % grid.columns
            val y = slot / grid.columns
            when (entry) {
                is ImportEntry.App -> byComponent[entry.component]?.let {
                    dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.PAGE, page = page, cellX = x, cellY = y, appKey = it.key.encode()))
                    placed++
                }
                is ImportEntry.Folder -> {
                    val folderId = dao.insert(HomeItemEntity(type = ItemType.FOLDER, container = Container.PAGE, page = page, cellX = x, cellY = y, title = entry.title.take(40)))
                    entry.components.mapNotNull { byComponent[it] }.forEachIndexed { rank, app ->
                        dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.FOLDER, parentId = folderId, rank = rank, appKey = app.key.encode()))
                        placed++
                    }
                }
            }
        }
        if (replaceDock && plan.dock.isNotEmpty()) {
            dao.all().filter { it.container == Container.DOCK }.forEach { dao.delete(it.id) }
            plan.dock.mapNotNull { byComponent[it] }.take(grid.dockSize).forEachIndexed { slot, app ->
                dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.DOCK, cellX = slot, appKey = app.key.encode()))
            }
        }
        placed
    }

    private val backupFile get() = java.io.File(context.filesDir, "backup-pre-bento.json")

    val hasBackup: Boolean get() = backupFile.exists()

    private suspend fun backup(items: List<HomeItemEntity>) {
        backupFile.writeText(HomeBackup.encode(items, dao.pages()))
    }

    suspend fun restoreBackup(): Boolean = writes.withLock {
        val text = runCatching { backupFile.readText() }.getOrNull() ?: return@withLock false
        val restored = HomeBackup.decode(text) ?: return@withLock false
        val current = dao.pages()
        dao.clear()
        dao.clearPages()
        restored.items.forEach { dao.insert(it) }

        val lastPage = restored.items.filter { it.container == Container.PAGE }.maxOfOrNull { it.page } ?: 0
        dao.upsertPages(restored.pages ?: current.filter { it.page <= lastPage })
        true
    }

    private suspend fun seedIfNeeded(apps: List<AppEntry>) = writes.withLock {
        if (settings.currentLayout().seeded) return@withLock
        val items = dao.all()
        val grid = spec.value
        if (items.none { it.type == ItemType.CLOCK }) {
            val spot = CellGrid.findFree(grid, items.filter { it.container == Container.PAGE && it.page == 0 && !it.backdrop }.map { it.rect }, grid.columns, 2)
            if (spot != null) dao.insert(HomeItemEntity(type = ItemType.CLOCK, container = Container.PAGE, page = 0, cellX = spot.x, cellY = spot.y, spanX = spot.w, spanY = spot.h))
        }
        val taken = items.filter { it.container == Container.DOCK }.map { it.cellX }.toMutableSet()
        val inDock = items.filter { it.container == Container.DOCK }.mapNotNull { it.appKey }.toSet()
        for (pkg in defaults.dockPackages()) {
            val app = apps.firstOrNull { it.key.packageName == pkg && it.profile == com.paulneja.quorvi.launcher.apps.Profile.MAIN } ?: continue
            if (app.key.encode() in inDock) continue
            val slot = (0 until grid.dockSize).firstOrNull { it !in taken } ?: break
            taken += slot
            dao.insert(HomeItemEntity(type = ItemType.APP, container = Container.DOCK, cellX = slot, appKey = app.key.encode()))
        }
        settings.markSeeded()
    }
}
