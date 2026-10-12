package com.paulneja.quorvi.control

import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.runtime.ExecRequest
import com.paulneja.quorvi.runtime.RuntimeClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class RootLevel { NONE, READ, CONTROL, EXEC, MODIFY, EXPERIMENTAL;
    fun allows(required: RootLevel) = ordinal >= required.ordinal && this != NONE
}

enum class PrivBackend { ROOT, SHIZUKU, ADB_TCP }

data class PrivResult(val backend: String, val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean)

class PrivUnavailable(message: String) : Exception(message)

@Singleton
class PrivilegedShells @Inject constructor(
    private val settings: QuorviSettings,
    private val runtime: RuntimeClient,
) {
    private val _degraded = MutableStateFlow<Set<PrivBackend>>(emptySet())

    val degraded: StateFlow<Set<PrivBackend>> = _degraded.asStateFlow()

    suspend fun config() = settings.privileged()

    suspend fun available(backend: PrivBackend): Boolean = when (backend) {
        PrivBackend.ROOT -> RootShell.available()
        PrivBackend.SHIZUKU -> ShizukuShell.available()
        PrivBackend.ADB_TCP -> runtime.availability() == com.paulneja.quorvi.runtime.RuntimeAvailability.READY
    }

    suspend fun verifyOrDegrade() {
        val cfg = config()
        if (PrivBackend.SHIZUKU in cfg.enabled) ShizukuShell.awaitBinder(3_000)
        val lost = cfg.enabled.filterNot { available(it) }.toSet()
        _degraded.value = lost
        settings.setRootDegraded(lost.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name })
    }

    suspend fun enable(backend: PrivBackend): EnableResult {
        val result = when (backend) {
            PrivBackend.ROOT -> if (RootShell.grant()) EnableResult.Ready else if (RootShell.available()) EnableResult.Denied else EnableResult.Missing
            PrivBackend.SHIZUKU -> ShizukuShell.grant()
            PrivBackend.ADB_TCP -> EnableResult.Ready
        }
        settings.setPrivBackend(backend, true)
        if (result == EnableResult.Ready) _degraded.value = _degraded.value - backend
        settings.setRootDegraded(_degraded.value.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name })
        return result
    }

    suspend fun disable(backend: PrivBackend) {
        settings.setPrivBackend(backend, false)
        _degraded.value = _degraded.value - backend
    }

    suspend fun clearDegraded() { _degraded.value = emptySet(); settings.setRootDegraded(null) }

    suspend fun exec(backend: PrivBackend, command: String, level: RootLevel = RootLevel.EXEC, timeoutSec: Long = 120): PrivResult {
        val cfg = config()
        if (backend !in cfg.enabled) throw PrivUnavailable("${backend.name} is turned off in settings")
        val start = System.currentTimeMillis()
        com.paulneja.quorvi.agent.engine.AgentLog.i(null, "priv ${backend.name} start: ${com.paulneja.quorvi.agent.engine.AgentLog.clean(command)}")
        return runCatching { execOn(backend, cfg, command, level, timeoutSec) }
            .onSuccess { com.paulneja.quorvi.agent.engine.AgentLog.i(null, "priv ${backend.name} exit=${it.exitCode} timedOut=${it.timedOut} in ${System.currentTimeMillis() - start} ms") }
            .onFailure { com.paulneja.quorvi.agent.engine.AgentLog.w(null, "priv ${backend.name} failed after ${System.currentTimeMillis() - start} ms", it) }
            .getOrThrow()
    }

    private suspend fun execOn(backend: PrivBackend, cfg: com.paulneja.quorvi.core.settings.PrivilegedConfig, command: String, level: RootLevel, timeoutSec: Long): PrivResult {
        return when (backend) {
            PrivBackend.ROOT -> {
                if (!cfg.rootLevel.allows(level)) throw PrivUnavailable("Root is limited to ${cfg.rootLevel} (you asked for $level)")
                RootShell.exec(command, level, cfg.preHook, cfg.postHook, timeoutSec)
            }
            PrivBackend.SHIZUKU -> ShizukuShell.exec(command, timeoutSec)
            PrivBackend.ADB_TCP -> execViaRuntime(cfg.adbEndpoint, command, timeoutSec)
        }
    }

    private suspend fun execViaRuntime(endpoint: String, command: String, timeoutSec: Long): PrivResult {
        require(endpoint.matches(Regex("^[A-Za-z0-9_.:\\[\\]-]+:\\d{1,5}$"))) { "Set a host:port for wireless debugging first" }
        val script = "adb connect $endpoint >/dev/null 2>&1; adb -s $endpoint shell " + shellQuote(command)
        val r = runtime.exec(ExecRequest(argv = listOf("bash", "-lc", script), timeoutMs = timeoutSec * 1000))
        return PrivResult("adb-tcp", r.exitCode, r.stdout, r.stderr, r.timedOut)
    }

    companion object {
        fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}

