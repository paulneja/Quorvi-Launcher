package com.paulneja.quorvi.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.connections.ContainerEntity
import com.paulneja.quorvi.connections.HostEntity
import com.paulneja.quorvi.connections.Hosts
import com.paulneja.quorvi.connections.HttpWidgetEntity
import com.paulneja.quorvi.connections.HttpWidgets
import com.paulneja.quorvi.connections.http.WidgetReading
import com.paulneja.quorvi.connections.ssh.DockerAction
import com.paulneja.quorvi.connections.ssh.HostHealth
import com.paulneja.quorvi.connections.ssh.HostKey
import com.paulneja.quorvi.connections.ssh.SshAuth
import com.paulneja.quorvi.connections.ssh.SshException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Loading : KeyCheck
    data class Found(val key: HostKey) : KeyCheck
    data class Failed(val reason: String) : KeyCheck
}

class Console {
    val text = MutableStateFlow("")
    val connected = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val input = Channel<ByteArray>(Channel.UNLIMITED)
    var job: Job? = null
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val hosts: Hosts,
    private val widgets: HttpWidgets,
    ai: com.paulneja.quorvi.agent.engine.AiSwitch,
) : ViewModel() {
    val aiEnabled = ai.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hostList: StateFlow<List<HostEntity>> = hosts.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val httpList: StateFlow<List<HttpWidgetEntity>> = widgets.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val keyCheck: StateFlow<KeyCheck> = _keyCheck.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _logs = MutableStateFlow<String?>(null)
    val logs: StateFlow<String?> = _logs.asStateFlow()
    private val _test = MutableStateFlow<Result<WidgetReading>?>(null)
    val test: StateFlow<Result<WidgetReading>?> = _test.asStateFlow()
    val console = Console()

    fun host(id: String): Flow<HostEntity?> = hosts.observe(id)
    fun containers(id: String): Flow<List<ContainerEntity>> = hosts.containers(id)
    fun health(host: HostEntity): HostHealth? = hosts.health(host)
    fun publicKey(host: HostEntity): String? = hosts.publicKey(host)

    fun create(name: String, hostname: String, port: Int, user: String, auth: SshAuth, password: String?, onCreated: (String) -> Unit) = viewModelScope.launch {
        runCatching { hosts.create(name, hostname, port, user, auth, password) }
            .onSuccess { onCreated(it.id) }
            .onFailure { _messages.emit(it.message ?: "error") }
    }

    fun fetchKey(host: HostEntity) = viewModelScope.launch {
        _keyCheck.value = KeyCheck.Loading
        _keyCheck.value = runCatching { hosts.probe(host) }.fold({ KeyCheck.Found(it) }, { KeyCheck.Failed(it.message ?: it::class.simpleName.orEmpty()) })
    }

    fun trust(host: HostEntity, key: HostKey) = viewModelScope.launch {
        hosts.trust(host, key)
        _keyCheck.value = KeyCheck.Idle
        refresh(host.copy(hostKeyFingerprint = key.fingerprint))
    }

    fun refresh(host: HostEntity) = viewModelScope.launch {
        _busy.value = true
        hosts.refresh(host).onFailure { _messages.emit(describe(it)) }
        _busy.value = false
    }

    fun setAgentAccess(host: HostEntity, allowed: Boolean) = viewModelScope.launch { hosts.setAgentAccess(host, allowed) }

    fun delete(host: HostEntity, onDone: () -> Unit) = viewModelScope.launch {
        hosts.delete(host)
        onDone()
    }

    fun docker(host: HostEntity, container: ContainerEntity, action: DockerAction) = viewModelScope.launch {
        _busy.value = true
        runCatching { hosts.docker(host, container.id, action) }
            .onSuccess { _messages.emit(if (it.exitCode == 0) "${action.verb} ${container.name}: ok" else it.stderr.ifBlank { "exit ${it.exitCode}" }.take(200)) }
            .onFailure { _messages.emit(describe(it)) }
        _busy.value = false
    }

    fun loadLogs(host: HostEntity, container: ContainerEntity) = viewModelScope.launch {
        _logs.value = ""
        _logs.value = runCatching { hosts.logs(host, container.id) }.getOrElse { describe(it) }.ifEmpty { EMPTY_LOGS }
    }

    fun closeLogs() { _logs.value = null }

    fun openConsole(host: HostEntity) {
        if (console.job?.isActive == true) return
        console.error.value = null
        console.job = viewModelScope.launch {
            console.connected.value = true
            runCatching {
                hosts.shell(host, 100, 40, "dumb", console.input).collect { chunk -> console.text.value = TerminalText.append(console.text.value, chunk) }
            }.onFailure { console.error.value = describe(it) }
            console.connected.value = false
        }
    }

    fun send(text: String) {
        console.input.trySend(text.toByteArray())
    }

    fun closeConsole() {
        console.job?.cancel()
        console.job = null
        console.connected.value = false
    }

    fun saveWidget(widget: HttpWidgetEntity, authorization: String?, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { widgets.save(widget, authorization) }
            .onSuccess {
                widgets.refresh(widget)
                onDone()
            }
            .onFailure { _messages.emit(it.message ?: "error") }
    }

    fun testWidget(widget: HttpWidgetEntity) = viewModelScope.launch {
        _test.value = null
        _test.value = widgets.test(widget)
    }

    fun clearTest() { _test.value = null }

    fun hasAuth(id: String) = widgets.hasAuth(id)

    suspend fun widget(id: String) = widgets.get(id)

    fun deleteWidget(id: String, onDone: () -> Unit) = viewModelScope.launch {
        widgets.delete(id)
        onDone()
    }

    fun triggerWidget(widget: HttpWidgetEntity) = viewModelScope.launch {
        widgets.trigger(widget).onSuccess { _messages.emit("HTTP ${it.status}") }.onFailure { _messages.emit(it.message ?: "error") }
    }

    private fun describe(t: Throwable): String = when (t) {
        is SshException.HostKeyChanged -> "The host key CHANGED (expected ${t.expected}, got ${t.actual.fingerprint}). Nexo refused to connect."
        is SshException.UnknownHostKey -> "Verify the host key first."
        is SshException.AuthFailed -> "Authentication failed: ${t.message}"
        is SshException.MissingCredential -> "No key or password stored for this host."
        is SshException.Unreachable -> "Unreachable: ${t.message}"
        else -> t.message ?: t::class.simpleName.orEmpty()
    }

    override fun onCleared() {
        closeConsole()
        super.onCleared()
    }
}

object TerminalText {
    private val csi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val osc = Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")
    private const val LIMIT = 64 * 1024

    fun append(current: String, chunk: String): String {
        val clean = chunk.replace(osc, "").replace(csi, "").replace("\u001B", "")
        val out = StringBuilder(current)
        var lineStart = out.lastIndexOf("\n") + 1
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            when {
                c == '\r' && clean.getOrNull(i + 1) == '\n' -> { out.append('\n'); lineStart = out.length; i++ }
                c == '\r' -> out.setLength(lineStart)
                c == '\b' -> if (out.length > lineStart) out.setLength(out.length - 1)
                c == '\n' -> { out.append('\n'); lineStart = out.length }
                c == '\u0007' -> Unit
                else -> out.append(c)
            }
            i++
        }
        return if (out.length > LIMIT) out.substring(out.length - LIMIT) else out.toString()
    }
}

internal const val EMPTY_LOGS = "\u0000empty"
