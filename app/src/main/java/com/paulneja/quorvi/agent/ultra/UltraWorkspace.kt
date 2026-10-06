package com.paulneja.quorvi.agent.ultra

import android.util.Base64
import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.ToolContext
import com.paulneja.quorvi.agent.tools.ToolResult
import com.paulneja.quorvi.agent.tools.schema
import com.paulneja.quorvi.agent.tools.string
import com.paulneja.quorvi.agent.tools.stringOrNull
import com.paulneja.quorvi.runtime.ExecRequest
import com.paulneja.quorvi.runtime.Workspace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class PrFile(val path: String, val content: String?, val mode: String = "100644", val additions: Int = 0, val deletions: Int = 0, val change: String = "") {
    val kind: String get() = change.ifEmpty { if (content == null) PrChange.DELETED else PrChange.MODIFIED }
}

object PrChange {
    const val ADDED = "added"
    const val MODIFIED = "modified"
    const val DELETED = "deleted"
}

@Serializable
data class PrDraft(
    val repo: String,
    val baseBranch: String,
    val headSha: String,
    val title: String,
    val body: String,
    val commitMessage: String,
    val files: List<PrFile>,
    val diff: String = "",
    val status: String = PrStatus.PENDING,
    val url: String? = null,
    val number: Int? = null,
    val error: String? = null,

    val excluded: List<String> = emptyList(),

    val edited: List<String> = emptyList(),
) {
    val editable: Boolean get() = status == PrStatus.PENDING || status == PrStatus.FAILED

    val included: List<PrFile> get() = files.filter { it.path !in excluded }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(raw: String): PrDraft? = runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
}

object PrStatus {
    const val PENDING = "pending"
    const val SUBMITTING = "submitting"
    const val SUBMITTED = "submitted"
    const val REJECTED = "rejected"
    const val BLOCKED = "blocked"
    const val FAILED = "failed"
}

class UltraWorkspace(private val runtime: Workspace, private val token: suspend () -> String?, chatId: String) {
    val dir = "ultra/$chatId"
    val repoDir = "$dir/repo"

    suspend fun bash(command: String, cwd: String = repoDir, timeoutMs: Long = 120_000, env: Map<String, String> = emptyMap()) =
        runtime.exec(ExecRequest(argv = listOf("bash", "-lc", command), cwd = cwd, env = env + ("GIT_TERMINAL_PROMPT" to "0"), timeoutMs = timeoutMs))

    suspend fun cloned(): Boolean = runCatching { bash("test -d .git && echo yes", timeoutMs = 10_000).stdout.contains("yes") }.getOrDefault(false)

    /** The token rides in a one-off header for this command only, so it is never written to .git/config. */
    suspend fun clone(repo: String): String {
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo) && ".." !in repo) { "Invalid repository name" }
        if (cloned()) return "Already cloned."
        val auth = token()?.let { t -> "-c http.extraHeader=\"Authorization: Basic ${Base64.encodeToString("x-access-token:$t".toByteArray(), Base64.NO_WRAP)}\" " }.orEmpty()
        val r = bash(
            "command -v git >/dev/null || pkg install -y git >/dev/null 2>&1; mkdir -p . && git ${auth}clone --depth 50 https://github.com/$repo.git repo 2>&1 | tail -5",
            cwd = dir, timeoutMs = 600_000,
        )
        if (!cloned()) error("Clone failed: ${r.stdout.takeLast(500)}${r.stderr.takeLast(300)}")
        bash("git config user.useConfigOnly true", timeoutMs = 10_000)
        return "Cloned $repo into the workspace (branch ${branch()})."
    }

    suspend fun branch(): String = bash("git rev-parse --abbrev-ref HEAD", timeoutMs = 10_000).stdout.trim()

    fun rel(path: String): String {
        val clean = path.trim().trimStart('/').removePrefix("./")
        require(clean.split('/').none { it == ".." } && clean != ".git" && !clean.startsWith(".git/")) { "Path not allowed: $path" }
        return if (clean.isEmpty()) repoDir else "$repoDir/$clean"
    }

    suspend fun list(path: String): String {
        val r = bash("find ${shell(rel(path).removePrefix(repoDir).trimStart('/').ifEmpty { "." })} -path ./.git -prune -o -print 2>/dev/null | head -400", timeoutMs = 20_000)
        return r.stdout.ifBlank { "(empty)" }
    }

    suspend fun read(path: String): String = runtime.read(rel(path), READ_CAP)

    suspend fun write(path: String, content: String): String {
        val target = rel(path)
        bash("mkdir -p ${shell(target.removePrefix(repoDir).trimStart('/').substringBeforeLast('/', "."))}", timeoutMs = 10_000)
        runtime.write(target, content)
        return "Wrote $path."
    }

    suspend fun edit(path: String, old: String, new: String): String {
        val text = read(path)
        val count = Regex(Regex.escape(old)).findAll(text).take(2).count()
        require(old.isNotEmpty() && count == 1) { if (count == 0) "old_text not found in $path" else "old_text appears more than once; include more context" }
        runtime.write(rel(path), text.replace(old, new))
        return "Edited $path."
    }

    suspend fun run(command: String, timeoutSeconds: Int): String {
        CommitPolicy.blockedCommand(command)?.let { return "Refused: $it" }
        val r = bash(command, timeoutMs = timeoutSeconds.coerceIn(5, 900) * 1000L)
        val out = (r.stdout + if (r.stderr.isNotBlank()) "\n" + r.stderr else "").let { if (it.length > OUTPUT_CAP) "…(cut)\n" + it.takeLast(OUTPUT_CAP) else it }
        val status = if (r.timedOut) "timed out" else "exit code ${r.exitCode}"
        return "$ $command\n$status\n\n${out.ifBlank { "(no output)" }}"
    }

    suspend fun changes(): String {
        val status = bash("git status --short -uall", timeoutMs = 20_000).stdout
        if (status.isBlank()) return "No changes."
        val diff = bash("git add -N . && git diff", timeoutMs = 30_000).stdout
        return status + "\n" + diff.take(DIFF_CAP)
    }

    suspend fun draft(title: String, body: String, commitMessage: String): PrDraft {
        check(cloned()) { "Clone a repository first" }
        val remote = bash("git config --get remote.origin.url", timeoutMs = 10_000).stdout.trim()
        val repo = Regex("github\\.com[/:]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(\\.git)?$").find(remote)?.groupValues?.get(1) ?: error("The clone does not come from GitHub")
        bash("git add -N .", timeoutMs = 20_000)
        val numstat = bash("git diff --numstat", timeoutMs = 30_000).stdout.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null else parts[2] to ((parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0))
        }.toMap()
        val entries = bash("git status --porcelain=v1 -uall", timeoutMs = 20_000).stdout.lines().filter { it.length > 3 }
        val files = mutableListOf<PrFile>()
        for (e in entries) {
            val code = e.substring(0, 2)
            val path = e.substring(3)
            if (path.contains(" -> ")) {
                val (from, to) = path.split(" -> ", limit = 2)
                files += PrFile(from, null, change = PrChange.DELETED)
                files += file(to, numstat[to], PrChange.ADDED)
            } else if ('D' in code) files += PrFile(path, null, deletions = numstat[path]?.second ?: 0, change = PrChange.DELETED)
            else files += file(path, numstat[path], if (code == "??" || 'A' in code) PrChange.ADDED else PrChange.MODIFIED)
        }
        require(files.isNotEmpty()) { "There are no changes to propose" }
        require(files.size <= MAX_FILES) { "Too many files for one pull request (${files.size})" }
        require(files.none { it.path.startsWith(".github/workflows/") }) { "Workflow files cannot go through a pull request from Nexo" }
        val diff = bash("git diff", timeoutMs = 30_000).stdout.take(DIFF_CAP)
        return PrDraft(repo, branch(), bash("git rev-parse HEAD", timeoutMs = 10_000).stdout.trim(), title.trim(), body.trim(), commitMessage.trim(), files, diff)
    }

    private suspend fun file(path: String, stat: Pair<Int, Int>?, change: String): PrFile {
        val content = runtime.read(rel(path), FILE_CAP)
        require('\u0000' !in content) { "$path is binary and cannot go in a pull request from Nexo" }
        val mode = bash("git ls-files -s -- ${shell(path)} | cut -d' ' -f1", timeoutMs = 10_000).stdout.trim().ifBlank { "100644" }
        return PrFile(path, content, mode, stat?.first ?: 0, stat?.second ?: 0, change)
    }

    private fun shell(s: String) = "'" + s.replace("'", "'\\''") + "'"

    companion object {
        const val READ_CAP = 60_000
        const val FILE_CAP = 400_000
        const val OUTPUT_CAP = 12_000
        const val DIFF_CAP = 60_000
        const val MAX_FILES = 60
    }
}

