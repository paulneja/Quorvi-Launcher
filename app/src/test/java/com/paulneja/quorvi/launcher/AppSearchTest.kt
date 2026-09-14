package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.apps.AppSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private val labels = listOf("Calculadora", "Cámara", "Google Calendar", "Chrome", "Contactos", "Slack")

    @Test
    fun emptyQueryKeepsOrder() {
        assertEquals(labels, AppSearch.filter(labels, "  ") { it })
    }

    @Test
    fun accentsAreIgnored() {
        assertEquals(listOf("Cámara"), AppSearch.filter(labels, "camara") { it })
    }

    @Test
    fun prefixBeatsWordPrefixBeatsContains() {
        val result = AppSearch.filter(listOf("Mail", "Gmail", "Google Mail"), "mail") { it }
        assertEquals(listOf("Mail", "Google Mail", "Gmail"), result)
    }

    @Test
    fun initialsMatch() {
        assertTrue(AppSearch.score("Google Calendar", "gc") > 0)
        assertEquals(0, AppSearch.score("Chrome", "x"))
    }
}
