package com.paulneja.quorvi.agent.capsules

import java.util.Calendar
import java.util.TimeZone

object CapsuleSchedule {
    const val MIN_INTERVAL = 15

    fun next(trigger: CapsuleTrigger, now: Long, zone: TimeZone = TimeZone.getDefault()): Long? = when (trigger.kind) {
        CapsuleTrigger.INTERVAL -> now + trigger.minutes.coerceAtLeast(MIN_INTERVAL) * 60_000L
        CapsuleTrigger.DAILY -> {
            val days = trigger.days.filter { it in 1..7 }.ifEmpty { (1..7).toList() }
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, trigger.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, trigger.minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var result: Long? = null
            for (i in 0..7) {
                if (cal.timeInMillis > now && cal.get(Calendar.DAY_OF_WEEK) in days) { result = cal.timeInMillis; break }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            result
        }
        else -> null
    }
}
