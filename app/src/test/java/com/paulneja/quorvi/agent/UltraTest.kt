package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.models.AiEvent
import com.paulneja.quorvi.agent.models.AiMessage
import com.paulneja.quorvi.agent.models.AiRequest
import com.paulneja.quorvi.agent.models.AiToolCall
import com.paulneja.quorvi.agent.models.AiToolSpec
import com.paulneja.quorvi.agent.models.AnthropicClient
import com.paulneja.quorvi.agent.models.ProviderKind
import com.paulneja.quorvi.agent.models.ReasoningConfig
import com.paulneja.quorvi.agent.models.ReasoningEffort
import com.paulneja.quorvi.agent.models.ReasoningSupport
import com.paulneja.quorvi.agent.ultra.CommitPolicy
import com.paulneja.quorvi.agent.ultra.CommitStyle
import com.paulneja.quorvi.agent.ultra.PrDraft
import com.paulneja.quorvi.agent.ultra.PrFile
import com.paulneja.quorvi.agent.ultra.UltraConfig
import com.paulneja.quorvi.agent.ultra.UltraPullRequests
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraTest {
    @Test
    fun coauthorsAndAiAttributionNeverPass() {
        val msg = "feat: add login\n\nCo-authored-by: Claude <noreply@anthropic.com>"
        assertEquals(1, CommitPolicy.violations(msg).size)
        assertEquals(1, CommitPolicy.violations("fix: x\n\n  co-authored-by:   Someone <a@b.c>").size)
        assertEquals(1, CommitPolicy.violations("Body\n\n🤖 Generated with Claude Code").size)
        assertEquals(1, CommitPolicy.violations("Signed-off-by: Bot <bot@x.y>").size)
        assertTrue(CommitPolicy.violations("feat: add login\n\nCloses #12").isEmpty())
        assertTrue(CommitPolicy.violations("Co-authored-by: Ana <ana@x.com>", listOf("Ana <ana@x.com>")).isEmpty())
        assertEquals(2, CommitPolicy.check(listOf("title", "Co-Authored-By: A <a@a>", "co-authored-by: B <b@b>")).size)
    }

    @Test
    fun theAgentCannotCommitPushOrUseGh() {
        assertNotNull(CommitPolicy.blockedCommand("git push origin main"))
        assertNotNull(CommitPolicy.blockedCommand("git -c user.name=x commit -m 'y'"))
        assertNotNull(CommitPolicy.blockedCommand("cd app && git   push --force"))
        assertNotNull(CommitPolicy.blockedCommand("gh pr merge 3"))
        assertNotNull(CommitPolicy.blockedCommand("git config user.email me@x.y"))
        assertNotNull(CommitPolicy.blockedCommand("git reset --hard HEAD~1"))
        assertNull(CommitPolicy.blockedCommand("./gradlew test && git status && git diff"))
        assertNull(CommitPolicy.blockedCommand("git log --oneline -5"))
    }

    @Test
    fun afterAPullRequestTheWorkspaceGoesBackToACleanBase() {
        val keep = UltraPullRequests.resetScript("", "main", 3, keepLeftovers = true)
        assertTrue(keep, keep.contains("git stash push -q -m 'left out of PR #3'"))
        assertTrue(keep, keep.indexOf("stash") < keep.indexOf("checkout"))
        val clean = UltraPullRequests.resetScript("", "main", 3, keepLeftovers = false)
        assertFalse(clean, clean.contains("stash"))
        assertTrue(clean, clean.contains("git checkout -q -f -B main FETCH_HEAD") && clean.endsWith("git clean -q -fd"))
    }

    @Test
    fun styleAndBranchFollowTheOwnersSettings() {
        assertTrue(CommitPolicy.styleRule(UltraConfig()).contains("Conventional"))
        assertTrue(CommitPolicy.styleRule(UltraConfig(commitStyle = CommitStyle.CUSTOM, customStyle = "emoji first")).contains("emoji first"))
        val branch = UltraPullRequests.branchName("nexo/", "Arreglá el login roto!")
        assertTrue(branch, branch.matches(Regex("nexo/arregl-el-login-roto-[a-z0-9]{1,5}")))
        assertTrue(UltraPullRequests.branchName("", "x").startsWith("x-"))
        val draft = PrDraft("o/r", "main", "abc", "t", "b", "m", listOf(PrFile("a.kt", "x"), PrFile("gone.kt", null)))
        assertEquals(draft, PrDraft.decode(draft.encode()))
    }

    @Test
    fun reasoningIsOfferedOnlyWhereTheProviderHonoursIt() {
        assertEquals(ReasoningSupport(effort = true, thinking = true), ReasoningSupport.of(ProviderKind.ANTHROPIC, "claude-sonnet-5"))
        assertFalse(ReasoningSupport.of(ProviderKind.ANTHROPIC, "claude-3-5-haiku").thinking)
        assertEquals(ReasoningSupport(effort = true, thinking = false), ReasoningSupport.of(ProviderKind.OPENAI_RESPONSES, "gpt-5"))
        assertEquals(ReasoningSupport.NONE, ReasoningSupport.of(ProviderKind.BRIDGE, "claude-sonnet-5.5"))
    }

    private class FakeHttp(private val events: List<String>) : Http(OkHttpClient(), Json { ignoreUnknownKeys = true }) {
        var body: JsonElement? = null
        override fun stream(url: String, body: JsonElement, headers: Map<String, String>): Flow<SseEvent> {
            this.body = body
            return flowOf(*events.map { SseEvent(null, it) }.toTypedArray())
        }
    }

    @Test
    fun anthropicStreamsTextToolsAndKeepsThinkingForTheNextTurn() = runBlocking {
        val http = FakeHttp(
            listOf(
                """{"type":"message_start","message":{"usage":{"input_tokens":12}}}""",
                """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me check"}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig1"}}""",
                """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Voy a leer"}}""",
                """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"ws_read"}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"path\":"}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"README.md\"}"}}""",
                """{"type":"message_delta","usage":{"output_tokens":40}}""",
            ),
        )
        val client = AnthropicClient(http, AnthropicClient.DEFAULT_URL, "key")
        val request = AiRequest("claude-sonnet-5", "sys", listOf(AiMessage.user("hola")), listOf(AiToolSpec("ws_read", "read", JsonObject(emptyMap()))), reasoning = ReasoningConfig(ReasoningEffort.MEDIUM, thinking = true))
        val events = client.stream(request).toList()
        assertEquals("Voy a leer", events.filterIsInstance<AiEvent.TextDelta>().joinToString("") { it.text })
        val call = events.filterIsInstance<AiEvent.ToolCalls>().single().calls.single()
        assertEquals("README.md", call.arguments["path"]?.jsonPrimitive?.content)
        val done = events.filterIsInstance<AiEvent.Done>().single()
        assertEquals(40L, done.usage?.outputTokens)
        val sent = http.body!!.jsonObject
        assertEquals("enabled", sent["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(sent["temperature"])

        val next = AiRequest("claude-sonnet-5", null, listOf(AiMessage.user("hola"), AiMessage.assistant("Voy a leer", listOf(call), done.signature), AiMessage.tool("toolu_1", "# Readme")))
        client.stream(next).toList()
        val messages = http.body!!.jsonObject["messages"]!!.jsonArray
        val assistant = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("thinking", assistant[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("sig1", assistant[0].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("tool_use", assistant.last().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tool_result", messages[2].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }
}
