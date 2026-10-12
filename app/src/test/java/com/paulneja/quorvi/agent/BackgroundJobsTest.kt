package com.paulneja.quorvi.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paulneja.quorvi.agent.chat.ChatEntity
import com.paulneja.quorvi.agent.chat.ChatLogic
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.engine.AgentLimits
import com.paulneja.quorvi.agent.engine.EngineLimits
import com.paulneja.quorvi.agent.jobs.JobNote
import com.paulneja.quorvi.agent.jobs.JobRunner
import com.paulneja.quorvi.agent.jobs.JobScripts
import com.paulneja.quorvi.agent.jobs.JobState
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import com.paulneja.quorvi.runtime.ExecRequest
import com.paulneja.quorvi.runtime.ExecResult
import com.paulneja.quorvi.runtime.ExportResult
import com.paulneja.quorvi.runtime.FileEntry
import com.paulneja.quorvi.runtime.Workspace
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class HostShell(val home: File) : Workspace {
    override suspend fun exec(request: ExecRequest): ExecResult {
        val dir = request.cwd?.let { File(home, it) } ?: home
        dir.mkdirs()
        val process = ProcessBuilder(request.argv).directory(dir).apply { environment()["HOME"] = home.absolutePath }.start()
        process.outputStream.close()
        val finished = process.waitFor(request.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        return ExecResult(if (finished) process.exitValue() else -1, process.inputStream.bufferedReader().readText(), process.errorStream.bufferedReader().readText(), !finished, false, 0)
    }
    override suspend fun kill(execId: String) = false
    override suspend fun killAll() = 0
    override suspend fun read(path: String, maxBytes: Int) = File(home, path).readText()
    override suspend fun write(path: String, content: String): Long { File(home, path).writeText(content); return content.length.toLong() }
    override suspend fun list(path: String): List<FileEntry> = emptyList()
    override suspend fun export(path: String, displayName: String, mime: String): ExportResult = throw UnsupportedOperationException()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundJobsTest {
    private lateinit var db: QuorviDatabase
    private lateinit var home: File
    private lateinit var jobs: JobRunner

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QuorviDatabase::class.java).allowMainThreadQueries().build()
        home = File(System.getProperty("java.io.tmpdir"), "jobs-home-" + System.nanoTime()).apply { mkdirs() }
        jobs = JobRunner(HostShell(home), db.chats())
        runBlocking { db.chats().upsertChat(ChatEntity("c1", "Test", 1, 1)) }
    }

    @After
    fun tearDown() {
        db.close()
        home.deleteRecursively()
    }

    private fun sweepUntilFinished(timeoutMs: Long = 10_000): List<com.paulneja.quorvi.agent.jobs.FinishedJob> = runBlocking {
        val until = System.currentTimeMillis() + timeoutMs
        var done = jobs.sweep()
        while (done.isEmpty() && System.currentTimeMillis() < until) {
            Thread.sleep(200)
            done = jobs.sweep()
        }
        done
    }

    @Test
    fun aJobRunsDetachedAndIsPickedUpWithItsOutputWhenItEnds() = runBlocking {
        val note = jobs.start("c1", "r1", "Build", "sleep 1; echo built-ok", "chats/c1")
        assertTrue(jobs.sweep().isEmpty())
        assertEquals(1, jobs.aliveCount())
        val finished = sweepUntilFinished().single()
        assertEquals(note.id, finished.note.id)
        assertEquals(JobState.DONE, finished.note.state)
        assertEquals(0, finished.note.exitCode)
        assertTrue(finished.tail.contains("built-ok"))
        assertTrue(finished.note.endedAt!! - finished.note.startedAt < 4_000)
        assertTrue(jobs.saveEnded(finished))
        assertFalse(jobs.saveEnded(finished))
        assertEquals(1, jobs.pending())
        jobs.markContinued(finished)
        assertEquals(0, jobs.pending())
        assertTrue(jobs.goal(finished).contains("built-ok"))
    }

    @Test
    fun aFailingJobReportsItsExitCode() = runBlocking {
        jobs.start("c1", "r1", "", "echo nope >&2; exit 3", "chats/c1")
        val finished = sweepUntilFinished().single()
        assertEquals(JobState.FAILED, finished.note.state)
        assertEquals(3, finished.note.exitCode)
        assertTrue(finished.tail.contains("nope"))
    }

    @Test
    fun stoppingKillsTheWholeJobAndSkipsTheFollowUp() = runBlocking {
        val note = jobs.start("c1", "r1", "Long", "sleep 60 & sleep 60; echo never", "chats/c1")
        val pid = File(home, "jobs/${note.id}/pid").readText().trim()
        val stopped = jobs.stop("c1", note.id)!!
        assertEquals(JobState.STOPPED, stopped.state)
        assertEquals(0, jobs.pending())
        val alive = ProcessBuilder("bash", "-c", "pgrep -g $pid || true").start().inputStream.bufferedReader().readText().trim()
        assertEquals("", alive)
        assertTrue(jobs.sweep().isEmpty())
    }

    @Test
    fun aJobWhoseProcessVanishedIsReportedAsLost() = runBlocking {
        val note = jobs.start("c1", "r1", "Gone", "sleep 60", "chats/c1")
        val pid = File(home, "jobs/${note.id}/pid").readText().trim()
        ProcessBuilder("bash", "-c", "kill -KILL -- -$pid").start().waitFor()
        val finished = sweepUntilFinished().single()
        assertEquals(JobState.LOST, finished.note.state)
    }

    @Test
    fun jobsOfAnotherChatAreNotVisible() = runBlocking {
        val note = jobs.start("c1", "r1", "Mine", "true", "chats/c1")
        assertEquals(null, jobs.find("c2", note.id))
        assertEquals(1, jobs.ofChat("c1").size)
    }

    @Test
    fun probeOutputIsParsed() {
        val parsed = JobScripts.parse("a1 running\nb2 done 0 1700000000\nc3 done 2\nd4 lost\n\ngarbage")
        assertEquals(JobState.RUNNING, parsed["a1"]!!.state)
        assertEquals(JobState.DONE, parsed["b2"]!!.state)
        assertEquals(1_700_000_000_000L, parsed["b2"]!!.endedAt)
        assertEquals(2, parsed["c3"]!!.exitCode)
        assertEquals(JobState.FAILED, parsed["c3"]!!.state)
        assertEquals(JobState.LOST, parsed["d4"]!!.state)
        assertEquals(4, parsed.size)
    }

    @Test
    fun theChatHistoryShowsTheJobAsANote() {
        val note = JobNote("ab12", "Build", "make", "chats/c1", 0, JobState.DONE, 0, 65_000)
        val history = ChatLogic.history(listOf(
            ChatMessageEntity("u", "c1", ChatRole.USER, "compilá", createdAt = 1),
            ChatMessageEntity("job-ab12", "c1", ChatRole.JOB, note.encode(), createdAt = 2),
        ))
        assertEquals(2, history.size)
        assertTrue(history[1].text.contains("ab12"))
        assertTrue(history[1].text.contains("1m 5s"))
        assertTrue(history[1].text.contains("jobs/ab12/out.log"))
    }

    @Test
    fun limitsAreClampedAndDriveTheEngine() {
        val l = AgentLimits(commandMinutes = 999, sshMinutes = 0, modelIdleSeconds = 5, modelTotalMinutes = 30).clamped()
        assertEquals(AgentLimits.MAX_COMMAND_MINUTES, l.commandMinutes)
        assertEquals(1, l.sshMinutes)
        assertEquals(30, l.modelIdleSeconds)
        val engine = l.applyTo(EngineLimits())
        assertEquals(30_000L, engine.modelTimeoutMs)
        assertEquals(30 * 60_000L, engine.modelTotalMs)
        assertEquals((AgentLimits.MAX_COMMAND_MINUTES + 1) * 60_000L, engine.toolTimeoutMs)
        assertEquals(AgentLimits(), AgentLimits.decode(AgentLimits.encode(AgentLimits())))
        assertEquals(AgentLimits(), AgentLimits.decode("not json"))
    }

    @Test
    fun jobCommandsGoThroughTheShellPolicy() {
        val tool = com.paulneja.quorvi.agent.jobs.JobStart(jobs, "c1")
        val policy = com.paulneja.quorvi.agent.policy.PolicyEngine()
        assertEquals(com.paulneja.quorvi.agent.policy.Verdict.DENY, policy.evaluate(tool, json("command" to "git push origin main", "title" to "x")).verdict)
        assertEquals(com.paulneja.quorvi.agent.policy.Verdict.ALLOW, policy.evaluate(tool, json("command" to "./gradlew assembleDebug", "title" to "x")).verdict)
        assertEquals(com.paulneja.quorvi.agent.policy.Category.SHELL, com.paulneja.quorvi.agent.policy.PolicyService.categoryOf(tool))
    }

    private fun processState(pid: String): String =
        ProcessBuilder("bash", "-c", "ps -o stat= -g $pid | head -1").start().inputStream.bufferedReader().readText().trim()

    @Test
    fun pausingFreezesTheJobQuietlyAndResumingLetsItFinish() = runBlocking {
        val note = jobs.start("c1", "r1", "Slow", "sleep 2; echo resumed-ok", "chats/c1")
        val pid = File(home, "jobs/${note.id}/pid").readText().trim()
        assertEquals(JobState.PAUSED, jobs.pause(note.id)!!.state)
        assertTrue(processState(pid).startsWith("T"))
        Thread.sleep(3_000)
        assertTrue(jobs.sweep().isEmpty())
        assertEquals(1, jobs.aliveCount())
        assertEquals(JobState.RUNNING, jobs.resume(note.id)!!.state)
        val finished = sweepUntilFinished().single()
        assertEquals(JobState.DONE, finished.note.state)
        assertTrue(finished.tail.contains("resumed-ok"))
    }

    @Test
    fun killingFromTheJobsScreenTellsItsChat() = runBlocking {
        val note = jobs.start("c1", "r1", "Big download", "sleep 60", "chats/c1")
        jobs.pause(note.id)
        val killed = jobs.end(note.id, force = true, byUser = true, tell = true)!!
        assertEquals(JobState.KILLED, killed.state)
        assertEquals(0, jobs.aliveCount())
        val finished = jobs.sweep().single()
        assertTrue(finished.note.byUser)
        assertFalse(jobs.saveEnded(finished))
        val goal = jobs.goal(jobs.current(finished)!!)
        assertTrue(goal.startsWith("[Background job stopped by the user]"))
        assertTrue(goal.contains("do not start it again"))
        jobs.markContinued(finished)
        assertTrue(jobs.sweep().isEmpty())
        assertEquals(null, jobs.current(finished))
    }

    @Test
    fun deletingAChatStopsItsJobsWithoutAFollowUp() = runBlocking {
        val note = jobs.start("c1", "r1", "Long", "sleep 60", "chats/c1")
        val pid = File(home, "jobs/${note.id}/pid").readText().trim()
        jobs.stopChat("c1")
        assertEquals(0, jobs.pending())
        assertEquals("", processState(pid))
    }

    @Test
    fun theJobsScreenListsLiveJobsFirstAndHidesOldOnes() {
        val now = 10 * 60 * 60_000L
        val items = listOf(
            com.paulneja.quorvi.agent.jobs.JobItem("c1", JobNote("old", "Old", "x", "", 0, JobState.DONE, 0, now - 3 * 60 * 60_000L)),
            com.paulneja.quorvi.agent.jobs.JobItem("c1", JobNote("recent", "Recent", "x", "", now - 60_000, JobState.DONE, 0, now - 1_000)),
            com.paulneja.quorvi.agent.jobs.JobItem("c2", JobNote("live", "Live", "x", "", 0, JobState.PAUSED)),
        )
        val rows = com.paulneja.quorvi.ui.panels.JobRows.of(items, mapOf("c1" to "Chat uno"), now)
        assertEquals(listOf("live", "recent"), rows.map { it.note.id })
        assertEquals("Chat uno", rows[1].chatTitle)
        assertEquals(null, rows[0].chatTitle)
    }
}
