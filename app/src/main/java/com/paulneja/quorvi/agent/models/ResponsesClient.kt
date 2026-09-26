package com.paulneja.quorvi.agent.models

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ResponsesClient(
    private val http: Http,
    private val baseUrl: String,
    private val apiKey: String?,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : AiClient {
    private val headers = buildMap { putAll(extraHeaders); if (!apiKey.isNullOrBlank()) put("Authorization", "Bearer $apiKey") }

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        try {
            run(request, withTemperature = request.temperature != null, withReasoning = request.reasoning.effort != null)
        } catch (e: HttpStatusException) {
            if (e.status != 400) throw e
            when {
                request.temperature != null && e.body.contains("temperature", ignoreCase = true) -> run(request, false, request.reasoning.effort != null)
                request.reasoning.effort != null && (e.body.contains("reasoning", true) || e.body.contains("effort", true)) -> {
                    emit(AiEvent.Notice("The model rejected the reasoning setting; retried without it."))
                    run(request, request.temperature != null, false)
                }
                else -> throw e
            }
        }
    }

    private suspend fun FlowCollector<AiEvent>.run(request: AiRequest, withTemperature: Boolean, withReasoning: Boolean) {
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("store", false)
            request.system?.let { put("instructions", it) }
            put("input", input(request.messages))
            put("max_output_tokens", request.maxTokens)
            if (withTemperature) request.temperature?.let { put("temperature", it.coerceIn(0.0, 2.0)) }
            if (withReasoning) request.reasoning.effort?.let { effort -> putJsonObject("reasoning") { put("effort", if (effort == ReasoningEffort.MAX) "high" else effort.name.lowercase()) } }
            putJsonArray("include") { add("reasoning.encrypted_content") }
            if (request.tools.isNotEmpty()) put("tools", buildJsonArray {
                request.tools.forEach { t -> addJsonObject { put("type", "function"); put("name", t.name); put("description", t.description); put("parameters", t.parameters) } }
            })
        }
        val calls = sortedMapOf<Int, PendingCall>()
        val reasoning = mutableListOf<JsonObject>()
        var usage: AiUsage? = null
        http.stream("${baseUrl.trimEnd('/')}/responses", body, headers).collect { sse ->
            val o = runCatching { http.json.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
            val index = (o["output_index"] as? JsonPrimitive)?.intOrNull ?: -1
            when ((o["type"] as? JsonPrimitive)?.contentOrNull ?: sse.event) {
                "response.output_text.delta" -> (o["delta"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.TextDelta(it)) }
                "response.output_item.added" -> {
                    val item = o["item"] as? JsonObject
                    if (item?.str("type") == "function_call") calls[index] = PendingCall(item.str("call_id"), item.str("name"))
                }
                "response.function_call_arguments.delta" -> calls[index]?.args?.append((o["delta"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                "response.output_item.done" -> {
                    val item = o["item"] as? JsonObject ?: return@collect
                    when (item.str("type")) {
                        "reasoning" -> reasoning += item
                        "function_call" -> {
                            val p = calls.getOrPut(index) { PendingCall(null, null) }
                            item.str("call_id")?.let { p.callId = it }
                            item.str("name")?.let { p.name = it }
                            item.str("arguments")?.let { p.args.setLength(0); p.args.append(it) }
                        }
                    }
                }
                "response.completed", "response.incomplete" -> {
                    val u = (o["response"] as? JsonObject)?.get("usage") as? JsonObject
                    if (u != null) usage = AiUsage((u["input_tokens"] as? JsonPrimitive)?.longOrNull, (u["output_tokens"] as? JsonPrimitive)?.longOrNull)
                }
                "response.failed", "error" -> {
                    val err = ((o["response"] as? JsonObject)?.get("error") ?: o["error"] ?: o) as? JsonObject
                    throw HttpStatusException(400, err?.str("message") ?: err.toString())
                }
            }
        }
        if (calls.isNotEmpty()) {
            emit(AiEvent.ToolCalls(calls.values.mapIndexed { i, p ->
                AiToolCall(p.callId ?: "call_$i", p.name.orEmpty(), runCatching { http.json.parseToJsonElement(p.args.toString().ifBlank { "{}" }).jsonObject }.getOrDefault(buildJsonObject { }))
            }))
        }
        emit(AiEvent.Done(usage, if (reasoning.isEmpty()) null else SIGNATURE_PREFIX + JsonArray(reasoning).toString()))
    }

    private fun input(messages: List<AiMessage>) = buildJsonArray {
        messages.filter { it.role != AiRole.SYSTEM }.forEach { m ->
            when (m.role) {
                AiRole.USER, AiRole.SYSTEM -> addJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        addJsonObject { put("type", "input_text"); put("text", m.text.ifBlank { "(attachment)" }) }
                        m.parts.filterIsInstance<AiPart.Image>().forEach { img -> addJsonObject { put("type", "input_image"); put("image_url", "data:${img.mediaType};base64,${img.base64}") } }
                    })
                }
                AiRole.ASSISTANT -> {
                    reasoningItems(m.signature).forEach { add(it) }
                    if (m.text.isNotBlank()) addJsonObject {
                        put("type", "message")
                        put("role", "assistant")
                        put("content", buildJsonArray { addJsonObject { put("type", "output_text"); put("text", m.text) } })
                    }
                    m.toolCalls.forEach { c -> addJsonObject { put("type", "function_call"); put("call_id", c.id); put("name", c.name); put("arguments", c.arguments.toString()) } }
                }
                AiRole.TOOL -> addJsonObject { put("type", "function_call_output"); put("call_id", m.toolCallId.orEmpty()); put("output", m.text) }
            }
        }
    }

    private fun reasoningItems(signature: String?): List<JsonObject> =
        signature?.takeIf { it.startsWith(SIGNATURE_PREFIX) }
            ?.let { runCatching { http.json.parseToJsonElement(it.removePrefix(SIGNATURE_PREFIX)).jsonArray.map { e -> e.jsonObject } }.getOrNull() }
            .orEmpty()

    override suspend fun generateImage(model: String, prompt: String): ByteArray? = OpenAiImages.generate(http, baseUrl, headers, model, prompt)

    override suspend fun listModels(): List<AiModelInfo> = OpenAiCompatibleClient(http, baseUrl, apiKey, extraHeaders).listModels()

    private class PendingCall(var callId: String?, var name: String?) {
        val args = StringBuilder()
    }

    companion object {
        const val SIGNATURE_PREFIX = "responses:"
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    }
}
