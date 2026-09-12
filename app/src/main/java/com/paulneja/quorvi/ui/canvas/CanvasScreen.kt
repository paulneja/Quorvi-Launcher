package com.paulneja.quorvi.ui.canvas

import com.paulneja.quorvi.ui.design.hideKeyboard
import com.paulneja.quorvi.ui.design.liquidGlass
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.BuildConfig
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.Shortcuts
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.backdrop
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.GlassBackdrops
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassBackdrops
import com.paulneja.quorvi.ui.design.LocalGlassHighlight
import com.paulneja.quorvi.ui.design.liquid.rememberCombinedBackdrop
import com.paulneja.quorvi.ui.design.rememberGravityHighlight
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.glass
import com.paulneja.quorvi.ui.design.LocalWindowEffects
import com.paulneja.quorvi.ui.design.MenuItem
import com.paulneja.quorvi.ui.design.MenuSection
import com.paulneja.quorvi.ui.design.MenuState
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviMenuHost
import com.paulneja.quorvi.ui.design.rememberHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val LIQUID = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

class CanvasCallbacks(
    val openSettings: () -> Unit,
    val openStudio: () -> Unit,
    val openOperations: () -> Unit,
    val openAgents: () -> Unit,
    val openHost: (String) -> Unit,
    val openHttp: (String) -> Unit,
    val openAccounts: () -> Unit,
    val openCapabilities: () -> Unit,
    val openCi: (String?, androidx.compose.ui.geometry.Rect) -> Unit,
    val openLabs: () -> Unit,
    val openWallpaper: () -> Unit,
    val pickWidget: (AppWidgetProviderInfo, page: Int, spanX: Int, spanY: Int) -> Unit,
    val makeDefault: () -> Unit,
)

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel,
    widgets: WidgetHostManager,
    shortcuts: Shortcuts,
    homePresses: Flow<Boolean>,
    isDefaultHome: Boolean,
    callbacks: CanvasCallbacks,
) {
    val state by viewModel.canvas.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val ciRecent by viewModel.ciRecent.collectAsStateWithLifecycle()
    val hostCards by viewModel.hosts.collectAsStateWithLifecycle()
    val containerCards by viewModel.containers.collectAsStateWithLifecycle()
    val httpCards by viewModel.http.collectAsStateWithLifecycle()
    val haFavorites by viewModel.haFavorites.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val hasHomeAssistant by viewModel.hasHomeAssistant.collectAsStateWithLifecycle()
    val activeRuns by viewModel.activeRuns.collectAsStateWithLifecycle()
    val agentIndicator by viewModel.agentIndicator.collectAsStateWithLifecycle()
    val canvas = state ?: return
    val wallpaperLayer = rememberLayerBackdrop()
    val canvasLayer = rememberLayerBackdrop()
    val overlayBackdrop = rememberCombinedBackdrop(wallpaperLayer, canvasLayer)
    val gravity = rememberGravityHighlight()
    val highlight = if (Quorvi.look.tiltHighlight) gravity else null
    // Without a wallpaper the wallpaper layer is never recorded; sampling it draws stale GPU memory (the grey blocks).
    val backdrops = GlassBackdrops(chrome = if (wallpaper.image != null) wallpaperLayer else null, overlay = if (wallpaper.image != null) overlayBackdrop else null, top = null)
    var showWallpaper by remember { mutableStateOf(false) }
    var showCommand by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    val panels: com.paulneja.quorvi.ui.panels.PanelsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val blocks: com.paulneja.quorvi.ui.bento.BlocksViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val topApps by blocks.topApps.collectAsStateWithLifecycle()
    val launchApp = rememberAppLauncher(viewModel)
    val lookParallax = Quorvi.look.parallax
    val lookPanelGrow = Quorvi.look.panelGrow && Quorvi.motion.enabled
    var openPanel by remember { mutableStateOf<Pair<String, androidx.compose.ui.geometry.Rect>?>(null) }
    val panelProgress = remember { Animatable(0f) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(panels) { panels.intents.collect { runCatching { context.startActivity(it) } } }
    var canvasWidth by remember { mutableStateOf(1) }
    var pullDown by remember { mutableStateOf(0f) }
    val spec = canvas.spec
    val drawer = rememberDrawerState()
    val drag = remember { DragController() }
    val menu = remember { MenuState() }
    val geometry = remember { CanvasGeometry() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val effects = LocalWindowEffects.current
    val motion = Quorvi.motion
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val dragging = drag.dragging
    var editing by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val editProgress = remember { Animatable(0f) }
    LaunchedEffect(editing) {
        if (!editing) selectedId = null
        editProgress.animateTo(if (editing) 1f else 0f, motion.settle())
    }
    val pager = rememberPagerState { canvas.pageCount + if (dragging || editing) 1 else 0 }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val folderProgress = remember { Animatable(0f) }
    var showWidgets by remember { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    val iconSize = 54.dp * appearance.iconScale

    val labels = MenuLabels.load()

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling -> if (scrolling) { keyboard?.hide(); focus.clearFocus(force = true); view.hideKeyboard() } }
    }
    LaunchedEffect(drawer) {
        snapshotFlow { drawer.open }.collect { open -> if (open) { showPages = false; focus.clearFocus(force = true); view.hideKeyboard() } }
    }

    LaunchedEffect(homePresses) {
        homePresses.collect { toFirst ->
            keyboard?.hide()
            view.hideKeyboard()
            focus.clearFocus(force = true)
            menu.dismiss()
            showPages = false
            showCommand = false
            showWallpaper = false
            showWidgets = false
            editing = false
            selectedId = null
            if (openFolderId != null) { folderProgress.snapTo(0f); openFolderId = null }
            if (openPanel != null) { launch { panelProgress.animateTo(0f, motion.settle()); openPanel = null } }
            if (drawer.progress > 0f) launch { drawer.animateTo(0f) }
            if (toFirst && pager.currentPage != 0) launch { if (pager.currentPage > 2) pager.scrollToPage(0) else pager.animateScrollToPage(0) }
        }
    }
    val drawerOpen by remember { derivedStateOf { drawer.open } }
    BackHandler(enabled = drawerOpen) { drawer.animateTo(0f) }
    BackHandler(enabled = showWidgets) { showWidgets = false }
    BackHandler(enabled = showWallpaper) { showWallpaper = false }
    BackHandler(enabled = editing && !showWidgets && !showWallpaper) { editing = false }
    CompositionLocalProvider(LocalGlassBackdrops provides backdrops, LocalGlassHighlight provides highlight, LocalBadges provides badges) {
    LaunchedEffect(effects) {
        snapshotFlow { maxOf(drawer.progress, folderProgress.value, if (showWidgets || showCommand) 1f else 0f) }.collect { effects?.blur(it) }
    }
    LaunchedEffect(effects) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction to pager.pageCount }.collect { (pos, count) -> effects?.scrollWallpaper(pos, count) }
    }

    var edgeJob by remember { mutableStateOf<Job?>(null) }
    SideEffect {
        drag.onDragStarted = {
            menu.dismiss()
            haptics.pickup()
            if (drawer.open) drawer.animateTo(0f)
            if (openFolderId != null) scope.launch { folderProgress.snapTo(0f); openFolderId = null }
        }
        drag.onMoved = { pointer ->
            val payload = drag.payload
            if (payload != null && drawer.progress < 0.05f) {
                val target = DropResolver.resolve(canvas, payload, pointer, drag.grab, geometry, pager.currentPage)
                if (target != drag.target) {
                    drag.target = target
                    if (target != null) haptics.snap()
                }
                val edge = with(density) { 28.dp.toPx() }
                val grid = geometry.grid
                val direction = when {
                    grid.width <= 0f || pointer.y !in grid.top..grid.bottom -> 0
                    pointer.x < grid.left + edge -> -1
                    pointer.x > grid.right - edge -> 1
                    else -> 0
                }
                if (direction == 0) {
                    edgeJob?.cancel()
                    edgeJob = null
                } else if (edgeJob?.isActive != true) {
                    edgeJob = scope.launch {
                        delay(600)
                        val next = (pager.currentPage + direction).coerceIn(0, pager.pageCount - 1)
                        if (next != pager.currentPage) pager.animateScrollToPage(next)
                        edgeJob = null
                    }
                }
            }
        }
        drag.onDropped = { payload, target ->
            edgeJob?.cancel()
            if (target != null) haptics.drop() else haptics.reject()
            viewModel.drop(payload, target)
        }
        drag.onReleasedWithoutDrag = { edgeJob?.cancel() }
    }

    fun appMenu(app: AppEntry, node: CanvasNode?, at: Offset, inFolder: Boolean) {
        val base = buildList {
            if (node == null) {
                add(MenuItem(labels.addHome, Glyphs.Plus) { viewModel.addToHome(app, Container.PAGE) })
                add(MenuItem(labels.addDock, Glyphs.Plus) { viewModel.addToHome(app, Container.DOCK) })
            }
            add(MenuItem(labels.info, Glyphs.Info) { viewModel.details(app) })
            if (node != null && !inFolder) add(MenuItem(if (node.locked) labels.unlock else labels.lock, if (node.locked) Glyphs.Unlock else Glyphs.Lock) { viewModel.setLocked(node.id, !node.locked) })
            if (inFolder && node != null) add(MenuItem(labels.removeFromFolder, Glyphs.Folder) { viewModel.removeFromFolder(node.id) })
            if (node != null) add(MenuItem(labels.remove, Glyphs.Remove) { viewModel.removeNode(node.id) })
            if (node == null) add(MenuItem(labels.hide, Glyphs.Hide) { viewModel.hide(app) })
            val suggested = app.key.encode() !in viewModel.unsuggested.value
            add(MenuItem(if (suggested) labels.unsuggest else labels.resuggest, Glyphs.Hide) { viewModel.setSuggested(app, !suggested) })
            add(MenuItem(labels.uninstall, Glyphs.Trash, danger = true) { viewModel.uninstall(app) })
        }
        menu.show(at, listOf(MenuSection(base)))
        scope.launch {
            val found = shortcuts.forApp(app)
            if (found.isNotEmpty() && menu.visible && menu.anchor == at) {
                menu.show(at, listOf(MenuSection(found.map { s -> MenuItem(s.label, Glyphs.Shortcut, image = s.icon, onLongClick = { viewModel.pinShortcut(app, s) }) { shortcuts.start(s) } }), MenuSection(base)))
            }
        }
    }

    fun homeItems() = listOf(
        MenuItem(labels.editHome, Glyphs.Edit) { editing = true },
        MenuItem(labels.widgets, Glyphs.Widgets) { showWidgets = true },
        MenuItem(labels.glass, Glyphs.Bento) { viewModel.addCard(com.paulneja.quorvi.launcher.home.GLASS_KIND, pager.currentPage) },
        MenuItem(labels.wallpaper, Glyphs.Wallpaper) { showWallpaper = true },
        MenuItem(labels.settings, Glyphs.Gear) { callbacks.openSettings() },
    )

    fun homeMenu(at: Offset) = menu.show(at, listOf(MenuSection(homeItems())))

    fun nodeMenu(node: CanvasNode, at: Offset) {
        when (node) {
            is CanvasNode.App -> appMenu(node.app, node, at, inFolder = false)
            else -> menu.show(
                at,
                listOf(
                    MenuSection(
                        buildList {
                            if (node is CanvasNode.Folder) add(MenuItem(labels.rename, Glyphs.Edit) { openFolderId = node.id })
                            add(MenuItem(if (node.locked) labels.unlock else labels.lock, if (node.locked) Glyphs.Unlock else Glyphs.Lock) { viewModel.setLocked(node.id, !node.locked) })
                            add(MenuItem(labels.remove, Glyphs.Remove, danger = node is CanvasNode.Folder) { viewModel.removeNode(node.id) })
                        },
                    ),
                    MenuSection(homeItems()),
                ),
            )
        }
    }

    val longPressNode: (CanvasNode) -> (androidx.compose.ui.input.pointer.PointerId, Offset, androidx.compose.ui.geometry.Rect) -> Unit = { node ->
        { id, at, bounds ->
            haptics.pickup()
            if (!node.locked) drag.arm(id, DragPayload.Existing(node), at, bounds)
            nodeMenu(node, at)
        }
    }

    val nodeContent: @Composable (CanvasNode) -> Unit = { node ->
        val clockBounds = if (node is CanvasNode.Clock) remember(node.id) { ContentBounds() } else null
        NodeHost(
            accepts = if (clockBounds != null && !editing) clockBounds::contains else null,
            node = node,
            geometry = geometry,
            hidden = drag.draggedNodeId == node.id || openFolderId == node.id,
            targeted = (drag.target as? DropTarget.Onto)?.nodeId == node.id,
            onLongPress = longPressNode(node),
            onClickAt = if (!editing && node is CanvasNode.App) ({ bounds -> launchApp(node.app, bounds) }) else null,
            onClick = when {
                editing && (node is CanvasNode.Widget || node is CanvasNode.Clock || node is CanvasNode.Card) -> ({ selectedId = if (selectedId == node.id) null else node.id })

                editing -> ({
                    val glass = if (node.container == Container.PAGE) canvas.pages.getOrNull(node.page).orEmpty().firstOrNull { it.backdrop && it.rect.overlaps(node.rect) } else null
                    selectedId = glass?.id?.takeIf { it != selectedId }
                })
                node is CanvasNode.App -> null
                node is CanvasNode.Folder -> ({ openFolderId = node.id })
                node is CanvasNode.Shortcut -> ({ viewModel.openShortcut(node) })
                else -> null
            },
        ) {
            Box {
            when (node) {
                is CanvasNode.App -> AppIconView(node.app, iconSize, appearance.showLabels && node.container != Container.DOCK)
                is CanvasNode.Folder -> FolderTile(node, iconSize, appearance.showLabels && node.container != Container.DOCK)
                is CanvasNode.Clock -> ClockBlock(bounds = clockBounds, extra = { if (Quorvi.look.clockWeather) com.paulneja.quorvi.ui.bento.ClockWeather(blocks) })
                is CanvasNode.Card -> when {
                    node.kind in com.paulneja.quorvi.ui.panels.PanelKinds.supported -> com.paulneja.quorvi.ui.panels.PanelTile(
                        kind = node.kind,
                        vm = panels,
                        onOpen = {
                            if (editing) {
                                selectedId = if (selectedId == node.id) null else node.id
                            } else {
                                panels.refreshOnOpen()
                                scope.launch { panelProgress.snapTo(0f) }
                                openPanel = node.kind to (geometry.nodes[node.id] ?: androidx.compose.ui.geometry.Rect.Zero)
                            }
                        },
                    )
                    node.backdrop -> com.paulneja.quorvi.ui.bento.GlassSpace(editing, empty = canvas.pages.getOrNull(node.page).orEmpty().none { !it.backdrop && it.rect.overlaps(node.rect) })
                    node.kind == "today" -> com.paulneja.quorvi.ui.bento.TodayBlock(blocks)
                    node.kind == "media" -> com.paulneja.quorvi.ui.bento.MediaBlock(blocks)
                    node.kind == "notifications" -> com.paulneja.quorvi.ui.bento.NotificationsBlock(blocks)
                    node.kind == "favlist" -> com.paulneja.quorvi.ui.bento.FavListBlock(topApps) { app, bounds -> launchApp(app, bounds) }
                    node.kind == "ghpending" -> com.paulneja.quorvi.ui.bento.GitHubPendingBlock(panels, onSetup = { context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.GITHUB)) })
                    node.kind == "apps" -> com.paulneja.quorvi.ui.bento.AppsBlock(topApps, iconSize * 0.86f, { app, bounds -> launchApp(app, bounds) }, badges)
                    node.kind == "photos" -> com.paulneja.quorvi.ui.bento.PhotosBlock(blocks)
                    node.kind == "toggles" -> com.paulneja.quorvi.ui.bento.TogglesBlock(panels)
                    node.kind == "system" -> com.paulneja.quorvi.ui.bento.SystemBlock(panels)
                    node.kind == "timer" -> com.paulneja.quorvi.ui.bento.TimerBlock(blocks, node.id, node.config)
                    node.kind == "capsules" -> {
                        val draft = stringResource(R.string.capsule_ai_draft)
                        com.paulneja.quorvi.ui.bento.CapsulesBlock(panels, onCreate = { com.paulneja.quorvi.ui.chat.ChatActivity.open(context, draft = draft) })
                    }
                    node.kind == "contacts" -> com.paulneja.quorvi.ui.bento.ContactsBlock(blocks)
                    node.kind == "note" -> com.paulneja.quorvi.ui.bento.NoteBlock(blocks, node.id, node.config)
                    node.kind == "agents" -> com.paulneja.quorvi.ui.bento.AgentsBlock(panels, onOpen = {
                        panels.refreshOnOpen(); scope.launch { panelProgress.snapTo(0f) }
                        openPanel = com.paulneja.quorvi.ui.panels.PanelKinds.AGENT to (geometry.nodes[node.id] ?: androidx.compose.ui.geometry.Rect.Zero)
                    })
                    node.kind == "ci" -> com.paulneja.quorvi.ui.bento.CiBlock(panels, onOpen = {
                        panels.refreshOnOpen(); scope.launch { panelProgress.snapTo(0f) }
                        openPanel = com.paulneja.quorvi.ui.panels.PanelKinds.GITHUB to (geometry.nodes[node.id] ?: androidx.compose.ui.geometry.Rect.Zero)
                    })
                    node.kind == "hosts" -> com.paulneja.quorvi.ui.bento.HostsBlock(hostCards, { viewModel.health(it) }, callbacks.openHost, callbacks.openOperations)
                    node.kind == "http" -> com.paulneja.quorvi.ui.bento.HttpBlock(httpCards, callbacks.openHttp, callbacks.openOperations)
                    node.kind == "ha" -> com.paulneja.quorvi.ui.bento.HaBlock(haFavorites, hasHomeAssistant, viewModel::toggleHa, callbacks.openAccounts)
                    node.kind.startsWith("host:") -> hostCards.firstOrNull { it.id == node.kind.removePrefix("host:") }
                        ?.let { h -> HostCard(h, viewModel.health(h)) { callbacks.openHost(h.id) } } ?: MissingCard()
                    node.kind.startsWith("docker:") -> hostCards.firstOrNull { it.id == node.kind.removePrefix("docker:") }
                        ?.let { h -> DockerCard(h, containerCards.filter { it.hostId == h.id }) { callbacks.openHost(h.id) } } ?: MissingCard()
                    node.kind.startsWith("http:") -> httpCards.firstOrNull { it.id == node.kind.removePrefix("http:") }
                        ?.let { w -> HttpCard(w, onOpen = { callbacks.openHttp(w.id) }, onTrigger = { viewModel.trigger(w) }) } ?: MissingCard()
                    else -> MissingCard()
                }
                is CanvasNode.Shortcut -> ShortcutView(node, shortcuts, iconSize, appearance.showLabels && node.container != Container.DOCK)
                is CanvasNode.Widget -> WidgetCell(node.widgetId, widgets)
            }

            if (editing && (node is CanvasNode.Widget || node is CanvasNode.Card || node is CanvasNode.Clock)) {
                Box(Modifier.matchParentSize().pointerInput(node.id) { detectTapGestures { selectedId = if (selectedId == node.id) null else node.id } })
            }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().then(with(drag) { Modifier.trackDrags { slop } })) {
        drawer.heightPx = with(density) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize()) {
        wallpaper.image?.let { image ->
            WallpaperLayer(
                image = image,
                blurred = wallpaper.blurred,
                position = { if (lookParallax) pager.currentPage + pager.currentPageOffsetFraction else (pager.pageCount - 1) / 2f },
                pageCount = { pager.pageCount },
                blur = { maxOf(drawer.progress, folderProgress.value) },
                modifier = if (LIQUID) Modifier.layerBackdrop(wallpaperLayer) else Modifier,
            )
        }
        val blurContent = (openFolderId != null || showCommand || showWidgets) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val landscape = configuration.screenWidthDp > configuration.screenHeightDp
        val portraitCellDp = (minOf(configuration.screenWidthDp, configuration.screenHeightDp) - 16f) / spec.columns
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress
                    val e = editProgress.value
                    alpha = 1f - p
                    scaleX = (1f - 0.04f * p) * (1f - 0.1f * e)
                    scaleY = (1f - 0.04f * p) * (1f - 0.1f * e)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .then(if (LIQUID) Modifier.layerBackdrop(canvasLayer) else Modifier)
                .then(if (blurContent) Modifier.blur(if (openFolderId != null) (16f * folderProgress.value).dp else 24.dp) else Modifier)
                .onSizeChanged { canvasWidth = it.width }
                .then(if (landscape) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !dragging && !editing && !landscape,
                    state = rememberDraggableState { delta ->
                        if (drawer.closed && delta > 0f) pullDown += delta else drawer.dragBy(delta)
                    },
                    onDragStopped = { velocity ->
                        if (pullDown > with(density) { 72.dp.toPx() } || (pullDown > 0f && velocity > 1500f)) {
                            showCommand = true
                        }
                        pullDown = 0f
                        drawer.settle(velocity)
                    },
                )
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            if (!isDefaultHome) DefaultHomeBanner(callbacks.makeDefault)
            var glassHintDismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { if (wallpaper.image == null) viewModel.refreshWallpaper() }
            if (wallpaper.image == null && !wallpaper.systemIsLive && !glassHintDismissed && !editing && LIQUID && !Quorvi.look.minimalChrome && !Quorvi.look.glass.material) {
                val rootAvailable by viewModel.rootAvailable.collectAsStateWithLifecycle()
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) viewModel.setWallpaper(uri, false)
                }
                val rootFailed = androidx.compose.ui.res.stringResource(R.string.glass_root_failed)
                GlassHint(
                    onPick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onAllFiles = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:" + context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onRoot = if (rootAvailable) ({ viewModel.wallpaperWithRoot { ok -> if (!ok) android.widget.Toast.makeText(context, rootFailed, android.widget.Toast.LENGTH_SHORT).show() } }) else null,
                    onDismiss = { glassHintDismissed = true },
                )
            }
            val look = Quorvi.look
            if (!editing && look.showPill && !look.minimalChrome) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                    val current = pager.currentPage.coerceIn(0, canvas.pageCount - 1)
                    PagePill(canvas.pageMeta[current].name(current), canvas.pageCount, current, showPages, onClick = { if (drawer.closed) showPages = !showPages })
                }
            }
            Box(
                Modifier
                    .then(if (landscape) Modifier.height((portraitCellDp * spec.rows).dp) else Modifier.weight(1f))
                    .padding(horizontal = 8.dp)
                    .onGloballyPositioned { geometry.grid = it.boundsInRoot() },
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
                    val coords = remember { CoordsHolder() }
                    CellLayout(
                        spec = spec,
                        nodes = canvas.pages.getOrNull(page).orEmpty(),
                        highlight = (drag.target as? DropTarget.Cell)?.takeIf { it.page == page }?.rect,
                        showCells = editing,
                        overlay = { cellW, cellH ->
                            val selected = selectedId?.let { id -> canvas.pages.getOrNull(page)?.firstOrNull { it.id == id } }
                            if (editing && selected != null) {
                                val limits = remember(selected.id, cellW, cellH) {
                                    limitsFor(selected, widgets, cellW.value, cellH.value, density.density)
                                }
                                ResizeFrame(
                                    node = selected,
                                    spec = spec,
                                    occupied = canvas.pages[page].filter { it.id != selected.id && it.backdrop == selected.backdrop }.map { it.rect },
                                    limits = limits,
                                    cellWidth = cellW,
                                    cellHeight = cellH,
                                    onResize = { viewModel.resize(selected.id, it) },
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords.value = it }
                            .pointerInput(page) {
                                detectTapGestures(
                                    onTap = { if (editing) selectedId = null },
                                    onLongPress = { local ->
                                        haptics.pickup()
                                        if (editing) return@detectTapGestures
                                        homeMenu(coords.value?.localToRoot(local) ?: local)
                                    },
                                )
                            },
                        node = nodeContent,
                    )
                }
            }
            if (editing) {
                val addPanelLabel = stringResource(R.string.pages_add_panel)
                EditToolbar(
                    buildList {
                        add(EditAction(labels.widgets, Glyphs.Widgets) { showWidgets = true })
                        add(EditAction(addPanelLabel, Glyphs.Plus) { editing = false; showPages = true })
                        add(EditAction(labels.wallpaper, Glyphs.Wallpaper) { showWallpaper = true })
                        add(EditAction(labels.studio, Glyphs.Studio) { callbacks.openStudio() })
                        add(EditAction(labels.settings, Glyphs.Gear) { callbacks.openSettings() })
                        if (BuildConfig.LABS) add(EditAction(labels.labs, Glyphs.Labs) { callbacks.openLabs() })
                    },
                )
            } else if (!look.minimalChrome) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !dragging && activeRuns.isNotEmpty(),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                ) { AgentIndicatorBar(activeRuns, agentIndicator, onOpen = callbacks.openAgents) }
                if (canvas.pageCount > 1 && look.showBar) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PanelBar(
                            pages = canvas.pageMeta,
                            position = { pager.currentPage + pager.currentPageOffsetFraction },
                            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                            modifier = Modifier.weight(1f),
                            labels = look.barLabels,
                        )
                        Spacer(Modifier.width(10.dp))
                        SearchButton(onClick = { showCommand = true })
                    }
                } else {
                    SearchBar(stringResource(R.string.search_hint), onClick = { showCommand = true }, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (!look.minimalChrome || editing) Dock(
                nodes = canvas.dock,
                slots = spec.dockSize,
                highlightSlot = (drag.target as? DropTarget.Dock)?.slot,
                ontoId = (drag.target as? DropTarget.Onto)?.nodeId,
                onBounds = { geometry.dock = it },
                node = nodeContent,
            )
        }

        if (editing && !dragging && !showWidgets && !showWallpaper) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val page = pager.currentPage
                if (canvas.pageCount > 1) {
                    var confirm by remember(page) { mutableStateOf(false) }
                    val empty = canvas.pages.getOrNull(page).orEmpty().isEmpty()
                    PillButton(if (confirm) labels.removePageConfirm else labels.removePage, danger = true) {
                        if (empty || confirm) { viewModel.removePage(page); confirm = false } else confirm = true
                    }
                }
                PillButton(labels.done) { editing = false }
            }
        }

        if (dragging) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val payload = drag.payload
                if (payload is DragPayload.Existing) {
                    DropZone(labels.dropRemove, Glyphs.Remove, drag.target == DropTarget.Remove, danger = false, onBounds = { geometry.remove = it }, modifier = Modifier.weight(1f))
                }
                val app = (payload as? DragPayload.NewApp)?.app ?: ((payload as? DragPayload.Existing)?.node as? CanvasNode.App)?.app
                if (app != null) {
                    DropZone(labels.dropUninstall, Glyphs.Trash, drag.target == DropTarget.Uninstall, danger = true, onBounds = { geometry.uninstall = it }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            geometry.remove = androidx.compose.ui.geometry.Rect.Zero
            geometry.uninstall = androidx.compose.ui.geometry.Rect.Zero
        }

        if (drawer.open && drawer.opening && drawer.progress < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(drawer) { detectTapGestures { focusSearch = true; drawer.animateTo(1f) } }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> drawer.dragBy(delta) },
                        onDragStopped = { velocity -> drawer.settle(velocity) },
                    ),
            )
        }
        AppDrawer(
            viewModel = viewModel,
            drawer = drawer,
            columns = spec.columns,
            focusSearch = focusSearch,
            onSearchFocused = { focusSearch = false },
            onLongPressApp = { app, id, at, bounds ->
                haptics.pickup()
                drag.arm(id, DragPayload.NewApp(app), at, bounds)
                appMenu(app, null, at, inFolder = false)
            },
            topApps = topApps,
        )

        val folder = openFolderId?.let { id -> canvas.node(id) as? CanvasNode.Folder }
        if (openFolderId != null && folder == null) LaunchedEffect(Unit) { openFolderId = null }
        if (folder != null) {
            FolderOverlay(
                folder = folder,
                origin = geometry.nodes[folder.id],
                geometry = geometry,
                progress = folderProgress,
                onClose = { openFolderId = null },
                onRename = { viewModel.renameFolder(folder.id, it) },
                onOpenApp = { launchApp(it.app, geometry.nodes[it.id]) },
                onLongPressApp = { app, id, at, bounds ->
                    haptics.pickup()
                    drag.arm(id, DragPayload.Existing(app), at, bounds)
                    appMenu(app.app, app, at, inFolder = true)
                },
            )
        }

        if (showWidgets) {
            val panelNames = listOf(R.string.panel_agent, R.string.panel_network, R.string.panel_system, R.string.shell_title).map { androidx.compose.ui.res.stringResource(it) }
            val blockNames = listOf(
                "media" to R.string.blk_media, "today" to R.string.page_today, "notifications" to R.string.blk_notifs, "photos" to R.string.blk_photos,
                "contacts" to R.string.blk_contacts, "timer" to R.string.blk_timer, "note" to R.string.blk_note, "system" to R.string.blk_system,
                "capsules" to R.string.blk_capsules_launcher, "agents" to R.string.blk_agents, "favlist" to R.string.blk_favorites,
                "ghpending" to R.string.blk_gh_pending, com.paulneja.quorvi.launcher.home.GLASS_KIND to R.string.blk_glass,
            ).map { (kind, label) -> kind to androidx.compose.ui.res.stringResource(R.string.picker_block, androidx.compose.ui.res.stringResource(label)) }
            WidgetPicker(
                widgets = widgets,
                cards = buildList {
                    add("panel:agent" to "Panel · " + panelNames[0])
                    add("panel:github" to "Panel · GitHub")
                    add("panel:network" to "Panel · " + panelNames[1])
                    add("panel:system" to "Panel · " + panelNames[2])
                    add("panel:shell" to "Panel · " + panelNames[3])
                    blockNames.forEach { add(it) }
                    hostCards.forEach { h ->
                        add("host:${h.id}" to "Host · ${h.name}")
                        if (viewModel.health(h)?.docker == true) add("docker:${h.id}" to "Docker · ${h.name}")
                    }
                    httpCards.forEach { add("http:${it.id}" to "HTTP · ${it.name}") }
                    if (hasHomeAssistant) add("ha" to "Home · Home Assistant")
                },
                onClose = { showWidgets = false },
                onPickCard = { kind -> showWidgets = false; viewModel.addCard(kind, pager.currentPage) },
                onPick = { info ->
                    showWidgets = false
                    val cellW = with(density) { (geometry.grid.width / spec.columns).toDp().value }
                    val cellH = with(density) { (geometry.grid.height / spec.rows).toDp().value }
                    val (w, h) = widgets.span(info, cellW, cellH, spec.columns, spec.rows)
                    callbacks.pickWidget(info, pager.currentPage, w, h)
                },
            )
        }
        }

        if (showCommand) {
            com.paulneja.quorvi.ui.command.CommandOverlay(
                viewModel = viewModel,
                shortcuts = shortcuts,
                onQuorviAction = { action ->
                    when (action) {
                        com.paulneja.quorvi.core.command.QuorviAction.EDIT -> editing = true
                        com.paulneja.quorvi.core.command.QuorviAction.WIDGETS -> showWidgets = true
                        com.paulneja.quorvi.core.command.QuorviAction.WALLPAPER -> showWallpaper = true
                        com.paulneja.quorvi.core.command.QuorviAction.STUDIO, com.paulneja.quorvi.core.command.QuorviAction.HIDDEN_APPS -> callbacks.openStudio()
                        com.paulneja.quorvi.core.command.QuorviAction.OPERATIONS -> callbacks.openOperations()
                        com.paulneja.quorvi.core.command.QuorviAction.CONTROL -> com.paulneja.quorvi.control.SystemPanels.quickSettings(context)
                        com.paulneja.quorvi.core.command.QuorviAction.ACCOUNTS -> callbacks.openAccounts()
                        com.paulneja.quorvi.core.command.QuorviAction.AGENTS -> callbacks.openAgents()
                        com.paulneja.quorvi.core.command.QuorviAction.CAPABILITIES -> callbacks.openCapabilities()
                        com.paulneja.quorvi.core.command.QuorviAction.SHELL -> QuorviShell.open(context, view, null)
                    }
                },
                onClose = { showCommand = false },
            )
        }

        if (showPages && drawer.closed) {
            PagesSheet(
                canvas = canvas,
                current = pager.currentPage.coerceIn(0, canvas.pageCount - 1),
                onSelect = { page -> showPages = false; scope.launch { pager.animateScrollToPage(page) } },
                onAdd = { template -> scope.launch { val page = viewModel.addPage(template); showPages = false; delay(120); pager.animateScrollToPage(page) } },
                onEdit = { showPages = false; editing = true },
                onMove = { from, to ->
                    viewModel.movePage(from, to)

                    val shown = pager.currentPage
                    val follow = when (shown) { from -> to; to -> from; else -> shown }
                    if (follow != shown) scope.launch { pager.scrollToPage(follow) }
                },
                onRemove = { page -> viewModel.removePage(page) },
                onClose = { showPages = false },
                onSettings = { showPages = false; callbacks.openSettings() },
            )
        }

        openPanel?.let { (kind, origin) ->
            com.paulneja.quorvi.ui.panels.PanelOverlay(
                kind = kind,
                origin = if (lookPanelGrow) origin else androidx.compose.ui.geometry.Rect.Zero,
                grow = lookPanelGrow,
                blurredWallpaper = wallpaper.blurred,
                progress = panelProgress,
                onClosed = { openPanel = null },
            ) {
                com.paulneja.quorvi.ui.panels.PanelContent(
                    kind,
                    panels,
                    com.paulneja.quorvi.ui.panels.PanelLinks(
                        openOperations = callbacks.openAgents,
                        openAccounts = callbacks.openAccounts,
                        openCapabilities = callbacks.openCapabilities,
                        openCi = { id -> callbacks.openCi(id, androidx.compose.ui.geometry.Rect.Zero) },
                        openShell = { mount -> QuorviShell.open(context, view, null, mount) },
                    ),
                )
            }
        }

        if (showWallpaper) {
            WallpaperSheet(
                state = wallpaper,
                canReadSystem = viewModel.canReadSystemWallpaper,
                onPick = viewModel::setWallpaper,
                onUseCurrent = viewModel::useSystemWallpaper,
                onSystemPicker = callbacks.openWallpaper,
                onClose = { showWallpaper = false },
            )
        }

        if (dragging) DragGhost(drag, iconSize, appearance.showLabels)

        val preset by viewModel.preset.collectAsStateWithLifecycle()
        var choosingPreset by remember { mutableStateOf(false) }
        if (preset == null || choosingPreset) {
            com.paulneja.quorvi.ui.onboarding.PresetChooser(
                current = preset,
                firstRun = preset == null,
                onChoose = { chosen ->
                    choosingPreset = false
                    viewModel.applyPreset(chosen)
                    scope.launch { pager.scrollToPage(0) }
                },
                onClose = { choosingPreset = false },
            )
        }

        QuorviMenuHost(menu)
    }
    }
}

