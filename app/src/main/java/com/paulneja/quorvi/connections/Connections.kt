package com.paulneja.quorvi.connections

import com.paulneja.quorvi.connections.http.HttpWidgetFetcher
import com.paulneja.quorvi.connections.ssh.Docker
import com.paulneja.quorvi.connections.ssh.DockerAction
import com.paulneja.quorvi.connections.ssh.HostHealth
import com.paulneja.quorvi.connections.ssh.HostKey
import com.paulneja.quorvi.connections.ssh.HostProbe
import com.paulneja.quorvi.connections.ssh.SshAuth
import com.paulneja.quorvi.connections.ssh.SshConnector
import com.paulneja.quorvi.connections.ssh.SshCredentials
import com.paulneja.quorvi.connections.ssh.SshCrypto
import com.paulneja.quorvi.connections.ssh.SshExecResult
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.security.SecretVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object ConnectionsModule {
    @Provides
    @Singleton
    fun sshCredentials(vault: SecretVault) = SshCredentials(vault)

    @Provides
    @Singleton
    fun sshConnector(credentials: SshCredentials) = SshConnector(credentials)

    @Provides
    @Singleton
    fun httpFetcher(http: Http, vault: SecretVault) = HttpWidgetFetcher(http, vault)
}

@Singleton
class Hosts @Inject constructor(
    private val dao: ConnectionsDao,
    private val ssh: SshConnector,
    private val credentials: SshCredentials,
) {
    private val json = Json { ignoreUnknownKeys = true }
    val all = dao.observeHosts()

    fun observe(id: String) = dao.observeHost(id)
    fun containers(id: String) = dao.observeContainers(id)

    suspend fun create(name: String, hostname: String, port: Int, username: String, auth: SshAuth, password: String?): HostEntity {
        val host = HostEntity(id = UUID.randomUUID().toString(), name = name.ifBlank { hostname }, hostname = hostname.trim(), port = port, username = username.trim(), auth = auth.name, createdAt = System.currentTimeMillis())
        when (auth) {
            SshAuth.KEY -> credentials.ensureKey(host.id)
            SshAuth.PASSWORD -> credentials.setPassword(host.id, password.orEmpty())
        }
        dao.upsertHost(host)
        return host
    }

    fun publicKey(host: HostEntity): String? = credentials.key(host.id)?.let { SshCrypto.authorizedKey(it.public, "nexo@${android.os.Build.MODEL.replace(' ', '-')}") }

    suspend fun probe(host: HostEntity): HostKey = ssh.probe(host)

    suspend fun trust(host: HostEntity, key: HostKey) = dao.trust(host.id, key.type, key.fingerprint)

    suspend fun setAgentAccess(host: HostEntity, allowed: Boolean) = dao.upsertHost(host.copy(agentAccess = allowed))

    suspend fun delete(host: HostEntity) {
        dao.deleteHost(host.id)
        dao.clearContainers(host.id)
        credentials.forget(host.id)
    }

    suspend fun refresh(host: HostEntity, now: Long = System.currentTimeMillis()): Result<HostHealth> = runCatching {
        val health = HostProbe.parse(ssh.exec(host, HostProbe.COMMAND, timeoutSec = 20).stdout)
        dao.setHealth(host.id, json.encodeToString(HostHealth.serializer(), health), now, null)
        if (health.docker) runCatching { refreshContainers(host, now) }
        health
    }.onFailure { dao.setHealth(host.id, host.healthJson, now, it.message ?: it::class.simpleName) }

    suspend fun refreshContainers(host: HostEntity, now: Long = System.currentTimeMillis()) {
        val result = ssh.exec(host, Docker.LIST, timeoutSec = 20)
        if (result.exitCode != 0) throw IllegalStateException(result.stderr.ifBlank { "docker ps failed" }.take(300))
        dao.replaceContainers(host.id, Docker.parse(result.stdout).map { ContainerEntity(host.id, it.id, it.name, it.image, it.state, it.status, now) })
    }

    suspend fun docker(host: HostEntity, id: String, action: DockerAction): SshExecResult {
        val result = ssh.exec(host, Docker.action(action, id), timeoutSec = 60)
        runCatching { refreshContainers(host) }
        return result
    }

    suspend fun logs(host: HostEntity, id: String): String = ssh.exec(host, Docker.logs(id), timeoutSec = 30).stdout

    suspend fun exec(host: HostEntity, command: String, timeoutSec: Long) = ssh.exec(host, command, timeoutSec)

    fun health(host: HostEntity): HostHealth? = host.healthJson?.let { runCatching { json.decodeFromString(HostHealth.serializer(), it) }.getOrNull() }

    fun shell(host: HostEntity, columns: Int, rows: Int, term: String, input: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>) = ssh.shell(host, columns, rows, term, input)
}

