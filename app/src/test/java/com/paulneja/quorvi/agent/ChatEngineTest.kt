package com.paulneja.quorvi.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paulneja.quorvi.agent.chat.ChatEntity
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.chat.ChatStatus
import com.paulneja.quorvi.agent.chat.RoomChatLog
import com.paulneja.quorvi.agent.engine.AgentEngine
import com.paulneja.quorvi.agent.engine.EngineLimits
import com.paulneja.quorvi.agent.engine.ModelChoice
import com.paulneja.quorvi.agent.engine.ModelSource
import com.paulneja.quorvi.agent.engine.RunSpec
import com.paulneja.quorvi.agent.models.ModelRole
import com.paulneja.quorvi.agent.orchestra.ModelBinding
import com.paulneja.quorvi.agent.orchestra.ModelRef
import com.paulneja.quorvi.agent.orchestra.Orchestra
import com.paulneja.quorvi.agent.orchestra.Specialist
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import com.paulneja.quorvi.ops.RunState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
class ChatEngineTest {
    private lateinit var db: QuorviDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workspace = MemoryWorkspace()
    private val chatId = "c1"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QuorviDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.chats().upsertChat(ChatEntity(chatId, "Test", 1, 1)) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun engine(coordinator: ScriptedModel, orchestra: Orchestra = Orchestra(), specialists: Map<String, ScriptedModel> = emptyMap()): AgentEngine {
        val models = object : ModelSource {
            override suspend fun forRole(role: ModelRole): ModelChoice = coordinator.choice()
            override suspend fun choice(ref: ModelRef): ModelChoice? = specialists[ref.model]?.let { ModelChoice("fake", ref.model, it) }
        }
        val log = RoomChatLog(ApplicationProvider.getApplicationContext(), db.chats(), db.ops())
        return AgentEngine(
            db.ops(), models, FakeToolBox(workspace, db.ops()), scope,
            com.paulneja.quorvi.agent.policy.PolicyService(MemoryPolicyStore(), FakeDevice()), EngineLimits(modelTimeoutMs = 5_000),
            chats = log, orchestra = { orchestra },
        )
    }

