package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.chat.ChatAge
import com.paulneja.quorvi.agent.chat.ChatAttachment
import com.paulneja.quorvi.agent.chat.ChatEntity
import com.paulneja.quorvi.agent.chat.ChatLogic
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.models.AiPart
import com.paulneja.quorvi.agent.models.AiRole
import com.paulneja.quorvi.agent.orchestra.ModelBinding
import com.paulneja.quorvi.agent.orchestra.ModelRef
import com.paulneja.quorvi.agent.orchestra.Orchestra
import com.paulneja.quorvi.agent.orchestra.Specialist
import com.paulneja.quorvi.ui.chat.MarkdownParser
import com.paulneja.quorvi.ui.chat.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLogicTest {
    private fun msg(role: String, text: String, at: Long, files: List<ChatAttachment> = emptyList(), run: String? = null) =
        ChatMessageEntity("m$at", "c", role, text, runId = run, createdAt = at, attachments = ChatAttachment.encode(files))

    @Test
    fun titleIsTheFirstLineCutAtAWord() {
        assertEquals("Cómo hago una tarta de manzana con poca", ChatLogic.titleFrom("¿cómo hago una tarta de manzana con poca azúcar?\nGracias"))
        assertEquals("Hola", ChatLogic.titleFrom("  hola!  "))
    }

    @Test
    fun historyStartsAtTheOwnerAndSummarizesTasks() {
        val h = ChatLogic.history(
            listOf(
                msg(ChatRole.ASSISTANT, "orphan", 1),
                msg(ChatRole.USER, "hacé un informe", 2),
                msg(ChatRole.TASK, "Informe de ventas", 3, run = "r1"),
                msg(ChatRole.ASSISTANT, "", 4),
            ),
            taskState = { if (it == "r1") "completed" else null },
        )
        assertEquals(listOf(AiRole.USER, AiRole.ASSISTANT), h.map { it.role })
        assertTrue(h[1].text.contains("completed"))
    }

    @Test
    fun onlyTheLatestAttachmentsTravelWhole() {
        val photo = ChatAttachment("/p.jpg", "p.jpg", "image/jpeg", 10)
        val messages = (1..5).map { msg(ChatRole.USER, "foto $it", it.toLong(), listOf(photo.copy(path = "/p$it.jpg"))) }
        val h = ChatLogic.history(messages, attach = { AiPart.Image("x", "image/jpeg") })
        val images = h.map { m -> m.parts.count { it is AiPart.Image } }
        assertEquals(listOf(0, 0, 1, 1, 1), images)
        assertTrue(h.first().text.contains("attached file"))
    }

    @Test
    fun chatsGroupByAge() {
        val now = 1_760_000_000_000L
        val day = 86_400_000L
        val groups = ChatLogic.grouped(listOf(ChatEntity("a", "a", 0, now), ChatEntity("b", "b", 0, now - 40 * day), ChatEntity("c", "c", 0, now - 3 * day)), now)
        assertEquals(listOf(ChatAge.TODAY, ChatAge.WEEK, ChatAge.OLDER), groups.map { it.first })
    }

    @Test
    fun orchestraOnlyUsesWhatTheOwnerSetUp() {
        val bound = ModelBinding(ModelRef("p", "m"))
        assertTrue(Orchestra(enabled = false, active = setOf(Specialist.THINK), specialists = mapOf(Specialist.THINK to bound)).delegates.isEmpty())
        assertTrue(Orchestra(enabled = true, active = setOf(Specialist.THINK)).delegates.isEmpty())
        assertEquals(listOf(Specialist.THINK), Orchestra(enabled = true, active = setOf(Specialist.THINK, Specialist.REVIEW), specialists = mapOf(Specialist.THINK to bound, Specialist.REVIEW to bound)).delegates)
        assertFalse(Orchestra(enabled = true, finalReview = true).reviewing)
        val round = Orchestra.decode(Orchestra.encode(Orchestra(enabled = true, maxRounds = 99, specialists = mapOf(Specialist.IMAGE to bound))))
        assertEquals(40, round.maxRounds)
        assertEquals(bound, round.specialists[Specialist.IMAGE])
        assertEquals(Orchestra(), Orchestra.decode("{broken"))
        assertTrue(Orchestra.decode(Orchestra.encode(Orchestra(maxRounds = Orchestra.UNLIMITED))).unlimited)
        assertEquals(2, Orchestra(maxRounds = 1).clamped().maxRounds)
    }

    @Test
    fun markdownHandlesListsHeadingsAndAHalfStreamedFence() {
        val blocks = MarkdownParser.parse("# Título\n\nTexto **fuerte**\n- uno\n  - dos\n1. primero\n```kotlin\nval x = 1")
        assertEquals(MdBlock.Heading(1, "Título"), blocks[0])
        assertEquals(MdBlock.Paragraph("Texto **fuerte**"), blocks[1])
        assertEquals(MdBlock.Bullet("uno", 0), blocks[2])
        assertEquals(MdBlock.Bullet("dos", 1), blocks[3])
        assertEquals(MdBlock.Bullet("primero", 0, "1"), blocks[4])
        assertEquals(MdBlock.Code("kotlin", "val x = 1", closed = false), blocks[5])
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        org.junit.Assert.assertTrue(com.paulneja.quorvi.agent.chat.ChatLogic.fold("Pronóstico para CAMPANA").contains(com.paulneja.quorvi.agent.chat.ChatLogic.fold("pronostico")))
    }
}
