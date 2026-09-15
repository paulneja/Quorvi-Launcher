package com.paulneja.quorvi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoopTest {
    private val loop = CrashLoop()

    @Test
    fun twoCrashesInTwoMinutesIsALoop() {
        assertTrue(loop.detected(listOf(1_000, 50_000), now = 55_000))
    }

    @Test
    fun aSingleCrashIsNot() {
        assertFalse(loop.detected(listOf(50_000), now = 55_000))
    }

    @Test
    fun oldCrashesDoNotCount() {
        assertFalse(loop.detected(listOf(1_000, 130_000), now = 135_000))
    }

    @Test
    fun futureTimestampsFromClockChangesAreIgnored() {
        assertFalse(loop.detected(listOf(90_000, 95_000), now = 10_000))
    }
}
