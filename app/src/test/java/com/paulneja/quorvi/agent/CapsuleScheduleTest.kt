package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.capsules.CapsuleSchedule
import com.paulneja.quorvi.agent.capsules.CapsuleTrigger
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapsuleScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test
    fun intervalsNeverGoBelowFifteenMinutes() {
        val now = at(2026, 10, 11, 10, 0)
        assertEquals(now + 15 * 60_000L, CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.INTERVAL, minutes = 1), now, utc))
        assertEquals(now + 120 * 60_000L, CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.INTERVAL, minutes = 120), now, utc))
    }

    @Test
    fun dailyRunsLaterTodayOrOnTheNextAllowedDay() {
        val now = at(2026, 10, 11, 10, 0)
        assertEquals(at(2026, 10, 11, 18, 30), CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.DAILY, hour = 18, minute = 30), now, utc))
        assertEquals(at(2026, 10, 12, 9, 0), CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.DAILY, hour = 9, minute = 0), now, utc))

        assertEquals(at(2026, 10, 12, 8, 0), CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.DAILY, hour = 8, days = listOf(2, 3, 4, 5, 6)), now, utc))
    }

    @Test
    fun eventTriggersHaveNoTimer() {
        assertNull(CapsuleSchedule.next(CapsuleTrigger(CapsuleTrigger.CHARGING), 0L, utc))
        assertNull(CapsuleSchedule.next(CapsuleTrigger(), 0L, utc))
    }
}
