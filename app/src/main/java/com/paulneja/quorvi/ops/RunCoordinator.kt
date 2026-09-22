package com.paulneja.quorvi.ops

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RunExecutor {
    fun submit(specJson: String): String
    fun cancel(runId: String)
    fun pause(runId: String)
    fun resume(runId: String)
    fun answer(runId: String, text: String)
    fun killAll()
}

@Singleton
class RunCoordinator @Inject constructor(
    private val dao: OpsDao,
    private val scope: CoroutineScope,
) {
    @Volatile var executor: RunExecutor? = null

    fun submit(specJson: String): String = executor?.submit(specJson) ?: ""

    fun cancel(runId: String) {
        executor?.cancel(runId) ?: scope.launch { move(runId, RunState.CANCELLING, "cancel requested") ; move(runId, RunState.CANCELLED, "cancelled") }
    }

    fun pause(runId: String) {
        executor?.pause(runId)
    }

    fun resume(runId: String) {
        executor?.resume(runId)
    }

    fun answer(runId: String, text: String) {
        executor?.answer(runId, text)
    }

    fun killAll() {
        executor?.killAll()
    }

    suspend fun move(runId: String, to: RunState, message: String, now: Long = System.currentTimeMillis()): Boolean {
        val run = dao.run(runId) ?: return false
        val from = RunState.valueOf(run.state)
        if (!RunMachine.canMove(from, to)) return false
        if (dao.transition(runId, from.name, to.name, now, if (to.terminal) now else run.finishedAt, run.error) == 0) return false
        dao.append(runId, "system", "state", message, "${from.name}->${to.name}", now)
        return true
    }

    suspend fun reconcile(now: Long = System.currentTimeMillis()) {
        dao.allRuns().forEach { run ->
            val from = RunState.valueOf(run.state)
            val to = RunMachine.afterRestart(from)
            if (to != from) {
                dao.upsertRun(run.copy(state = to.name, updatedAt = now))
                dao.append(run.id, "system", "state", "interrupted: the process was restarted", "${from.name}->${to.name}", now)
            }
        }
        dao.runningSteps().forEach { step ->
            dao.upsertStep(step.copy(state = RunMachine.afterRestart(StepState.valueOf(step.state)).name, finishedAt = now))
        }
    }
}
