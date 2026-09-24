package com.paulneja.quorvi.connections

import com.paulneja.quorvi.agent.tools.CiFailureLogs
import com.paulneja.quorvi.agent.tools.ToolContext
import com.paulneja.quorvi.agent.tools.ToolStatus
import com.paulneja.quorvi.connections.github.DevicePoll
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubDeviceFlow
import com.paulneja.quorvi.connections.github.GitHubTokens
import com.paulneja.quorvi.core.net.Http
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CiDiagnosisTest {
    private val server = MockWebServer()
    private val http = Http(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun json(body: String) = MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").build()

    private fun context(ci: String?) = ToolContext("run", "run/logs", "runs/run", ci) { _, _, _ -> }

    @Test
    fun failedJobsComeBackWithTheirLogExcerptAsUntrustedData() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val tokens = GitHubTokens(MemoryVault(), GitHubDeviceFlow(http, base), "client", now = { 0L })
        tokens.save(DevicePoll.Token("tok", "r", 28_800, 3_600))
        server.enqueue(json("""{"id":7,"name":"Build","status":"completed","conclusion":"failure","head_branch":"main","head_sha":"abc123","run_number":42}"""))
        server.enqueue(json("""{"jobs":[{"id":1,"name":"lint","status":"completed","conclusion":"success","steps":[]},{"id":2,"name":"test","status":"completed","conclusion":"failure","steps":[{"name":"Run tests","status":"completed","conclusion":"failure","number":3}]}]}"""))
        server.enqueue(MockResponse.Builder().code(200).body("2026-10-09T10:00:00.0000000Z ##[group]Run ./gradlew test\n2026-10-09T10:00:01.0000000Z > Task :app:test FAILED\n2026-10-09T10:00:01.0000000Z ExampleTest > adds FAILED\n2026-10-09T10:00:02.0000000Z ##[error]Process completed with exit code 1.\n").build())

        val result = CiFailureLogs(GitHubApi(http, tokens, base)).invoke(buildJsonObject { }, context("github:octo/app:7"))

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertTrue(result.output.startsWith("<untrusted"))
        assertTrue(result.output.contains("## Job test (failure)"))
        assertTrue(result.output.contains("Failed step: Run tests"))
        assertTrue(result.output.contains("exit code 1"))
        assertTrue(!result.output.contains("Job lint"))
        assertEquals("/repos/octo/app/actions/runs/7", server.takeRequest().url.encodedPath)
        assertEquals("Bearer tok", server.takeRequest().headers["Authorization"])
        assertEquals("/repos/octo/app/actions/jobs/2/logs", server.takeRequest().url.encodedPath)
    }

    @Test
    fun withoutALinkedRunTheToolSaysSo() = runBlocking {
        val result = CiFailureLogs(GitHubApi(http, null, server.url("/").toString())).invoke(buildJsonObject { }, context(null))
        assertEquals(ToolStatus.UNSUPPORTED, result.status)
    }
}
