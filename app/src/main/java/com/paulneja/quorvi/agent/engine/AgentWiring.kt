package com.paulneja.quorvi.agent.engine

import com.paulneja.quorvi.agent.models.ModelProviders
import com.paulneja.quorvi.agent.models.ModelRole
import com.paulneja.quorvi.agent.tools.ArtifactExport
import com.paulneja.quorvi.agent.tools.CiFailureLogs
import com.paulneja.quorvi.agent.tools.FsList
import com.paulneja.quorvi.agent.tools.FsRead
import com.paulneja.quorvi.agent.tools.FsWrite
import com.paulneja.quorvi.agent.tools.ShellExec
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.WebFetch
import com.paulneja.quorvi.agent.tools.WebSearch
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubSession
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.runtime.RuntimeAvailability
import com.paulneja.quorvi.runtime.RuntimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class ProviderModels @Inject constructor(private val providers: ModelProviders) : ModelSource {
    override suspend fun forRole(role: ModelRole): ModelChoice? =
        providers.forRole(role)?.let { (entity, client) -> ModelChoice(entity.name, entity.model, client) }

    override suspend fun choice(ref: com.paulneja.quorvi.agent.orchestra.ModelRef): ModelChoice? =
        providers.byId(ref.providerId)?.let { (entity, client) -> ModelChoice(entity.name, ref.model, client) }
}

class RuntimeToolBox @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val settings: com.paulneja.quorvi.core.settings.QuorviSettings,
    private val runtime: RuntimeClient,
    private val http: Http,
    private val dao: OpsDao,
    private val github: GitHubApi,
    private val session: GitHubSession,
    private val hosts: com.paulneja.quorvi.connections.Hosts,
    private val connections: com.paulneja.quorvi.connections.ConnectionsDao,
    private val memory: com.paulneja.quorvi.agent.memory.MemoryStore,
    private val services: com.paulneja.quorvi.connections.services.ServicesRepo,
    private val servicesDao: com.paulneja.quorvi.connections.services.ServicesDao,
    private val privileged: com.paulneja.quorvi.control.PrivilegedShells,
    private val limitsStore: LimitsStore,
    private val jobs: com.paulneja.quorvi.agent.jobs.BackgroundJobs,
) : ToolBox {
    private suspend fun homePlace(): Triple<String, Double, Double>? = runCatching {
        val p = settings.weatherPlace.first()?.split('\u0001')?.takeIf { it.size == 3 } ?: return@runCatching null
        val name = settings.weatherCache.first()?.split('\u0001')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: p[0]
        Triple(name, p[1].toDouble(), p[2].toDouble())
    }.getOrNull()

    override suspend fun whereabouts(): String? = runCatching {
        val place = settings.weatherPlace.first()?.split('\u0001')?.takeIf { it.size == 3 }
        val cachedName = settings.weatherCache.first()?.split('\u0001')?.firstOrNull()?.trim()
        val name = cachedName?.takeIf { it.isNotEmpty() } ?: place?.get(0)?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val coords = place?.let { p -> p[1].toDoubleOrNull()?.let { lat -> p[2].toDoubleOrNull()?.let { lon -> lat to lon } } }
        if (coords == null) name else "$name. For weather or what to wear, call weather_forecast once; do not use other weather sites."
    }.getOrNull()

    override suspend fun memories(goal: String): List<String> = memory.contextFor(goal).map { "[${it.kind.lowercase()}, from ${it.source}] ${it.text}" }

    override suspend fun allTools(): List<Tool> = buildTools(includeCi = session.tokens.signedIn())

    override suspend fun limits(): AgentLimits = limitsStore.current()

    override suspend fun jobTools(chatId: String): List<Tool> =
        if (runtime.availability() == RuntimeAvailability.READY) com.paulneja.quorvi.agent.jobs.jobTools(jobs, chatId) else emptyList()

    private suspend fun buildTools(includeCi: Boolean): List<Tool> {
        val limits = limitsStore.current()
        val tools = mutableListOf<Tool>(FsWrite(runtime), FsRead(runtime), FsList(runtime), ShellExec(runtime, limits.commandSeconds), ArtifactExport(runtime, dao), WebSearch(http), WebFetch(http), com.paulneja.quorvi.agent.tools.WeatherForecast(http) { homePlace() }, com.paulneja.quorvi.agent.tools.MemorySearch(memory), com.paulneja.quorvi.agent.tools.MemoryPropose(memory))
        connections.hosts().filter { it.agentAccess && it.hostKeyFingerprint != null }.let { if (it.isNotEmpty()) tools += com.paulneja.quorvi.agent.tools.SshExec(hosts, it, limits.sshSeconds) }
        services.workers().let { if (it.isNotEmpty()) tools += com.paulneja.quorvi.agent.tools.RemoteRun(services, it) }
        services.discordChannels().let { if (it.isNotEmpty()) tools += com.paulneja.quorvi.agent.tools.DiscordPost(services, it) }
        if (servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.HOME_ASSISTANT.name).isNotEmpty()) tools += com.paulneja.quorvi.agent.tools.HaCallService(services)
        if (includeCi) {
            tools += CiFailureLogs(github)

            tools += com.paulneja.quorvi.agent.tools.githubTools(github)
        }

        tools += com.paulneja.quorvi.agent.tools.AndroidLaunch(appContext)
        tools += com.paulneja.quorvi.agent.tools.AndroidListApps(appContext)

        privileged.verifyOrDegrade()
        val backends = privileged.config().enabled
        if (backends.isNotEmpty()) {
            tools += com.paulneja.quorvi.agent.tools.PrivShell(privileged, backends)
            if (com.paulneja.quorvi.control.PrivBackend.SHIZUKU in backends) tools += com.paulneja.quorvi.agent.tools.InstallApk(appContext)
            if (com.paulneja.quorvi.control.PrivBackend.ROOT in backends) tools += com.paulneja.quorvi.agent.tools.RootPowerHook(privileged)
        }
        return tools
    }

    override suspend fun ultraWorkspace(chatId: String): com.paulneja.quorvi.agent.ultra.UltraWorkspace? {
        if (!runtime.ensureReady()) return null
        return com.paulneja.quorvi.agent.ultra.UltraWorkspace(runtime, { session.tokens.valid() }, chatId)
    }

    override fun ultraGitHub(): com.paulneja.quorvi.agent.ultra.GitHubReads = object : com.paulneja.quorvi.agent.ultra.GitHubReads {
        override suspend fun issues(repo: String, state: String) = github.issuesText(repo, state)
        override suspend fun issue(repo: String, number: Int) = github.issueText(repo, number)
        override suspend fun pulls(repo: String, state: String) = github.pullsText(repo, state)
    }

    override suspend fun chatTools(): List<Tool> {
        val ready = runtime.availability() == RuntimeAvailability.READY
        val runtimeTools = setOf("fs.write", "fs.read", "fs.list", "shell.exec", "artifact.export")
        return buildTools(includeCi = session.tokens.signedIn()).filter { ready || it.name !in runtimeTools } + com.paulneja.quorvi.agent.tools.MemorySave(memory)
    }

    override suspend fun available(kind: GoalKind, ciExecutionId: String?): Capability {
        when (runtime.availability()) {
            RuntimeAvailability.INSTALLING -> return Capability.Missing("The terminal is still preparing its Unix environment.")
            RuntimeAvailability.FAILED, RuntimeAvailability.UNBOUND -> {
                runCatching { runtime.install() }
                if (runtime.availability() != RuntimeAvailability.READY) return Capability.Missing("Quorvi Shell is not ready yet. Open it once to finish the setup.")
            }
            RuntimeAvailability.READY -> Unit
        }
        if (kind == GoalKind.CI_DIAGNOSIS && !session.tokens.signedIn()) return Capability.Missing("Sign in to GitHub in Accounts to read the workflow logs.")
        return Capability.Ready(buildTools(includeCi = kind == GoalKind.CI_DIAGNOSIS))
    }

    override suspend fun killProcesses(runId: String?) {
        runCatching { runtime.killAll() }
    }

    override suspend fun read(path: String): String? = runCatching { runtime.read(path) }.getOrNull()
}

