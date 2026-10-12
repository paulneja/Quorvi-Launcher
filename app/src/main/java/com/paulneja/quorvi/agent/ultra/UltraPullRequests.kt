package com.paulneja.quorvi.agent.ultra

import com.paulneja.quorvi.agent.chat.ChatDao
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.runtime.RuntimeClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UltraPullRequests @Inject constructor(
    private val dao: ChatDao,
    private val api: GitHubApi,
    private val store: UltraStore,
    private val runtime: RuntimeClient,
) {
    suspend fun submit(messageId: String) {
        val message = dao.message(messageId) ?: return
        val draft = PrDraft.decode(message.text) ?: return
        if (draft.status != PrStatus.PENDING && draft.status != PrStatus.FAILED) return
        val config = store.current()

        val violations = CommitPolicy.check(listOf(draft.title, draft.body, draft.commitMessage), config.allowedCoauthors)
        if (violations.isNotEmpty()) {
            save(messageId, draft.copy(status = PrStatus.BLOCKED, error = violations.joinToString("\n") { it.line }))
            return
        }
        save(messageId, draft.copy(status = PrStatus.SUBMITTING, error = null))
        val result = runCatching {
            check(draft.included.isNotEmpty()) { "every file was left out of the pull request" }
            val tree = api.createTree(draft.repo, api.commitTree(draft.repo, draft.headSha), draft.included.map { Triple(it.path, it.mode, it.content) })
            val commit = api.createCommit(draft.repo, draft.commitMessage, tree, draft.headSha)
            val branch = branchName(config.branchPrefix, draft.title)
            api.createBranch(draft.repo, branch, commit)
            val (number, url) = api.createPull(draft.repo, draft.title, draft.body, branch, draft.baseBranch, config.draftPullRequests)
            resetToBase(message.chatId, draft.baseBranch, number, draft.excluded.isNotEmpty())
            draft.copy(status = PrStatus.SUBMITTED, number = number, url = url)
        }.getOrElse { e ->
            val detail = (e as? com.paulneja.quorvi.core.net.HttpStatusException)?.let { h ->
                when (h.status) {
                    401 -> "sign in to GitHub in Accounts (HTTP 401)"
                    403, 404 -> "your account cannot write to ${draft.repo} (HTTP ${h.status}). Fork it first or ask for access."
                    422 -> "GitHub rejected the change (HTTP 422): ${h.body.take(200)}"
                    else -> "HTTP ${h.status}: ${h.body.take(200)}"
                }
            } ?: (e.message ?: e::class.simpleName).orEmpty()
            draft.copy(status = PrStatus.FAILED, error = detail.take(400))
        }
        save(messageId, result)
    }

    suspend fun edit(messageId: String, title: String, body: String, commitMessage: String, excluded: List<String>): String? {
        val message = dao.message(messageId) ?: return null
        val draft = PrDraft.decode(message.text)?.takeIf { it.editable } ?: return null
        if (title.isBlank() || commitMessage.isBlank()) return "empty"
        val keep = excluded.filter { p -> draft.files.any { it.path == p } }.distinct()
        if (draft.files.all { it.path in keep }) return "no-files"
        val violations = CommitPolicy.check(listOf(title, body, commitMessage), store.current().allowedCoauthors)
        if (violations.isNotEmpty()) return violations.joinToString("\n") { it.line }
        save(messageId, draft.copy(title = title.trim(), body = body.trim(), commitMessage = commitMessage.trim(), excluded = keep, status = PrStatus.PENDING, error = null))
        return null
    }

    suspend fun editFile(messageId: String, path: String, content: String): String? {
        val message = dao.message(messageId) ?: return null
        val draft = PrDraft.decode(message.text)?.takeIf { it.editable } ?: return null
        return runCatching {
            val ws = UltraWorkspace(runtime, { api.token() }, message.chatId)
            check(ws.cloned()) { "Clone a repository first" }
            ws.write(path, content)
            val fresh = ws.draft(draft.title, draft.body, draft.commitMessage)
            save(messageId, fresh.copy(excluded = draft.excluded.filter { p -> fresh.files.any { it.path == p } }, edited = (draft.edited + path).distinct()))
            null
        }.getOrElse { e -> if (e is kotlinx.coroutines.CancellationException) throw e; e.message ?: e::class.simpleName.orEmpty() }
    }

    suspend fun reject(messageId: String) {
        val message = dao.message(messageId) ?: return
        val draft = PrDraft.decode(message.text) ?: return
        if (draft.status == PrStatus.PENDING || draft.status == PrStatus.FAILED || draft.status == PrStatus.BLOCKED) save(messageId, draft.copy(status = PrStatus.REJECTED))
    }

    private suspend fun resetToBase(chatId: String, base: String, number: Int, keepLeftovers: Boolean) {
        runCatching {
            val ws = UltraWorkspace(runtime, { api.token() }, chatId)
            val auth = api.token()?.let { t -> "-c http.extraHeader=\"Authorization: Basic ${android.util.Base64.encodeToString("x-access-token:$t".toByteArray(), android.util.Base64.NO_WRAP)}\" " }.orEmpty()
            ws.bash(resetScript(auth, base, number, keepLeftovers), timeoutMs = 120_000)
        }
    }

    private suspend fun save(id: String, draft: PrDraft) {
        val m = dao.message(id) ?: return
        dao.upsertMessage(m.copy(text = draft.encode()))
    }

    companion object {
        fun resetScript(auth: String, base: String, number: Int, keepLeftovers: Boolean): String =
            (if (keepLeftovers) "git add -A && (git diff --cached --quiet || git stash push -q -m 'left out of PR #$number'); " else "") +
                "git ${auth}fetch -q origin $base && git checkout -q -f -B $base FETCH_HEAD && git reset -q --hard FETCH_HEAD && git clean -q -fd"

        fun branchName(prefix: String, title: String): String {
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trimEnd('-').ifBlank { "change" }
            return prefix.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" } + slug + "-" + System.currentTimeMillis().toString(36).takeLast(5)
        }
    }
}
