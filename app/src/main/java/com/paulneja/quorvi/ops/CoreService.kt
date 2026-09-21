package com.paulneja.quorvi.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.engine.Agents
import com.paulneja.quorvi.core.IQuorviCore
import com.paulneja.quorvi.ui.operations.OperationsActivity
import com.paulneja.quorvi.core.net.HttpStatusException
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

@AndroidEntryPoint
class CoreService : Service() {
    @Inject lateinit var dao: OpsDao
    @Inject lateinit var github: GitHubSync
    @Inject lateinit var runs: RunCoordinator
    @Inject lateinit var agents: Agents
    @Inject lateinit var connections: com.paulneja.quorvi.connections.ConnectionsRefresher
    @Inject lateinit var services: com.paulneja.quorvi.connections.services.ServicesRepo
    @Inject lateinit var servicesDao: com.paulneja.quorvi.connections.services.ServicesDao
    @Inject lateinit var capsules: com.paulneja.quorvi.agent.capsules.CapsuleRunner
    @Inject lateinit var privileged: com.paulneja.quorvi.control.PrivilegedShells
    @Inject lateinit var backgroundGuard: com.paulneja.quorvi.control.BackgroundGuard
    private var live: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Boolean>(Channel.CONFLATED)
    @Volatile private var visible = false
    @Volatile private var forced = false
    private var loop: Job? = null

    private val binder = object : IQuorviCore.Stub() {
        override fun version(): Int = VERSION
        override fun submitRun(specJson: String): String = runs.submit(specJson).also { if (it.isNotEmpty()) promote() }
        override fun cancelRun(runId: String) { runs.cancel(runId) }
        override fun pauseRun(runId: String) { runs.pause(runId) }
        override fun resumeRun(runId: String) {
            promote()
            runs.resume(runId)
        }
        override fun answerRun(runId: String, answer: String) {
            promote()
            runs.answer(runId, answer)
        }
        override fun killAll() { runs.killAll() }
        override fun runCapsule(capsuleId: String): String {
            var id = ""
            kotlinx.coroutines.runBlocking { id = runCatching { capsules.run(capsuleId) }.getOrDefault("") }
            if (id.isNotEmpty()) promote()
            return id
        }
        override fun refreshConnections(force: Boolean) { wake.trySend(force) }
        override fun setVisible(visible: Boolean) {
            this@CoreService.visible = visible
            if (visible) wake.trySend(false)
            setLive(visible)
        }
        override fun syncExecution(executionId: String) {
            scope.launch {
                runCatching { if (executionId.startsWith("gitlab:")) services.syncGitLabExecution(executionId) else github.syncExecution(executionId) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runs.executor = agents.engine
        scope.launch { runs.reconcile() }
        scope.launch {
            agents.engine.active.collect { count -> if (count > 0) showWorking(count) else demote() }
        }
        loop = scope.launch { pollConnections() }
        scope.launch { runCatching { backgroundGuard.protectWorkProcess() } }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KILL) runs.killAll()
        if (intent?.action == com.paulneja.quorvi.agent.capsules.CapsuleLaunch.ACTION) {
            val id = intent.getStringExtra(com.paulneja.quorvi.agent.capsules.CapsuleLaunch.EXTRA_ID)
            val reason = intent.getStringExtra(com.paulneja.quorvi.agent.capsules.CapsuleLaunch.EXTRA_REASON)
            showWorking(maxOf(1, agents.engine.active.value))
            if (id != null) scope.launch {
                runCatching { capsules.run(id, reason) }
                delay(5_000)
                if (agents.engine.active.value == 0) demote()
            }
            return START_NOT_STICKY
        }
        if (agents.engine.active.value == 0 && intent?.action != ACTION_WORK) {
            demote()
        } else {
            showWorking(maxOf(1, agents.engine.active.value))
            scope.launch {
                delay(5_000)
                if (agents.engine.active.value == 0) demote()
            }
        }
        return START_NOT_STICKY
    }

    private fun promote() {
        runCatching { startForegroundService(Intent(this, CoreService::class.java).setAction(ACTION_WORK)) }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun holdWake() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "nexo:agents").apply { setReferenceCounted(false); acquire(6 * 60 * 60_000L) }
    }

    private fun releaseWake() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun showWorking(count: Int) {
        holdWake()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.agents_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, OperationsActivity.agents(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val kill = PendingIntent.getService(this, 1, Intent(this, CoreService::class.java).setAction(ACTION_KILL), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(resources.getQuantityString(R.plurals.agents_working, count, count))
            .setContentText(getString(R.string.agents_working_hint))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.agents_stop_all), kill).build())
            .build()
        runCatching { startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
    }

    private fun demote() {
        releaseWake()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    private fun setLive(on: Boolean) {
        live?.cancel()
        live = null
        if (!on) return
        live = scope.launch {
            servicesDao.ofKind(com.paulneja.quorvi.connections.services.ServiceKind.HOME_ASSISTANT.name).forEach { service ->
                launch {
                    while (true) {
                        runCatching { services.haEvents(service)?.collect { services.applyState(service.id, it) } }
                        delay(30_000)
                    }
                }
            }
        }
    }

    private suspend fun pollConnections() {
        var backoff = 0L
        var githubAt = 0L
        while (true) {
            val now = System.currentTimeMillis()
            val active = runCatching { dao.activeCi().isNotEmpty() }.getOrDefault(false)
            val githubEvery = maxOf(backoff, if (active) 15_000L else if (visible) 120_000L else 600_000L)
            if (now - githubAt >= githubEvery || forced) {
                forced = false
                githubAt = now
                val report = runCatching { github.syncAll() }
                backoff = when (val error = report.exceptionOrNull()) {
                    is HttpStatusException -> if (error.rateLimited) 15 * 60_000L else if (error.transient) (backoff * 2).coerceIn(30_000L, 10 * 60_000L) else 0L
                    else -> 0L
                }
            }
            runCatching { connections.refreshDue(visible) }
            runCatching { agents.resumeWaiting() }
            runCatching { privileged.verifyOrDegrade() }
            val connectionsDelay = runCatching { connections.nextDelay(visible) }.getOrDefault(Long.MAX_VALUE)
            val githubLeft = (githubAt + maxOf(backoff, if (active) 15_000L else if (visible) 120_000L else 600_000L) - System.currentTimeMillis()).coerceAtLeast(1_000L)
            select<Unit> {
                wake.onReceive { force -> if (force) forced = true }
                onTimeout(minOf(connectionsDelay, githubLeft)) { }
            }
            delay(250)
        }
    }

    companion object {
        const val VERSION = 2
        private const val CHANNEL = "agents"
        private const val NOTIFICATION = 7
        private const val ACTION_WORK = "com.paulneja.quorvi.action.AGENT_WORK"
        private const val ACTION_KILL = "com.paulneja.quorvi.action.KILL_AGENTS"
    }
}
