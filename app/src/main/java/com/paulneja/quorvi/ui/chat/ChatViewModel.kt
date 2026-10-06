package com.paulneja.quorvi.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.agent.chat.ChatEntity
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRepository
import com.paulneja.quorvi.agent.memory.MemoryEntity
import com.paulneja.quorvi.agent.memory.MemoryStore
import com.paulneja.quorvi.agent.models.AiModelInfo
import com.paulneja.quorvi.agent.models.ModelProviders
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.orchestra.Orchestra
import com.paulneja.quorvi.agent.orchestra.OrchestraStore
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunEntity
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ops.StepEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RunView(val run: RunEntity?, val steps: List<StepEntity>) {
    val state: RunState? get() = run?.state?.let { s -> runCatching { RunState.valueOf(s) }.getOrNull() }
    val busy: Boolean get() = state?.let { !it.terminal && it != RunState.WAITING_USER && it != RunState.PAUSED && it != RunState.INTERRUPTED } == true
    val current: StepEntity? get() = steps.lastOrNull { it.state == "RUNNING" }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val ops: OpsDao,
    private val store: OrchestraStore,
    private val providers: ModelProviders,
    private val memory: MemoryStore,
    private val ultraStore: com.paulneja.quorvi.agent.ultra.UltraStore,
    private val pulls: com.paulneja.quorvi.agent.ultra.UltraPullRequests,
    private val github: com.paulneja.quorvi.connections.github.GitHubApi,
    private val nexoSettings: com.paulneja.quorvi.core.settings.QuorviSettings,
) : ViewModel() {
    private val _pendingMode = MutableStateFlow(com.paulneja.quorvi.agent.chat.ChatMode.CHAT)
    val pendingMode: StateFlow<String> = _pendingMode.asStateFlow()
    private val _pendingRepo = MutableStateFlow<String?>(null)
    val pendingRepo: StateFlow<String?> = _pendingRepo.asStateFlow()
    private val _repos = MutableStateFlow<Result<List<com.paulneja.quorvi.connections.github.GhRepo>>?>(null)
    val repos: StateFlow<Result<List<com.paulneja.quorvi.connections.github.GhRepo>>?> = _repos.asStateFlow()
    val ultra: StateFlow<com.paulneja.quorvi.agent.ultra.UltraConfig> = ultraStore.config.stateIn(viewModelScope, SharingStarted.Eagerly, com.paulneja.quorvi.agent.ultra.UltraConfig())

    fun setPendingMode(mode: String) { _pendingMode.value = mode; if (mode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA) loadRepos() }

    fun pickRepo(repo: String?) = viewModelScope.launch {
        val id = _chatId.value
        if (id != null && chat.value?.ultra == true) this@ChatViewModel.repo.setRepo(id, repo) else _pendingRepo.value = repo
    }

    fun loadRepos(force: Boolean = false) {
        if (!force && _repos.value?.isSuccess == true) return
        viewModelScope.launch { _repos.value = null; _repos.value = runCatching { github.repos(2) } }
    }

    fun approvePr(messageId: String) = viewModelScope.launch { pulls.submit(messageId) }

    fun editPr(messageId: String, title: String, body: String, commitMessage: String, excluded: List<String>, done: (String?) -> Unit) =
        viewModelScope.launch { done(pulls.edit(messageId, title, body, commitMessage, excluded)) }

    fun editPrFile(messageId: String, path: String, content: String, done: (String?) -> Unit) =
        viewModelScope.launch { done(pulls.editFile(messageId, path, content)) }

    fun rejectPr(messageId: String) = viewModelScope.launch { pulls.reject(messageId) }

    private val _draftSeed = MutableStateFlow<String?>(null)
    val draftSeed: StateFlow<String?> = _draftSeed.asStateFlow()
    fun seedDraft(text: String) { _draftSeed.value = text }
    fun consumeDraft() { _draftSeed.value = null }

    fun acceptUltra(messageId: String) = viewModelScope.launch { repo.acceptUltra(messageId) }

    fun approvePlan(messageId: String) = viewModelScope.launch { repo.approvePlan(messageId) }

    fun changePlan(messageId: String, note: String) = viewModelScope.launch { repo.changePlan(messageId, note) }

    fun dismissCard(messageId: String) = viewModelScope.launch { repo.dismiss(messageId) }

    fun saveCapsule(messageId: String, onSaved: (String?) -> Unit) = viewModelScope.launch { onSaved(repo.saveCapsule(messageId)) }

    fun updateUltra(change: (com.paulneja.quorvi.agent.ultra.UltraConfig) -> com.paulneja.quorvi.agent.ultra.UltraConfig) = viewModelScope.launch { ultraStore.update(change) }

    init { viewModelScope.launch { repo.pruneOrphanRuns() } }

    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId.asStateFlow()

    val chats: StateFlow<List<ChatEntity>> = repo.chats.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chat: StateFlow<ChatEntity?> = _chatId.flatMapLatest { id -> id?.let { repo.chat(it) } ?: flowOf(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val messages: StateFlow<List<ChatMessageEntity>?> = _chatId.flatMapLatest { id -> id?.let { repo.messages(it) } ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val live: StateFlow<RunView?> = messages.flatMapLatest { list ->
        val runId = list?.lastOrNull { it.runId != null && it.role == "assistant" }?.runId
        runId?.let { runView(it) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val orchestra: StateFlow<Orchestra> = store.orchestra.stateIn(viewModelScope, SharingStarted.Eagerly, Orchestra())
    val voiceEnabled: StateFlow<Boolean> = nexoSettings.voice.map { it.enabled }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val providerList: StateFlow<List<ProviderEntity>> = providers.providers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memories: StateFlow<List<MemoryEntity>> = memory.all.map { all -> all.sortedWith(compareBy<MemoryEntity> { it.reviewed }.thenByDescending { it.updatedAt }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _models = MutableStateFlow<Map<String, Result<List<AiModelInfo>>>>(emptyMap())
    val models: StateFlow<Map<String, Result<List<AiModelInfo>>>> = _models.asStateFlow()

    fun runView(runId: String): Flow<RunView> = combine(ops.observeRun(runId), ops.observeSteps(runId)) { r, s -> RunView(r, s) }

    fun open(id: String?) {
        _chatId.value = id
        if (id == null) { _pendingMode.value = com.paulneja.quorvi.agent.chat.ChatMode.CHAT; _pendingRepo.value = null }
    }

    fun send(text: String, files: List<Uri>) = viewModelScope.launch {
        val id = repo.send(_chatId.value, text, files, _pendingMode.value, _pendingRepo.value)
        if (id != null) _chatId.value = id
    }

    fun stop() = viewModelScope.launch { _chatId.value?.let { repo.stop(it) } }

    fun retry() = viewModelScope.launch { _chatId.value?.let { repo.retry(it) } }

    fun rename(id: String, title: String) = viewModelScope.launch { repo.rename(id, title) }

    fun delete(id: String) = viewModelScope.launch {
        if (_chatId.value == id) _chatId.value = null
        repo.delete(id)
    }

    fun deleteAll() = viewModelScope.launch {
        _chatId.value = null
        repo.deleteAll()
    }

    fun updateOrchestra(change: (Orchestra) -> Orchestra) = viewModelScope.launch { store.update(change) }

    fun loadModels(providerId: String, force: Boolean = false) {
        if (!force && _models.value[providerId]?.isSuccess == true) return
        viewModelScope.launch {
            _models.value = _models.value - providerId
            _models.value = _models.value + (providerId to runCatching { providers.listModels(providerId) })
        }
    }

    fun forget(id: String) = viewModelScope.launch { memory.forget(id) }

    fun keep(m: MemoryEntity) = viewModelScope.launch { runCatching { memory.update(m.copy(reviewed = true)) } }

    fun pin(m: MemoryEntity) = viewModelScope.launch { runCatching { memory.update(m.copy(pinned = !m.pinned)) } }

    fun forgetAll() = viewModelScope.launch { memories.value.forEach { memory.forget(it.id) } }
}