enum class EnableResult { Ready, Denied, NotRunning, Missing }

object RootShell {
    fun available(): Boolean = runCatching {
        ProcessBuilder("sh", "-c", "command -v su").start().let { it.waitFor(3, TimeUnit.SECONDS) && it.exitValue() == 0 }
    }.getOrDefault(false)

    suspend fun grant(): Boolean = withContext(Dispatchers.IO) {
        val r = runProcess("root", arrayOf("su", "-c", "id -u"), 30)
        r.exitCode == 0 && r.stdout.trim() == "0"
    }

    suspend fun exec(command: String, level: RootLevel, preHook: String, postHook: String, timeoutSec: Long, globalNamespace: Boolean = false): PrivResult = withContext(Dispatchers.IO) {
        val suArgs = if (globalNamespace || level.ordinal >= RootLevel.MODIFY.ordinal) arrayOf("su", "--mount-master", "-c") else arrayOf("su", "-c")
        val script = buildString {
            if (preHook.isNotBlank()) append(preHook).append('\n')
            append(command)
            if (postHook.isNotBlank()) append('\n').append(postHook)
        }
        runProcess("root", suArgs + script, timeoutSec)
    }
}

object ShizukuShell {
    fun running(): Boolean = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)

    fun available(): Boolean = runCatching {
        rikka.shizuku.Shizuku.pingBinder() && !rikka.shizuku.Shizuku.isPreV11() &&
            rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun awaitBinder(timeoutMs: Long): Boolean {
        if (running()) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val listener = object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        rikka.shizuku.Shizuku.removeBinderReceivedListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(true))
                    }
                }
                rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(listener)
                cont.invokeOnCancellation { rikka.shizuku.Shizuku.removeBinderReceivedListener(listener) }
            }
        } ?: false
    }

    suspend fun grant(): EnableResult {
        if (!awaitBinder(3_000)) return EnableResult.NotRunning
        if (available()) return EnableResult.Ready
        if (rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale()) return EnableResult.Denied
        val granted = kotlinx.coroutines.withTimeoutOrNull(60_000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val listener = object : rikka.shizuku.Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != REQUEST) return
                        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED))
                    }
                }
                rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
                cont.invokeOnCancellation { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) }
                rikka.shizuku.Shizuku.requestPermission(REQUEST)
            }
        } ?: false
        return if (granted) EnableResult.Ready else EnableResult.Denied
    }

    private const val REQUEST = 7301

    suspend fun pipe(argv: Array<String>, input: java.io.File, timeoutSec: Long): PrivResult = withContext(Dispatchers.IO) {
        val shizuku = Class.forName("rikka.shizuku.Shizuku")
        val newProcess = shizuku.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).apply { isAccessible = true }
        val process = newProcess.invoke(null, argv, null, null) as Process
        Thread { runCatching { process.outputStream.use { out -> input.inputStream().use { it.copyTo(out) } } } }.apply { isDaemon = true; start() }
        collect("shizuku", process, timeoutSec)
    }

    suspend fun exec(command: String, timeoutSec: Long): PrivResult = withContext(Dispatchers.IO) {
        val shizuku = Class.forName("rikka.shizuku.Shizuku")
        val newProcess = shizuku.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).apply { isAccessible = true }
        val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        collect("shizuku", process, timeoutSec)
    }
}

private fun runProcess(backend: String, argv: Array<String>, timeoutSec: Long): PrivResult =
    try {
        collect(backend, ProcessBuilder(*argv).redirectErrorStream(false).start(), timeoutSec)
    } catch (e: Exception) {
        PrivResult(backend, -1, "", e.message ?: e.javaClass.simpleName, false)
    }

private fun collect(backend: String, process: Process, timeoutSec: Long): PrivResult {
    val out = StringBuilder()
    val err = StringBuilder()
    val tOut = Thread { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }.apply { isDaemon = true; start() }
    val tErr = Thread { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }.apply { isDaemon = true; start() }
    var code = -1
    val waiter = Thread { code = runCatching { process.waitFor() }.getOrDefault(-1) }.apply { isDaemon = true; start() }
    waiter.join(timeoutSec * 1000)
    val finished = !waiter.isAlive
    if (!finished) process.destroyForcibly()
    tOut.join(2000); tErr.join(2000)
    return PrivResult(backend, if (finished) code else -1, out.toString().take(64_000), err.toString().take(16_000), !finished)
}
