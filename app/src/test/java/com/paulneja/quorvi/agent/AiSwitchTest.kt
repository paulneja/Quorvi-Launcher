package com.paulneja.quorvi.agent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paulneja.quorvi.agent.capsules.Capsule
import com.paulneja.quorvi.agent.capsules.CapsuleEntity
import com.paulneja.quorvi.agent.capsules.CapsuleStep
import com.paulneja.quorvi.agent.engine.AgentEngine
import com.paulneja.quorvi.agent.engine.AiSwitch
import com.paulneja.quorvi.agent.engine.EngineLimits
import com.paulneja.quorvi.agent.engine.ModelSource
import com.paulneja.quorvi.agent.engine.RunSpec
import com.paulneja.quorvi.agent.policy.PolicyService
import com.paulneja.quorvi.launcher.home.PageTemplate
import com.paulneja.quorvi.launcher.home.PageTemplates
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import com.paulneja.quorvi.launcher.home.GridSpec
import com.paulneja.quorvi.ops.RunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiSwitchTest {
    private lateinit var db: QuorviDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workspace = MemoryWorkspace()

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), QuorviDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() { scope.cancel(); db.close() }

    @Test
    fun aiIsOnUntilTurnedOff() = runBlocking {
        val ai = AiSwitch(db.agentConfig())
        assertTrue(ai.current())
        assertTrue(ai.enabled.first())
        ai.set(false)
        assertFalse(ai.current())
        assertFalse(ai.enabled.first())
        assertFalse(ai.now())
        ai.set(true)
        assertTrue(ai.enabled.first())
    }

    @Test
    fun engineRefusesAgentRunsButKeepsFixedCapsulesWhenAiIsOff() = runBlocking {
        val ai = AiSwitch(db.agentConfig())
        ai.set(false)
        val engine = AgentEngine(db.ops(), ModelSource { null }, FakeToolBox(workspace, db.ops()), scope, PolicyService(MemoryPolicyStore(), FakeDevice()), EngineLimits(), aiOn = ai::now)
        assertEquals("", engine.submit(engine.encode(RunSpec(goal = "summarize my week"))))
        val id = engine.submitCapsule("c1", "Note", listOf("fs.write" to JsonObject(mapOf("path" to JsonPrimitive("a.txt"), "content" to JsonPrimitive("hi")))))
        val run = withTimeout(30_000) {
            while (true) {
                val r = db.ops().run(id)
                if (r != null && RunState.valueOf(r.state).terminal) return@withTimeout r
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE") error("x")
        }
        assertEquals(RunState.COMPLETED.name, run.state)
        assertEquals(1, db.ops().observeRuns().first().size)
    }

    @Test
    fun capsuleKnowsWhetherItNeedsAi() {
        val json = Json { encodeDefaults = true }
        val fixed = Capsule(name = "Fixed", steps = listOf(CapsuleStep("fs.write")))
        val smart = Capsule(name = "Smart", prompt = "check the weather")
        assertFalse(CapsuleEntity("a", "Fixed", "bolt", json.encodeToString(Capsule.serializer(), fixed), createdAt = 0).ai)
        assertTrue(CapsuleEntity("b", "Smart", "bolt", json.encodeToString(Capsule.serializer(), smart), createdAt = 0).ai)
    }

    @Test
    fun homeSeedSkipsTheAgentPanelWithoutAi() {
        val spec = GridSpec(columns = 4, rows = 6)
        assertTrue(PageTemplates.blocks(PageTemplate.HOME, spec).any { it.kind == "panel:agent" })
        val off = PageTemplates.blocks(PageTemplate.HOME, spec, ai = false)
        assertFalse(off.any { it.kind == "panel:agent" })
        assertTrue(off.any { it.kind == "panel:shell" })
    }
}