@Singleton
class Agents @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    private val dao: OpsDao,
    models: ProviderModels,
    toolbox: RuntimeToolBox,
    scope: CoroutineScope,
    private val store: com.paulneja.quorvi.agent.policy.RoomPolicyStore,
    private val device: com.paulneja.quorvi.agent.policy.AndroidDeviceContext,
    chats: com.paulneja.quorvi.agent.chat.RoomChatLog,
    orchestra: com.paulneja.quorvi.agent.orchestra.OrchestraStore,
    ultra: com.paulneja.quorvi.agent.ultra.UltraStore,
    limits: LimitsStore,
    val ai: AiSwitch,
) {
    val engine = AgentEngine(dao, models, toolbox, scope, com.paulneja.quorvi.agent.policy.PolicyService(store, device), chats = chats, orchestra = orchestra::current, ultra = ultra::current, texts = AndroidEngineTexts(context), tuning = limits::current, aiOn = ai::now)

    suspend fun resumeWaiting() {
        val waiting = dao.runsIn(com.paulneja.quorvi.ops.RunState.WAITING_CAPABILITY.name).filter { it.error?.startsWith(AgentEngine.WAIT_PREFIX) == true }
        if (waiting.isEmpty()) return
        val config = store.config()
        val state = device.state()
        waiting.forEach { run ->
            val reason = run.error.orEmpty()
            val ready = when {
                reason.contains("battery") -> state.charging || state.battery >= config.minBattery
                reason.contains("unmetered") -> state.unmetered || !config.networkOnlyUnmetered
                else -> true
            }
            if (ready) engine.resume(run.id)
        }
    }
}
