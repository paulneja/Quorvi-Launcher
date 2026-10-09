package com.paulneja.quorvi.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VoiceConfig(

    val enabled: Boolean = false,
    val providerId: String = "",
    val sttModel: String = "",
    val ttsModel: String = "",
    val voice: String = "",

    val style: String = "",
    val speak: Boolean = true,

    val followUp: Boolean = true,

    val speed: Float = 1.15f,

    val bargeIn: Boolean = true,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(raw: String?): VoiceConfig = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: VoiceConfig()
        fun encode(c: VoiceConfig): String = json.encodeToString(serializer(), c)
    }
}
