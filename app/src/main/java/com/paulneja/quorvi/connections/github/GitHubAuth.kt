package com.paulneja.quorvi.connections.github

import com.paulneja.quorvi.BuildConfig
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import android.content.Context
import com.paulneja.quorvi.core.security.FileProcessLock
import com.paulneja.quorvi.core.security.ProcessLock
import com.paulneja.quorvi.core.security.SecretVault
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.paulneja.quorvi.core.security.Vault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUri: String, val expiresInSeconds: Int, val intervalSeconds: Int)

sealed interface DevicePoll {
    data class Token(val access: String, val refresh: String?, val expiresIn: Long?, val refreshExpiresIn: Long?) : DevicePoll
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data object Expired : DevicePoll
    data object Denied : DevicePoll
}

class GitHubDeviceFlow(private val http: Http, private val base: String = "https://github.com") {
    suspend fun requestCode(clientId: String, scope: String = SCOPE): DeviceCode {
        val json = http.postForm("$base/login/device/code", mapOf("client_id" to clientId, "scope" to scope), ACCEPT).jsonObject
        return DeviceCode(
            deviceCode = json.str("device_code") ?: error("No device code"),
            userCode = json.str("user_code") ?: error("No user code"),
            verificationUri = json.str("verification_uri") ?: "https://github.com/login/device",
            expiresInSeconds = json.str("expires_in")?.toIntOrNull() ?: 900,
            intervalSeconds = json.str("interval")?.toIntOrNull() ?: 5,
        )
    }

    suspend fun poll(clientId: String, deviceCode: String): DevicePoll = parse(
        http.postForm("$base/login/oauth/access_token", mapOf("client_id" to clientId, "device_code" to deviceCode, "grant_type" to GRANT), ACCEPT).jsonObject,
    )

    suspend fun refresh(clientId: String, refreshToken: String): DevicePoll.Token {
        val json = http.postForm("$base/login/oauth/access_token", mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refreshToken), ACCEPT).jsonObject
        val token = parse(json) as? DevicePoll.Token ?: throw HttpStatusException(401, json.str("error") ?: "refresh failed")
        if (token.refresh.isNullOrBlank()) throw HttpStatusException(401, "refresh returned no refresh token")
        return token
    }

    companion object {
        const val SCOPE = "repo read:user"
        private const val GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private val ACCEPT = mapOf("Accept" to "application/json")

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

        fun parse(json: JsonObject): DevicePoll {
            json.str("access_token")?.let {
                return DevicePoll.Token(it, json.str("refresh_token"), json.str("expires_in")?.toLongOrNull(), json.str("refresh_token_expires_in")?.toLongOrNull())
            }
            return when (json.str("error")) {
                "authorization_pending" -> DevicePoll.Pending
                "slow_down" -> DevicePoll.SlowDown
                "expired_token" -> DevicePoll.Expired
                "access_denied" -> DevicePoll.Denied
                else -> throw HttpStatusException(400, json.str("error_description") ?: json.toString())
            }
        }
    }
}

@Serializable
data class GitHubCredentials(val access: String, val refresh: String? = null, val expiresAt: Long? = null, val refreshExpiresAt: Long? = null)

