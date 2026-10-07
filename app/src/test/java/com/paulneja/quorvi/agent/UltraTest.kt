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
}
