package com.paulneja.quorvi.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class RuntimeUnavailable(message: String) : IllegalStateException(message)

interface Workspace {
    suspend fun exec(request: ExecRequest): ExecResult
    suspend fun kill(execId: String): Boolean
    suspend fun killAll(): Int
    suspend fun read(path: String, maxBytes: Int = 64 * 1024): String
    suspend fun write(path: String, content: String): Long
    suspend fun list(path: String): List<FileEntry>
    suspend fun export(path: String, displayName: String, mime: String): ExportResult
}

enum class RuntimeAvailability { UNBOUND, INSTALLING, READY, FAILED }

@Singleton
class RuntimeClient @Inject constructor(@ApplicationContext private val context: Context) : Workspace {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val service = MutableStateFlow<IShellService?>(null)
    val connected: StateFlow<IShellService?> = service.asStateFlow()
    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service.value = IShellService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.value = null
        }

        override fun onBindingDied(name: ComponentName?) {
            service.value = null
            synchronized(this@RuntimeClient) { binding = false }
            runCatching { context.unbindService(this) }
        }
    }

    fun bind(): Boolean = synchronized(this) {
        if (binding) return true
        binding = runCatching { context.bindService(Intent(context, ShellService::class.java), connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        binding
    }

    suspend fun awaitService(timeoutMs: Long = 5_000): IShellService {
        service.value?.let { return it }
        bind()
        withTimeoutOrNull(timeoutMs) { service.filterNotNull().first() }?.let { return it }
        wake()
        return withTimeoutOrNull(10_000) { service.filterNotNull().first() } ?: throw RuntimeUnavailable("The terminal did not answer")
    }

    private fun wake() {
        com.paulneja.quorvi.agent.engine.AgentLog.w(null, "terminal service not answering: waking its process")
        runCatching {
            context.startActivity(Intent(context, com.paulneja.quorvi.runtime.ShellWakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        synchronized(this) {
            if (binding) runCatching { context.unbindService(connection) }
            binding = false
        }
        bind()
    }

    suspend fun status(): RuntimeStatus = io { json.decodeFromString(RuntimeStatus.serializer(), awaitService().status()) }

    suspend fun availability(): RuntimeAvailability {
        val status = runCatching { status() }.getOrNull() ?: return RuntimeAvailability.UNBOUND
        return when {
            status.ready -> RuntimeAvailability.READY
            status.error != null -> RuntimeAvailability.FAILED
            status.stage != null -> RuntimeAvailability.INSTALLING
            else -> RuntimeAvailability.UNBOUND
        }
    }

    suspend fun install() = io { awaitService().install() }

    suspend fun ensureReady(timeoutMs: Long = 6 * 60_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var asked = false
        while (System.currentTimeMillis() < deadline) {
            when (availability()) {
                RuntimeAvailability.READY -> return true
                RuntimeAvailability.FAILED -> if (asked) return false
                else -> Unit
            }
            if (!asked) { com.paulneja.quorvi.agent.engine.AgentLog.i(null, "runtime not ready: installing"); runCatching { install() }; asked = true }
            kotlinx.coroutines.delay(2_000)
        }
        return availability() == RuntimeAvailability.READY
    }

    override suspend fun exec(request: ExecRequest): ExecResult = io {
        json.decodeFromString(ExecResult.serializer(), awaitService().exec(json.encodeToString(ExecRequest.serializer(), request)))
    }

    override suspend fun kill(execId: String): Boolean = io { awaitService().kill(execId) }

    override suspend fun killAll(): Int = io { service.value?.killAll() ?: 0 }

    override suspend fun read(path: String, maxBytes: Int): String = io { unwrap(awaitService().readText(path, maxBytes)) }

    override suspend fun write(path: String, content: String): Long = io { unwrap(awaitService().writeText(path, content)).toLong() }

    override suspend fun list(path: String): List<FileEntry> = io { json.decodeFromString(unwrap(awaitService().list(path))) }

    override suspend fun export(path: String, displayName: String, mime: String): ExportResult =
        io { json.decodeFromString(ExportResult.serializer(), unwrap(awaitService().export(path, displayName, mime))) }

    private fun unwrap(raw: String): String {
        val outcome = json.decodeFromString(Outcome.serializer(), raw)
        if (!outcome.ok) throw IllegalArgumentException(outcome.error ?: "Runtime error")
        return outcome.value.orEmpty()
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
