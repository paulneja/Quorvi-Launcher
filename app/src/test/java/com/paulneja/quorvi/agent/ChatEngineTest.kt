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

    private val policyStore = MemoryPolicyStore()

    private fun engine(coordinator: ScriptedModel, orchestra: Orchestra = Orchestra(), specialists: Map<String, ScriptedModel> = emptyMap()): AgentEngine {
        val models = object : ModelSource {
            override suspend fun forRole(role: ModelRole): ModelChoice = coordinator.choice()
            override suspend fun choice(ref: ModelRef): ModelChoice? = specialists[ref.model]?.let { ModelChoice("fake", ref.model, it) }
        }
        val log = RoomChatLog(ApplicationProvider.getApplicationContext(), db.chats(), db.ops())
        return AgentEngine(
            db.ops(), models, FakeToolBox(workspace, db.ops()), scope,
            com.paulneja.quorvi.agent.policy.PolicyService(policyStore, FakeDevice()), EngineLimits(modelTimeoutMs = 5_000),
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
    fun anApprovalResumesTheSameTurnWithItsContextAndRunsTheApprovedCall() {
        val model = ScriptedModel(listOf(
            call("fs.write", "path" to "notes.txt", "content" to "hola"),
            call("shell.exec", "argv" to listOf("git", "push")),
            Turn.Text("Listo, publiqué."),
        ))
        val engine = engine(model)
        val id = submit(engine, "guardá y publicá")
        await(id, RunState.WAITING_USER)
        val approval = policyStore.approvals.single()
        policyStore.grants += com.paulneja.quorvi.agent.policy.GrantEntity("g1", id, approval.action, approval.resource, 0, usesLeft = 1)
        engine.resume(id)
        await(id, RunState.COMPLETED)
        assertEquals(1, workspace.commands.count { it == listOf("git", "push") })
        val resumed = model.requests.last().messages
        assertTrue(resumed.any { it.role == com.paulneja.quorvi.agent.models.AiRole.TOOL && it.text.contains("notes.txt") })
        assertEquals(2, resumed.count { it.role == com.paulneja.quorvi.agent.models.AiRole.TOOL && it.text.startsWith("[SUCCESS]") })
        assertEquals(1, assistant().count { it.runId == id })
        assertTrue(assistant().last().text.endsWith("Listo, publiqué."))
    }

    @Test
    fun aQuestionAlreadyAskedInTheTextIsNotRepeated() {
        val model = ScriptedModel(listOf(Turn.Calls(listOf("ask_user" to json("question" to "¿En qué ciudad vas a estar mañana temprano?")), "Necesito saber en qué ciudad estás para el pronóstico de mañana temprano. ¿Dónde estás?")))
        val id = submit(engine(model), "¿llevo campera mañana?")
        await(id, RunState.WAITING_USER)
        val text = assistant().last().text
        assertEquals(1, Regex("\\?").findAll(text).count())
    }

    @Test
    fun aSilentModelEndsInAnErrorNotAStop() {
        val id = submit(engine(ScriptedModel(listOf(Turn.Hang))), "hola")
        await(id, RunState.FAILED, RunState.COMPLETED, RunState.CANCELLED)
        val reply = assistant().last()
        assertEquals(ChatStatus.ERROR, reply.status)
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

    @Test
    fun anAiCapsuleIsInstructionsTheAgentCarriesOutEachTime() {
        val model = ScriptedModel(listOf(
            call("capsule_propose", "name" to "Resumen de PRs", "description" to "Lo que espera mi revisión", "instructions" to "Revisá mis PRs pendientes en GitHub y resumime cuáles necesitan mi atención."),
            Turn.Text("Listo."),
        ))
        val id = submit(engine(model), "haceme una cápsula que me resuma los PR")
        await(id, RunState.COMPLETED)
        val draft = Json.decodeFromString(com.paulneja.quorvi.agent.capsules.Capsule.serializer(), messages(ChatRole.CAPSULE).single().text)
        assertTrue(draft.ai)
        assertTrue(draft.steps.isEmpty())
        assertTrue(draft.prompt.startsWith("Revisá mis PRs"))
    }

    @Test
    fun theAiCanScheduleTheCapsuleItProposes() {
        val model = ScriptedModel(listOf(
            call("capsule_propose", "name" to "Buen día", "description" to "Resumen matinal", "instructions" to "Contame el clima y mi agenda.", "when" to "daily", "at" to "08:30", "days" to "2,3,4,5,6"),
            Turn.Text("Listo."),
        ))
        val id = submit(engine(model), "todas las mañanas de semana a las 8:30 resumime el día")
        await(id, RunState.COMPLETED)
        val trigger = Json.decodeFromString(com.paulneja.quorvi.agent.capsules.Capsule.serializer(), messages(ChatRole.CAPSULE).single().text).trigger
        assertEquals("daily", trigger.kind)
        assertEquals(8, trigger.hour)
        assertEquals(30, trigger.minute)
        assertEquals(listOf(2, 3, 4, 5, 6), trigger.days)
    }

    @Test
    fun theAiIsNeverGivenAToolToSubmitOrApproveAPullRequest() {
        runBlocking { db.chats().setMode(chatId, com.paulneja.quorvi.agent.chat.ChatMode.ULTRA) }
        val model = ScriptedModel(listOf(Turn.Text("ok")))
        val id = submit(engine(model), "hola")
        await(id, RunState.COMPLETED)
        val names = model.requests.single().tools.map { it.name.lowercase() }
        assertTrue(names.none { n -> listOf("submit", "approve", "merge", "push").any { it in n } })
    }

    private fun await(id: String, vararg states: RunState) = runBlocking {
        withTimeout(10_000) {
            while (true) {
                val run = db.ops().run(id)
                if (run != null && RunState.valueOf(run.state) in states) return@withTimeout run
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    private fun assistant() = runBlocking { db.chats().messages(chatId).filter { it.role == ChatRole.ASSISTANT } }

    @Test
    fun aSimpleQuestionIsAnsweredInTheChatWithTheWholeHistory() {
        val model = ScriptedModel(listOf(Turn.Text("¡Hola! ¿Qué necesitás?")))
        val id = submit(engine(model), "hola")
        await(id, RunState.COMPLETED)
        val reply = assistant().single()
        assertEquals("¡Hola! ¿Qué necesitás?", reply.text)
        assertEquals(ChatStatus.DONE, reply.status)
        assertEquals(id, reply.runId)
        assertEquals("hola", model.requests.single().messages.last().text)
    }

    @Test
    fun theCoordinatorDelegatesOnlyToAConfiguredSpecialistWithItsOwnModel() {
        val thinker = ScriptedModel(listOf(Turn.Text("Conviene la opción B por costo.")))
        val coordinator = ScriptedModel(listOf(call("delegate", "role" to "THINK", "task" to "¿A o B?"), Turn.Text("Te recomiendo B.")))
        val orchestra = Orchestra(enabled = true, active = setOf(Specialist.THINK), specialists = mapOf(Specialist.THINK to ModelBinding(ModelRef("p", "deep-1"))))
        val id = submit(engine(coordinator, orchestra, mapOf("deep-1" to thinker)), "¿A o B?")
        await(id, RunState.COMPLETED)
        assertEquals("deep-1", thinker.requests.single().model)
        val step = runBlocking { db.ops().steps(id) }.single { it.kind == "delegate:THINK" }
        assertEquals("SUCCEEDED", step.state)
        assertTrue(coordinator.requests[1].messages.any { it.text.contains("opción B") })
        assertEquals("Te recomiendo B.", assistant().single().text)
    }

    @Test
    fun aModelFailureClosesTheAnswerWithAReadableError() {
        val broken = object : com.paulneja.quorvi.agent.models.AiClient {
            override fun stream(request: com.paulneja.quorvi.agent.models.AiRequest) = kotlinx.coroutines.flow.flow<com.paulneja.quorvi.agent.models.AiEvent> {
                throw com.paulneja.quorvi.core.net.NetException(com.paulneja.quorvi.core.net.NetFailure.OFFLINE)
            }
            override suspend fun listModels() = emptyList<com.paulneja.quorvi.agent.models.AiModelInfo>()
        }
        val models = ModelSource { ModelChoice("fake", "m", broken) }
        val log = RoomChatLog(ApplicationProvider.getApplicationContext(), db.chats(), db.ops())
        val engine = AgentEngine(db.ops(), models, FakeToolBox(workspace, db.ops()), scope, com.paulneja.quorvi.agent.policy.PolicyService(MemoryPolicyStore(), FakeDevice()), EngineLimits(modelTimeoutMs = 5_000), chats = log)
        val id = submit(engine, "hola")
        await(id, RunState.FAILED)
        val reply = assistant().single()
        assertEquals(ChatStatus.ERROR, reply.status)
        assertTrue(reply.text.contains("Couldn't reach the model"))
    }

    @Test
    fun aSpecialistWithoutAChosenModelIsNeverOffered() {
        val coordinator = ScriptedModel(listOf(Turn.Text("ok")))
        val orchestra = Orchestra(enabled = true, active = setOf(Specialist.THINK, Specialist.IMAGE))
        val id = submit(engine(coordinator, orchestra), "hola")
        await(id, RunState.COMPLETED)
        assertFalse(coordinator.requests.single().tools.any { it.name == "delegate" })
    }

    @Test
    fun aQuestionPausesTheTurnAndTheNextMessageAnswersIt() {
        val coordinator = ScriptedModel(listOf(call("ask_user", "question" to "¿Para qué ciudad?"), Turn.Text("En Córdoba hace 20 °C.")))
        val engine = engine(coordinator)
        val id = submit(engine, "¿Qué clima hace?")
        await(id, RunState.WAITING_USER)
        assertEquals("¿Para qué ciudad?", assistant().single().text)
        say("Córdoba")
        engine.answer(id, "Córdoba")
        await(id, RunState.COMPLETED)
        assertEquals("En Córdoba hace 20 °C.", assistant().last().text)
        val history = coordinator.requests.last().messages.map { it.text }
        assertEquals(1, history.count { it.contains("Córdoba") })
    }

    @Test
    fun aFinishedBackgroundJobOpensANewTurnWithItsResult() {
        say("compilá la app")
        runBlocking { db.chats().upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.ASSISTANT, "Lo dejo compilando y te aviso.", ChatStatus.DONE, null, System.currentTimeMillis())) }
        val model = ScriptedModel(listOf(Turn.Text("Terminó bien, el APK está listo.")))
        val goal = "[Background job finished] background job ab12 \"Build\" → done (exit 0)"
        val id = engine(model).submit(Json.encodeToString(RunSpec.serializer(), RunSpec(goal = goal, label = "Background job: Build", origin = "chat:$chatId", chatId = chatId, jobId = "ab12")))
        await(id, RunState.COMPLETED)
        val request = model.requests.single()
        assertEquals(goal, request.messages.last().text)
        assertEquals("Background job: Build", runBlocking { db.ops().run(id) }?.goal)
        assertTrue(request.system.orEmpty().contains("background job you started has just ended"))
        assertTrue(messages(ChatRole.ASSISTANT).last().text.endsWith("el APK está listo."))
    }
}
