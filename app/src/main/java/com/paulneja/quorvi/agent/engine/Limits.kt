package com.paulneja.quorvi.agent.engine

import com.paulneja.quorvi.agent.orchestra.AgentConfigDao
import com.paulneja.quorvi.agent.orchestra.AgentConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentLimits(
    val commandMinutes: Int = 20,
    val sshMinutes: Int = 10,
    val modelIdleSeconds: Int = 120,
    val modelTotalMinutes: Int = 15,
) {
    fun clamped() = copy(
        commandMinutes = commandMinutes.coerceIn(1, MAX_COMMAND_MINUTES),
        sshMinutes = sshMinutes.coerceIn(1, MAX_COMMAND_MINUTES),
        modelIdleSeconds = modelIdleSeconds.coerceIn(30, 900),
        modelTotalMinutes = modelTotalMinutes.coerceIn(2, 120),
    )

    val commandSeconds: Int get() = commandMinutes * 60
    val sshSeconds: Int get() = sshMinutes * 60

    fun applyTo(base: EngineLimits) = base.copy(
        modelTimeoutMs = modelIdleSeconds * 1000L,
        modelTotalMs = modelTotalMinutes * 60_000L,
        toolTimeoutMs = maxOf(commandMinutes, sshMinutes) * 60_000L + 60_000L,
    )

    companion object {
        const val MAX_COMMAND_MINUTES = 180
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(raw: String?): AgentLimits = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.clamped() ?: AgentLimits()
        fun encode(l: AgentLimits): String = json.encodeToString(serializer(), l.clamped())
    }
}

@Singleton
class LimitsStore @Inject constructor(private val dao: AgentConfigDao) {
    val limits: Flow<AgentLimits> = dao.observe(KEY).map { AgentLimits.decode(it) }

    suspend fun current(): AgentLimits = AgentLimits.decode(dao.get(KEY))

    suspend fun update(change: (AgentLimits) -> AgentLimits) = dao.put(AgentConfigEntity(KEY, AgentLimits.encode(change(current()))))

    private companion object {
        const val KEY = "limits"
    }
}
