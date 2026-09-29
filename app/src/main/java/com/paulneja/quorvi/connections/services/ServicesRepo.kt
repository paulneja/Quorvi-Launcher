package com.paulneja.quorvi.connections.services

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.NetGuard
import com.paulneja.quorvi.core.security.SecretVault
import com.paulneja.quorvi.ops.OpsDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

@Singleton
class ServicesRepo @Inject constructor(
    private val dao: ServicesDao,
    private val ops: OpsDao,
    private val vault: SecretVault,
    private val http: Http,
    private val okhttp: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    val all = dao.observe()
    val favorites = dao.observeFavorites()

    fun entities(serviceId: String) = dao.observeEntities(serviceId)

    private suspend fun guard(url: String) {
        val ok = withContext(Dispatchers.IO) { NetGuard.secureEnough(url) }
        require(ok) { "Use https for services on the internet; plain http only works on your local network" }
    }

    private fun token(id: String) = vault.get(tokenRef(id))

    fun homeAssistant(service: ServiceEntity) = token(service.id)?.let { HomeAssistantClient(http, service.baseUrl, it) }
    fun gitLab(service: ServiceEntity) = token(service.id)?.let { GitLabClient(http, service.baseUrl, it) }
    fun worker(service: ServiceEntity) = WorkerClient(http, service.baseUrl, token(service.id))

    suspend fun addHomeAssistant(name: String, url: String, token: String): ServiceEntity {
        guard(url)
        val status = HomeAssistantClient(http, url, token).check()
        val service = ServiceEntity(UUID.randomUUID().toString(), ServiceKind.HOME_ASSISTANT.name, name.ifBlank { "Home Assistant" }, url.trim().trimEnd('/'), createdAt = System.currentTimeMillis(), status = status, statusAt = System.currentTimeMillis())
        vault.put(tokenRef(service.id), token)
        dao.put(service)
        syncHomeAssistant(service)
        return service
    }

    suspend fun addGitLab(name: String, url: String, token: String, projects: List<String>): ServiceEntity {
        guard(url)
        val user = GitLabClient(http, url, token).user()
        val extra = json.encodeToString(ListSerializer(String.serializer()), projects.map { it.trim().trim('/') }.filter { it.contains('/') })
        val service = ServiceEntity(UUID.randomUUID().toString(), ServiceKind.GITLAB.name, name.ifBlank { "GitLab" }, url.trim().trimEnd('/'), extra = extra, createdAt = System.currentTimeMillis(), status = "@$user", statusAt = System.currentTimeMillis())
        vault.put(tokenRef(service.id), token)
        dao.put(service)
        syncGitLab(service)
        return service
    }

    suspend fun pairWorker(name: String, url: String, code: String, device: String): ServiceEntity {
        guard(url)
        val paired = WorkerClient(http, url, null).pair(code, device)
        val service = ServiceEntity(UUID.randomUUID().toString(), ServiceKind.WORKER.name, name.ifBlank { "Worker" }, url.trim().trimEnd('/'), createdAt = System.currentTimeMillis())
        vault.put(tokenRef(service.id), paired.token)
        dao.put(service)
        val hello = runCatching { worker(service).hello() }.getOrNull()
        dao.setStatus(service.id, hello?.let { "${it.hostname} · ${it.os}" }, System.currentTimeMillis())
        return service
    }

    suspend fun addDiscord(name: String, webhook: String): ServiceEntity {
        val uri = java.net.URI(webhook.trim())
        require(uri.scheme == "https" && uri.host in setOf("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com") && uri.path.startsWith("/api/webhooks/")) { "Paste a Discord webhook URL (https://discord.com/api/webhooks/…)" }
        val service = ServiceEntity(UUID.randomUUID().toString(), ServiceKind.DISCORD.name, name.ifBlank { "Discord" }, "https://discord.com", createdAt = System.currentTimeMillis(), status = "webhook")
        vault.put(tokenRef(service.id), webhook.trim())
        dao.put(service)
        return service
    }

    suspend fun postDiscord(channel: String, message: String): String {
        val service = dao.ofKind(ServiceKind.DISCORD.name).firstOrNull { it.name.equals(channel, true) || it.id == channel } ?: error("Unknown Discord channel $channel")
        val url = token(service.id) ?: error("Webhook missing")
        http.postJson("$url?wait=true", kotlinx.serialization.json.buildJsonObject { put("content", message.take(2000)); put("allowed_mentions", kotlinx.serialization.json.buildJsonObject { put("parse", kotlinx.serialization.json.JsonArray(emptyList())) }) }, emptyMap())
        return service.name
    }

    suspend fun discordChannels() = dao.ofKind(ServiceKind.DISCORD.name)

    suspend fun remove(service: ServiceEntity) {
        dao.delete(service.id)
        dao.clearEntities(service.id)
        vault.remove(tokenRef(service.id))
        if (service.kind == ServiceKind.GITLAB.name) projects(service).forEach { ops.forgetCi(it) }
    }

    fun projects(service: ServiceEntity): List<String> = runCatching { json.decodeFromString(ListSerializer(String.serializer()), service.extra) }.getOrDefault(emptyList())

    suspend fun syncHomeAssistant(service: ServiceEntity, now: Long = System.currentTimeMillis()) = runCatching {
        val client = homeAssistant(service) ?: error("No token")
        val favorites = dao.entities(service.id).filter { it.favorite }.map { it.entityId }.toSet()
        val states = client.states().filter { it.entityId.substringBefore('.') in INTERESTING }
        dao.putEntities(states.map { HaEntity(service.id, it.entityId, it.name, it.state, it.unit, it.entityId in favorites, now) })
        dao.setStatus(service.id, "${states.size} entities", now)
    }.onFailure { dao.setStatus(service.id, "error: ${it.message}", now) }

    suspend fun applyState(serviceId: String, state: HaState, now: Long = System.currentTimeMillis()) = dao.setState(serviceId, state.entityId, state.state, now)

    suspend fun setFavorite(entity: HaEntity, favorite: Boolean) = dao.setFavorite(entity.serviceId, entity.entityId, favorite)

    suspend fun toggle(entity: HaEntity) {
        val service = dao.get(entity.serviceId) ?: return
        val (domain, action) = HaParsing.toggleService(entity.entityId) ?: error("${entity.entityId} cannot be switched from Nexo")
        homeAssistant(service)?.callService(domain, action, entity.entityId) ?: error("No token")
        runCatching { syncHomeAssistant(service) }
    }

    suspend fun callService(entityId: String, domain: String?, service: String): String {
        val owner = dao.ofKind(ServiceKind.HOME_ASSISTANT.name).firstOrNull { s -> dao.entities(s.id).any { it.entityId == entityId } } ?: error("Unknown entity $entityId")
        val d = domain ?: entityId.substringBefore('.')
        homeAssistant(owner)?.callService(d, service, entityId) ?: error("No token")
        syncHomeAssistant(owner)
        return dao.entities(owner.id).firstOrNull { it.entityId == entityId }?.state ?: "unknown"
    }

    fun haEvents(service: ServiceEntity) = homeAssistant(service)?.events(okhttp)

    suspend fun syncGitLab(service: ServiceEntity, now: Long = System.currentTimeMillis()) = runCatching {
        val client = gitLab(service) ?: error("No token")
        projects(service).forEach { path -> ops.upsertCi(client.pipelines(path).map { GitLabClient.toEntity(path, it, now) }) }
        dao.setStatus(service.id, service.status?.substringBefore(" ·") ?: "ok", now)
    }.onFailure { dao.setStatus(service.id, "error: ${it.message}", now) }

    suspend fun syncGitLabExecution(executionId: String) {
        val (path, pipeline) = GitLabClient.parseId(executionId) ?: return
        val service = dao.ofKind(ServiceKind.GITLAB.name).firstOrNull { path in projects(it) } ?: return
        val client = gitLab(service) ?: return
        ops.clearCiJobs(executionId)
        ops.upsertCiJobs(client.jobs(path, pipeline).map { GitLabClient.toJob(executionId, it) })
    }

    suspend fun gitLabTrace(executionId: String, jobEntityId: String): String {
        val (path, _) = GitLabClient.parseId(executionId) ?: error("Not a GitLab pipeline")
        val service = dao.ofKind(ServiceKind.GITLAB.name).firstOrNull { path in projects(it) } ?: error("GitLab account removed")
        return gitLab(service)?.trace(path, jobEntityId.substringAfterLast('/').toLong()) ?: error("No token")
    }

    suspend fun gitLabRetry(executionId: String) {
        val (path, pipeline) = GitLabClient.parseId(executionId) ?: error("Not a GitLab pipeline")
        val service = dao.ofKind(ServiceKind.GITLAB.name).firstOrNull { path in projects(it) } ?: error("GitLab account removed")
        gitLab(service)?.retry(path, pipeline) ?: error("No token")
    }

    suspend fun workers() = dao.ofKind(ServiceKind.WORKER.name)

    suspend fun refreshWorkers(now: Long = System.currentTimeMillis()) = workers().forEach { s ->
        val hello = runCatching { worker(s).hello() }
        dao.setStatus(s.id, hello.fold({ "${it.hostname} · ${it.os}" }, { "offline: ${it.message}" }), now)
    }

    companion object {
        val INTERESTING = setOf("light", "switch", "sensor", "binary_sensor", "climate", "cover", "fan", "lock", "scene", "script", "input_boolean", "media_player", "person", "automation")
    }
}
