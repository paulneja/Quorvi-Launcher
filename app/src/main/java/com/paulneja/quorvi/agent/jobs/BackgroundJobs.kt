package com.paulneja.quorvi.agent.jobs

import com.paulneja.quorvi.agent.chat.ChatDao
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.chat.ChatStatus
import com.paulneja.quorvi.runtime.ExecRequest
import com.paulneja.quorvi.runtime.RuntimeClient
import com.paulneja.quorvi.runtime.Workspace
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object JobState {
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
    const val STOPPED = "stopped"
    const val KILLED = "killed"
    const val PAUSED = "paused"
    const val LOST = "lost"
}

@Serializable
data class JobNote(
    val id: String,
    val title: String,
    val command: String,
    val cwd: String,
    val startedAt: Long,
    val state: String = JobState.RUNNING,
    val exitCode: Int? = null,
    val endedAt: Long? = null,
    val continued: Boolean = false,
    val byUser: Boolean = false,
) {
    val running: Boolean get() = state == JobState.RUNNING
    val alive: Boolean get() = state == JobState.RUNNING || state == JobState.PAUSED
    val log: String get() = "${JobScripts.DIR}/$id/out.log"

    fun summary(): String = buildString {
        append("background job ").append(id).append(" \"").append(title).append("\" → ").append(state)
        if (byUser) append(" by the user")
        exitCode?.let { append(" (exit ").append(it).append(')') }
        endedAt?.let { append(", took ").append(JobScripts.duration(it - startedAt)) }
        append(", log ").append(log)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(raw: String): JobNote? = runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
}

data class JobProbe(val state: String, val exitCode: Int?, val endedAt: Long? = null)

object JobScripts {
    const val DIR = "jobs"

    private const val LAUNCH = "d=\"\$HOME/$DIR/\$2\"; mkdir -p \"\$d\" || exit 1; " +
        "s=\$(command -v setsid || echo /system/bin/setsid); [ -x \"\$s\" ] || s=; " +
        "\$s bash -c 'bash -lc \"\$1\"; echo \$? > \"\$2/exit\"' job \"\$1\" \"\$d\" > \"\$d/out.log\" 2>&1 < /dev/null & " +
        "echo \$! > \"\$d/pid\"; echo started"

    private const val PROBE = "for id in \"\$@\"; do d=\"\$HOME/$DIR/\$id\"; " +
        "if [ -f \"\$d/exit\" ]; then echo \"\$id done \$(cat \"\$d/exit\") \$(stat -c %Y \"\$d/exit\" 2>/dev/null)\"; " +
        "elif [ -f \"\$d/pid\" ] && kill -0 \"\$(cat \"\$d/pid\")\" 2>/dev/null; then echo \"\$id running\"; " +
        "elif [ -f \"\$d/exit\" ]; then echo \"\$id done \$(cat \"\$d/exit\") \$(stat -c %Y \"\$d/exit\" 2>/dev/null)\"; " +
        "else echo \"\$id lost\"; fi; done"

    private const val STOP = "d=\"\$HOME/$DIR/\$1\"; p=\$(cat \"\$d/pid\" 2>/dev/null) || exit 3; " +
        "kill -TERM -- -\$p 2>/dev/null || kill -TERM \$p 2>/dev/null; kill -CONT -- -\$p 2>/dev/null; sleep 2; kill -KILL -- -\$p 2>/dev/null; " +
        "[ -f \"\$d/exit\" ] || echo 143 > \"\$d/exit\"; echo stopped"

    private const val KILL = "d=\"\$HOME/$DIR/\$1\"; p=\$(cat \"\$d/pid\" 2>/dev/null) || exit 3; " +
        "kill -KILL -- -\$p 2>/dev/null || kill -KILL \$p 2>/dev/null; " +
        "[ -f \"\$d/exit\" ] || echo 137 > \"\$d/exit\"; echo killed"

    private const val SIGNAL = "p=\$(cat \"\$HOME/$DIR/\$1/pid\" 2>/dev/null) || exit 3; kill -\$2 -- -\$p 2>/dev/null || kill -\$2 \$p"

    fun launch(id: String, command: String): List<String> = listOf("bash", "-c", LAUNCH, "launch", command, id)

    fun probe(ids: List<String>): List<String> = listOf("bash", "-c", PROBE, "probe") + ids

    fun stop(id: String): List<String> = listOf("bash", "-c", STOP, "stop", id)

    fun kill(id: String): List<String> = listOf("bash", "-c", KILL, "kill", id)

    fun signal(id: String, signal: String): List<String> = listOf("bash", "-c", SIGNAL, "signal", id, signal)

    fun tail(id: String, bytes: Int): List<String> = listOf("tail", "-c", bytes.toString(), "$DIR/$id/out.log")

    fun parse(output: String): Map<String, JobProbe> = output.lines().mapNotNull { line ->
        val parts = line.trim().split(' ')
        val id = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        when (parts.getOrNull(1)) {
            "running" -> id to JobProbe(JobState.RUNNING, null)
            "done" -> {
                val code = parts.getOrNull(2)?.toIntOrNull()
                id to JobProbe(if (code == 0) JobState.DONE else JobState.FAILED, code, parts.getOrNull(3)?.toLongOrNull()?.times(1000))
            }
            "lost" -> id to JobProbe(JobState.LOST, null)
            else -> null
        }
    }.toMap()

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${s % 3600 / 60}m"
        }
    }
}

