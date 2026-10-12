package com.paulneja.quorvi.runtime

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExecRequest(
    val argv: List<String>,
    val id: String? = null,
    val cwd: String? = null,
    val env: Map<String, String> = emptyMap(),
    val stdin: String? = null,
    val timeoutMs: Long = 60_000,
    val maxOutputBytes: Int = 256 * 1024,
)

@Serializable
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val truncated: Boolean,
    val durationMs: Long,
)

@Serializable
data class RuntimeStatus(val ready: Boolean, val abi: String?, val supported: Boolean, val stage: String?, val error: String?, val prefix: String, val home: String)

@Serializable
data class ExportResult(val uri: String, val displayName: String, val size: Long, val sha256: String, val rereadSha256: String, val magic: String)

@Serializable
data class FileEntry(val name: String, val dir: Boolean, val size: Long, val modified: Long)

@Serializable
data class Outcome(val ok: Boolean, val error: String? = null, val value: String? = null)

class ShellService : Service() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var bootstrap: Bootstrap
    private val stage = AtomicReference<String?>(null)
    private val error = AtomicReference<String?>(null)
    @Volatile private var installing: Thread? = null
    private val running = java.util.concurrent.ConcurrentHashMap<String, Process>()

    override fun onCreate() {
        super.onCreate()
        bootstrap = Bootstrap(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IShellService.Stub() {
        override fun version(): Int = VERSION

        override fun status(): String = json.encodeToString(
            RuntimeStatus(
                ready = bootstrap.ready,
                abi = Build.SUPPORTED_ABIS.firstOrNull(),
                supported = bootstrap.asset() != null,
                stage = stage.get(),
                error = error.get(),
                prefix = bootstrap.prefix.absolutePath,
                home = bootstrap.home.absolutePath,
            ),
        )

        override fun install() {
            synchronized(this@ShellService) {
                if (bootstrap.ready || installing?.isAlive == true) return
                error.set(null)
                installing = thread(name = "nexo-bootstrap") {
                    runCatching { bootstrap.install { stage.set(it) } }
                        .onFailure { error.set(it.message ?: it.javaClass.simpleName); stage.set("failed") }
                }
            }
        }

        override fun exec(requestJson: String): String {
            val result = runCatching { run(json.decodeFromString(ExecRequest.serializer(), requestJson)) }
                .getOrElse { ExecResult(-1, "", it.message ?: it.javaClass.simpleName, timedOut = false, truncated = false, durationMs = 0) }
            return json.encodeToString(ExecResult.serializer(), result)
        }

        override fun kill(execId: String): Boolean = running.remove(execId)?.let { stop(it); true } ?: false

        override fun killAll(): Int {
            val all = running.keys.toList()
            all.forEach { id -> running.remove(id)?.let(::stop) }
            return all.size
        }

        override fun export(path: String, displayName: String, mime: String): String = outcome {
            json.encodeToString(ExportResult.serializer(), exportToDownloads(resolve(path), displayName, mime))
        }

        override fun readText(path: String, maxBytes: Int): String = outcome {
            val file = resolve(path)
            file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes.coerceIn(0, MAX_READ))
                val n = input.read(buffer).coerceAtLeast(0)
                String(buffer, 0, n)
            }
        }

        override fun writeText(path: String, content: String): String = outcome {
            val file = resolve(path)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, ".${file.name}.tmp")
            temp.writeText(content)
            check(temp.renameTo(file)) { "Could not write ${file.name}" }
            file.length().toString()
        }

        override fun list(path: String): String = outcome {
            val dir = resolve(path)
            val entries = dir.listFiles().orEmpty().sortedBy { it.name }.map { FileEntry(it.name, it.isDirectory, it.length(), it.lastModified()) }
            json.encodeToString(entries)
        }
    }

    private inline fun outcome(block: () -> String): String =
        json.encodeToString(Outcome.serializer(), runCatching { Outcome(ok = true, value = block()) }.getOrElse { Outcome(ok = false, error = it.message ?: it.javaClass.simpleName) })

    private fun resolve(path: String): File {
        val home = bootstrap.home.canonicalFile
        val file = (if (path.startsWith("/")) File(path) else File(home, path)).canonicalFile
        require(file.path == home.path || file.path.startsWith(home.path + "/")) { "Path outside the workspace: $path" }
        return file
    }

    private fun run(request: ExecRequest): ExecResult {
        require(request.argv.isNotEmpty()) { "argv is empty" }
        check(bootstrap.ready) { "Runtime is not installed" }
        val started = System.nanoTime()
        val cwd = request.cwd?.let(::resolve) ?: bootstrap.home
        cwd.mkdirs()
        val process = ProcessBuilder(resolveExecutable(request.argv.first()), *request.argv.drop(1).toTypedArray())
            .directory(cwd)
            .apply {
                environment().clear()
                environment().putAll(Environment.of(bootstrap))
                environment().putAll(request.env)
            }
            .start()
        request.id?.let { running[it] = process }
        val stdout = Capture(process.inputStream, request.maxOutputBytes)
        val stderr = Capture(process.errorStream, request.maxOutputBytes)
        thread(name = "nexo-stdin") {
            runCatching { process.outputStream.use { out -> request.stdin?.let { out.write(it.toByteArray()) } } }
        }
        val finished = process.waitFor(request.timeoutMs.coerceIn(100, MAX_TIMEOUT), TimeUnit.MILLISECONDS)
        if (!finished) stop(process)
        request.id?.let { running.remove(it) }
        stdout.join(); stderr.join()
        return ExecResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.text(),
            stderr = stderr.text(),
            timedOut = !finished,
            truncated = stdout.truncated || stderr.truncated,
            durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        )
    }

    private fun stop(process: Process) {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
    }

    private fun resolveExecutable(name: String): String =
        if (name.contains('/')) name else File(bootstrap.prefix, "bin/$name").takeIf { it.exists() }?.absolutePath ?: name

    private fun exportToDownloads(file: File, displayName: String, mime: String): ExportResult {
        require(file.isFile) { "Not a file: ${file.name}" }
        val name = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { file.name }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore refused the file")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        size += n
                    }
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }

        val kept = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
        val reread = MessageDigest.getInstance("SHA-256")
        val head = ByteArray(8)
        var headSize = 0
        resolver.openInputStream(uri)!!.use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (headSize < head.size) {
                    val take = minOf(head.size - headSize, n)
                    System.arraycopy(buffer, 0, head, headSize, take)
                    headSize += take
                }
                reread.update(buffer, 0, n)
            }
        }
        return ExportResult(uri.toString(), kept, size, digest.hex(), reread.hex(), head.copyOf(headSize).joinToString("") { "%02x".format(it) })
    }

    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }

    private class Capture(private val input: java.io.InputStream, private val limit: Int) {
        private val buffer = java.io.ByteArrayOutputStream()
        @Volatile var truncated = false
            private set
        private val worker = thread(name = "nexo-capture") {
            val chunk = ByteArray(8192)
            try {
                input.use {
                    while (true) {
                        val n = it.read(chunk)
                        if (n < 0) break
                        synchronized(buffer) {
                            val room = limit - buffer.size()
                            if (room > 0) buffer.write(chunk, 0, minOf(room, n))
                            if (n > room) truncated = true
                        }
                    }
                }
            } catch (_: java.io.IOException) {
            }
        }

        fun join() = worker.join(5_000)
        fun text(): String = synchronized(buffer) { buffer.toString(Charsets.UTF_8.name()) }
    }

    companion object {
        const val VERSION = 2
        private const val MAX_READ = 1 shl 20
        private const val MAX_TIMEOUT = 181L * 60_000
    }
}
