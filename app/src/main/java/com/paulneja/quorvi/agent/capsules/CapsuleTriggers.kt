package com.paulneja.quorvi.agent.capsules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ops.OpsDao
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Singleton
class CapsuleTriggers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CapsuleDao,
    private val ops: OpsDao,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lastFired = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var started = false
    private val seenFailures = mutableSetOf<String>()

    fun start() {
        if (started) return
        started = true
        scope.launch { dao.observe().collect { list -> schedule(list) } }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = fire(CapsuleTrigger.CHARGING, context.getString(R.string.trigger_reason_charging))
        }, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        runCatching {
            context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
                    @Suppress("DEPRECATION") val ssid = runCatching { context.getSystemService(WifiManager::class.java).connectionInfo?.ssid?.trim('"') }.getOrNull()
                    fire(CapsuleTrigger.WIFI, context.getString(R.string.trigger_reason_wifi)) { t -> t.ssid.isBlank() || t.ssid.equals(ssid, ignoreCase = true) }
                }
            })
        }
        scope.launch {
            ops.observeCi().collect { list ->
                val failed = list.filter { it.conclusion == "failure" }
                if (seenFailures.isEmpty()) { seenFailures += failed.map { it.id }; return@collect }
                failed.filter { seenFailures.add(it.id) }.forEach { e ->
                    fire(CapsuleTrigger.CI_FAILED, context.getString(R.string.trigger_reason_ci, e.repo)) { t -> t.repo.isBlank() || t.repo.equals(e.repo, ignoreCase = true) }
                }
            }
        }
    }

    fun onNotification(pkg: String) {
        if (pkg == context.packageName) return
        fire(CapsuleTrigger.NOTIFICATION, context.getString(R.string.trigger_reason_notification)) { t -> t.app.equals(pkg, ignoreCase = true) }
    }

    fun onBoot() = fire(CapsuleTrigger.BOOT, context.getString(R.string.trigger_reason_boot))

    private fun fire(kind: String, reason: String, matches: (CapsuleTrigger) -> Boolean = { true }) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao.observe().first().filter { it.enabled }.forEach { e ->
                val capsule = runCatching { json.decodeFromString(Capsule.serializer(), e.json) }.getOrNull() ?: return@forEach
                if (capsule.trigger.kind != kind || !matches(capsule.trigger)) return@forEach

                if (now - (lastFired[e.id] ?: 0L) < 120_000) return@forEach
                lastFired[e.id] = now
                CapsuleLaunch.start(context, e.id, reason)
            }
        }
    }

    private fun schedule(list: List<CapsuleEntity>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        list.forEach { e ->
            val pending = alarmIntent(e.id)
            val capsule = runCatching { json.decodeFromString(Capsule.serializer(), e.json) }.getOrNull()
            val at = if (e.enabled && capsule != null) CapsuleSchedule.next(capsule.trigger, now) else null
            if (at == null) alarms.cancel(pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun alarmIntent(id: String): PendingIntent =
        PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, CapsuleAlarmReceiver::class.java).putExtra(CapsuleLaunch.EXTRA_ID, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun rescheduleAll() = scope.launch { schedule(dao.observe().first()) }
}

@AndroidEntryPoint
class CapsuleAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var triggers: CapsuleTriggers

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(CapsuleLaunch.EXTRA_ID)?.let { CapsuleLaunch.start(context, it, context.getString(R.string.trigger_reason_timer)) }
        triggers.rescheduleAll()
    }
}

@AndroidEntryPoint
class CapsuleBootReceiver : BroadcastReceiver() {
    @Inject lateinit var triggers: CapsuleTriggers

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        triggers.start()
        triggers.onBoot()
    }
}
