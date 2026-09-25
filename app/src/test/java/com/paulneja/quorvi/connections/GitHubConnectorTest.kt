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

    @Test
    fun rateLimitAndServerErrorsAreTyped() = runBlocking {
        val vault = MemoryVault()
        val t = tokens(vault)
        t.save(DevicePoll.Token("tok", null, null, null))
        val api = GitHubApi(http, t, base())
        server.enqueue(MockResponse.Builder().code(403).body("{}").addHeader("x-ratelimit-remaining", "0").build())
        try { api.viewer(); fail() } catch (e: HttpStatusException) { assertTrue(e.rateLimited); assertTrue(!e.unauthorized) }
        server.enqueue(json(500, "{}"))
        try { api.viewer(); fail() } catch (e: HttpStatusException) { assertTrue(e.transient) }
    }

    @Test
    fun reposArePaginatedUntilAShortPage() = runBlocking {
        val vault = MemoryVault()
        val t = tokens(vault)
        t.save(DevicePoll.Token("tok", null, null, null))
        val api = GitHubApi(http, t, base())
        val full = (1..100).joinToString(",", "[", "]") { """{"full_name":"o/r$it"}""" }
        server.enqueue(json(200, full))
        server.enqueue(json(200, """[{"full_name":"o/last","private":true}]"""))
        val repos = api.repos()
        assertEquals(101, repos.size)
        assertTrue(repos.last().private)
    }

    @Test
    fun runsAndJobsDecode() = runBlocking {
        val vault = MemoryVault()
        val t = tokens(vault)
        t.save(DevicePoll.Token("tok", null, null, null))
        val api = GitHubApi(http, t, base())
        server.enqueue(json(200, """{"total_count":1,"workflow_runs":[{"id":7,"name":"CI","status":"completed","conclusion":"failure","run_number":42,"head_branch":"main","html_url":"u"}]}"""))
        server.enqueue(json(200, """{"jobs":[{"id":9,"name":"build","conclusion":"failure","steps":[{"name":"Checkout","number":1,"conclusion":"success"},{"name":"Test","number":2,"conclusion":"failure"}]}]}"""))
        val run = api.runs("o/r").single()
        assertEquals("failure", run.conclusion)
        assertEquals(42, run.runNumber)
        val job = api.jobs("o/r", run.id).single()
        assertEquals("Test", job.steps.first { it.conclusion == "failure" }.name)
    }

    @Test
    fun logsSplitIntoStepsWithErrors() {
        val raw = """
            2024-05-01T10:00:00.0000000Z ##[group]Run actions/checkout@v4
            2024-05-01T10:00:01.0000000Z Syncing repository
            2024-05-01T10:00:02.0000000Z ##[endgroup]
            2024-05-01T10:00:03.0000000Z ##[group]Run ./gradlew test
            2024-05-01T10:00:04.0000000Z > Task :app:test FAILED
            2024-05-01T10:00:05.0000000Z ##[error]Process completed with exit code 1.
        """.trimIndent()
        val sections = ActionsLog.sections(raw)
        assertEquals(listOf("actions/checkout@v4", "./gradlew test"), sections.map { it.title })
        assertEquals(listOf("Process completed with exit code 1."), sections[1].errors)
        assertTrue(ActionsLog.failureExcerpt(raw).startsWith("Step: ./gradlew test"))
    }
}
