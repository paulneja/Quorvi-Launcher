package com.paulneja.quorvi.agent.engine

import com.paulneja.quorvi.agent.orchestra.AgentConfigDao
import com.paulneja.quorvi.agent.orchestra.AgentConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Singleton
class AiSwitch @Inject constructor(private val dao: AgentConfigDao) {
    val enabled: Flow<Boolean> = dao.observe(KEY).map { it != OFF }.distinctUntilChanged()

    @Volatile private var cached: Boolean? = null

    suspend fun current(): Boolean = dao.get(KEY) != OFF

    suspend fun set(on: Boolean) {
        dao.put(AgentConfigEntity(KEY, if (on) ON else OFF))
        cached = on
    }

    fun now(): Boolean = cached ?: runBlocking(Dispatchers.IO) { current() }.also { cached = it }

    fun watch(scope: CoroutineScope) = scope.launch { enabled.collect { cached = it } }

    private companion object {
        const val KEY = "ai"
        const val ON = "on"
        const val OFF = "off"
    }
}
