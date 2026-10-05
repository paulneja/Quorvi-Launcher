package com.paulneja.quorvi.agent.orchestra

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.paulneja.quorvi.agent.models.ReasoningEffort
import com.paulneja.quorvi.agent.tools.Risk
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Specialist(val toolRisks: Set<Risk>) {
    THINK(emptySet()),
    PLAN(setOf(Risk.READ)),
    RESEARCH(setOf(Risk.READ, Risk.NETWORK)),
    CODE(setOf(Risk.READ, Risk.WRITE, Risk.NETWORK)),
    WRITE(setOf(Risk.READ, Risk.WRITE, Risk.EXPORT)),
    IMAGE(emptySet()),
    REVIEW(setOf(Risk.READ)),
}

@Serializable
data class ModelRef(val providerId: String, val model: String)

@Serializable
data class ModelBinding(val primary: ModelRef, val fallbacks: List<ModelRef> = emptyList(), val effort: ReasoningEffort? = null, val thinking: Boolean = false) {
    val chain: List<ModelRef> get() = listOf(primary) + fallbacks
}

@Serializable
data class Orchestra(
    val enabled: Boolean = false,
    val coordinator: ModelBinding? = null,
    val specialists: Map<Specialist, ModelBinding> = emptyMap(),
    val active: Set<Specialist> = emptySet(),
    val finalReview: Boolean = false,
    val maxRounds: Int = 12,
    val memory: Boolean = true,
    val autoMemory: Boolean = true,
    val pastChats: Boolean = true,
    val backgroundTasks: Boolean = true,
    val instructions: String = "",
    val effort: ReasoningEffort? = null,
    val thinking: Boolean = false,
) {
    fun ready(s: Specialist): Boolean = enabled && s in active && specialists[s] != null

    val delegates: List<Specialist> get() = Specialist.entries.filter { it != Specialist.REVIEW && ready(it) }

    val reviewing: Boolean get() = finalReview && ready(Specialist.REVIEW)

    val unlimited: Boolean get() = maxRounds == UNLIMITED

    fun clamped() = copy(maxRounds = if (unlimited) UNLIMITED else maxRounds.coerceIn(2, 40), instructions = instructions.take(4000))

    companion object {
        const val UNLIMITED = 0
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(raw: String?): Orchestra = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.clamped() ?: Orchestra()
        fun encode(o: Orchestra): String = json.encodeToString(serializer(), o.clamped())
    }
}

@Entity(tableName = "agent_config")
data class AgentConfigEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface AgentConfigDao {
    @Query("SELECT value FROM agent_config WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM agent_config WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: AgentConfigEntity)
}

@Singleton
class OrchestraStore @Inject constructor(private val dao: AgentConfigDao) {
    val orchestra: Flow<Orchestra> = dao.observe(KEY).map { Orchestra.decode(it) }

    suspend fun current(): Orchestra = Orchestra.decode(dao.get(KEY))

    suspend fun update(change: (Orchestra) -> Orchestra) {
        dao.put(AgentConfigEntity(KEY, Orchestra.encode(change(current()))))
    }

    private companion object {
        const val KEY = "orchestra"
    }
}
