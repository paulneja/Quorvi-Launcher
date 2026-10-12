package com.paulneja.quorvi.agent.jobs

import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.ToolContext
import com.paulneja.quorvi.agent.tools.ToolResult
import com.paulneja.quorvi.agent.tools.ToolStatus
import com.paulneja.quorvi.agent.tools.intOrNull
import com.paulneja.quorvi.agent.tools.schema
import com.paulneja.quorvi.agent.tools.string
import com.paulneja.quorvi.agent.tools.stringOrNull
import com.paulneja.quorvi.agent.ultra.CommitPolicy
import kotlinx.serialization.json.JsonObject

fun jobTools(jobs: JobRunner, chatId: String): List<Tool> = listOf(JobStart(jobs, chatId), JobStatus(jobs, chatId), JobStop(jobs, chatId), JobList(jobs, chatId))

private fun JobNote.line() = "$id · $title · $state" + (exitCode?.let { " · exit $it" } ?: "")

class JobStart(private val jobs: JobRunner, private val chatId: String) : Tool {
    override val name = "job.start"
    override val description = "Start a bash command as a background job with no time limit (downloads, builds, package installs, long scripts). " +
        "It returns at once with a job id; output goes to jobs/<id>/out.log. When the job ends, at any hour, Quorvi notifies the user and opens a new turn in this chat with the result so you can continue. " +
        "Tell the user it is running and end your turn; do not wait or poll in a loop."
    override val risk = Risk.WRITE
    override val idempotent = false
    override val parameters = schema {
        string("command", "Bash command line, e.g. \"./gradlew assembleDebug\" or \"curl -LO https://…\"")
        string("title", "Short label shown to the user, e.g. \"Building the app\"")
        string("cwd", "Working directory relative to this chat's workspace (default: the workspace, or the repository in Ultra)", optional = true)
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val command = args.string("command").trim()
        if (command.isEmpty()) return ToolResult.failed("Empty command")
        val ultra = context.workspace.startsWith("ultra/")
        if (ultra) CommitPolicy.blockedCommand(command)?.let { return ToolResult(ToolStatus.DENIED, "Refused: $it") }
        val cwd = context.path(args.stringOrNull("cwd") ?: if (ultra) "repo" else ".")
        val note = runCatching { jobs.start(chatId, context.runId, args.stringOrNull("title").orEmpty(), command, cwd) }
            .getOrElse { return ToolResult.failed(it.message ?: "Could not start the job") }
        context.emit("exec", "job ${note.id}: ${command.take(300)}", note.id)
        return ToolResult.ok("Started background job ${note.id} (\"${note.title}\") in $cwd. Log: ${note.log}. You will get a new turn with the result when it ends.")
    }
}

class JobStatus(private val jobs: JobRunner, private val chatId: String) : Tool {
    override val name = "job.status"
    override val description = "Check a background job: state, exit code and the end of its output."
    override val risk = Risk.READ
    override val idempotent = true
    override val parameters = schema {
        string("id", "Job id")
        integer("tailBytes", "How much of the end of the log to return (default 4000, max 20000)")
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val note = jobs.refresh(chatId, args.string("id")) ?: return ToolResult.failed("No job with that id in this chat")
        val tail = jobs.tail(note.id, (args.intOrNull("tailBytes") ?: 4_000).coerceIn(200, 20_000))
        return ToolResult.ok(note.summary() + "\n\n" + tail.ifBlank { "(no output yet)" })
    }
}

class JobStop(private val jobs: JobRunner, private val chatId: String) : Tool {
    override val name = "job.stop"
    override val description = "Stop a running background job and everything it started."
    override val risk = Risk.WRITE
    override val idempotent = true
    override val parameters = schema { string("id", "Job id") }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val note = jobs.stop(chatId, args.string("id")) ?: return ToolResult.failed("No job with that id in this chat")
        return ToolResult.ok(note.summary())
    }
}

class JobList(private val jobs: JobRunner, private val chatId: String) : Tool {
    override val name = "job.list"
    override val description = "List this chat's background jobs."
    override val risk = Risk.READ
    override val idempotent = true
    override val parameters = schema { }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult =
        ToolResult.ok(jobs.ofChat(chatId).takeLast(20).joinToString("\n") { it.line() }.ifBlank { "No background jobs in this chat." })
}
