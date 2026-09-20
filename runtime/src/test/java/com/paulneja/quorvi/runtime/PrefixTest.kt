package com.paulneja.quorvi.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PrefixTest {
    private val prefix = Prefix("quorvi.app")

    @Test
    fun rewritesEveryOccurrenceKeepingLength() {
        val input = "/data/data/com.termux/files/usr\u0000x/data/data/com.termux/files/home".toByteArray()
        val out = prefix.rewrite(input)
        assertEquals(input.size, out.size)
        assertEquals("/data/data/quorvi.app/files/usr\u0000x/data/data/quorvi.app/files/home", String(out))
    }

    @Test
    fun leavesBytesWithoutThePrefixUntouched() {
        val input = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 1, 2, 3)
        assertSame(input, prefix.rewrite(input))
        assertArrayEquals("com.termu".toByteArray(), prefix.rewrite("com.termu".toByteArray()))
    }

    @Test
    fun rewritesSymlinkTargets() {
        assertEquals("/data/data/quorvi.app/files/usr/bin/bash", prefix.rewrite("/data/data/com.termux/files/usr/bin/bash"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anAppIdOfAnotherLengthCannotHostTheShell() {
        Prefix("com.paulneja.quorvi")
    }
}
