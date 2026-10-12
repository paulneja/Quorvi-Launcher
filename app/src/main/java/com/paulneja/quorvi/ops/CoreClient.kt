package com.paulneja.quorvi.ops

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.paulneja.quorvi.core.IQuorviCore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CoreClient @Inject constructor(@ApplicationContext private val context: Context) {
    private val _core = MutableStateFlow<IQuorviCore?>(null)
    val connected: StateFlow<IQuorviCore?> = _core.asStateFlow()
    private var bound = 0
    private var visible = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val core = IQuorviCore.Stub.asInterface(service)
            com.paulneja.quorvi.agent.engine.AgentLog.i(null, "core service connected")
            _core.value = core
            runCatching { core.setVisible(visible) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            com.paulneja.quorvi.agent.engine.AgentLog.w(null, "core service disconnected")
            _core.value = null
        }
    }

    fun attach() {
        if (bound++ == 0) context.bindService(Intent(context, CoreService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun ensure() {
        if (bound == 0) attach()
    }

    fun wake() {
        com.paulneja.quorvi.agent.engine.AgentLog.w(null, "core service not answering: waking its process")
        runCatching {
            context.startActivity(Intent(context, CoreWakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        if (bound > 0) {
            runCatching { context.unbindService(connection) }
            context.bindService(Intent(context, CoreService::class.java), connection, Context.BIND_AUTO_CREATE)
        } else {
            attach()
        }
    }

    fun detach() {
        if (bound > 0 && --bound == 0) {
            runCatching { context.unbindService(connection) }
            _core.value = null
        }
    }

    fun setVisible(value: Boolean) {
        visible = value
        call { it.setVisible(value) }
    }

    fun refresh(force: Boolean = true) = call { it.refreshConnections(force) }

    fun syncExecution(id: String) = call { it.syncExecution(id) }

    fun cancelRun(id: String) = call { it.cancelRun(id) }

    fun pauseRun(id: String) = call { it.pauseRun(id) }

    fun resumeRun(id: String) = call { it.resumeRun(id) }

    fun answerRun(id: String, answer: String) = call { it.answerRun(id, answer) }

    fun killAll() = call { it.killAll() }

    fun runCapsule(id: String): String? = _core.value?.let { runCatching { it.runCapsule(id) }.getOrNull() }?.takeIf { it.isNotEmpty() }

    fun submitRun(specJson: String): String? {
        val core = _core.value ?: run { com.paulneja.quorvi.agent.engine.AgentLog.w(null, "submit failed: core service not connected"); return null }
        return runCatching { core.submitRun(specJson) }.onFailure { com.paulneja.quorvi.agent.engine.AgentLog.w(null, "submit failed", it) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun call(block: (IQuorviCore) -> Unit) {
        _core.value?.let { runCatching { block(it) } }
    }
}
