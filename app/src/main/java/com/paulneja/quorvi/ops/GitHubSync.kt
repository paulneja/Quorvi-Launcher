package com.paulneja.quorvi.ops

import com.paulneja.quorvi.connections.github.GhJob
import com.paulneja.quorvi.connections.github.GhRun
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class SyncReport(val repos: Int, val runs: Int, val failures: Map<String, String>)

@Singleton
class GitHubSync @Inject constructor(
    private val api: GitHubApi,
    private val session: GitHubSession,
    private val dao: OpsDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val signedIn: Boolean get() = session.tokens.signedIn()

    suspend fun syncAll(now: Long = System.currentTimeMillis()): SyncReport {
        if (!signedIn) return SyncReport(0, 0, emptyMap())
        val failures = mutableMapOf<String, String>()
        var count = 0
        val repos = dao.watched()
        repos.forEach { repo ->
            runCatching { syncRepo(repo.fullName, now) }
                .onSuccess { count += it }
                .onFailure { failures[repo.fullName] = it.message ?: it::class.simpleName.orEmpty() }
        }
        return SyncReport(repos.size, count, failures)
    }

    suspend fun syncRepo(repo: String, now: Long = System.currentTimeMillis()): Int {
        val runs = api.runs(repo, perPage = 15)
        dao.upsertCi(runs.map { it.toEntity(repo, now) })
        runs.filter { it.status != "completed" || it.conclusion == "failure" }.take(3).forEach { syncJobs(repo, it.id) }
        return runs.size
    }

    suspend fun syncExecution(id: String) {
        val (repo, runId) = parseId(id) ?: return
        val run = api.run(repo, runId)
        dao.upsertCi(listOf(run.toEntity(repo, System.currentTimeMillis())))
        syncJobs(repo, runId)
    }

    private suspend fun syncJobs(repo: String, runId: Long) {
        val id = executionId(repo, runId)
        val jobs = api.jobs(repo, runId)
        dao.clearCiJobs(id)
        dao.upsertCiJobs(jobs.map { it.toEntity(id) })
    }

    private fun GhRun.toEntity(repo: String, now: Long) = CiExecutionEntity(
        id = executionId(repo, id),
        provider = "github",
        repo = repo,
        runId = id,
        workflow = name ?: "workflow",
        title = displayTitle ?: name ?: "#$runNumber",
        branch = branch,
        event = event,
        status = status ?: "unknown",
        conclusion = conclusion,
        number = runNumber,
        attempt = attempt,
        createdAt = createdAt.millis(),
        updatedAt = updatedAt.millis(),
        startedAt = startedAt.millis(),
        url = htmlUrl,
        actor = actor?.login,
        fetchedAt = now,
    )

    private fun GhJob.toEntity(executionId: String) = CiJobEntity(
        id = "$executionId/$id",
        executionId = executionId,
        name = name,
        status = status,
        conclusion = conclusion,
        startedAt = startedAt.millis(),
        completedAt = completedAt.millis(),
        steps = json.encodeToString(ListSerializer(com.paulneja.quorvi.connections.github.GhStep.serializer()), steps),
        url = htmlUrl,
    )

    companion object {
        fun executionId(repo: String, runId: Long) = "github:$repo:$runId"

        fun parseId(id: String): Pair<String, Long>? {
            val parts = id.split(':')
            if (parts.size != 3 || parts[0] != "github") return null
            return parts[1] to (parts[2].toLongOrNull() ?: return null)
        }

        fun jobIdOf(jobEntityId: String): Long? = jobEntityId.substringAfterLast('/').toLongOrNull()

        private fun String?.millis(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}
