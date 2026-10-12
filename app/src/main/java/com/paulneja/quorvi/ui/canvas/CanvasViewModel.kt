package com.paulneja.quorvi.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.core.settings.Appearance
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.apps.AppActions
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.AppSearch
import com.paulneja.quorvi.launcher.apps.Profile
import com.paulneja.quorvi.launcher.apps.ProfileState
import com.paulneja.quorvi.launcher.home.CanvasState
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.HomeRepository
import com.paulneja.quorvi.launcher.home.PlaceResult
import com.paulneja.quorvi.launcher.wallpaper.WallpaperRepository
import com.paulneja.quorvi.launcher.wallpaper.WallpaperState
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerContent(
    val personal: List<AppEntry> = emptyList(),
    val work: List<AppEntry> = emptyList(),
    val private: List<AppEntry> = emptyList(),
    val workState: ProfileState? = null,
    val privateState: ProfileState? = null,
    val searching: Boolean = false,
)

sealed interface CanvasMessage {
    data object NoSpace : CanvasMessage
    data object CannotPlace : CanvasMessage
    data class LaunchFailed(val label: String) : CanvasMessage
    data object WallpaperFailed : CanvasMessage
    data class Error(val text: String) : CanvasMessage
}

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val catalog: AppCatalog,
    private val home: HomeRepository,
    private val actions: AppActions,
    private val settings: QuorviSettings,
    private val widgets: WidgetHostManager,
    private val wallpapers: WallpaperRepository,
    private val shortcuts: com.paulneja.quorvi.launcher.apps.Shortcuts,
    private val commands: com.paulneja.quorvi.core.command.CommandEngine,
    ops: com.paulneja.quorvi.ops.OpsDao,
    private val core: com.paulneja.quorvi.ops.CoreClient,
    connections: com.paulneja.quorvi.connections.ConnectionsDao,
    private val httpWidgets: com.paulneja.quorvi.connections.HttpWidgets,
    private val services: com.paulneja.quorvi.connections.services.ServicesRepo,
    notifications: com.paulneja.quorvi.control.NotificationHub,
    private val githubAccount: com.paulneja.quorvi.connections.github.GitHubAccount,
    providers: com.paulneja.quorvi.agent.models.ProviderDao,
    private val runtime: com.paulneja.quorvi.runtime.RuntimeClient,
) : ViewModel() {
    suspend fun runtimeAvailability(): com.paulneja.quorvi.runtime.RuntimeAvailability = runtime.availability()

    val badges: StateFlow<Set<String>> = notifications.badges.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val signedIn: StateFlow<Boolean> = githubAccount.login.map { it != null || githubAccount.tokens.signedIn() }.stateIn(viewModelScope, SharingStarted.Eagerly, githubAccount.tokens.signedIn())
    val hasModel: StateFlow<Boolean> = providers.observe().map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val haFavorites: StateFlow<List<com.paulneja.quorvi.connections.services.HaEntity>> = services.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasHomeAssistant: StateFlow<Boolean> = services.all.map { list -> list.any { it.kind == com.paulneja.quorvi.connections.services.ServiceKind.HOME_ASSISTANT.name } }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleHa(entity: com.paulneja.quorvi.connections.services.HaEntity) = viewModelScope.launch { runCatching { services.toggle(entity) }.onFailure { _messages.tryEmit(CanvasMessage.Error(it.message ?: "error")) } }

    private val healthJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val hosts: StateFlow<List<com.paulneja.quorvi.connections.HostEntity>> = connections.observeHosts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val containers: StateFlow<List<com.paulneja.quorvi.connections.ContainerEntity>> = connections.observeAllContainers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val http: StateFlow<List<com.paulneja.quorvi.connections.HttpWidgetEntity>> = connections.observeHttp().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun health(host: com.paulneja.quorvi.connections.HostEntity) = host.healthJson?.let { runCatching { healthJson.decodeFromString(com.paulneja.quorvi.connections.ssh.HostHealth.serializer(), it) }.getOrNull() }

    fun trigger(widget: com.paulneja.quorvi.connections.HttpWidgetEntity) = viewModelScope.launch { httpWidgets.trigger(widget) }

    val activeRuns: StateFlow<List<com.paulneja.quorvi.ops.RunEntity>> = kotlinx.coroutines.flow.combine(ops.observeRuns(20), settings.dismissedRuns) { runs, dismissed ->
        val stale = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        runs.filter { r ->
            val state = runCatching { com.paulneja.quorvi.ops.RunState.valueOf(r.state) }.getOrNull() ?: return@filter false
            !state.terminal && r.id !in dismissed && !(state == com.paulneja.quorvi.ops.RunState.INTERRUPTED && r.updatedAt < stale)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun dismissRuns(runs: List<com.paulneja.quorvi.ops.RunEntity>) = viewModelScope.launch { settings.dismissRuns(runs.map { it.id }) }
    val agentIndicator: StateFlow<com.paulneja.quorvi.core.settings.AgentIndicator> = settings.agentIndicator
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.paulneja.quorvi.core.settings.AgentIndicator.COMPACT)

    fun startAgent(goal: String, ciExecutionId: String? = null): Boolean {
        val spec = kotlinx.serialization.json.Json.encodeToString(com.paulneja.quorvi.agent.engine.RunSpec.serializer(), com.paulneja.quorvi.agent.engine.RunSpec(goal = goal, origin = "command", ciExecutionId = ciExecutionId))
        return core.submitRun(spec) != null
    }

    val ciRecent: StateFlow<List<com.paulneja.quorvi.ops.CiExecutionEntity>> = ops.observeCi(4).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCard(kind: String, page: Int) = viewModelScope.launch {
        val size = com.paulneja.quorvi.launcher.home.BlockKinds.of(kind)?.default
        report(home.addCard(kind, size?.w ?: 4, size?.h ?: 2, page))
    }
    private val _commandQuery = MutableStateFlow("")
    val commandQuery: StateFlow<String> = _commandQuery.asStateFlow()
    private val _commandResults = MutableStateFlow<List<com.paulneja.quorvi.core.command.CommandResult>>(emptyList())
    val commandResults: StateFlow<List<com.paulneja.quorvi.core.command.CommandResult>> = _commandResults.asStateFlow()
    private val _commandRecent = MutableStateFlow<List<String>>(emptyList())
    val commandRecent: StateFlow<List<String>> = _commandRecent.asStateFlow()
    private var commandJob: kotlinx.coroutines.Job? = null

    fun openCommand() = viewModelScope.launch {
        _commandQuery.value = ""
        _commandRecent.value = commands.recentQueries()
        _commandResults.value = commands.suggestions()
    }

    fun setCommandQuery(value: String) {
        _commandQuery.value = value
        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            if (value.isBlank()) {
                _commandResults.value = commands.suggestions()
                return@launch
            }
            val quick = commands.quick(value)
            _commandResults.value = quick
            kotlinx.coroutines.delay(120)
            val slow = commands.slow(value)
            if (slow.isNotEmpty()) _commandResults.value = com.paulneja.quorvi.core.command.CommandRanking.merge(quick + slow)
        }
    }

    fun recordCommand(result: com.paulneja.quorvi.core.command.CommandResult) = viewModelScope.launch {
        commands.record(result, _commandQuery.value)
    }

    fun clearCommandHistory() = viewModelScope.launch {
        commands.clearHistory()
        _commandRecent.value = emptyList()
    }
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _messages = MutableSharedFlow<CanvasMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<CanvasMessage> = _messages.asSharedFlow()

    val canvas: StateFlow<CanvasState?> = home.canvas.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val appearance: StateFlow<Appearance> = settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
    val look: StateFlow<com.paulneja.quorvi.core.settings.Look> = settings.look.stateIn(viewModelScope, SharingStarted.Eagerly, com.paulneja.quorvi.core.settings.Look())
    val preset: StateFlow<com.paulneja.quorvi.core.settings.HomePreset?> = settings.preset.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hidden: StateFlow<Set<String>> = settings.hiddenApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val drawer: StateFlow<DrawerContent> = combine(catalog.apps, catalog.profiles, _query, settings.hiddenApps) { all, profiles, q, hiddenKeys ->
        val visible = all.orEmpty().filter { it.key.encode() !in hiddenKeys }
        val searching = AppSearch.normalize(q).isNotEmpty()
        val workState = profiles.firstOrNull { it.profile == Profile.WORK }
        val privateState = profiles.firstOrNull { it.profile == Profile.PRIVATE }
        if (searching) {
            DrawerContent(personal = AppSearch.filter(visible, q) { it.label }, workState = workState, privateState = privateState, searching = true)
        } else {
            DrawerContent(
                personal = visible.filter { it.profile == Profile.MAIN },
                work = visible.filter { it.profile == Profile.WORK },
                private = visible.filter { it.profile == Profile.PRIVATE },
                workState = workState,
                privateState = privateState,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, DrawerContent())

    val results: StateFlow<List<AppEntry>> = combine(catalog.apps, _query, settings.hiddenApps) { all, q, hiddenKeys ->
        AppSearch.filter(all.orEmpty().filter { it.key.encode() !in hiddenKeys && it.profile != Profile.PRIVATE }, q) { it.label }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setProfilePaused(state: ProfileState, paused: Boolean) = viewModelScope.launch(Dispatchers.Default) {
        catalog.setPaused(state.user, paused)
    }

    val wallpaper: StateFlow<WallpaperState> = wallpapers.state

    val canReadSystemWallpaper: Boolean get() = wallpapers.canReadSystem

    fun setWallpaper(uri: android.net.Uri, alsoLockScreen: Boolean) = viewModelScope.launch {
        if (wallpapers.setFromUri(uri, alsoLockScreen).isFailure) _messages.tryEmit(CanvasMessage.WallpaperFailed)
    }

    fun useSystemWallpaper() = viewModelScope.launch { wallpapers.useSystem() }

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _rootAvailable.value = wallpapers.rootAvailable }
        viewModelScope.launch { kotlinx.coroutines.delay(1_500); runCatching { home.offerTerminalPage() } }
    }

    fun wallpaperWithRoot(onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(wallpapers.importWithRoot()) }

    fun refreshWallpaper() = viewModelScope.launch { wallpapers.reload() }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun launch(app: AppEntry, source: android.graphics.Rect? = null, options: android.os.Bundle? = null) {
        if (!actions.launch(app, source, options)) _messages.tryEmit(CanvasMessage.LaunchFailed(app.label))
        else viewModelScope.launch { commands.recordLaunch(app) }
    }

    fun details(app: AppEntry) = actions.details(app)

    fun uninstall(app: AppEntry) = actions.uninstall(app)

    fun hide(app: AppEntry) = viewModelScope.launch { settings.setHidden(app.key.encode(), true) }
    val unsuggested: StateFlow<Set<String>> = settings.unsuggested.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun setSuggested(app: AppEntry, suggested: Boolean) = viewModelScope.launch {
        settings.setSuggested(app.key.encode(), suggested)
        if (_commandQuery.value.isBlank()) _commandResults.value = commands.suggestions()
    }

    fun addToHome(app: AppEntry, container: Int = Container.PAGE) = viewModelScope.launch {
        report(home.addApp(app, container))
    }

    fun drop(payload: DragPayload, target: DropTarget?) = viewModelScope.launch {
        val result = when (target) {
            null -> PlaceResult.Rejected
            is DropTarget.Cell -> when (payload) {
                is DragPayload.Existing -> home.move(payload.node.id, Container.PAGE, target.page, target.rect)
                is DragPayload.NewApp -> home.addAppAt(payload.app, Container.PAGE, target.page, target.rect)
            }
            is DropTarget.Dock -> when (payload) {
                is DragPayload.Existing -> home.move(payload.node.id, Container.DOCK, 0, CellRect(target.slot, 0))
                is DragPayload.NewApp -> home.addAppAt(payload.app, Container.DOCK, 0, CellRect(target.slot, 0))
            }
            is DropTarget.Onto -> when (payload) {
                is DragPayload.Existing -> home.dropOnto(target.nodeId, payload.node.id, null)
                is DragPayload.NewApp -> home.dropOnto(target.nodeId, null, payload.app)
            }
            DropTarget.Remove -> {
                if (payload is DragPayload.Existing) remove(payload.node.id)
                PlaceResult.Done
            }
            DropTarget.Uninstall -> {
                val app = when (payload) {
                    is DragPayload.Existing -> (payload.node as? com.paulneja.quorvi.launcher.home.CanvasNode.App)?.app
                    is DragPayload.NewApp -> payload.app
                }
                app?.let(actions::uninstall)
                PlaceResult.Done
            }
        }
        if (target != null) report(result)
    }

    suspend fun remove(id: Long) {
        val removed = home.remove(id) ?: return
        removed.widgetId?.let(widgets::release)
        if (removed.type == com.paulneja.quorvi.launcher.home.ItemType.SHORTCUT) {
            val key = removed.appKey?.let(com.paulneja.quorvi.launcher.apps.AppKey::decode) ?: return
            val user = catalog.user(key.userSerial) ?: return
            shortcuts.pin(key.packageName, home.pinnedShortcutIds(key.packageName, key.userSerial), user)
        }
    }

    fun pinShortcut(app: AppEntry, shortcut: com.paulneja.quorvi.launcher.apps.AppShortcut) = viewModelScope.launch {
        val result = home.addShortcut(app.key.packageName, shortcut.info.id, app.key.userSerial)
        if (result == PlaceResult.Done) shortcuts.pin(app.key.packageName, home.pinnedShortcutIds(app.key.packageName, app.key.userSerial), app.user)
        report(result)
    }

    fun openShortcut(node: com.paulneja.quorvi.launcher.home.CanvasNode.Shortcut) {
        if (!shortcuts.start(node.packageName, node.shortcutId, node.user)) _messages.tryEmit(CanvasMessage.LaunchFailed(node.packageName))
    }

    fun removeNode(id: Long) = viewModelScope.launch { remove(id) }

    fun removePage(page: Int) = viewModelScope.launch { home.removePage(page).forEach(widgets::release) }

    suspend fun addPage(template: com.paulneja.quorvi.launcher.home.PageTemplate): Int = home.addPage(template)

    fun movePage(from: Int, to: Int) = viewModelScope.launch { home.movePage(from, to) }

    fun applyPreset(preset: com.paulneja.quorvi.core.settings.HomePreset) = viewModelScope.launch {
        home.applyPreset(preset, home.currentApps())
        settings.setLook(look.value.copy(minimalChrome = preset == com.paulneja.quorvi.core.settings.HomePreset.MINIMAL))
    }

    fun renameFolder(id: Long, title: String) = viewModelScope.launch { home.renameFolder(id, title) }

    fun removeFromFolder(id: Long) = viewModelScope.launch { report(home.removeFromFolder(id)) }

    fun setLocked(id: Long, locked: Boolean) = viewModelScope.launch { home.setLocked(id, locked) }

    fun addClock() = viewModelScope.launch { report(home.addClock()) }

    fun addWidget(widgetId: Int, spanX: Int, spanY: Int, page: Int) = viewModelScope.launch {
        if (home.addWidget(widgetId, spanX, spanY, page) == null) {
            widgets.release(widgetId)
            _messages.tryEmit(CanvasMessage.NoSpace)
        }
    }

    fun resize(id: Long, rect: CellRect) = viewModelScope.launch { report(home.resize(id, rect)) }

    private fun report(result: PlaceResult) {
        when (result) {
            PlaceResult.NoSpace -> _messages.tryEmit(CanvasMessage.NoSpace)
            PlaceResult.Rejected -> _messages.tryEmit(CanvasMessage.CannotPlace)
            else -> Unit
        }
    }
}