interface GitHubReads {
    suspend fun issues(repo: String, state: String): String
    suspend fun issue(repo: String, number: Int): String
    suspend fun pulls(repo: String, state: String): String
}

fun ultraTools(ws: UltraWorkspace, config: UltraConfig, github: GitHubReads? = null): List<Tool> = buildList {
    add(UltraTool("repo.clone", "Clone a GitHub repository (owner/name) into this chat's workspace. Uses the user's GitHub account.", Risk.NETWORK, schema { string("repo", "owner/name") }) { a, _ -> ws.clone(a.string("repo")) })
    add(UltraTool("ws.list", "List files in the repository (skips .git, capped at 400).", Risk.READ, schema { string("path", "Folder, empty for the root", optional = true) }) { a, _ -> ws.list(a.stringOrNull("path").orEmpty()) })
    add(UltraTool("ws.read", "Read a text file of the repository (capped at 60k characters).", Risk.READ, schema { string("path", "File path") }) { a, _ -> ws.read(a.string("path")) })
    add(UltraTool("ws.write", "Create or fully replace a text file in the repository.", Risk.WRITE, schema { string("path", "File path"); string("content", "Complete file text") }) { a, _ -> ws.write(a.string("path"), a.string("content")) })
    add(UltraTool("ws.edit", "Replace one exact snippet in a file; the old text must appear exactly once.", Risk.WRITE, schema {
        string("path", "File path"); string("old_text", "Exact existing text"); string("new_text", "Replacement")
    }) { a, _ -> ws.edit(a.string("path"), a.string("old_text"), a.string("new_text")) })
    add(UltraTool("ws.changes", "Git status and diff of the uncommitted changes.", Risk.READ, schema { }) { _, _ -> ws.changes() })
    if (config.allowCommands) add(UltraTool("ws.run", "Run a bash command in the repository folder (build, test, grep, package managers). git commit/push and gh writes are refused: Nexo makes the commit and the pull request.", Risk.WRITE, schema {
        string("command", "The bash command line"); string("timeout_seconds", "5-900, default 120", optional = true)
    }) { a, _ -> ws.run(a.string("command"), a.stringOrNull("timeout_seconds")?.toIntOrNull() ?: 120) })
}

private class UltraTool(
    override val name: String,
    override val description: String,
    override val risk: Risk,
    override val parameters: JsonObject,
    private val block: suspend (JsonObject, ToolContext) -> String,
) : Tool {
    override val idempotent = risk == Risk.READ

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult = runCatching { ToolResult.ok(block(args, context)) }
        .getOrElse { ToolResult.failed("${name}: ${it.message ?: it::class.simpleName}") }
}
