package com.paulneja.quorvi.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLogicTest {
    @Test
    fun wavRoundTripsAndSkipsExtraChunks() {
        val pcm = ByteArray(64) { it.toByte() }
        val wav = Wav.encode(pcm, 16_000)
        Wav.decode(wav)!!.let { assertEquals(16_000, it.sampleRate); assertArrayEquals(pcm, it.data) }

        val list = "LIST".toByteArray() + byteArrayOf(4, 0, 0, 0) + "info".toByteArray()
        val withList = wav.copyOfRange(0, 36) + list + wav.copyOfRange(36, wav.size)
        assertArrayEquals(pcm, Wav.decode(withList)!!.data)
        assertNull(Wav.decode("ID3 not a wav file".toByteArray()))
        assertEquals(24_000, Wav.rateOf("audio/L16;codec=pcm;rate=24000"))
    }

    @Test
    fun gateWaitsForSpeechThenEndsOnAPause() {
        val g = SpeechGate()
        repeat(50) { g.feed(0.003f) }
        assertEquals(SpeechGate.State.WAITING, g.state)
        repeat(30) { g.feed(0.2f) }
        assertEquals(SpeechGate.State.SPEAKING, g.state)
        repeat(20) { g.feed(0.003f) }
        assertEquals(SpeechGate.State.SPEAKING, g.state)
        repeat(60) { g.feed(0.003f) }
        assertEquals(SpeechGate.State.DONE, g.state)
    }
}
