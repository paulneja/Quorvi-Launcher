package com.paulneja.quorvi.ui

import com.paulneja.quorvi.ui.chat.orb.OrbState
import com.paulneja.quorvi.ui.chat.orb.OrbStates
import com.paulneja.quorvi.voice.AssistantViewModel.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class OrbStateTest {
    @Test
    fun voicePhasesBecomeOrbStates() {
        assertEquals(OrbState.LISTENING, OrbStates.of(Phase.LISTENING))
        assertEquals(OrbState.THINKING, OrbStates.of(Phase.TRANSCRIBING))
        assertEquals(OrbState.THINKING, OrbStates.of(Phase.THINKING))
        assertEquals(OrbState.SPEAKING, OrbStates.of(Phase.SPEAKING))
        assertEquals(OrbState.IDLE, OrbStates.of(Phase.ERROR))
        assertEquals(OrbState.IDLE, OrbStates.of(Phase.IDLE))
    }

    @Test
    fun theClockWrapsEveryHourAndSmallOrbsUseLessDetail() {
        assertEquals(1.5f, OrbStates.clock(1_500_000_000L, 0L), 1e-4f)
        assertEquals(10f, OrbStates.clock(3_610_000_000_000L, 0L), 1e-3f)
        assertEquals(2f, OrbStates.detail(28f))
        assertEquals(4f, OrbStates.detail(104f))
        assertEquals(5f, OrbStates.detail(280f))
    }
}
