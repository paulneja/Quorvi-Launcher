package com.paulneja.quorvi.voice

class SpeechGate(
    private val frameMs: Int = 20,
    private val silenceMs: Int = 1_100,
    private val noSpeechMs: Int = 7_000,
    private val maxMs: Int = 45_000,
    private val minSpeechMs: Int = 240,

    private val minLevel: Float = 0.012f,
) {
    enum class State { WAITING, SPEAKING, DONE, NOTHING }

    var state = State.WAITING
        private set
    private var floor = 0.004f
    private var elapsed = 0
    private var quiet = 0
    private var voiced = 0

    fun feed(level: Float): State {
        if (state == State.DONE || state == State.NOTHING) return state
        elapsed += frameMs
        val loud = level > maxOf(floor * 3.2f, minLevel)

        if (!loud) floor = floor * 0.95f + level * 0.05f
        when (state) {
            State.WAITING -> {
                voiced = if (loud) voiced + frameMs else 0
                if (voiced >= minSpeechMs) { state = State.SPEAKING; quiet = 0 }
                else if (elapsed >= noSpeechMs) state = State.NOTHING
            }
            State.SPEAKING -> {
                quiet = if (loud) 0 else quiet + frameMs
                if (quiet >= silenceMs || elapsed >= maxMs) state = State.DONE
            }
            else -> Unit
        }
        return state
    }
}