class FinishedJob(val chatId: String, val messageId: String, val note: JobNote, val tail: String)

class JobItem(val chatId: String, val note: JobNote)

open class JobRunner(private val runtime: Workspace, private val dao: ChatDao, private val clock: () -> Long = System::currentTimeMillis) {
    private val _started = kotlinx.coroutines.flow.MutableStateFlow(0)
    val started: kotlinx.coroutines.flow.StateFlow<Int> = _started

    val items: kotlinx.coroutines.flow.Flow<List<JobItem>> = dao.observeByRole(ChatRole.JOB).map { list ->
        list.mapNotNull { m -> JobNote.decode(m.text)?.let { JobItem(m.chatId, it) } }
    }

    suspend fun start(chatId: String, runId: String, title: String, command: String, cwd: String): JobNote {
        val active = aliveCount()
        require(active < MAX_RUNNING) { "There are already $active background jobs running; wait for one to finish or stop one." }
        val id = UUID.randomUUID().toString().take(8)
        val r = runtime.exec(ExecRequest(argv = JobScripts.launch(id, command), cwd = cwd, timeoutMs = 20_000))
        check(r.exitCode == 0 && r.stdout.contains("started")) { "Could not start the job: ${(r.stderr + r.stdout).take(400)}" }
        val note = JobNote(id, title.trim().ifBlank { command.take(60) }.take(80), command.take(2000), cwd, clock())
        dao.upsertMessage(ChatMessageEntity("job-$id", chatId, ChatRole.JOB, note.encode(), ChatStatus.DONE, runId, clock()))
        _started.value++
        return note
    }

    suspend fun find(chatId: String, id: String): Pair<ChatMessageEntity, JobNote>? = byId(id)?.takeIf { it.first.chatId == chatId }

    suspend fun ofChat(chatId: String): List<JobNote> = dao.messages(chatId).filter { it.role == ChatRole.JOB }.mapNotNull { JobNote.decode(it.text) }

    suspend fun tail(id: String, bytes: Int = 6_000): String =
        runCatching { runtime.exec(ExecRequest(argv = JobScripts.tail(id, bytes), timeoutMs = 10_000)).stdout }.getOrDefault("")

    suspend fun stop(chatId: String, id: String, byUser: Boolean = false): JobNote? {
        find(chatId, id) ?: return null
        return end(id, force = false, byUser = byUser, tell = false)
    }

    suspend fun end(id: String, force: Boolean, byUser: Boolean, tell: Boolean): JobNote? {
        val (message, note) = byId(id) ?: return null
        if (!note.alive) return note
        runtime.exec(ExecRequest(argv = if (force) JobScripts.kill(note.id) else JobScripts.stop(note.id), timeoutMs = 15_000))
        val ended = note.copy(
            state = if (force) JobState.KILLED else JobState.STOPPED, exitCode = if (force) 137 else 143,
            endedAt = clock(), continued = !tell, byUser = byUser,
        )
        dao.setText(message.id, ended.encode(), message.status)
        return ended
    }

    suspend fun pause(id: String): JobNote? = signal(id, "STOP", JobState.RUNNING, JobState.PAUSED)

    suspend fun resume(id: String): JobNote? = signal(id, "CONT", JobState.PAUSED, JobState.RUNNING)

    suspend fun stopChat(chatId: String) {
        dao.messages(chatId).filter { it.role == ChatRole.JOB }.mapNotNull { JobNote.decode(it.text) }.filter { it.alive }
            .forEach { runCatching { end(it.id, force = false, byUser = true, tell = false) } }
    }

