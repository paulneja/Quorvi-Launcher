package com.paulneja.quorvi.agent.engine

import com.paulneja.quorvi.agent.models.AiMessage
import com.paulneja.quorvi.agent.models.AiPart
import com.paulneja.quorvi.agent.models.AiRole
import com.paulneja.quorvi.agent.models.AiToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class Transcript(val messages: List<AiMessage>, val pending: List<AiToolCall>, val askId: String? = null) {
    fun encode(): String = buildJsonObject {
        put("messages", buildJsonArray { messages.forEach { add(encode(it)) } })
        put("pending", buildJsonArray { pending.forEach { add(encode(it)) } })
        askId?.let { put("ask", it) }
    }.toString()

    companion object {
        const val SAVED = "transcript"
        const val USED = "transcript-used"

        fun decode(raw: String): Transcript? = runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            Transcript(
                o["messages"]!!.jsonArray.map { message(it.jsonObject) },
                o["pending"]!!.jsonArray.map { call(it.jsonObject) },
                (o["ask"] as? JsonPrimitive)?.content,
            )
        }.getOrNull()

        private fun encode(m: AiMessage) = buildJsonObject {
            put("role", m.role.name)
            put("text", m.text)
            if (m.toolCalls.isNotEmpty()) put("calls", buildJsonArray { m.toolCalls.forEach { add(encode(it)) } })
            m.toolCallId?.let { put("callId", it) }
            m.signature?.let { put("signature", it) }
        }

        private fun encode(c: AiToolCall) = buildJsonObject {
            put("id", c.id)
            put("name", c.name)
            put("arguments", c.arguments)
        }

        private fun message(o: JsonObject) = AiMessage(
            AiRole.valueOf(o["role"]!!.jsonPrimitive.content),
            listOf(AiPart.Text(o["text"]?.jsonPrimitive?.content.orEmpty())),
            (o["calls"] as? JsonArray)?.map { call(it.jsonObject) }.orEmpty(),
            (o["callId"] as? JsonPrimitive)?.content,
            (o["signature"] as? JsonPrimitive)?.content,
        )

        private fun call(o: JsonObject) = AiToolCall(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, o["arguments"]!!.jsonObject)
    }
}
