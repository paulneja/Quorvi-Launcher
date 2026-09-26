package com.paulneja.quorvi.connections

import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.ToolContext
import com.paulneja.quorvi.agent.tools.githubTools
import com.paulneja.quorvi.connections.github.DevicePoll
import com.paulneja.quorvi.connections.github.GitHubApi
import com.paulneja.quorvi.connections.github.GitHubDeviceFlow
import com.paulneja.quorvi.connections.github.GitHubTokens
import com.paulneja.quorvi.core.net.Http
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubToolsTest {
    private val server = MockWebServer()
    private val http = Http(OkHttpClient(), Json { ignoreUnknownKeys = true })
    private val context = ToolContext("run", "run/logs", "runs/run", null) { _, _, _ -> }

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun json(body: String) = MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").build()

    private fun tools() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val tokens = GitHubTokens(MemoryVault(), GitHubDeviceFlow(http, base), "client", now = { 0L })
        tokens.save(DevicePoll.Token("tok", "r", 28_800, 3_600))
        githubTools(GitHubApi(http, tokens, base)).associateBy { it.name }
    }

    @Test
    fun readsAPrivateFileWithTheSession() = runBlocking {
        server.enqueue(json("""{"type":"file","path":"src/App.kt","content":"ZnVuIG1haW4oKSB7fQ==\n"}"""))
        val r = tools().getValue("github.file").invoke(buildJsonObject { put("repo", JsonPrimitive("me/secret")); put("path", JsonPrimitive("src/App.kt")) }, context)
        assertEquals("fun main() {}", r.output)
        val req = server.takeRequest()
        assertEquals("/repos/me/secret/contents/src/App.kt", req.url.encodedPath)
        assertEquals("Bearer tok", req.headers["Authorization"])
    }

    @Test
    fun writingIsExternalSoThePolicyAsksFirst() {
        val t = tools()
        assertEquals(Risk.EXTERNAL, t.getValue("github.comment").risk)
        assertEquals(Risk.EXTERNAL, t.getValue("github.issue_create").risk)
        assertTrue(t.values.filter { it.name != "github.comment" && it.name != "github.issue_create" }.all { it.risk == Risk.NETWORK })
    }
}
