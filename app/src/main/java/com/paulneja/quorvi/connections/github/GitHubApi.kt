package com.paulneja.quorvi.connections.github

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class GhUser(val login: String, @SerialName("avatar_url") val avatarUrl: String? = null)

@Serializable
data class GhRepo(
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
data class GhRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("head_branch") val branch: String? = null,
    @SerialName("head_sha") val headSha: String? = null,
    val event: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("run_attempt") val attempt: Int = 1,
    @SerialName("workflow_id") val workflowId: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("run_started_at") val startedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val actor: GhUser? = null,
)

@Serializable
data class GhStep(
    val name: String,
    val number: Int,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class GhJob(
    val id: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val steps: List<GhStep> = emptyList(),
)

@Serializable
data class GhArtifact(val id: Long, val name: String, @SerialName("size_in_bytes") val size: Long = 0, val expired: Boolean = false)

@Serializable
data class GhPull(
    val number: Int,
    val title: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("repository_url") val repositoryUrl: String = "",
    val user: GhUser? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable private data class RunsPage(@SerialName("total_count") val total: Int = 0, @SerialName("workflow_runs") val runs: List<GhRun> = emptyList())
@Serializable private data class JobsPage(val jobs: List<GhJob> = emptyList())
@Serializable private data class ArtifactsPage(val artifacts: List<GhArtifact> = emptyList())
@Serializable private data class SearchPage(val items: List<GhPull> = emptyList())

@Singleton
class GitHubApi(private val http: Http, private val tokens: GitHubTokens?, private val base: String = "https://api.github.com") {
    @Inject constructor(http: Http, session: GitHubSession) : this(http, session.tokens)

    private fun headers(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    private suspend fun <T> authed(block: suspend (Map<String, String>) -> T): T {
        val token = tokens?.valid() ?: throw HttpStatusException(401, "Not signed in to GitHub")
        return try {
            block(headers(token))
        } catch (e: HttpStatusException) {
            if (e.status != 401) throw e
            val fresh = tokens.afterUnauthorized(token) ?: throw e
            block(headers(fresh))
        }
    }

    private suspend fun get(path: String): JsonElement = authed { http.getJson("$base$path", it) }

    suspend fun viewer(): GhUser = http.json.decodeFromJsonElement(get("/user"))

    suspend fun repos(pages: Int = 3): List<GhRepo> = buildList {
        for (page in 1..pages) {
            val batch: List<GhRepo> = http.json.decodeFromJsonElement(get("/user/repos?per_page=100&sort=pushed&page=$page"))
            addAll(batch)
            if (batch.size < 100) break
        }
    }

    suspend fun runs(repo: String, perPage: Int = 20): List<GhRun> =
        http.json.decodeFromJsonElement<RunsPage>(get("/repos/$repo/actions/runs?per_page=$perPage")).runs

    suspend fun run(repo: String, id: Long): GhRun = http.json.decodeFromJsonElement(get("/repos/$repo/actions/runs/$id"))

    suspend fun jobs(repo: String, runId: Long): List<GhJob> =
        http.json.decodeFromJsonElement<JobsPage>(get("/repos/$repo/actions/runs/$runId/jobs?per_page=100&filter=latest")).jobs

    suspend fun artifacts(repo: String, runId: Long): List<GhArtifact> =
        http.json.decodeFromJsonElement<ArtifactsPage>(get("/repos/$repo/actions/runs/$runId/artifacts?per_page=100")).artifacts

    suspend fun jobLog(repo: String, jobId: Long, limitBytes: Long = 4L * 1024 * 1024): String =
        authed { String(http.getBytes("$base/repos/$repo/actions/jobs/$jobId/logs", it, limitBytes), Charsets.UTF_8) }

    suspend fun rerun(repo: String, runId: Long, failedOnly: Boolean) = authed {
        http.postJson("$base/repos/$repo/actions/runs/$runId/${if (failedOnly) "rerun-failed-jobs" else "rerun"}", buildJsonObject { }, it)
    }

    suspend fun dispatch(repo: String, workflowId: Long, ref: String, inputs: Map<String, String> = emptyMap()) = authed {
        http.postJson("$base/repos/$repo/actions/workflows/$workflowId/dispatches", buildJsonObject {
            put("ref", ref)
            putJsonObject("inputs") { inputs.forEach { (k, v) -> put(k, v) } }
        }, it)
    }

    suspend fun token(): String? = tokens?.valid()

    suspend fun read(path: String): JsonElement {
        require(path.startsWith("/") && !path.contains("://")) { "GitHub API paths start with /" }
        return get(path)
    }

    suspend fun write(path: String, body: kotlinx.serialization.json.JsonObject): JsonElement {
        require(path.startsWith("/") && !path.contains("://")) { "GitHub API paths start with /" }
        return post(path, body)
    }

    suspend fun issuesText(repo: String, state: String = "open"): String {
        val list = get("/repos/$repo/issues?state=$state&per_page=30") as? kotlinx.serialization.json.JsonArray ?: return "No issues."
        return list.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .filter { it["pull_request"] == null }
            .joinToString("\n") { o -> "#${o.str("number")} ${o.str("title")} [${o.str("state")}] by @${o.str("user", "login")}" }
            .ifBlank { "No $state issues." }
    }

    suspend fun issueText(repo: String, number: Int): String {
        val issue = get("/repos/$repo/issues/$number")
        val comments = get("/repos/$repo/issues/$number/comments?per_page=50") as? kotlinx.serialization.json.JsonArray
        return buildString {
            append("#").append(number).append(' ').append(issue.str("title")).append(" [").append(issue.str("state")).append("] by @").append(issue.str("user", "login")).append("\n\n")
            append(issue.str("body").orEmpty().take(8000))
            comments?.forEach { c -> append("\n\n— @").append(c.str("user", "login")).append(": ").append(c.str("body").orEmpty().take(2000)) }
        }
    }

    suspend fun pullsText(repo: String, state: String = "open"): String {
        val list = get("/repos/$repo/pulls?state=$state&per_page=30") as? kotlinx.serialization.json.JsonArray ?: return "No pull requests."
        return list.joinToString("\n") { o -> "#${o.str("number")} ${o.str("title")} (${o.str("head", "ref")} → ${o.str("base", "ref")}) by @${o.str("user", "login")}" }.ifBlank { "No $state pull requests." }
    }

    private suspend fun post(path: String, body: kotlinx.serialization.json.JsonObject) = authed { http.postJson("$base$path", body, it) } ?: error("Empty reply from GitHub")

    suspend fun commitTree(repo: String, sha: String): String =
        get("/repos/$repo/git/commits/$sha").str("tree", "sha") ?: error("Commit $sha has no tree")

    suspend fun createTree(repo: String, baseTree: String, files: List<Triple<String, String, String?>>): String =
        post("/repos/$repo/git/trees", buildJsonObject {
            put("base_tree", baseTree)
            put("tree", kotlinx.serialization.json.buildJsonArray {
                files.forEach { (path, mode, content) ->
                    add(buildJsonObject {
                        put("path", path); put("mode", mode); put("type", "blob")
                        if (content == null) put("sha", kotlinx.serialization.json.JsonNull) else put("content", content)
                    })
                }
            })
        }).str("sha") ?: error("GitHub did not return the tree")

    suspend fun createCommit(repo: String, message: String, tree: String, parent: String): String =
        post("/repos/$repo/git/commits", buildJsonObject {
            put("message", message); put("tree", tree)
            put("parents", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) })
        }).str("sha") ?: error("GitHub did not return the commit")

    suspend fun createBranch(repo: String, branch: String, sha: String) {
        post("/repos/$repo/git/refs", buildJsonObject { put("ref", "refs/heads/$branch"); put("sha", sha) })
    }

    suspend fun createPull(repo: String, title: String, body: String, head: String, baseBranch: String, draft: Boolean): Pair<Int, String> {
        val reply = post("/repos/$repo/pulls", buildJsonObject {
            put("title", title); put("body", body); put("head", head); put("base", baseBranch); put("draft", draft)
        })
        return (reply.str("number")?.toIntOrNull() ?: 0) to (reply.str("html_url") ?: "")
    }

    private fun JsonElement.str(vararg path: String): String? {
        var e: JsonElement? = this
        for (p in path) e = (e as? kotlinx.serialization.json.JsonObject)?.get(p)
        return (e as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    suspend fun reviewRequests(): List<GhPull> =
        http.json.decodeFromJsonElement<SearchPage>(get("/search/issues?q=is%3Apr+is%3Aopen+review-requested%3A%40me&per_page=20")).items
}
