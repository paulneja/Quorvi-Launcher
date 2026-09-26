package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.engine.Capability
import com.paulneja.quorvi.agent.engine.GoalKind
import com.paulneja.quorvi.agent.engine.ModelChoice
import com.paulneja.quorvi.agent.engine.ToolBox
import com.paulneja.quorvi.agent.models.AiClient
import com.paulneja.quorvi.agent.models.AiEvent
import com.paulneja.quorvi.agent.models.AiModelInfo
import com.paulneja.quorvi.agent.models.AiRequest
import com.paulneja.quorvi.agent.models.AiToolCall
import com.paulneja.quorvi.agent.models.AiUsage
import com.paulneja.quorvi.agent.tools.ArtifactExport
import com.paulneja.quorvi.agent.tools.FsList
import com.paulneja.quorvi.agent.tools.FsRead
import com.paulneja.quorvi.agent.tools.FsWrite
import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.ShellExec
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.ToolContext
import com.paulneja.quorvi.agent.tools.ToolResult
import com.paulneja.quorvi.agent.tools.schema
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.runtime.ExecRequest
import com.paulneja.quorvi.runtime.ExecResult
import com.paulneja.quorvi.runtime.ExportResult
import com.paulneja.quorvi.runtime.FileEntry
import com.paulneja.quorvi.runtime.Workspace
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface Turn {
    data class Text(val text: String) : Turn
    data class Calls(val calls: List<Pair<String, JsonObject>>) : Turn
    data object Hang : Turn
}

fun call(name: String, vararg args: Pair<String, Any>): Turn = Turn.Calls(listOf(name to json(*args)))

fun json(vararg args: Pair<String, Any>): JsonObject = buildJsonObject {
    args.forEach { (k, v) ->
        when (v) {
            is String -> put(k, v)
            is Int -> put(k, v)
            is List<*> -> put(k, buildJsonArray { v.forEach { add(JsonPrimitive(it.toString())) } })
            else -> error("unsupported $v")
        }
    }
}

class ScriptedModel(turns: List<Turn>) : AiClient {
    private val queue = ArrayDeque(turns)
    private val ids = AtomicInteger()
    val requests: MutableList<AiRequest> = Collections.synchronizedList(mutableListOf())

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        requests += request
        val turn = synchronized(queue) { queue.removeFirstOrNull() } ?: Turn.Text("I have nothing else to do.")
        when (turn) {
            is Turn.Text -> emit(AiEvent.TextDelta(turn.text))
            is Turn.Calls -> emit(AiEvent.ToolCalls(turn.calls.map { (name, args) -> AiToolCall("c${ids.incrementAndGet()}", name, args) }))
            Turn.Hang -> awaitCancellation()
        }
        emit(AiEvent.Done(AiUsage(100, 20)))
    }

    override suspend fun listModels(): List<AiModelInfo> = emptyList()

    fun choice() = ModelChoice("fake", "fake-1", this)
}

class MemoryWorkspace : Workspace {
    val files: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
    val commands: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
    var killed = 0
    var corruptExport = false

    override suspend fun exec(request: ExecRequest): ExecResult {
        commands += request.argv
        return ExecResult(0, "ok\n", "", timedOut = false, truncated = false, durationMs = 3)
    }

    override suspend fun kill(execId: String) = true
    override suspend fun killAll(): Int { killed++; return 0 }
    override suspend fun read(path: String, maxBytes: Int): String = files[path] ?: throw IllegalArgumentException("No such file: $path")
    override suspend fun write(path: String, content: String): Long { files[path] = content; return content.toByteArray().size.toLong() }
    override suspend fun list(path: String): List<FileEntry> = files.keys.filter { it.startsWith("$path/") }.map { FileEntry(it.removePrefix("$path/"), false, files[it]!!.length.toLong(), 0) }

    override suspend fun export(path: String, displayName: String, mime: String): ExportResult {
        val bytes = (files[path] ?: throw IllegalArgumentException("Not a file: $path")).toByteArray()
        val sha = sha(bytes)
        val magic = bytes.take(8).joinToString("") { "%02x".format(it) }
        return ExportResult("content://media/external/downloads/1", displayName, bytes.size.toLong(), sha, if (corruptExport) sha(byteArrayOf(1)) else sha, magic)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class FakeFetch(private val page: String) : Tool {
    override val name = "web.fetch"
    override val description = "fetch"
    override val risk = Risk.NETWORK
    override val idempotent = true
    override val parameters = schema { string("url", "url") }
    override suspend fun invoke(args: JsonObject, context: ToolContext) = ToolResult.ok("<untrusted source=\"x\">\n$page\n</untrusted>")
}

class FakeToolBox(val workspace: MemoryWorkspace, private val dao: OpsDao, private val extra: List<Tool> = emptyList(), var missing: String? = null) : ToolBox {
    override suspend fun available(kind: GoalKind, ciExecutionId: String?): Capability =
        missing?.let { Capability.Missing(it) } ?: Capability.Ready(listOf(FsWrite(workspace), FsRead(workspace), FsList(workspace), ShellExec(workspace), ArtifactExport(workspace, dao)) + extra)

    override suspend fun killProcesses(runId: String?) { workspace.killAll() }
    override suspend fun read(path: String): String? = workspace.files[path]
    override suspend fun allTools() = (available(GoalKind.GENERAL, null) as? Capability.Ready)?.tools ?: emptyList()
}

class MemoryPolicyStore(var config: com.paulneja.quorvi.agent.policy.PolicyConfig = com.paulneja.quorvi.agent.policy.PolicyConfig()) : com.paulneja.quorvi.agent.policy.PolicyStore {
    val grants = Collections.synchronizedList(mutableListOf<com.paulneja.quorvi.agent.policy.GrantEntity>())
    val approvals = Collections.synchronizedList(mutableListOf<com.paulneja.quorvi.agent.policy.ApprovalEntity>())
    val audit = Collections.synchronizedList(mutableListOf<com.paulneja.quorvi.agent.policy.AuditEntity>())

    override suspend fun config() = config
    override suspend fun grants(action: String, runId: String, now: Long) =
        grants.filter { it.action == action && (it.subject == runId || it.subject == "agent") && (it.expiresAt == null || it.expiresAt > now) && (it.usesLeft == null || it.usesLeft > 0) }
    override suspend fun consume(grant: com.paulneja.quorvi.agent.policy.GrantEntity) {
        val i = grants.indexOfFirst { it.id == grant.id }
        if (i >= 0 && grants[i].usesLeft != null) grants[i] = grants[i].copy(usesLeft = grants[i].usesLeft!! - 1)
    }
    override suspend fun requestApproval(approval: com.paulneja.quorvi.agent.policy.ApprovalEntity) { approvals += approval }
    override suspend fun audit(entry: com.paulneja.quorvi.agent.policy.AuditEntity) { audit += entry }
}

class FakeDevice(var state: com.paulneja.quorvi.agent.policy.DeviceState = com.paulneja.quorvi.agent.policy.DeviceState(80, false, true)) : com.paulneja.quorvi.agent.policy.DeviceContext {
    override fun state() = state
}
