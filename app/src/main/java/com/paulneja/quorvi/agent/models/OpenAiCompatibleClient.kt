package com.paulneja.quorvi.agent.models

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class OpenAiCompatibleClient(
    private val http: Http,
    private val baseUrl: String,
    private val apiKey: String?,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : AiClient {
    private val headers: Map<String, String>
        get() = buildMap { putAll(extraHeaders); if (!apiKey.isNullOrBlank()) put("Authorization", "Bearer $apiKey") }

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            request.temperature?.let { put("temperature", it.coerceIn(0.0, 2.0)) }
            put("max_tokens", request.maxTokens)
            put("messages", buildJsonArray {
                request.system?.let { addJsonObject { put("role", "system"); put("content", it) } }
                request.messages.forEach { add(encode(it)) }
            })
            if (request.tools.isNotEmpty()) put("tools", buildJsonArray {
                request.tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") { put("name", tool.name); put("description", tool.description); put("parameters", tool.parameters) }
                    }
                }
            })
        }
        val pending = linkedMapOf<Int, PendingCall>()
        var usage: AiUsage? = null
        http.stream("${baseUrl.trimEnd('/')}/chat/completions", body, headers).collect { sse ->
            if (sse.data == "[DONE]") return@collect
            val chunk = runCatching { http.json.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            (chunk["error"] as? JsonObject)?.let { err -> throw HttpStatusException(502, (err["message"] as? JsonPrimitive)?.content ?: err.toString()) }
            (chunk["usage"] as? JsonObject)?.let { u ->
                usage = AiUsage(u["prompt_tokens"]?.jsonPrimitive?.content?.toLongOrNull(), u["completion_tokens"]?.jsonPrimitive?.content?.toLongOrNull())
            }
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@collect
            val delta = choice["delta"] as? JsonObject
            (delta?.get("content") as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.let { emit(AiEvent.TextDelta(it.content)) }
            (delta?.get("tool_calls") as? JsonArray)?.forEach { tc ->
                val o = tc.jsonObject
                val index = o["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val p = pending.getOrPut(index) { PendingCall() }
                (o["id"] as? JsonPrimitive)?.content?.let { p.id = it }
                (o["function"] as? JsonObject)?.let { f ->
                    (f["name"] as? JsonPrimitive)?.content?.let { p.name = it }
                    (f["arguments"] as? JsonPrimitive)?.content?.let { p.args.append(it) }
                }
            }
        }
        if (pending.isNotEmpty()) {
            emit(AiEvent.ToolCalls(pending.values.mapIndexed { i, p -> AiToolCall(p.id ?: "call_$i", p.name.orEmpty(), parseArgs(p.args.toString())) }))
        }
        emit(AiEvent.Done(usage))
    }

    private fun parseArgs(raw: String): JsonObject =
        runCatching { http.json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }.getOrDefault(buildJsonObject { })

    private fun encode(m: AiMessage): JsonObject = buildJsonObject {
        when (m.role) {
            AiRole.SYSTEM -> { put("role", "system"); put("content", m.text) }
            AiRole.USER -> {
                put("role", "user")
                val images = m.parts.filterIsInstance<AiPart.Image>()
                if (images.isEmpty()) put("content", m.text) else put("content", buildJsonArray {
                    addJsonObject { put("type", "text"); put("text", m.text) }
                    images.forEach { img -> addJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", "data:${img.mediaType};base64,${img.base64}") } } }
                })
            }
            AiRole.ASSISTANT -> {
                put("role", "assistant")
                put("content", m.text)
                if (m.toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { c -> addJsonObject { put("id", c.id); put("type", "function"); putJsonObject("function") { put("name", c.name); put("arguments", c.arguments.toString()) } } }
                })
            }
            AiRole.TOOL -> { put("role", "tool"); put("tool_call_id", m.toolCallId.orEmpty()); put("content", m.text) }
        }
    }

    override suspend fun generateImage(model: String, prompt: String): ByteArray? = OpenAiImages.generate(http, baseUrl, headers, model, prompt)

    override suspend fun listModels(): List<AiModelInfo> {
        val json = http.getJson("${baseUrl.trimEnd('/')}/models", headers).jsonObject
        return (json["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            AiModelInfo(id, (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: id, (o["vision"] as? JsonPrimitive)?.content == "true")
        }.sortedBy { it.id }
    }

    private class PendingCall {
        var id: String? = null
        var name: String? = null
        val args = StringBuilder()
    }
}
