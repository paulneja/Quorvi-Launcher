package com.paulneja.quorvi.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.agent.models.AiModelInfo
import com.paulneja.quorvi.agent.models.CopilotStatus
import com.paulneja.quorvi.agent.models.ModelProviders
import com.paulneja.quorvi.agent.models.ModelRole
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.models.ProviderKind
import com.paulneja.quorvi.connections.github.GhRepo
import com.paulneja.quorvi.connections.github.GitHubAccount
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.SignInState
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.WatchedRepoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Probe {
    data object Idle : Probe
    data object Working : Probe
    data class Ok(val detail: String, val models: List<AiModelInfo>) : Probe
    data class Failed(val reason: String) : Probe
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val github: GitHubAccount,
    private val api: GitHubApi,
    private val dao: OpsDao,
    private val providers: ModelProviders,
    private val core: CoreClient,
) : ViewModel() {
    val signIn: StateFlow<SignInState> = github.state
    val login: StateFlow<String?> = github.login
    val available: Boolean get() = github.available
    val watched: StateFlow<List<WatchedRepoEntity>> = dao.observeWatched().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val modelProviders: StateFlow<List<ProviderEntity>> = providers.providers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _repos = MutableStateFlow<List<GhRepo>?>(null)
    val repos: StateFlow<List<GhRepo>?> = _repos.asStateFlow()
    private val _copilot = MutableStateFlow<Result<CopilotStatus?>?>(null)
    val copilot: StateFlow<Result<CopilotStatus?>?> = _copilot.asStateFlow()
    private val _probe = MutableStateFlow<Probe>(Probe.Idle)
    val probe: StateFlow<Probe> = _probe.asStateFlow()

    init {
        viewModelScope.launch { login.collect { if (it != null) refreshCopilot() } }
    }

    fun signInStart() = github.start()
    fun signInCancel() = github.cancel()
    fun signOut() = github.signOut()

    fun loadRepos() = viewModelScope.launch {
        _repos.value = runCatching { api.repos() }.getOrElse { emptyList() }
    }

    fun toggleWatch(repo: String, watch: Boolean) = viewModelScope.launch {
        if (watch) dao.watch(WatchedRepoEntity(repo, System.currentTimeMillis())) else {
            dao.unwatch(repo)
            dao.forgetCi(repo)
        }
        core.refresh(true)
    }

    fun refreshCopilot() = viewModelScope.launch { _copilot.value = runCatching { providers.copilotStatus() } }

    fun probe(kind: ProviderKind, url: String, secret: String) = viewModelScope.launch {
        _probe.value = Probe.Working
        _probe.value = runCatching {
            val temp = ProviderEntity("probe", kind.name, "probe", url, "", createdAt = 0)
            if (kind == ProviderKind.BRIDGE) {
                val health = providers.bridgeHealth(url)
                providers.save(temp, secret)
                val models = providers.client(temp)?.listModels().orEmpty()
                providers.remove("probe")
                Probe.Ok(health, models)
            } else {
                if (kind != ProviderKind.COPILOT) providers.save(temp, secret.ifBlank { null })
                val models = providers.client(temp)?.listModels().orEmpty()
                if (kind != ProviderKind.COPILOT) providers.remove("probe")
                Probe.Ok(kind.name.lowercase(), models)
            }
        }.getOrElse { Probe.Failed(it.message ?: it::class.simpleName.orEmpty()) }
    }

    fun resetProbe() { _probe.value = Probe.Idle }

    fun addProvider(kind: ProviderKind, name: String, url: String, model: String, secret: String, roles: Set<ModelRole>) = viewModelScope.launch {
        val entity = ProviderEntity(UUID.randomUUID().toString(), kind.name, name.ifBlank { kind.name }, url, model, roles.joinToString(",") { it.name }, System.currentTimeMillis())
        _probe.value = runCatching { providers.save(entity, secret.ifBlank { null }) }.fold({ Probe.Idle }, { Probe.Failed(it.message ?: it::class.simpleName.orEmpty()) })
    }

    fun removeProvider(id: String) = viewModelScope.launch { providers.remove(id) }

    private val _models = MutableStateFlow<Map<String, Result<List<com.paulneja.quorvi.agent.models.AiModelInfo>>>>(emptyMap())
    val models: StateFlow<Map<String, Result<List<com.paulneja.quorvi.agent.models.AiModelInfo>>>> = _models.asStateFlow()

    fun loadModels(id: String) = viewModelScope.launch {
        _models.value = _models.value - id
        _models.value = _models.value + (id to runCatching { providers.listModels(id) })
    }

    fun setModel(id: String, model: String) = viewModelScope.launch { providers.setModel(id, model) }
}
