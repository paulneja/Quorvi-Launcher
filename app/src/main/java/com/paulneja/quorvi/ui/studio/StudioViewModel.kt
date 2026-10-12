package com.paulneja.quorvi.ui.studio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.core.scene.ImportReport
import com.paulneja.quorvi.core.scene.SceneRepository
import com.paulneja.quorvi.core.settings.Appearance
import com.paulneja.quorvi.core.settings.GridMode
import com.paulneja.quorvi.core.settings.LayoutPrefs
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.launcher.home.HomeRepository
import com.paulneja.quorvi.launcher.icons.IconPack
import com.paulneja.quorvi.launcher.icons.IconPacks
import com.paulneja.quorvi.launcher.wallpaper.WallpaperRepository
import com.paulneja.quorvi.launcher.wallpaper.WallpaperState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudioSnapshot(val appearance: Appearance, val gridMode: GridMode, val columns: Int, val rows: Int)

sealed interface StudioMessage {
    data class Imported(val report: ImportReport) : StudioMessage
    data object ImportFailed : StudioMessage
    data object Exported : StudioMessage
    data object ExportFailed : StudioMessage
    data object LayoutCleared : StudioMessage
}

@HiltViewModel
class StudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: QuorviSettings,
    private val home: HomeRepository,
    private val scenes: SceneRepository,
    private val packs: IconPacks,
    private val wallpapers: WallpaperRepository,
    catalog: AppCatalog,
    private val importer: com.paulneja.quorvi.launcher.home.LauncherImportSource,
    private val ai: com.paulneja.quorvi.agent.engine.AiSwitch,
) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
    val layout: StateFlow<LayoutPrefs> = settings.layout.stateIn(viewModelScope, SharingStarted.Eagerly, LayoutPrefs())
    val look: StateFlow<com.paulneja.quorvi.core.settings.Look> = settings.look.stateIn(viewModelScope, SharingStarted.Eagerly, com.paulneja.quorvi.core.settings.Look())
    val preset: StateFlow<com.paulneja.quorvi.core.settings.HomePreset?> = settings.preset.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hasBackup: Boolean get() = home.hasBackup

    fun setLook(change: (com.paulneja.quorvi.core.settings.Look) -> com.paulneja.quorvi.core.settings.Look) = viewModelScope.launch { settings.setLook(change(look.value)) }

    fun resetLook() = viewModelScope.launch { settings.setLook(com.paulneja.quorvi.core.settings.Look(minimalChrome = look.value.minimalChrome)) }

    fun applyPreset(preset: com.paulneja.quorvi.core.settings.HomePreset) = viewModelScope.launch {
        home.applyPreset(preset, home.currentApps(), ai.current())
        settings.setLook(look.value.copy(minimalChrome = preset == com.paulneja.quorvi.core.settings.HomePreset.MINIMAL))
    }

    fun restoreBackup() = viewModelScope.launch { home.restoreBackup() }

    val importSources: List<com.paulneja.quorvi.launcher.home.SourceLauncher> get() = importer.installedSources()
    val rootAvailable: Boolean get() = importer.rootAvailable()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    fun importFromRoot(pkg: String, replaceDock: Boolean) = viewModelScope.launch {
        _importResult.value = runImport(importer.copyWithRoot(pkg), replaceDock)
    }

    fun importFromFile(uri: Uri, replaceDock: Boolean) = viewModelScope.launch {
        _importResult.value = runImport(importer.copyFromUri(uri), replaceDock)
    }

    fun clearImportResult() { _importResult.value = null }

    private suspend fun runImport(file: Result<java.io.File>, replaceDock: Boolean): String {
        val f = file.getOrElse { return context.getString(com.paulneja.quorvi.R.string.import_failed, it.message ?: "") }
        val rows = importer.rows(f).getOrElse { return context.getString(com.paulneja.quorvi.R.string.import_failed, it.message ?: "") }
        val apps = home.currentApps()
        val installed = apps.map { it.key.packageName + "/" + it.key.className }.toSet()
        val plan = com.paulneja.quorvi.launcher.home.LauncherImport.plan(rows, installed)
        if (plan.entries.isEmpty()) return context.getString(com.paulneja.quorvi.R.string.import_empty)
        val placed = home.importApps(plan, apps, replaceDock)
        val folders = plan.entries.count { it is com.paulneja.quorvi.launcher.home.ImportEntry.Folder }
        return context.getString(com.paulneja.quorvi.R.string.import_done, placed, folders)
    }
    val autoSpec: GridSpec get() = home.spec.value.takeIf { layout.value.gridMode == GridMode.AUTO } ?: context.resources.displayMetrics.let { GridSpec.auto(it.widthPixels / it.density, it.heightPixels / it.density) }
    val wallpaper: StateFlow<WallpaperState> = wallpapers.state
    val hiddenApps: StateFlow<List<AppEntry>> = combine(catalog.apps, settings.hiddenApps) { apps, hidden ->
        apps.orEmpty().filter { it.key.encode() in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _packs = MutableStateFlow<List<IconPack>>(emptyList())
    val iconPacks: StateFlow<List<IconPack>> = _packs.asStateFlow()
    private val undo = ArrayDeque<StudioSnapshot>()
    private val redo = ArrayDeque<StudioSnapshot>()
    private val _history = MutableStateFlow(false to false)
    val history: StateFlow<Pair<Boolean, Boolean>> = _history.asStateFlow()
    private val _messages = MutableSharedFlow<StudioMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<StudioMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { _packs.value = withContext(Dispatchers.IO) { packs.installed() } }
    }

    private fun snapshot() = StudioSnapshot(appearance.value, layout.value.gridMode, layout.value.columns, layout.value.rows)

    private suspend fun apply(s: StudioSnapshot) {
        settings.setAppearance(s.appearance)
        settings.setGrid(s.gridMode, s.columns, s.rows)
    }

    private fun change(block: (StudioSnapshot) -> StudioSnapshot) = viewModelScope.launch {
        val current = snapshot()
        val next = block(current)
        if (next == current) return@launch
        undo.addLast(current)
        if (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        apply(next)
        _history.value = undo.isNotEmpty() to false
    }

    fun undo() = viewModelScope.launch {
        val previous = undo.removeLastOrNull() ?: return@launch
        redo.addLast(snapshot())
        apply(previous)
        _history.value = undo.isNotEmpty() to redo.isNotEmpty()
    }

    fun redo() = viewModelScope.launch {
        val next = redo.removeLastOrNull() ?: return@launch
        undo.addLast(snapshot())
        apply(next)
        _history.value = undo.isNotEmpty() to redo.isNotEmpty()
    }

    fun setAppearance(block: (Appearance) -> Appearance) = change { it.copy(appearance = block(it.appearance)) }

    fun setGrid(mode: GridMode, columns: Int, rows: Int) = change {
        it.copy(gridMode = mode, columns = columns.coerceIn(3, 8), rows = rows.coerceIn(3, 10))
    }

    fun resetDefaults() = change { StudioSnapshot(Appearance(), GridMode.AUTO, it.columns, it.rows) }

    fun unhide(app: AppEntry) = viewModelScope.launch { settings.setHidden(app.key.encode(), false) }
    val unsuggestedApps: StateFlow<List<AppEntry>> = combine(catalog.apps, settings.unsuggested) { apps, keys ->
        apps.orEmpty().filter { it.key.encode() in keys }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun resuggest(app: AppEntry) = viewModelScope.launch { settings.setSuggested(app.key.encode(), true) }

    fun setWallpaper(uri: Uri, lockToo: Boolean) = viewModelScope.launch { wallpapers.setFromUri(uri, lockToo) }

    fun useSystemWallpaper() = viewModelScope.launch { wallpapers.useSystem() }

    fun refreshWallpaper() = viewModelScope.launch { wallpapers.reload() }

    fun clearLayout() = viewModelScope.launch {
        home.clear()
        _messages.tryEmit(StudioMessage.LayoutCleared)
    }

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            val text = scenes.export()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
        }.isSuccess
        _messages.tryEmit(if (ok) StudioMessage.Exported else StudioMessage.ExportFailed)
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        }
        val report = text?.let { scenes.import(it).getOrNull() }
        if (report == null) {
            _messages.tryEmit(StudioMessage.ImportFailed)
        } else {
            home.relayoutNow()
            _messages.tryEmit(StudioMessage.Imported(report))
        }
    }

    suspend fun currentLayout(): LayoutPrefs = settings.layout.first()

    private companion object {
        const val MAX_HISTORY = 50
    }
}
