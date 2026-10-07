package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.ultra.PrDiff
import com.paulneja.quorvi.agent.ultra.PrRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrRequestTest {
    private var n = 0
    private fun msg(role: String, text: String) = ChatMessageEntity("m${n++}", "c", role, text, createdAt = n.toLong())

    @Test
    fun recognisesAskingForAPullRequest() {
        assertTrue(PrRequest.asks("dale, hacé el PR"))
        assertTrue(PrRequest.asks("abrí un pull request con esto"))
        assertTrue(PrRequest.asks("/pr"))
        assertFalse(PrRequest.asks("arreglá el problema de la app"))
        assertFalse(PrRequest.asks("mejorá la presión del código"))
    }

    @Test
    fun aDraftAnswersTheRequestUntilTheOwnerAsksAgain() {
        val asked = listOf(msg(ChatRole.USER, "arreglá el login"), msg(ChatRole.ASSISTANT, "listo"), msg(ChatRole.USER, "ahora hacé el PR"))
        assertTrue(PrRequest.pending(asked))
        val drafted = asked + msg(ChatRole.PR, "{}") + msg(ChatRole.USER, "cambiá también el título de la pantalla")
        assertFalse(PrRequest.pending(drafted))
        assertTrue(PrRequest.pending(drafted + msg(ChatRole.USER, "/pr")))
        assertFalse(PrRequest.pending(listOf(msg(ChatRole.USER, "arreglá el login"))))
    }

    @Test
    fun splitsADiffPerFile() {
        val diff = """
            diff --git a/src/A.kt b/src/A.kt
            index 1..2 100644
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -1 +1 @@
            -old
            +new
            diff --git a/README.md b/README.md
            new file mode 100644
            +hola
        """.trimIndent()
        val parts = PrDiff.byFile(diff)
        assertEquals(listOf("src/A.kt", "README.md"), parts.keys.toList())
        assertTrue(parts.getValue("src/A.kt").endsWith("+new"))
        assertEquals("diff --git a/README.md b/README.md\nnew file mode 100644\n+hola", parts.getValue("README.md"))
    }
}
