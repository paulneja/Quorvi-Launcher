package com.paulneja.quorvi.agent.models

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface AiClient {
    fun stream(request: AiRequest): Flow<AiEvent>
    suspend fun listModels(): List<AiModelInfo>

    suspend fun generateImage(model: String, prompt: String): ByteArray? = null
}

enum class ReasoningEffort { LOW, MEDIUM, HIGH, MAX }

@Serializable
data class ReasoningConfig(val effort: ReasoningEffort? = null, val thinking: Boolean = false)

enum class AiRole { SYSTEM, USER, ASSISTANT, TOOL }

sealed interface AiPart {
    data class Text(val text: String) : AiPart
    data class Image(val base64: String, val mediaType: String) : AiPart
}

data class AiToolCall(val id: String, val name: String, val arguments: JsonObject)

data class AiMessage(
    val role: AiRole,
    val parts: List<AiPart> = emptyList(),
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolCallId: String? = null,
    val signature: String? = null,
) {
    val text: String get() = parts.filterIsInstance<AiPart.Text>().joinToString("\n") { it.text }

    companion object {
        fun user(text: String) = AiMessage(AiRole.USER, listOf(AiPart.Text(text)))
        fun assistant(text: String, calls: List<AiToolCall> = emptyList(), signature: String? = null) = AiMessage(AiRole.ASSISTANT, listOf(AiPart.Text(text)), calls, signature = signature)
        fun tool(callId: String, result: String) = AiMessage(AiRole.TOOL, listOf(AiPart.Text(result)), toolCallId = callId)
    }
}

data class AiToolSpec(val name: String, val description: String, val parameters: JsonObject)

data class AiUsage(val inputTokens: Long? = null, val outputTokens: Long? = null)

sealed interface AiEvent {
    data class TextDelta(val text: String) : AiEvent
    data class ToolCalls(val calls: List<AiToolCall>) : AiEvent
    data class Done(val usage: AiUsage?, val signature: String? = null) : AiEvent
    data class Notice(val text: String) : AiEvent
}

data class AiRequest(
    val model: String,
    val system: String?,
    val messages: List<AiMessage>,
    val tools: List<AiToolSpec> = emptyList(),
    val temperature: Double? = 0.3,
    val maxTokens: Int = 4096,
    val conversationId: String? = null,
    val reasoning: ReasoningConfig = ReasoningConfig(),
)

data class AiModelInfo(val id: String, val displayName: String = id, val vision: Boolean = false)