    private fun say(text: String) = runBlocking {
        db.chats().upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.USER, text, ChatStatus.DONE, null, System.currentTimeMillis()))
    }

    private fun submit(engine: AgentEngine, text: String, meta: Boolean = false): String {
        say(text)
        return engine.submit(Json.encodeToString(RunSpec.serializer(), RunSpec(goal = text, origin = "chat:$chatId", chatId = chatId, meta = meta)))
    }

    private fun messages(role: String) = runBlocking { db.chats().messages(chatId).filter { it.role == role } }

    private fun submitSpec(engine: AgentEngine, text: String, spec: RunSpec): String {
        say(text)
        return engine.submit(Json.encodeToString(RunSpec.serializer(), spec))
    }

    @Test
    fun planModeUsesThePlannerCannotWriteAndHandsAPlanForApproval() {
        val planner = ScriptedModel(listOf(call("plan_ready", "plan" to "1. Leer el código\n2. Cambiar la pantalla", "summary" to "Te dejé el plan.")))
        val coordinator = ScriptedModel(listOf(Turn.Text("no debería hablar")))
        val orchestra = Orchestra(specialists = mapOf(com.paulneja.quorvi.agent.orchestra.Specialist.PLAN to ModelBinding(ModelRef("p", "planner-1"))))
        val id = submitSpec(engine(coordinator, orchestra, mapOf("planner-1" to planner)), "/plan rehacé la pantalla", RunSpec(goal = "/plan rehacé la pantalla", origin = "chat:$chatId", chatId = chatId, plan = true))
        await(id, RunState.COMPLETED)
        assertTrue(coordinator.requests.isEmpty())
        val names = planner.requests.single().tools.map { it.name }
        assertTrue("plan_ready" in names)
        assertTrue(names.none { it == "fs_write" || it == "shell_exec" || it == "pr_propose" || it == "capsule_propose" })
        assertEquals("1. Leer el código\n2. Cambiar la pantalla", messages(ChatRole.PLAN).single().text)
    }

    @Test
    fun researchModeAsksToDigDeeperWithoutAStepLimit() {
        val listing = (1..4).map { call("fs.list", "path" to ".") }
        val model = ScriptedModel(listing + Turn.Text("Encontré esto, con fuentes."))
        val id = submitSpec(engine(model, Orchestra(maxRounds = 2)), "@investigar algo", RunSpec(goal = "@investigar algo", origin = "chat:$chatId", chatId = chatId, research = true))
        await(id, RunState.COMPLETED)
        assertTrue(model.requests.first().system.orEmpty().contains("DEEP RESEARCH"))
        assertEquals(5, model.requests.size)
    }

    @Test
    fun metaKeepsWorkingUntilItCallsFinish() {
        val model = ScriptedModel(listOf(Turn.Text("Empiezo."), Turn.Text("Sigo."), call("finish", "status" to "done", "reason" to "Listo.")))
        val id = submit(engine(model), "armá todo", meta = true)
        await(id, RunState.COMPLETED)
        assertEquals(3, model.requests.size)
        assertTrue(model.requests[1].messages.last().text.contains("Meta mode"))
        assertTrue(model.requests.first().tools.any { it.name == "finish" })
    }

    @Test
    fun metaThatHitsAWallSaysWhatItNeeds() {
        val model = ScriptedModel(listOf(call("finish", "status" to "blocked", "reason" to "Falta la clave del servidor.")))
        val id = submit(engine(model), "deployá", meta = true)
        await(id, RunState.COMPLETED)
        val text = assistant().single().text
        assertTrue(text.contains("Blocked:"))
        assertTrue(text.contains("Falta la clave del servidor."))
    }

    @Test
    fun aNormalChatOffersUltraAsACardOnlyOnce() {
        val model = ScriptedModel(listOf(call("offer_ultra", "reason" to "Es un proyecto de código."), call("offer_ultra", "reason" to "otra vez"), Turn.Text("Te dejo la opción.")))
        val id = submit(engine(model), "haceme una app")
        await(id, RunState.COMPLETED)
        val offers = messages(ChatRole.OFFER)
        assertEquals(1, offers.size)
        assertEquals("Es un proyecto de código.", offers.single().text)
    }

    @Test
    fun anUltraChatHasNoStepLimitPerAnswer() {
        runBlocking { db.chats().setMode(chatId, com.paulneja.quorvi.agent.chat.ChatMode.ULTRA) }
        val listing = (1..5).map { call("fs.list", "path" to ".") }
        val model = ScriptedModel(listing + Turn.Text("Terminé."))
        val id = submit(engine(model, Orchestra(maxRounds = 2)), "revisá todo")
        await(id, RunState.COMPLETED)
        assertEquals(6, model.requests.size)
        assertTrue(model.requests.dropLast(1).all { r -> r.tools.isNotEmpty() })
        assertTrue(messages(ChatRole.OFFER).isEmpty())
    }

    @Test
    fun aCapsuleIsOnlyDraftedNeverSaved() {
        val bad = "[{\"tool\":\"nope\"}]"

        val good = "[{\"tool\":\"fs_list\",\"args\":{\"path\":\".\"}}]"
        val model = ScriptedModel(listOf(
            call("capsule_propose", "name" to "Listar", "description" to "Lista archivos", "steps" to bad),
            call("capsule_propose", "name" to "Listar", "description" to "Lista archivos", "steps" to good),
            Turn.Text("Te dejé la cápsula."),
        ))
        val id = submit(engine(model), "haceme una cápsula que liste archivos")
        await(id, RunState.COMPLETED)
        assertTrue(model.requests[1].messages.last().text.contains("does not compile"))
        assertEquals(1, messages(ChatRole.CAPSULE).size)
        assertTrue(messages(ChatRole.CAPSULE).single().text.contains("\"fs.list\""))
        assertTrue(model.requests[1].messages.last().text.contains("Tools a capsule can use"))
        assertTrue(runBlocking { db.capsules().observe().first() }.isEmpty())
    }
}
