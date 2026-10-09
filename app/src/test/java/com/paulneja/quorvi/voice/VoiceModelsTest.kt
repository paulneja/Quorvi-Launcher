package com.paulneja.quorvi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceModelsTest {
    private val gemini = listOf(
        "models/gemini-2.5-flash", "models/gemini-2.5-flash-preview-tts", "models/gemini-2.5-pro-preview-tts", "models/gemini-2.5-pro",
        "models/gemini-2.0-flash-preview-image-generation", "models/text-embedding-004", "models/imagen-3.0-generate-002", "models/gemma-3-27b-it",
    )
    private val openai = listOf("gpt-4o", "gpt-4o-mini-tts", "tts-1", "tts-1-hd", "whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe", "dall-e-3")

    @Test
    fun picksTheModelsThatSpeak() {
        assertEquals(listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"), VoiceModels.speech(gemini))
        assertEquals(listOf("gpt-4o-mini-tts", "tts-1", "tts-1-hd"), VoiceModels.speech(openai))
    }

    @Test
    fun picksTheModelsThatHear() {
        assertEquals(listOf("gemini-2.5-pro", "gemini-2.5-flash"), VoiceModels.hearing(gemini, gemini = true))
        assertEquals(listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1"), VoiceModels.hearing(openai, gemini = false))
    }

    @Test
    fun aVoiceFromAnotherProviderFallsBackToThisOnesDefault() {
        assertEquals("Kore", VoiceModels.voiceFor("coral", gemini = true))
        assertEquals("Kore", VoiceModels.voiceFor("", gemini = true))
        assertEquals("Leda", VoiceModels.voiceFor("leda", gemini = true))
        assertEquals("coral", VoiceModels.voiceFor("Kore", gemini = false))
        assertEquals("nova", VoiceModels.voiceFor("nova", gemini = false))
    }
}