@Composable
private fun DragGhost(drag: DragController, iconSize: androidx.compose.ui.unit.Dp, showLabels: Boolean) {
    val density = LocalDensity.current
    val size = drag.visualSize
    Box(
        Modifier
            .offset { IntOffset((drag.pointer.x - drag.grab.x).roundToInt(), (drag.pointer.y - drag.grab.y).roundToInt()) }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .graphicsLayer {
                scaleX = 1.06f
                scaleY = 1.06f
                alpha = if (drag.target == null) 0.85f else 1f
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val payload = drag.payload) {
            is DragPayload.NewApp -> AppIconView(payload.app, iconSize, showLabels)
            is DragPayload.Existing -> when (val node = payload.node) {
                is CanvasNode.App -> AppIconView(node.app, iconSize, showLabels)
                is CanvasNode.Folder -> FolderTile(node, iconSize, showLabels)
                is CanvasNode.Clock -> ClockBlock()
                is CanvasNode.Card -> if (node.backdrop) com.paulneja.quorvi.ui.bento.GlassSpace(editing = true, empty = false) else WidgetGhost(if (node.kind == "ci") stringResource(R.string.card_ci) else node.kind.substringBefore(':').replaceFirstChar { it.uppercase() })
                is CanvasNode.Shortcut -> WidgetGhost("")
                is CanvasNode.Widget -> WidgetGhost(stringResource(R.string.menu_widgets))
            }
            null -> Unit
        }
    }
}

