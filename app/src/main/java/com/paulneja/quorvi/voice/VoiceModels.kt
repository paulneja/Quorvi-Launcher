package com.paulneja.quorvi.voice

object VoiceModels {
    val openAiVoices = listOf("coral", "nova", "sage", "shimmer", "alloy", "ash", "ballad", "echo", "fable", "onyx", "verse")
    val geminiVoices = listOf("Kore", "Aoede", "Leda", "Zephyr", "Puck", "Charon", "Fenrir", "Orus")

    fun voiceFor(saved: String, gemini: Boolean): String {
        val list = if (gemini) geminiVoices else openAiVoices
        val other = if (gemini) openAiVoices else geminiVoices
        if (saved.isBlank() || other.any { it.equals(saved, true) }) return list.first()
        return list.firstOrNull { it.equals(saved, true) } ?: saved
    }

    fun clean(id: String): String = id.removePrefix("models/")

    fun speech(ids: List<String>): List<String> = ids.map(::clean).filter { "tts" in it.lowercase() }.distinct().sorted()

    fun hearing(ids: List<String>, gemini: Boolean): List<String> {
        val all = ids.map(::clean).distinct()
        if (gemini) {
            val skip = Regex("tts|image|imagen|veo|embedding|aqa|learnlm|gemma|live|native-audio", RegexOption.IGNORE_CASE)
            return all.filter { it.startsWith("gemini") && !skip.containsMatchIn(it) }.sortedDescending()
        }
        val dedicated = all.filter { Regex("transcribe|whisper", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        return (dedicated.ifEmpty { all.filter { "audio" in it.lowercase() } }).sorted()
    }
}
