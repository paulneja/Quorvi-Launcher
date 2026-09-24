package com.paulneja.quorvi.connections

import com.paulneja.quorvi.connections.github.ActionsLog
import com.paulneja.quorvi.connections.github.DevicePoll
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubDeviceFlow
import com.paulneja.quorvi.connections.github.GitHubTokens
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import com.paulneja.quorvi.core.security.CredentialRef
import com.paulneja.quorvi.core.security.Vault
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

internal class MemoryVault : Vault {
    val map = mutableMapOf<String, String>()
    override fun get(ref: CredentialRef) = map[ref.name]
    override fun put(ref: CredentialRef, value: String) { map[ref.name] = value }
    override fun remove(ref: CredentialRef) { map.remove(ref.name) }
}

class GitHubConnectorTest {
    private val server = MockWebServer()
    private val http = Http(OkHttpClient(), Json { ignoreUnknownKeys = true })
    private var clock = 1_000_000L

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    private fun tokens(vault: MemoryVault): GitHubTokens = GitHubTokens(vault, GitHubDeviceFlow(http, base()), "client", now = { clock })

    @Test
    fun devicePollStatesAreParsed() {
        assertEquals(DevicePoll.Pending, GitHubDeviceFlow.parse(buildJsonObject { put("error", "authorization_pending") }))
        assertEquals(DevicePoll.SlowDown, GitHubDeviceFlow.parse(buildJsonObject { put("error", "slow_down") }))
        assertEquals(DevicePoll.Denied, GitHubDeviceFlow.parse(buildJsonObject { put("error", "access_denied") }))
        val token = GitHubDeviceFlow.parse(buildJsonObject { put("access_token", "a"); put("refresh_token", "r"); put("expires_in", "28800") }) as DevicePoll.Token
        assertEquals(28800L, token.expiresIn)
    }

    @Test
    fun expiringTokenIsRefreshedBeforeUse() = runBlocking {
        val vault = MemoryVault()
        val t = tokens(vault)
        t.save(DevicePoll.Token("old", "r1", 60, 3600))
        server.enqueue(json(200, """{"access_token":"new","refresh_token":"r2","expires_in":28800}"""))
        assertEquals("new", t.valid())
        assertEquals("r2", t.credentials()?.refresh)
        val form = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(form.contains("grant_type=refresh_token") && form.contains("refresh_token=r1"))
    }

    @Test
    fun unauthorizedTriggersOneRefreshAndRetry() = runBlocking {
        val vault = MemoryVault()
        val t = tokens(vault)
        t.save(DevicePoll.Token("old", "r1", 28800, 3600))
        val api = GitHubApi(http, t, base())
        server.enqueue(json(401, """{"message":"Bad credentials"}"""))
        server.enqueue(json(200, """{"access_token":"new","refresh_token":"r2","expires_in":28800}"""))
        server.enqueue(json(200, """{"login":"paulneja"}"""))
        assertEquals("paulneja", api.viewer().login)
        server.takeRequest()
        server.takeRequest()
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
    }
}