@Composable
private fun Dock(
    nodes: List<CanvasNode>,
    slots: Int,
    highlightSlot: Int?,
    ontoId: Long?,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    node: @Composable (CanvasNode) -> Unit,
) {
    val bySlot = nodes.associateBy { it.rect.x }
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(84.dp)
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slots) { slot ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (slot == highlightSlot || (ontoId != null && bySlot[slot]?.id == ontoId)) Modifier.background(Color.White.copy(alpha = 0.10f), Quorvi.shapes.control) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                bySlot[slot]?.let { node(it) }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = Quorvi.type.action,
        color = if (danger) Quorvi.colors.danger else Quorvi.colors.text,
        modifier = Modifier
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun GlassHint(onPick: () -> Unit, onAllFiles: () -> Unit, onRoot: (() -> Unit)?, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.paulneja.quorvi.ui.design.Glyph(Glyphs.Agent, 18.dp, Quorvi.colors.accent)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.glass_hint_title), style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            com.paulneja.quorvi.ui.design.Glyph(Glyphs.Close, 18.dp, Quorvi.colors.textSecondary, Modifier.clickable(onClick = onDismiss).padding(6.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.paulneja.quorvi.ui.design.ChipButton(stringResource(R.string.glass_opt_pick), Glyphs.Photo, onPick)
            com.paulneja.quorvi.ui.design.ChipButton(stringResource(R.string.glass_opt_current), Glyphs.Wallpaper, onAllFiles)
            if (onRoot != null) com.paulneja.quorvi.ui.design.ChipButton(stringResource(R.string.glass_opt_root), Glyphs.Shield, onRoot)
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.make_default),
            style = Quorvi.type.action,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), Quorvi.shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

data class MenuLabels(
    val addHome: String, val addDock: String, val info: String, val lock: String, val unlock: String,
    val remove: String, val removeFromFolder: String, val hide: String, val uninstall: String, val rename: String,
    val widgets: String, val addClock: String, val wallpaper: String, val settings: String, val labs: String,
    val dropRemove: String, val dropUninstall: String, val studio: String, val done: String,
    val removePage: String, val removePageConfirm: String, val editHome: String,
    val unsuggest: String, val resuggest: String, val glass: String,
) {
    companion object {
        @Composable
        fun load() = MenuLabels(
            stringResource(R.string.menu_add_home), stringResource(R.string.menu_add_dock), stringResource(R.string.menu_info),
            stringResource(R.string.menu_lock), stringResource(R.string.menu_unlock), stringResource(R.string.menu_remove),
            stringResource(R.string.menu_remove_from_folder), stringResource(R.string.menu_hide), stringResource(R.string.menu_uninstall),
            stringResource(R.string.menu_rename), stringResource(R.string.menu_widgets), stringResource(R.string.menu_add_clock),
            stringResource(R.string.menu_wallpaper), stringResource(R.string.menu_settings), stringResource(R.string.menu_labs),
            stringResource(R.string.drop_remove), stringResource(R.string.drop_uninstall), stringResource(R.string.menu_studio),
            stringResource(R.string.edit_done), stringResource(R.string.edit_remove_page), stringResource(R.string.edit_remove_page_confirm),
            stringResource(R.string.menu_edit_home),
            stringResource(R.string.menu_unsuggest), stringResource(R.string.menu_resuggest), stringResource(R.string.menu_glass),
        )
    }
}
