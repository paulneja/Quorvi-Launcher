package com.paulneja.quorvi.agent.models

import com.paulneja.quorvi.core.net.Http
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AnthropicClient(private val http: Http, baseUrl: String, private val apiKey: String?) : AiClient {
    private val root = baseUrl.trimEnd('/').removeSuffix("/v1")
    private val headers: Map<String, String>
        get() = buildMap {
            apiKey?.takeIf { it.isNotBlank() }?.let { put("x-api-key", it) }
            put("anthropic-version", "2023-06-01")
        }

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val support = ReasoningSupport.of(ProviderKind.ANTHROPIC, request.model)
        val thinking = request.reasoning.thinking && support.thinking
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", request.maxTokens.coerceAtLeast(if (thinking) budget(request.reasoning.effort) + 1024 else 256))
            request.system?.let { put("system", it) }
            if (!thinking) request.temperature?.let { put("temperature", it.coerceIn(0.0, 1.0)) }
            if (thinking) putJsonObject("thinking") { put("type", "enabled"); put("budget_tokens", budget(request.reasoning.effort)) }
            if (support.effort && request.reasoning.effort != null) putJsonObject("output_config") { put("effort", request.reasoning.effort.name.lowercase()) }
            put("messages", encode(request.messages))
            if (request.tools.isNotEmpty()) putJsonArray("tools") {
                request.tools.forEach { t -> addJsonObject { put("name", t.name); put("description", t.description); put("input_schema", t.parameters) } }
            }
        }
        val blocks = mutableMapOf<Int, Block>()
        var usageIn: Long? = null
        var usageOut: Long? = null
        http.stream("$root/v1/messages", body, headers).collect { sse ->
            val o = runCatching { http.json.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            when (o.str("type")) {
                "message_start" -> usageIn = o["message"]?.jsonObject?.get("usage")?.jsonObject?.get("input_tokens")?.let { (it as? JsonPrimitive)?.longOrNull }
                "content_block_start" -> {
                    val index = o.int("index")
                    val block = o["content_block"]?.jsonObject ?: return@collect
                    blocks[index] = Block(block.str("type").orEmpty(), block.str("id"), block.str("name"))
                    block.str("text")?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.TextDelta(it)) }
                }
                "content_block_delta" -> {
                    val b = blocks[o.int("index")] ?: return@collect
                    val delta = o["delta"]?.jsonObject ?: return@collect
                    when (delta.str("type")) {
                        "text_delta" -> delta.str("text")?.let { emit(AiEvent.TextDelta(it)) }
                        "input_json_delta" -> b.json.append(delta.str("partial_json").orEmpty())
                        "thinking_delta" -> b.thinking.append(delta.str("thinking").orEmpty())
                        "signature_delta" -> b.signature.append(delta.str("signature").orEmpty())
                    }
                }
                "message_delta" -> usageOut = o["usage"]?.jsonObject?.get("output_tokens")?.let { (it as? JsonPrimitive)?.longOrNull }
                "error" -> throw com.paulneja.quorvi.core.net.HttpStatusException(502, o["error"]?.jsonObject?.str("message") ?: sse.data.take(300))
            }
        }
        val ordered = blocks.toSortedMap().values
        val calls = ordered.filter { it.type == "tool_use" }.map { b ->
            AiToolCall(b.id ?: "toolu_${b.hashCode()}", b.name.orEmpty(), runCatching { http.json.parseToJsonElement(b.json.toString().ifBlank { "{}" }).jsonObject }.getOrDefault(JsonObject(emptyMap())))
        }
        if (calls.isNotEmpty()) emit(AiEvent.ToolCalls(calls))
        val thoughts = ordered.filter { it.type == "thinking" || it.type == "redacted_thinking" }
        val signature = if (thoughts.isEmpty()) null else SIGNATURE_PREFIX + buildJsonArray {
            thoughts.forEach { t -> addJsonObject { put("type", "thinking"); put("thinking", t.thinking.toString()); put("signature", t.signature.toString()) } }
        }.toString()
        emit(AiEvent.Done(AiUsage(usageIn, usageOut), signature))
    }

    private fun encode(messages: List<AiMessage>): JsonArray = buildJsonArray {
        var pendingResults = mutableListOf<JsonObject>()
        fun flushResults(target: kotlinx.serialization.json.JsonArrayBuilder) {
            if (pendingResults.isEmpty()) return
            target.addJsonObject { put("role", "user"); put("content", JsonArray(pendingResults)) }
            pendingResults = mutableListOf()
        }
        for (m in messages) {
            when (m.role) {
                AiRole.TOOL -> pendingResults += buildJsonObject {
                    put("type", "tool_result"); put("tool_use_id", m.toolCallId.orEmpty()); put("content", m.text)
                }
                AiRole.USER -> {
                    flushResults(this)
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            m.parts.forEach { p ->
                                when (p) {
                                    is AiPart.Text -> if (p.text.isNotBlank()) addJsonObject { put("type", "text"); put("text", p.text) }
                                    is AiPart.Image -> addJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") { put("type", "base64"); put("media_type", p.mediaType); put("data", p.base64) }
                                    }
                                }
                            }
                            if (m.parts.none { it is AiPart.Text && it.text.isNotBlank() } && m.parts.none { it is AiPart.Image }) addJsonObject { put("type", "text"); put("text", "(empty)") }
                        }
                    }
                }
                AiRole.ASSISTANT -> {
                    flushResults(this)
                    addJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            m.signature?.takeIf { it.startsWith(SIGNATURE_PREFIX) }?.let { sig ->
                                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(sig.removePrefix(SIGNATURE_PREFIX)).jsonArray }.getOrNull()?.forEach { add(it) }
                            }
                            if (m.text.isNotBlank()) addJsonObject { put("type", "text"); put("text", m.text) }
                            m.toolCalls.forEach { c -> addJsonObject { put("type", "tool_use"); put("id", c.id); put("name", c.name); put("input", c.arguments) } }
                            if (m.text.isBlank() && m.toolCalls.isEmpty()) addJsonObject { put("type", "text"); put("text", "…") }
                        }
                    }
                }
                AiRole.SYSTEM -> Unit
            }
        }
        flushResults(this)
    }

    override suspend fun listModels(): List<AiModelInfo> {
        val json = http.getJson("$root/v1/models?limit=100", headers).jsonObject
        return (json["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            AiModelInfo(id, o.str("display_name") ?: id, vision = true)
        }
    }

    private class Block(val type: String, val id: String?, val name: String?) {
        val json = StringBuilder()
        val thinking = StringBuilder()
        val signature = StringBuilder()
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0

    companion object {
        const val DEFAULT_URL = "https://api.anthropic.com"
        const val SIGNATURE_PREFIX = "anthropic-thinking:"

        fun budget(effort: ReasoningEffort?): Int = when (effort) {
            null, ReasoningEffort.LOW -> 2_048
            ReasoningEffort.MEDIUM -> 8_192
            ReasoningEffort.HIGH -> 16_384
            ReasoningEffort.MAX -> 32_768
        }
    }
}

data class ReasoningSupport(val effort: Boolean, val thinking: Boolean) {
    companion object {
        val NONE = ReasoningSupport(effort = false, thinking = false)

        fun of(kind: ProviderKind, model: String): ReasoningSupport {
            val m = model.lowercase()
            return when (kind) {
                ProviderKind.ANTHROPIC -> ReasoningSupport(
                    effort = Regex("claude-(opus|sonnet|fable|mythos)-(4-[5-9]|[5-9])").containsMatchIn(m),
                    thinking = Regex("claude-(3-7|opus|sonnet|haiku-4|fable|mythos)").containsMatchIn(m),
                )
                ProviderKind.OPENAI_RESPONSES -> ReasoningSupport(effort = reasoningOpenAi(m), thinking = false)
                ProviderKind.COPILOT -> ReasoningSupport(effort = reasoningOpenAi(m), thinking = false)
                ProviderKind.BRIDGE, ProviderKind.OPENAI_COMPATIBLE -> NONE
            }
        }

        private fun reasoningOpenAi(m: String) = m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-5")
    }
}
