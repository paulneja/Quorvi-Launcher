package com.paulneja.quorvi.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenArbitrationTest {
    @Test
    fun withoutAServiceControlIsRefused() {
        val broker = ScreenControlBroker()
        assertFalse(broker.acquire())
        assertEquals(Arbitration.RELEASED, broker.state.value)
        broker.onForeground("com.android.settings")
        assertEquals("com.android.settings", broker.foreground.value)
    }

    @Test
    fun redactorHidesPasswordsButKeepsNormalText() {
        val pw = UiNode(0, "field", "hunter2", "pin", clickable = false, editable = true, scrollable = false, password = true, bounds = android.graphics.Rect())
        val label = UiNode(1, "text", "Welcome back", null, clickable = false, editable = false, scrollable = false, password = false, bounds = android.graphics.Rect())
        val text = UiSnapshot("com.app", listOf(pw, label), sensitive = true).describe()
        assertTrue(text.contains("sensitive"))
        assertTrue(text.contains("••••"))
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("Welcome back"))
    }

    private fun assertEquals(a: Any?, b: Any?) = org.junit.Assert.assertEquals(a, b)
}
