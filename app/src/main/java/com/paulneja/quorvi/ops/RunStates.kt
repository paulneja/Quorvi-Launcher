package com.paulneja.quorvi.ops

enum class RunState {
    QUEUED, PLANNING, RUNNING, WAITING_CAPABILITY, WAITING_USER, PAUSING, PAUSED, RETRYING, VERIFYING, CANCELLING,
    COMPLETED, FAILED, CANCELLED, INTERRUPTED;

    val terminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val active: Boolean get() = !terminal && this != PAUSED && this != INTERRUPTED
}

enum class StepState {
    PENDING, READY, RUNNING, SUCCEEDED, SKIPPED, BLOCKED, FAILED, CANCELLED, INTERRUPTED;

    val terminal: Boolean get() = this == SUCCEEDED || this == SKIPPED || this == FAILED || this == CANCELLED
}

class IllegalTransition(from: Enum<*>, to: Enum<*>) : IllegalStateException("$from → $to is not allowed")

object RunMachine {
    private val run: Map<RunState, Set<RunState>> = mapOf(
        RunState.QUEUED to setOf(RunState.PLANNING, RunState.RUNNING, RunState.CANCELLED, RunState.INTERRUPTED),
        RunState.PLANNING to setOf(RunState.RUNNING, RunState.WAITING_USER, RunState.WAITING_CAPABILITY, RunState.FAILED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.RUNNING to setOf(RunState.VERIFYING, RunState.WAITING_USER, RunState.WAITING_CAPABILITY, RunState.PAUSING, RunState.RETRYING, RunState.COMPLETED, RunState.FAILED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.WAITING_CAPABILITY to setOf(RunState.PLANNING, RunState.RUNNING, RunState.FAILED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.WAITING_USER to setOf(RunState.RUNNING, RunState.PLANNING, RunState.FAILED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.PAUSING to setOf(RunState.PAUSED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.PAUSED to setOf(RunState.RUNNING, RunState.CANCELLED),
        RunState.RETRYING to setOf(RunState.RUNNING, RunState.FAILED, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.VERIFYING to setOf(RunState.COMPLETED, RunState.FAILED, RunState.RETRYING, RunState.WAITING_USER, RunState.CANCELLING, RunState.INTERRUPTED),
        RunState.CANCELLING to setOf(RunState.CANCELLED, RunState.INTERRUPTED),
        RunState.INTERRUPTED to setOf(RunState.RUNNING, RunState.PLANNING, RunState.CANCELLED, RunState.FAILED),
    )

    private val step: Map<StepState, Set<StepState>> = mapOf(
        StepState.PENDING to setOf(StepState.READY, StepState.BLOCKED, StepState.SKIPPED, StepState.CANCELLED),
        StepState.BLOCKED to setOf(StepState.READY, StepState.SKIPPED, StepState.CANCELLED),
        StepState.READY to setOf(StepState.RUNNING, StepState.SKIPPED, StepState.CANCELLED),
        StepState.RUNNING to setOf(StepState.SUCCEEDED, StepState.FAILED, StepState.CANCELLED, StepState.INTERRUPTED),
        StepState.INTERRUPTED to setOf(StepState.READY, StepState.FAILED, StepState.CANCELLED),
        StepState.FAILED to setOf(StepState.READY),
    )

    fun canMove(from: RunState, to: RunState): Boolean = from == to || to in run[from].orEmpty()

    fun canMove(from: StepState, to: StepState): Boolean = from == to || to in step[from].orEmpty()

    fun require(from: RunState, to: RunState): RunState = if (canMove(from, to)) to else throw IllegalTransition(from, to)

    fun require(from: StepState, to: StepState): StepState = if (canMove(from, to)) to else throw IllegalTransition(from, to)

    fun afterRestart(state: RunState): RunState = if (state.active && state != RunState.QUEUED) RunState.INTERRUPTED else state

    fun afterRestart(state: StepState): StepState = if (state == StepState.RUNNING) StepState.INTERRUPTED else state

    fun progress(steps: List<StepState>): Pair<Int, Int> = steps.count { it == StepState.SUCCEEDED || it == StepState.SKIPPED } to steps.size
}