    suspend fun stopAll() {
        all().filter { it.second.alive }.forEach { runCatching { end(it.second.id, force = false, byUser = true, tell = false) } }
    }

    suspend fun refresh(chatId: String, id: String): JobNote? {
        val (message, note) = find(chatId, id) ?: return null
        if (!note.alive) return note
        val probe = probe(listOf(note.id))[note.id] ?: return note
        if (probe.state == JobState.RUNNING) return note
        val ended = note.copy(state = probe.state, exitCode = probe.exitCode, endedAt = probe.endedAt ?: clock(), continued = true)
        dao.setText(message.id, ended.encode(), message.status)
        return ended
    }

    suspend fun pending(): Int = all().count { it.second.alive || !it.second.continued }

    suspend fun aliveCount(): Int = all().count { it.second.alive }

    suspend fun chatExists(chatId: String): Boolean = dao.chat(chatId) != null

    fun goal(job: FinishedJob): String = buildString {
        if (job.note.byUser) {
            append("[Background job stopped by the user] ").append(job.note.summary()).append('\n')
            append("The user stopped it on purpose from the background jobs screen. Tell them in one short line that it was stopped and what that leaves pending; do not start it again unless they ask.")
            return@buildString
        }
        append("[Background job finished] ").append(job.note.summary()).append('\n')
        append("Command: ").append(job.note.command.take(500)).append('\n')
        append("Last output:\n<untrusted>\n").append(job.tail.takeLast(5_000).ifBlank { "(no output)" }).append("\n</untrusted>")
    }

    suspend fun sweep(): List<FinishedJob> {
        val jobs = all().filter { it.second.alive || !it.second.continued }
        if (jobs.isEmpty()) return emptyList()
        val alive = jobs.filter { it.second.alive }
        val probes = if (alive.isEmpty()) emptyMap() else probe(alive.map { it.second.id })
        return jobs.mapNotNull { (message, note) ->
            val current = if (note.alive) {
                val p = probes[note.id] ?: return@mapNotNull null
                if (p.state == JobState.RUNNING) return@mapNotNull null
                note.copy(state = p.state, exitCode = p.exitCode, endedAt = p.endedAt ?: clock())
            } else note
            FinishedJob(message.chatId, message.id, current, if (current.byUser) "" else tail(note.id))
        }
    }

    suspend fun saveEnded(job: FinishedJob): Boolean {
        val message = dao.message(job.messageId) ?: return false
        val stored = JobNote.decode(message.text) ?: return false
        if (!stored.alive) return false
        dao.setText(message.id, job.note.encode(), message.status)
        return true
    }

    suspend fun current(job: FinishedJob): FinishedJob? {
        val stored = dao.message(job.messageId)?.let { JobNote.decode(it.text) } ?: return null
        return if (stored.alive || stored.continued) null else FinishedJob(job.chatId, job.messageId, stored, job.tail)
    }

    suspend fun markContinued(job: FinishedJob) {
        val message = dao.message(job.messageId) ?: return
        val stored = JobNote.decode(message.text) ?: return
        dao.setText(message.id, stored.copy(continued = true).encode(), message.status)
    }

    private suspend fun signal(id: String, signal: String, from: String, to: String): JobNote? {
        val (message, note) = byId(id) ?: return null
        if (note.state != from) return note
        val r = runtime.exec(ExecRequest(argv = JobScripts.signal(note.id, signal), timeoutMs = 10_000))
        if (r.exitCode != 0) return note
        val changed = note.copy(state = to)
        dao.setText(message.id, changed.encode(), message.status)
        return changed
    }

    private suspend fun byId(id: String): Pair<ChatMessageEntity, JobNote>? =
        dao.message("job-${id.trim()}")?.let { m -> JobNote.decode(m.text)?.let { m to it } }

    private suspend fun probe(ids: List<String>): Map<String, JobProbe> =
        runCatching { JobScripts.parse(runtime.exec(ExecRequest(argv = JobScripts.probe(ids), timeoutMs = 20_000)).stdout) }.getOrDefault(emptyMap())

    private suspend fun all(): List<Pair<ChatMessageEntity, JobNote>> = dao.byRole(ChatRole.JOB).mapNotNull { m -> JobNote.decode(m.text)?.let { m to it } }

    companion object {
        const val MAX_RUNNING = 4
    }
}

@Singleton
class BackgroundJobs @Inject constructor(runtime: RuntimeClient, dao: ChatDao) : JobRunner(runtime, dao)
