package com.paulneja.quorvi.agent.capsules

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CapsuleStep(
    val tool: String,
    val args: Map<String, JsonElement> = emptyMap(),
    @SerialName("continueOnError") val continueOnError: Boolean = false,
)

@Serializable
data class CapsuleTrigger(
    val kind: String = MANUAL,

    val minutes: Int = 60,

    val hour: Int = 9,
    val minute: Int = 0,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),

    val ssid: String = "",

    val app: String = "",

    val repo: String = "",
) {
    companion object {
        const val MANUAL = "manual"
        const val INTERVAL = "interval"
        const val DAILY = "daily"
        const val CHARGING = "charging"
        const val WIFI = "wifi"
        const val NOTIFICATION = "notification"
        const val CI_FAILED = "ci_failed"
        const val BOOT = "boot"
    }
}

@Serializable
data class Capsule(
    val schema: Int = 1,
    val name: String,
    val description: String = "",
    val icon: String = "bolt",
    val steps: List<CapsuleStep> = emptyList(),

    val prompt: String = "",
    val trigger: CapsuleTrigger = CapsuleTrigger(),
) {
    val ai: Boolean get() = prompt.isNotBlank()
}

@Entity(tableName = "capsules")
data class CapsuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val json: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastRunAt: Long? = null,
) {
    val ai: Boolean get() = runCatching { lenient.decodeFromString(Capsule.serializer(), json).ai }.getOrDefault(false)

    private companion object {
        val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules ORDER BY name")
    fun observe(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE id = :id")
    suspend fun get(id: String): CapsuleEntity?

    @Upsert
    suspend fun put(capsule: CapsuleEntity)

    @Query("UPDATE capsules SET lastRunAt = :at WHERE id = :id")
    suspend fun markRun(id: String, at: Long)

    @Query("DELETE FROM capsules WHERE id = :id")
    suspend fun delete(id: String)
}

sealed interface CompileResult {
    data class Ok(val capsule: Capsule, val warnings: List<String>) : CompileResult
    data class Failed(val errors: List<String>) : CompileResult
}

class CapsuleCompiler(private val tools: Map<String, com.paulneja.quorvi.agent.tools.Tool>) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun parse(text: String): CompileResult {
        val capsule = runCatching { json.decodeFromString(Capsule.serializer(), text) }.getOrElse { return CompileResult.Failed(listOf("Not valid capsule JSON: ${it.message}")) }
        return compile(capsule)
    }

    fun compile(capsule: Capsule): CompileResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (capsule.schema != 1) errors += "Unsupported schema ${capsule.schema}"
        if (capsule.name.isBlank()) errors += "The capsule needs a name"
        if (capsule.steps.isEmpty() && !capsule.ai) errors += "The capsule needs instructions for the AI or at least one step"
        capsule.steps.forEachIndexed { i, step ->
            val tool = tools[step.tool]
            if (tool == null) {
                errors += "Step ${i + 1}: unknown tool \"${step.tool}\""
                return@forEachIndexed
            }
            val schema = tool.parameters
            val required = (schema["required"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            val properties = (schema["properties"] as? kotlinx.serialization.json.JsonObject)?.keys.orEmpty()
            required.filter { it !in step.args }.forEach { errors += "Step ${i + 1} (${step.tool}): missing \"$it\"" }
            step.args.keys.filter { it !in properties }.forEach { warnings += "Step ${i + 1} (${step.tool}): unknown argument \"$it\"" }
        }
        return if (errors.isEmpty()) CompileResult.Ok(capsule, warnings) else CompileResult.Failed(errors)
    }

    fun encode(capsule: Capsule): String = json.encodeToString(Capsule.serializer(), capsule)
}
