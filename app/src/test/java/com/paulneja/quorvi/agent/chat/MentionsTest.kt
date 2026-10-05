package com.paulneja.quorvi.agent.chat

import com.paulneja.quorvi.agent.orchestra.Specialist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionsTest {
    @Test
    fun readsModesAndSpecialistsInEitherLanguage() {
        val m = Mentions.parse("@meta armá el informe y @imagen una portada, después @Plan")
        assertTrue(m.meta)
        assertFalse(m.ultra)
        assertEquals(listOf(Specialist.IMAGE, Specialist.PLAN), m.specialists)
        assertEquals(listOf(Specialist.THINK), Mentions.parse("@think about it").specialists)
    }

    @Test
    fun emailsAndUnknownWordsAreNotMentions() {
        val m = Mentions.parse("escribile a meta@empresa.com sobre @desconocido")
        assertFalse(m.meta)
        assertTrue(m.specialists.isEmpty())
    }

    @Test
    fun ultraIsRecognized() {
        assertTrue(Mentions.parse("pasemos a @ultra").ultra)
    }

    @Test
    fun researchAndPlanAreModes() {
        assertTrue(Mentions.parse("@investigar el mercado").research)
        assertTrue(Mentions.parse("/plan migrar la base").plan)
        assertFalse(Mentions.parse("un plan sin barra").plan)
        assertTrue(Mentions.parse("/investigar precios").research)
    }
}
