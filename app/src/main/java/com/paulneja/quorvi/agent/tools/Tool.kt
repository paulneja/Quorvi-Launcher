package com.paulneja.quorvi.agent.tools

import com.paulneja.quorvi.agent.models.AiToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class Risk { READ, WRITE, NETWORK, EXPORT, EXTERNAL }

enum class ToolStatus { SUCCESS, UNSUPPORTED, UNAUTHORIZED, TIMED_OUT, FAILED, CANCELLED, DENIED }

data class ToolResult(
    val status: ToolStatus,
    val output: String,
    val artifactId: String? = null,
) {
    val ok: Boolean get() = status == ToolStatus.SUCCESS

    companion object {
        fun ok(output: String, artifactId: String? = null) = ToolResult(ToolStatus.SUCCESS, output, artifactId)
        fun failed(output: String, status: ToolStatus = ToolStatus.FAILED) = ToolResult(status, output)
    }
}

class ToolContext(
    val runId: String,
    val stepId: String,
    val workspace: String,
    val ciExecutionId: String?,
    val emit: suspend (type: String, message: String, payload: String?) -> Unit,
) {
    fun path(raw: String): String {
        val clean = raw.trim().removePrefix("./")
        require(!clean.startsWith("/") && clean.split('/').none { it == ".." }) { "Paths must stay inside the run workspace: $raw" }
        return if (clean.isEmpty() || clean == ".") workspace else "$workspace/$clean"
    }
}

interface Tool {
    val name: String
    val version: Int get() = 1
    val description: String
    val risk: Risk
    val idempotent: Boolean
    val parameters: JsonObject

    suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult

    fun spec() = AiToolSpec(name, description, parameters)
}

class ToolArgumentException(message: String) : IllegalArgumentException(message)

fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: throw ToolArgumentException("Missing \"$key\"")
fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
fun JsonObject.boolOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull ?: throw ToolArgumentException("\"$key\" must be an array of strings") }
        ?: throw ToolArgumentException("Missing \"$key\"")

class SchemaBuilder {
    private val properties = linkedMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, optional: Boolean = false) = add(name, "string", description, optional)
    fun integer(name: String, description: String, optional: Boolean = true) = add(name, "integer", description, optional)
    fun stringArray(name: String, description: String, optional: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "array")
            putJsonObject("items") { put("type", "string") }
            put("description", description)
        }
        if (!optional) required += name
    }

    private fun add(name: String, type: String, description: String, optional: Boolean) {
        properties[name] = buildJsonObject { put("type", type); put("description", description) }
        if (!optional) required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        put("additionalProperties", false)
    }
}

fun schema(block: SchemaBuilder.() -> Unit): JsonObject = SchemaBuilder().apply(block).build()