@Singleton
class HttpWidgets @Inject constructor(
    private val dao: ConnectionsDao,
    private val fetcher: HttpWidgetFetcher,
    private val vault: SecretVault,
) {
    val all = dao.observeHttp()

    suspend fun get(id: String) = dao.httpWidget(id)

    suspend fun save(widget: HttpWidgetEntity, authorization: String?) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetcher.check(widget.url, widget.allowLan) }?.let { throw IllegalArgumentException(it) }
        when {
            authorization == null -> Unit
            authorization.isBlank() -> vault.remove(fetcher.authRef(widget.id))
            else -> vault.put(fetcher.authRef(widget.id), authorization)
        }
        dao.upsertHttp(widget)
    }

    fun hasAuth(id: String) = vault.has(fetcher.authRef(id))

    suspend fun delete(id: String) {
        dao.deleteHttp(id)
        vault.remove(fetcher.authRef(id))
    }

    suspend fun refresh(widget: HttpWidgetEntity, now: Long = System.currentTimeMillis()) = runCatching { fetcher.fetch(widget) }
        .onSuccess { dao.setHttpValue(widget.id, it.value, HttpWidgetFetcher.appendHistory(widget.history, it.value), now) }
        .onFailure { dao.setHttpError(widget.id, (it.message ?: it::class.simpleName.orEmpty()).take(300), now) }

    suspend fun trigger(widget: HttpWidgetEntity) = runCatching { fetcher.fetch(widget, action = true) }.also { refresh(widget) }

    suspend fun test(widget: HttpWidgetEntity) = runCatching { fetcher.fetch(widget) }
}

@Singleton
class ConnectionsRefresher @Inject constructor(
    private val dao: ConnectionsDao,
    private val hosts: Hosts,
    private val widgets: HttpWidgets,
    private val services: com.paulneja.quorvi.connections.services.ServicesRepo,
    private val servicesDao: com.paulneja.quorvi.connections.services.ServicesDao,
) {
    private val lastSync = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun due(key: String, every: Long, now: Long): Boolean {
        if (now - (lastSync[key] ?: 0L) < every) return false
        lastSync[key] = now
        return true
    }

    suspend fun refreshDue(visible: Boolean, now: Long = System.currentTimeMillis()) {
        servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.HOME_ASSISTANT.name).forEach { s ->
            if (due(s.id, if (visible) 60_000L else 15 * 60_000L, now)) services.syncHomeAssistant(s, now)
        }
        servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.GITLAB.name).forEach { s ->
            if (due(s.id, if (visible) 120_000L else 10 * 60_000L, now)) services.syncGitLab(s, now)
        }
        if (due("workers", if (visible) 5 * 60_000L else 30 * 60_000L, now)) services.refreshWorkers(now)
        dao.hosts().filter { it.hostKeyFingerprint != null }.forEach { host ->
            val every = if (visible) 120_000L else 15 * 60_000L
            if (now - (host.healthAt ?: 0) >= every) hosts.refresh(host, now)
        }
        dao.httpWidgets().forEach { w ->
            val every = maxOf(15_000L, w.refreshSec * 1000L).let { if (visible) it else maxOf(it, 15 * 60_000L) }
            if (now - (w.fetchedAt ?: 0) >= every) widgets.refresh(w, now)
        }
    }

    suspend fun nextDelay(visible: Boolean): Long {
        val shortestWidget = dao.httpWidgets().minOfOrNull { maxOf(15_000L, it.refreshSec * 1000L) }
        val hasServices = servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.HOME_ASSISTANT.name).isNotEmpty() || servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.GITLAB.name).isNotEmpty()
        return when {
            !visible -> if (hasServices) 10 * 60_000L else 15 * 60_000L
            shortestWidget != null -> minOf(shortestWidget, if (hasServices) 60_000L else 120_000L)
            hasServices -> 60_000L
            dao.hosts().isNotEmpty() -> 120_000L
            else -> Long.MAX_VALUE
        }
    }
}
