package com.paulneja.quorvi.core

import com.paulneja.quorvi.core.settings.GlassStyle
import com.paulneja.quorvi.core.settings.IconStyle
import com.paulneja.quorvi.core.settings.Look
import com.paulneja.quorvi.core.settings.MotionStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class LookTest {
    @Test
    fun missingOrBrokenJsonFallsBackToDefaults() {
        assertEquals(Look(), Look.decode(null))
        assertEquals(Look(), Look.decode("{not json"))
        assertEquals(Look(glass = GlassStyle.SOLID), Look.decode("""{"glass":"SOLID","fieldFromTheFuture":1}"""))
    }

    @Test
    fun valuesAreClampedOnTheWayInAndOut() {
        val wild = Look(refraction = 9f, blurDp = -3f, speed = 10f, textScale = 0.1f, radiusDp = 99f)
        val back = Look.decode(Look.encode(wild))
        assertEquals(1f, back.refraction)
        assertEquals(0f, back.blurDp)
        assertEquals(2f, back.speed)
        assertEquals(0.85f, back.textScale)
        assertEquals(40f, back.radiusDp)
    }

    @Test
    fun roundTripKeepsEveryChoice() {
        val look = Look(glass = GlassStyle.FROSTED, motion = MotionStyle.DRAMATIC, iconStyle = IconStyle.TINTED, appOpenFromIcon = false, minimalChrome = true, bounce = 0.8f)
        assertEquals(look, Look.decode(Look.encode(look)))
    }
}
