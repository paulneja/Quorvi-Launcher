package com.paulneja.quorvi.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.connections.github.ActionsLog
import com.paulneja.quorvi.connections.github.GhStep
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubSession
import com.paulneja.quorvi.connections.github.LogSection
import com.paulneja.quorvi.ops.ArtifactEntity
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.CiJobEntity
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.EventEntity
import com.paulneja.quorvi.ops.GitHubSync
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunEntity
import com.paulneja.quorvi.ops.StepEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class JobView(val entity: CiJobEntity, val steps: List<GhStep>)

sealed interface LogState {
    data object Idle : LogState
    data object Loading : LogState
    data class Ready(val jobName: String, val sections: List<LogSection>) : LogState
    data class Failed(val reason: String) : LogState
}

@HiltViewModel
class OperationsViewModel @Inject constructor(
    private val dao: OpsDao,
    private val api: GitHubApi,
    private val session: GitHubSession,
    private val core: CoreClient,
    private val runtime: com.paulneja.quorvi.runtime.RuntimeClient,
    private val settings: com.paulneja.quorvi.core.settings.QuorviSettings,
    private val services: com.paulneja.quorvi.connections.services.ServicesRepo,
    ai: com.paulneja.quorvi.agent.engine.AiSwitch,
) : ViewModel() {
    val aiEnabled: StateFlow<Boolean> = ai.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val _runtime = MutableStateFlow<com.paulneja.quorvi.runtime.RuntimeAvailability?>(null)
    val runtimeState: StateFlow<com.paulneja.quorvi.runtime.RuntimeAvailability?> = _runtime.asStateFlow()
    val indicator = settings.agentIndicator.stateIn(viewModelScope, SharingStarted.Eagerly, com.paulneja.quorvi.core.settings.AgentIndicator.COMPACT)

    fun checkRuntime() = viewModelScope.launch { _runtime.value = runtime.availability() }

    fun installRuntime() = viewModelScope.launch {
        runCatching { runtime.install() }
        _runtime.value = runtime.availability()
    }

    fun cycleIndicator() = viewModelScope.launch {
        val all = com.paulneja.quorvi.core.settings.AgentIndicator.entries
        settings.setAgentIndicator(all[(indicator.value.ordinal + 1) % all.size])
    }

    fun answerRun(id: String, text: String) = core.answerRun(id, text)

    fun killAll() = core.killAll()

    suspend fun question(id: String): String? = dao.lastEvent(id, "question")?.message

    fun diagnose(e: CiExecutionEntity, goal: String): String? =
        core.submitRun(json.encodeToString(com.paulneja.quorvi.agent.engine.RunSpec.serializer(), com.paulneja.quorvi.agent.engine.RunSpec(goal = goal, origin = "ci", ciExecutionId = e.id)))

    private val json = Json { ignoreUnknownKeys = true }
    val signedIn: Boolean get() = session.tokens.signedIn()

    val runs: StateFlow<List<RunEntity>> = kotlinx.coroutines.flow.combine(dao.observeRuns(), dao.observeRunsWithSteps()) { all, worked -> val ids = worked.toSet(); all.filter { !it.origin.startsWith("chat:") || it.id in ids } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ci: StateFlow<List<CiExecutionEntity>> = dao.observeCi().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watched = dao.observeWatched().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _log = MutableStateFlow<LogState>(LogState.Idle)
    val log: StateFlow<LogState> = _log.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun execution(id: String): Flow<CiExecutionEntity?> = dao.observeCiExecution(id)

    fun jobs(id: String): Flow<List<CiJobEntity>> = dao.observeCiJobs(id)

    fun steps(job: CiJobEntity): List<GhStep> = runCatching { json.decodeFromString(ListSerializer(GhStep.serializer()), job.steps) }.getOrDefault(emptyList())

    fun run(id: String): Flow<RunEntity?> = dao.observeRun(id)
    fun runSteps(id: String): Flow<List<StepEntity>> = dao.observeSteps(id)
    fun events(id: String): Flow<List<EventEntity>> = dao.observeEvents(id)
    fun artifacts(id: String): Flow<List<ArtifactEntity>> = dao.observeArtifacts(id)

    fun refresh() = core.refresh(true)

    fun openExecution(id: String) = core.syncExecution(id)

    fun loadLog(execution: CiExecutionEntity, job: CiJobEntity) = viewModelScope.launch {
        _log.value = LogState.Loading
        _log.value = runCatching {
            val raw = if (execution.provider == "gitlab") services.gitLabTrace(execution.id, job.id) else api.jobLog(execution.repo, GitHubSync.jobIdOf(job.id) ?: error("unknown job"))
            LogState.Ready(job.name, ActionsLog.sections(raw))
        }.getOrElse { LogState.Failed(it.message ?: "error") }
    }

    fun closeLog() { _log.value = LogState.Idle }

    fun rerun(execution: CiExecutionEntity, failedOnly: Boolean, done: String, failed: String) = viewModelScope.launch {
        runCatching { if (execution.provider == "gitlab") services.gitLabRetry(execution.id) else api.rerun(execution.repo, execution.runId, failedOnly) }
            .onSuccess { _messages.tryEmit(done); core.syncExecution(execution.id) }
            .onFailure { _messages.tryEmit(failed.replace("%1\$s", it.message.orEmpty())) }
    }

    fun cancelRun(id: String) = core.cancelRun(id)
    fun pauseRun(id: String) = core.pauseRun(id)
    fun resumeRun(id: String) = core.resumeRun(id)
}
