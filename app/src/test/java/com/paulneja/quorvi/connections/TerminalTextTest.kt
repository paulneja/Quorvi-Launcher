package com.paulneja.quorvi.connections

import com.paulneja.quorvi.ui.operations.TerminalText
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextTest {
    @Test
    fun escapesAreDroppedAndControlCharactersApplied() {
        var t = TerminalText.append("", "\u001B[1;32muser@box\u001B[0m:~$ ls\r\n")
        assertEquals("user@box:~$ ls\n", t)
        t = TerminalText.append(t, "progress 10%\rprogress 99%\n")
        assertEquals("user@box:~$ ls\nprogress 99%\n", t)
        t = TerminalText.append(t, "abc\b\bX\u0007")
        assertEquals("user@box:~$ ls\nprogress 99%\naX", t)
        assertEquals("title gone", TerminalText.append("", "\u001B]0;window title\u0007title gone"))
    }
}
