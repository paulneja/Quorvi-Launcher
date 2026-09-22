package com.paulneja.quorvi.core

import com.paulneja.quorvi.ops.IllegalTransition
import com.paulneja.quorvi.ops.RunMachine
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ops.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMachineTest {
    @Test
    fun happyPathIsAllowed() {
        var s = RunState.QUEUED
        for (next in listOf(RunState.PLANNING, RunState.RUNNING, RunState.VERIFYING, RunState.COMPLETED)) s = RunMachine.require(s, next)
        assertEquals(RunState.COMPLETED, s)
    }

    @Test
    fun terminalStatesAreFinal() {
        RunState.entries.filter { it.terminal }.forEach { t ->
            RunState.entries.filter { it != t }.forEach { assertFalse("$t → $it", RunMachine.canMove(t, it)) }
        }
    }

    @Test(expected = IllegalTransition::class)
    fun cannotCompleteWithoutRunning() {
        RunMachine.require(RunState.QUEUED, RunState.COMPLETED)
    }

    @Test
    fun cancellationGoesThroughCancelling() {
        assertFalse(RunMachine.canMove(RunState.RUNNING, RunState.CANCELLED))
        assertTrue(RunMachine.canMove(RunState.RUNNING, RunState.CANCELLING))
        assertTrue(RunMachine.canMove(RunState.CANCELLING, RunState.CANCELLED))
    }

    @Test
    fun restartMarksLiveWorkInterrupted() {
        assertEquals(RunState.INTERRUPTED, RunMachine.afterRestart(RunState.RUNNING))
        assertEquals(RunState.INTERRUPTED, RunMachine.afterRestart(RunState.VERIFYING))
        assertEquals(RunState.QUEUED, RunMachine.afterRestart(RunState.QUEUED))
        assertEquals(RunState.COMPLETED, RunMachine.afterRestart(RunState.COMPLETED))
        assertEquals(StepState.INTERRUPTED, RunMachine.afterRestart(StepState.RUNNING))
    }

    @Test
    fun progressCountsOnlyFinishedWork() {
        assertEquals(3 to 5, RunMachine.progress(listOf(StepState.SUCCEEDED, StepState.SKIPPED, StepState.SUCCEEDED, StepState.RUNNING, StepState.PENDING)))
    }
}
