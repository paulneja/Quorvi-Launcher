package com.paulneja.quorvi.ui.panels

import com.paulneja.quorvi.control.message
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.agent.capsules.CapsuleDao
import com.paulneja.quorvi.agent.capsules.CapsuleEntity
import com.paulneja.quorvi.agent.models.ProviderDao
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.policy.AutonomyMode
import com.paulneja.quorvi.agent.policy.PolicyConfig
import com.paulneja.quorvi.agent.policy.PolicyDao
import com.paulneja.quorvi.agent.policy.PolicySettingEntity
import com.paulneja.quorvi.connections.github.GhPull
import com.paulneja.quorvi.connections.github.GitHubAccount
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.control.DeviceControls
import com.paulneja.quorvi.control.PowerHooks
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.core.settings.PrivilegedConfig
import com.paulneja.quorvi.launcher.live.ActionOutcome
import com.paulneja.quorvi.launcher.live.BatteryInfo
import com.paulneja.quorvi.launcher.live.BatteryState
import com.paulneja.quorvi.launcher.live.Connectivity
import com.paulneja.quorvi.launcher.live.DataUsage
import com.paulneja.quorvi.launcher.live.DataUsageSource
import com.paulneja.quorvi.launcher.live.DeviceSettingsInfo
import com.paulneja.quorvi.launcher.live.DeviceSettingsState
import com.paulneja.quorvi.launcher.live.NetState
import com.paulneja.quorvi.launcher.live.PrivStatus
import com.paulneja.quorvi.launcher.live.PrivStatusSource
import com.paulneja.quorvi.launcher.live.QuickActions
import com.paulneja.quorvi.launcher.live.SystemInfo
import com.paulneja.quorvi.launcher.live.SystemStats
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunMachine
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ops.StepState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PanelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    battery: BatteryState,
    system: SystemStats,
    connectivity: Connectivity,
    deviceSettings: DeviceSettingsState,
    private val privSource: PrivStatusSource,
    private val shells: com.paulneja.quorvi.control.PrivilegedShells,
    private val actions: QuickActions,
    private val controls: DeviceControls,
    private val ops: OpsDao,
    providers: ProviderDao,
    private val policy: PolicyDao,
    capsules: CapsuleDao,
    private val settings: QuorviSettings,
    private val core: CoreClient,
    private val chatRepo: com.paulneja.quorvi.agent.chat.ChatRepository,
    private val pendingSource: com.paulneja.quorvi.connections.github.GitHubPending,
    private val github: GitHubAccount,
    private val api: GitHubApi,
    private val usageSource: DataUsageSource,
    private val modelProviders: com.paulneja.quorvi.agent.models.ModelProviders,
    chats: com.paulneja.quorvi.agent.chat.ChatDao,
    private val runtime: com.paulneja.quorvi.runtime.RuntimeClient,
    private val backgroundJobs: com.paulneja.quorvi.agent.jobs.BackgroundJobs,
) : ViewModel() {
    init { viewModelScope.launch { runCatching { chatRepo.pruneOrphanRuns() } } }

    data class ShellEntry(val command: String, val output: String, val exitCode: Int?, val running: Boolean = false)

    private val _runtimeState = MutableStateFlow<com.paulneja.quorvi.runtime.RuntimeAvailability?>(null)
    val runtimeState: StateFlow<com.paulneja.quorvi.runtime.RuntimeAvailability?> = _runtimeState.asStateFlow()
    private val _shellHistory = MutableStateFlow<List<ShellEntry>>(emptyList())
    val shellHistory: StateFlow<List<ShellEntry>> = _shellHistory.asStateFlow()
    private var shellCwd: String? = null

    fun refreshRuntime() = viewModelScope.launch { _runtimeState.value = runCatching { runtime.availability() }.getOrNull() }

    fun runShell(command: String) {
        val cmd = command.trim().takeIf { it.isNotEmpty() } ?: return
        val entry = ShellEntry(cmd, "", null, running = true)
        _shellHistory.value = (_shellHistory.value + entry).takeLast(30)
        viewModelScope.launch {
            val marker = "__NEXO_CWD__"
            val result = runCatching {
                runtime.exec(com.paulneja.quorvi.runtime.ExecRequest(
                    argv = listOf("bash", "-lc", cmd + "; __s=\$?; printf '\\n" + marker + "%s' \"\$(pwd)\"; exit \$__s"),
                    cwd = shellCwd, timeoutMs = 120_000, maxOutputBytes = 64 * 1024,
                ))
            }
            val done = result.fold(
                onSuccess = { r ->
                    val out = r.stdout.substringBeforeLast(marker).trimEnd() + if (r.stderr.isNotBlank()) "\n" + r.stderr.trimEnd() else ""
                    r.stdout.substringAfterLast(marker, "").trim().takeIf { it.startsWith("/") }?.let { shellCwd = it }
                    entry.copy(output = out.takeLast(6000), exitCode = r.exitCode, running = false)
                },
                onFailure = { e -> entry.copy(output = e.message ?: "error", exitCode = -1, running = false) },
            )
            _shellHistory.value = _shellHistory.value.map { if (it === entry) done else it }
        }
    }

    fun clearShell() { _shellHistory.value = emptyList() }

    val recentChats: StateFlow<List<com.paulneja.quorvi.agent.chat.ChatEntity>> = chats.observeChats().map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _models = MutableStateFlow<Result<List<com.paulneja.quorvi.agent.models.AiModelInfo>>?>(null)
    val models: StateFlow<Result<List<com.paulneja.quorvi.agent.models.AiModelInfo>>?> = _models.asStateFlow()

    fun loadModels(providerId: String) = viewModelScope.launch {
        _models.value = null
        _models.value = runCatching { modelProviders.listModels(providerId) }
    }

    fun setModel(providerId: String, model: String) = viewModelScope.launch { modelProviders.setModel(providerId, model) }

    val battery: StateFlow<BatteryInfo?> = battery.info
    val system: StateFlow<SystemInfo?> = system.info
    val net: StateFlow<NetState> = connectivity.state
    val device: StateFlow<DeviceSettingsInfo> = deviceSettings.state
    val usage: StateFlow<DataUsage?> = usageSource.month
    val priv: StateFlow<PrivStatus?> = privSource.status

    val providers: StateFlow<List<ProviderEntity>> = providers.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val policyConfig: StateFlow<PolicyConfig> = policy.observeSettings().map { list -> PolicyConfig.from(list.associate { it.key to it.value }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PolicyConfig())
    val privileged: StateFlow<PrivilegedConfig> = settings.privileged.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivilegedConfig())
    val capsules: StateFlow<List<CapsuleEntity>> = capsules.observe().map { list -> list.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runs: StateFlow<List<RunView>> = ops.observeRuns(30).flatMapLatest { runs ->
        val active = runs.filter { runCatching { RunState.valueOf(it.state) }.getOrNull()?.terminal == false }.take(4)
        val stepsFlows = active.map { r -> ops.observeSteps(r.id).map { r.id to it } }
        val stepsByRun = if (stepsFlows.isEmpty()) flowOf(emptyMap()) else combine(stepsFlows) { it.toMap() }
        stepsByRun.map { byRun ->
            runs.mapNotNull { r ->
                val state = runCatching { RunState.valueOf(r.state) }.getOrNull() ?: return@mapNotNull null
                val steps = byRun[r.id].orEmpty()
                val (done, total) = RunMachine.progress(steps.mapNotNull { runCatching { StepState.valueOf(it.state) }.getOrNull() })
                val current = steps.firstOrNull { it.state == StepState.RUNNING.name }?.title
                RunView(r.id, r.goal, state, done, total, current, r.updatedAt, r.error, r.origin)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ci: StateFlow<List<CiExecutionEntity>> = ops.observeCi(40).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val watched: StateFlow<List<String>> = ops.observeWatched().map { list -> list.map { it.fullName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val login: StateFlow<String?> = github.login
    val signedIn: StateFlow<Boolean> = github.login.map { it != null || github.tokens.signedIn() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), github.tokens.signedIn())

    private val _pending = MutableStateFlow<List<com.paulneja.quorvi.connections.github.PendingItem>?>(null)
    val pending: StateFlow<List<com.paulneja.quorvi.connections.github.PendingItem>?> = _pending.asStateFlow()
    val ignoredGitHub: StateFlow<Set<String>> = settings.ignoredGitHub.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private var pendingAt = 0L

    fun refreshPending(force: Boolean = false) {
        if (!github.tokens.signedIn()) return
        val now = System.currentTimeMillis()
        if (!force && now - pendingAt < 10 * 60_000 && _pending.value != null) return
        pendingAt = now
        viewModelScope.launch { _pending.value = runCatching { pendingSource.fetch() }.getOrNull() ?: _pending.value ?: emptyList() }
    }

    fun ignoreGitHub(key: String, ignored: Boolean) = viewModelScope.launch { settings.setIgnoredGitHub(key, ignored) }

    private val _prs = MutableStateFlow<List<GhPull>?>(null)
    val prs: StateFlow<List<GhPull>?> = _prs.asStateFlow()

    private val _intents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val intents: SharedFlow<Intent> = _intents.asSharedFlow()

    val hooksAtLevel: StateFlow<Int> = settings.privileged.map { cfg -> PowerHooks.catalog.count { cfg.rootLevel.allows(it.level) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun refreshOnOpen() {
        if (github.tokens.signedIn()) viewModelScope.launch { _prs.value = runCatching { api.reviewRequests() }.getOrNull() ?: _prs.value }
    }

    fun open(intent: Intent) { _intents.tryEmit(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun handle(outcome: ActionOutcome) = when (outcome) {
        ActionOutcome.Done -> Unit
        is ActionOutcome.OpenSettings -> open(outcome.intent)
        is ActionOutcome.Failed -> Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
    }

    fun startAgent(goal: String): Boolean {
        if (goal.isBlank()) return false
        val spec = kotlinx.serialization.json.Json.encodeToString(com.paulneja.quorvi.agent.engine.RunSpec.serializer(), com.paulneja.quorvi.agent.engine.RunSpec(goal = goal.trim(), origin = "panel"))
        return core.submitRun(spec) != null
    }

    val jobs: StateFlow<List<JobRow>> = combine(backgroundJobs.items, chats.observeChats()) { items, all ->
        JobRows.of(items, all.associate { it.id to it.title }, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pauseJob(id: String) = viewModelScope.launch { runCatching { backgroundJobs.pause(id) } }
    fun resumeJob(id: String) = viewModelScope.launch { runCatching { backgroundJobs.resume(id) } }
    fun endJob(id: String, force: Boolean) = viewModelScope.launch { runCatching { backgroundJobs.end(id, force = force, byUser = true, tell = true) } }

    fun stopAll() = core.killAll()
    fun cancel(runId: String) = core.cancelRun(runId)
    fun runCapsule(id: String) = core.runCapsule(id)

    val runningCapsules: StateFlow<Set<String>> = ops.observeRuns(60).map { list ->
        list.filter { (it.origin.startsWith("capsule:") || it.origin.startsWith("chat:capsule-")) && runCatching { !com.paulneja.quorvi.ops.RunState.valueOf(it.state).terminal }.getOrDefault(false) }
            .map { it.origin.removePrefix("capsule:").removePrefix("chat:capsule-") }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun launchCapsule(c: CapsuleEntity) {
        val started = runCapsule(c.id) != null
        Toast.makeText(context, context.getString(if (started) com.paulneja.quorvi.R.string.capsule_started else com.paulneja.quorvi.R.string.capsule_not_started, c.name), Toast.LENGTH_SHORT).show()
    }

    fun setMode(mode: AutonomyMode) = viewModelScope.launch { policy.putSetting(PolicySettingEntity("mode", mode.name)) }
    fun setRelaxNetwork(on: Boolean) = viewModelScope.launch { policy.putSetting(PolicySettingEntity("relaxNetwork", on.toString())) }
    fun setBackend(backend: PrivBackend, on: Boolean) = viewModelScope.launch {
        if (on) Toast.makeText(context, shells.enable(backend).message(context, backend), Toast.LENGTH_LONG).show() else shells.disable(backend)
        privSource.refresh()
    }

    fun rerun(e: CiExecutionEntity) = viewModelScope.launch {
        runCatching { api.rerun(e.repo, e.runId, failedOnly = true) }
            .onSuccess { core.syncExecution(e.id) }
            .onFailure { Toast.makeText(context, it.message ?: "error", Toast.LENGTH_SHORT).show() }
    }

    fun refreshCi() = core.refresh(force = true)

    fun wifi(on: Boolean) = viewModelScope.launch { handle(actions.wifi(on)) }
    fun mobileData(on: Boolean) = viewModelScope.launch { handle(actions.mobileData(on)) }
    fun bluetooth(on: Boolean) = viewModelScope.launch { handle(actions.bluetooth(on)) }
    fun adbWifi(on: Boolean) = viewModelScope.launch { handle(actions.adbWifi(on)) }
    fun batterySaver(on: Boolean) = viewModelScope.launch { handle(actions.batterySaver(on)) }
    fun privateDns(host: String?) = viewModelScope.launch { handle(actions.privateDns(host)) }
    fun hotspot() = open(actions.hotspotSettings)
    fun vpn() = open(actions.vpnSettings)
    fun usageAccess() = open(usageSource.accessIntent)

    fun torch() = controls.toggleTorch()

    fun dnd() {
        if (controls.canSetDnd) controls.toggleDnd() else open(DeviceControls.DND_ACCESS)
    }

    fun brightness(fraction: Float) {
        if (controls.canWriteSettings) controls.setBrightness((fraction * 255).toInt()) else open(DeviceControls.writeSettings(context))
    }

    fun volume(fraction: Float) = controls.setVolume(fraction)

    fun autoRotate(on: Boolean) {
        if (controls.canWriteSettings) {
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        } else viewModelScope.launch { handle(actions.autoRotate(on)) }
    }
}
