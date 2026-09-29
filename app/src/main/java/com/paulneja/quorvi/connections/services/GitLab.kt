package com.paulneja.quorvi.connections.services

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.CiJobEntity
import java.net.URLEncoder
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class GlPipeline(
    val id: Long,
    val iid: Int = 0,
    val status: String,
    val ref: String? = null,
    val sha: String? = null,
    val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("web_url") val webUrl: String = "",
)

@Serializable
data class GlUser(val username: String? = null)

@Serializable
data class GlJob(
    val id: Long,
    val name: String,
    val stage: String? = null,
    val status: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
)

class GitLabClient(private val http: Http, baseUrl: String, private val token: String) {
    private val api = baseUrl.trimEnd('/') + "/api/v4"
    private val json = Json { ignoreUnknownKeys = true }
    private val headers get() = mapOf("PRIVATE-TOKEN" to token)

    private fun project(path: String) = URLEncoder.encode(path, "UTF-8")

    suspend fun user(): String = json.decodeFromString(GlUser.serializer(), http.getText("$api/user", headers)).username ?: "?"

    suspend fun pipelines(path: String, perPage: Int = 15): List<GlPipeline> =
        json.decodeFromString(ListSerializer(GlPipeline.serializer()), http.getText("$api/projects/${project(path)}/pipelines?per_page=$perPage", headers))

    suspend fun jobs(path: String, pipelineId: Long): List<GlJob> =
        json.decodeFromString(ListSerializer(GlJob.serializer()), http.getText("$api/projects/${project(path)}/pipelines/$pipelineId/jobs?per_page=100", headers))

    suspend fun trace(path: String, jobId: Long): String =
        String(http.getBytes("$api/projects/${project(path)}/jobs/$jobId/trace", headers, 4L * 1024 * 1024), Charsets.UTF_8)

    suspend fun retry(path: String, pipelineId: Long) {
        http.postJson("$api/projects/${project(path)}/pipelines/$pipelineId/retry", kotlinx.serialization.json.JsonObject(emptyMap()), headers)
    }

    companion object {
        fun executionId(path: String, pipelineId: Long) = "gitlab:$path:$pipelineId"

        fun parseId(id: String): Pair<String, Long>? {
            if (!id.startsWith("gitlab:")) return null
            val rest = id.removePrefix("gitlab:")
            val pipeline = rest.substringAfterLast(':').toLongOrNull() ?: return null
            return rest.substringBeforeLast(':') to pipeline
        }

        private fun millis(value: String?) = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        fun toEntity(path: String, p: GlPipeline, now: Long) = CiExecutionEntity(
            id = executionId(path, p.id),
            provider = "gitlab",
            repo = path,
            runId = p.id,
            workflow = "pipeline",
            title = "${p.ref ?: "pipeline"} · ${p.sha?.take(8).orEmpty()}",
            branch = p.ref,
            event = p.source,
            status = when (p.status) { "success", "failed", "canceled", "skipped", "manual" -> "completed"; "running" -> "in_progress"; else -> "queued" },
            conclusion = when (p.status) { "success" -> "success"; "failed" -> "failure"; "canceled" -> "cancelled"; "skipped" -> "skipped"; else -> null },
            number = p.iid,
            attempt = 1,
            createdAt = millis(p.createdAt),
            updatedAt = millis(p.updatedAt),
            startedAt = millis(p.createdAt),
            url = p.webUrl,
            actor = null,
            fetchedAt = now,
        )

        fun toJob(executionId: String, j: GlJob) = CiJobEntity(
            id = "$executionId/${j.id}",
            executionId = executionId,
            name = listOfNotNull(j.stage, j.name).joinToString(" · "),
            status = when (j.status) { "success", "failed", "canceled", "skipped", "manual" -> "completed"; "running" -> "in_progress"; else -> "queued" },
            conclusion = when (j.status) { "success" -> "success"; "failed" -> "failure"; "canceled" -> "cancelled"; "skipped" -> "skipped"; else -> null },
            startedAt = millis(j.startedAt),
            completedAt = millis(j.finishedAt),
            steps = "[]",
            url = j.webUrl,
        )
    }
}
