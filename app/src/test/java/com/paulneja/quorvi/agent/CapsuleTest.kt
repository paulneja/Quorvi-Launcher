package com.paulneja.quorvi.agent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paulneja.quorvi.agent.capsules.Capsule
import com.paulneja.quorvi.agent.capsules.CapsuleCompiler
import com.paulneja.quorvi.agent.capsules.CapsuleStep
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import com.paulneja.quorvi.agent.capsules.CompileResult
import com.paulneja.quorvi.agent.engine.AgentEngine
import com.paulneja.quorvi.agent.engine.EngineLimits
import com.paulneja.quorvi.agent.engine.ModelSource
import com.paulneja.quorvi.agent.policy.Category
import com.paulneja.quorvi.agent.policy.GrantEntity
import com.paulneja.quorvi.agent.policy.PolicyConfig
import com.paulneja.quorvi.agent.policy.PolicyService
import com.paulneja.quorvi.agent.policy.Rule
import com.paulneja.quorvi.agent.tools.ArtifactExport
import com.paulneja.quorvi.agent.tools.FsWrite
import com.paulneja.quorvi.agent.tools.ShellExec
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import com.paulneja.quorvi.ops.RunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapsuleTest {
    private lateinit var db: QuorviDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workspace = MemoryWorkspace()
    private val policyStore = MemoryPolicyStore()
    private val device = FakeDevice()

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), QuorviDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() { scope.cancel(); db.close() }

    private fun compiler() = CapsuleCompiler(mapOf("fs.write" to FsWrite(workspace), "shell.exec" to ShellExec(workspace), "artifact.export" to ArtifactExport(workspace, db.ops())))

    @Test
    fun compilerCatchesUnknownToolsAndMissingArgs() {
        val bad = compiler().compile(Capsule(name = "x", steps = listOf(CapsuleStep("fs.write", mapOf("path" to JsonPrimitive("a"))), CapsuleStep("nope.do"))))
        bad as CompileResult.Failed
        assertTrue(bad.errors.any { it.contains("missing \"content\"") })
        assertTrue(bad.errors.any { it.contains("unknown tool") })
        val empty = compiler().compile(Capsule(name = "", steps = emptyList()))
        assertTrue((empty as CompileResult.Failed).errors.size >= 2)
    }

    @Test
    fun compilerWarnsOnUnknownArgsButCompiles() {
        val ok = compiler().compile(Capsule(name = "ok", steps = listOf(CapsuleStep("fs.write", mapOf("path" to JsonPrimitive("a.txt"), "content" to JsonPrimitive("hi"), "extra" to JsonPrimitive("1"))))))
        ok as CompileResult.Ok
        assertTrue(ok.warnings.single().contains("extra"))
    }

    @Test
    fun jsonRoundTrips() {
        val c = Capsule(name = "Deploy", steps = listOf(CapsuleStep("shell.exec", mapOf("argv" to buildJsonArray { add("make") }))))
        val text = compiler().encode(c)
        assertEquals(c, (compiler().parse(text) as CompileResult.Ok).capsule)
    }

    private fun engine() = AgentEngine(db.ops(), ModelSource { null }, FakeToolBox(workspace, db.ops()), scope, PolicyService(policyStore, device), EngineLimits())

    private fun await(id: String, vararg s: RunState) = runBlocking {
        withTimeout(30_000) { while (true) { val r = db.ops().run(id); if (r != null && RunState.valueOf(r.state) in s) return@withTimeout r; delay(20) }; @Suppress("UNREACHABLE_CODE") error("x") }
    }

    @Test
    fun capsuleRunsEachStepThroughThePolicy() = runBlocking {
        val id = engine().submitCapsule("c1", "Prepare", listOf(
            "fs.write" to json("path" to "notes.md", "content" to "hello"),
            "shell.exec" to json("argv" to listOf("echo", "done")),
        ))
        val run = await(id, RunState.COMPLETED, RunState.FAILED)
        assertEquals(RunState.COMPLETED.name, run.state)
        assertEquals("hello", workspace.files["runs/$id/notes.md"])
        assertEquals(listOf(listOf("echo", "done")), workspace.commands)
        assertTrue(db.ops().steps(id).all { it.state == "SUCCEEDED" })
    }

    @Test
    fun capsulePublishStepWaitsForApprovalThenResumes() = runBlocking {
        val engine = engine()
        val id = engine.submitCapsule("c2", "Publish", listOf(
            "fs.write" to json("path" to "a.txt", "content" to "x"),
            "shell.exec" to json("argv" to listOf("git", "push")),
        ))
        await(id, RunState.WAITING_USER)
        assertTrue(workspace.commands.none { it.contains("push") })
        val approval = policyStore.approvals.single()
        policyStore.grants += GrantEntity("g", id, approval.action, approval.resource, 0, usesLeft = 1)
        engine.resume(id)
        assertEquals(RunState.COMPLETED.name, await(id, RunState.COMPLETED, RunState.FAILED).state)
        assertEquals(listOf(listOf("git", "push")), workspace.commands)
    }
}