class GitHubTokens(
    private val vault: Vault,
    private val flow: GitHubDeviceFlow,
    private val clientId: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val processLock: ProcessLock = ProcessLock.None,
) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun credentials(): GitHubCredentials? = vault.get(SecretVault.GITHUB)?.let { runCatching { json.decodeFromString(GitHubCredentials.serializer(), it) }.getOrNull() }

    fun signedIn(): Boolean = credentials() != null

    fun save(token: DevicePoll.Token) {
        val t = now()
        vault.put(SecretVault.GITHUB, json.encodeToString(GitHubCredentials.serializer(), GitHubCredentials(token.access, token.refresh, token.expiresIn?.let { t + it * 1000 }, token.refreshExpiresIn?.let { t + it * 1000 })))
    }

    fun clear() = vault.remove(SecretVault.GITHUB)

    private fun fresh(c: GitHubCredentials) = c.refresh == null || c.expiresAt == null || c.expiresAt > now() + MARGIN_MS

    suspend fun valid(): String? = lock.withLock {
        val current = credentials() ?: return@withLock null
        if (fresh(current)) return@withLock current.access
        processLock.withLock {
            val again = credentials() ?: return@withLock null
            if (fresh(again)) again.access else renew(again)
        }
    }

    suspend fun afterUnauthorized(failed: String): String? = lock.withLock {
        processLock.withLock {
            val current = credentials() ?: return@withLock null
            if (current.access != failed) return@withLock current.access
            if (current.refresh == null) null else renew(current)
        }
    }

    private suspend fun renew(current: GitHubCredentials): String {
        val refresh = current.refresh ?: return current.access
        val token = flow.refresh(clientId, refresh)
        save(token)
        return token.access
    }

    companion object {
        const val MARGIN_MS = 5 * 60_000L
    }
}

sealed interface SignInState {
    data object Idle : SignInState
    data object Requesting : SignInState
    data class Waiting(val userCode: String, val uri: String, val expiresAt: Long) : SignInState
    data class Done(val login: String) : SignInState
    data class Failed(val reason: String) : SignInState
}

@Singleton
class GitHubSession @Inject constructor(http: Http, vault: SecretVault, @ApplicationContext context: Context) {
    val clientId: String = BuildConfig.GITHUB_CLIENT_ID
    val flow = GitHubDeviceFlow(http)
    val tokens = GitHubTokens(vault, flow, clientId, processLock = FileProcessLock(File(context.noBackupFilesDir, "vault/.github.lock")))
}

@Singleton
class GitHubAccount @Inject constructor(
    session: GitHubSession,
    private val api: GitHubApi,
    private val scope: CoroutineScope,
) {
    val clientId: String = session.clientId
    private val flow = session.flow
    val tokens = session.tokens
    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()
    private val _login = MutableStateFlow<String?>(null)
    val login: StateFlow<String?> = _login.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch { if (tokens.signedIn()) _login.value = runCatching { api.viewer().login }.getOrNull() }
    }

    val available: Boolean get() = clientId.isNotBlank()

    fun start() {
        if (job?.isActive == true || !available) return
        _state.value = SignInState.Requesting
        job = scope.launch { run() }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SignInState.Idle
    }

    fun signOut() {
        tokens.clear()
        _login.value = null
        _state.value = SignInState.Idle
    }

    private suspend fun run() {
        try {
            var code = flow.requestCode(clientId)
            var interval = code.intervalSeconds.coerceAtLeast(1).toLong()
            var expiresAt = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            _state.value = SignInState.Waiting(code.userCode, code.verificationUri, expiresAt)
            var failures = 0
            while (currentCoroutineContext().isActive) {
                if (System.currentTimeMillis() >= expiresAt) {
                    code = flow.requestCode(clientId)
                    interval = code.intervalSeconds.coerceAtLeast(1).toLong()
                    expiresAt = System.currentTimeMillis() + code.expiresInSeconds * 1000L
                    _state.value = SignInState.Waiting(code.userCode, code.verificationUri, expiresAt)
                }
                delay(interval * 1000)
                val poll = try {
                    flow.poll(clientId, code.deviceCode).also { failures = 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++failures > 12) throw e
                    continue
                }
                when (poll) {
                    DevicePoll.Pending -> Unit
                    DevicePoll.SlowDown -> interval += 5
                    DevicePoll.Expired -> expiresAt = 0
                    DevicePoll.Denied -> { _state.value = SignInState.Failed("denied"); return }
                    is DevicePoll.Token -> {
                        tokens.save(poll)
                        val login = api.viewer().login
                        _login.value = login
                        _state.value = SignInState.Done(login)
                        return
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SignInState.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }
}
