package com.paulneja.quorvi.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.ui.design.MotionProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(com.paulneja.quorvi.core.backup.FullBackup.SETTINGS_NAME)

enum class GridMode { AUTO, MANUAL }

enum class AgentIndicator { OFF, SUBTLE, COMPACT, OPERATIONS_ONLY, VERBOSE }

data class Appearance(
    val accent: Long = DEFAULT_ACCENT,
    val glass: Float = 0.7f,
    val motion: MotionProfile = MotionProfile.CINEMATIC,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val iconPack: String? = null,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF5EA7FFL
    }
}

data class LayoutPrefs(
    val gridMode: GridMode = GridMode.AUTO,
    val columns: Int = 4,
    val rows: Int = 5,
    val seeded: Boolean = false,
    val bentoSeeded: Boolean = false,
    val appliedColumns: Int = 0,
    val appliedRows: Int = 0,
)

@Singleton
class QuorviSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val store = context.store

    val appearance: Flow<Appearance> = store.data.map { p ->
        Appearance(
            accent = p[ACCENT]?.toLong() ?: Appearance.DEFAULT_ACCENT,
            glass = p[GLASS] ?: 0.7f,
            motion = p[MOTION]?.let { runCatching { MotionProfile.valueOf(it) }.getOrNull() } ?: MotionProfile.CINEMATIC,
            iconScale = p[ICON_SCALE] ?: 1f,
            showLabels = p[SHOW_LABELS] ?: true,
            iconPack = p[ICON_PACK],
        )
    }

    val layout: Flow<LayoutPrefs> = store.data.map { p ->
        LayoutPrefs(
            gridMode = p[GRID_MODE]?.let { runCatching { GridMode.valueOf(it) }.getOrNull() } ?: GridMode.AUTO,
            columns = p[COLUMNS] ?: 4,
            rows = p[ROWS] ?: 5,
            seeded = p[SEEDED] ?: false,
            bentoSeeded = p[BENTO_SEEDED] ?: false,
            appliedColumns = p[APPLIED_COLUMNS] ?: 0,
            appliedRows = p[APPLIED_ROWS] ?: 0,
        )
    }

    val hiddenApps: Flow<Set<String>> = store.data.map { it[HIDDEN].orEmpty() }

    val dismissedRuns: Flow<Set<String>> = store.data.map { it[DISMISSED_RUNS].orEmpty() }

    suspend fun dismissRuns(ids: Collection<String>) = store.edit { it[DISMISSED_RUNS] = (it[DISMISSED_RUNS].orEmpty() + ids).toList().takeLast(200).toSet() }

    val unsuggested: Flow<Set<String>> = store.data.map { it[UNSUGGESTED].orEmpty() }

    val backgroundProtection: Flow<Boolean> = store.data.map { it[BG_PROTECTION] == true }

    suspend fun setBackgroundProtection(on: Boolean) = store.edit { it[BG_PROTECTION] = on }

    val voice: Flow<VoiceConfig> = store.data.map { VoiceConfig.decode(it[VOICE]) }

    suspend fun voiceNow(): VoiceConfig = voice.first()

    suspend fun setVoice(change: (VoiceConfig) -> VoiceConfig) = store.edit { it[VOICE] = VoiceConfig.encode(change(VoiceConfig.decode(it[VOICE]))) }

    val ignoredGitHub: Flow<Set<String>> = store.data.map { it[IGNORED_GITHUB].orEmpty() }

    suspend fun setIgnoredGitHub(key: String, ignored: Boolean) = store.edit {
        val current = it[IGNORED_GITHUB].orEmpty()
        it[IGNORED_GITHUB] = if (ignored) current + key else current - key
    }

    val agentIndicator: Flow<AgentIndicator> = store.data.map { p ->
        p[AGENT_INDICATOR]?.let { runCatching { AgentIndicator.valueOf(it) }.getOrNull() } ?: AgentIndicator.COMPACT
    }

    suspend fun setAgentIndicator(value: AgentIndicator) = store.edit { it[AGENT_INDICATOR] = value.name }

    suspend fun currentLayout(): LayoutPrefs = layout.first()

    suspend fun setAppearance(value: Appearance) = store.edit {
        it[ACCENT] = value.accent.toString()
        it[GLASS] = value.glass
        it[MOTION] = value.motion.name
        it[ICON_SCALE] = value.iconScale
        it[SHOW_LABELS] = value.showLabels
        if (value.iconPack == null) it.remove(ICON_PACK) else it[ICON_PACK] = value.iconPack
    }

    suspend fun setGrid(mode: GridMode, columns: Int, rows: Int) = store.edit {
        it[GRID_MODE] = mode.name
        it[COLUMNS] = columns
        it[ROWS] = rows
    }

    suspend fun markApplied(spec: GridSpec) = store.edit {
        it[APPLIED_COLUMNS] = spec.columns
        it[APPLIED_ROWS] = spec.rows
    }

    suspend fun markSeeded() = store.edit { it[SEEDED] = true }

    suspend fun markBentoSeeded() = store.edit { it[BENTO_SEEDED] = true }

    suspend fun terminalPageOffered(): Boolean = store.data.first()[TERMINAL_PAGE] ?: false

    suspend fun markTerminalPageOffered() = store.edit { it[TERMINAL_PAGE] = true }

    suspend fun setSuggested(appKey: String, suggested: Boolean) = store.edit {
        val current = it[UNSUGGESTED].orEmpty()
        it[UNSUGGESTED] = if (suggested) current - appKey else current + appKey
    }

    suspend fun setHidden(appKey: String, hidden: Boolean) = store.edit {
        val current = it[HIDDEN].orEmpty()
        it[HIDDEN] = if (hidden) current + appKey else current - appKey
    }

    val privileged: Flow<PrivilegedConfig> = store.data.map { p ->
        PrivilegedConfig(
            enabled = p[PRIV_BACKENDS].orEmpty().mapNotNull { runCatching { com.paulneja.quorvi.control.PrivBackend.valueOf(it) }.getOrNull() }.toSet(),
            rootLevel = p[ROOT_LEVEL]?.let { runCatching { com.paulneja.quorvi.control.RootLevel.valueOf(it) }.getOrNull() } ?: com.paulneja.quorvi.control.RootLevel.NONE,
            preHook = p[ROOT_PRE_HOOK].orEmpty(),
            postHook = p[ROOT_POST_HOOK].orEmpty(),
            adbEndpoint = p[ADB_ENDPOINT].orEmpty(),
        )
    }

    suspend fun privileged(): PrivilegedConfig = privileged.first()

    suspend fun setPrivBackend(backend: com.paulneja.quorvi.control.PrivBackend, on: Boolean) = store.edit {
        val current = it[PRIV_BACKENDS].orEmpty()
        it[PRIV_BACKENDS] = if (on) current + backend.name else current - backend.name
    }

    suspend fun setRootLevel(level: com.paulneja.quorvi.control.RootLevel) = store.edit { it[ROOT_LEVEL] = level.name }

    suspend fun setRootHooks(pre: String, post: String) = store.edit { it[ROOT_PRE_HOOK] = pre; it[ROOT_POST_HOOK] = post }

    suspend fun setAdbEndpoint(endpoint: String) = store.edit { it[ADB_ENDPOINT] = endpoint.trim() }

    val look: Flow<Look> = store.data.map { Look.decode(it[LOOK]) }

    suspend fun setLook(value: Look) = store.edit { it[LOOK] = Look.encode(value) }

    val preset: Flow<HomePreset?> = store.data.map { p -> p[PRESET]?.let { runCatching { HomePreset.valueOf(it) }.getOrNull() } }

    suspend fun setPreset(value: HomePreset) = store.edit { it[PRESET] = value.name }

    val weatherPlace: Flow<String?> = store.data.map { it[WEATHER_PLACE] }
    val weatherCache: Flow<String?> = store.data.map { it[WEATHER_CACHE] }

    suspend fun setWeatherPlace(raw: String) = store.edit { it[WEATHER_PLACE] = raw }
    suspend fun setWeatherCache(raw: String) = store.edit { it[WEATHER_CACHE] = raw }

    val rootDegraded: Flow<String?> = store.data.map { it[ROOT_DEGRADED] }

    suspend fun setRootDegraded(backends: String?) = store.edit { if (backends == null) it.remove(ROOT_DEGRADED) else it[ROOT_DEGRADED] = backends }

    private companion object {
        val ACCENT = stringPreferencesKey("accent")
        val GLASS = floatPreferencesKey("glass")
        val MOTION = stringPreferencesKey("motion")
        val ICON_SCALE = floatPreferencesKey("icon_scale")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val GRID_MODE = stringPreferencesKey("grid_mode")
        val COLUMNS = intPreferencesKey("grid_columns")
        val ROWS = intPreferencesKey("grid_rows")
        val SEEDED = booleanPreferencesKey("seeded")
        val BENTO_SEEDED = booleanPreferencesKey("bento_seeded")
        val TERMINAL_PAGE = booleanPreferencesKey("terminal_page_offered")
        val WEATHER_PLACE = stringPreferencesKey("weather_place")
        val LOOK = stringPreferencesKey("look")
        val PRESET = stringPreferencesKey("home_preset")
        val WEATHER_CACHE = stringPreferencesKey("weather_cache")
        val APPLIED_COLUMNS = intPreferencesKey("applied_columns")
        val APPLIED_ROWS = intPreferencesKey("applied_rows")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
        val DISMISSED_RUNS = stringSetPreferencesKey("dismissed_runs")
        val UNSUGGESTED = stringSetPreferencesKey("unsuggested_apps")
        val IGNORED_GITHUB = stringSetPreferencesKey("ignored_github")
        val BG_PROTECTION = booleanPreferencesKey("background_protection")
        val AGENT_INDICATOR = stringPreferencesKey("agent_indicator")
        val VOICE = stringPreferencesKey("voice_config")
        val PRIV_BACKENDS = stringSetPreferencesKey("priv_backends")
        val ROOT_LEVEL = stringPreferencesKey("root_level")
        val ROOT_PRE_HOOK = stringPreferencesKey("root_pre_hook")
        val ROOT_POST_HOOK = stringPreferencesKey("root_post_hook")
        val ADB_ENDPOINT = stringPreferencesKey("adb_endpoint")
        val ROOT_DEGRADED = stringPreferencesKey("root_degraded")
    }
}

data class PrivilegedConfig(
    val enabled: Set<com.paulneja.quorvi.control.PrivBackend> = emptySet(),
    val rootLevel: com.paulneja.quorvi.control.RootLevel = com.paulneja.quorvi.control.RootLevel.NONE,
    val preHook: String = "",
    val postHook: String = "",
    val adbEndpoint: String = "",
)
