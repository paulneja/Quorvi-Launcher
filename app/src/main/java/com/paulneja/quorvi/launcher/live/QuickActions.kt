package com.paulneja.quorvi.launcher.live

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Immutable
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.control.PrivilegedShells
import com.paulneja.quorvi.control.RootLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@Immutable
data class AppUsageBytes(val packageName: String, val label: String, val bytes: Long)

@Immutable
sealed interface DataUsage {
    data object NeedsAccess : DataUsage
    data class Month(val mobileBytes: Long, val wifiBytes: Long, val topMobile: List<AppUsageBytes>) : DataUsage
}

@Singleton
class DataUsageSource @Inject constructor(@ApplicationContext private val context: Context, scope: CoroutineScope) {
    private val stats = context.getSystemService(NetworkStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    val accessIntent: Intent get() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun read(): DataUsage {
        if (!hasAccess()) return DataUsage.NeedsAccess
        val start = monthStart()
        val now = System.currentTimeMillis()
        fun device(type: Int) = runCatching { stats.querySummaryForDevice(type, null, start, now).let { it.rxBytes + it.txBytes } }.getOrDefault(0L)
        val perUid = mutableMapOf<Int, Long>()
        runCatching {
            stats.querySummary(ConnectivityManager.TYPE_MOBILE, null, start, now).use { summary ->
                val bucket = NetworkStats.Bucket()
                while (summary.hasNextBucket()) {
                    summary.getNextBucket(bucket)
                    perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
                }
            }
        }
        val pm = context.packageManager
        val top = perUid.entries.sortedByDescending { it.value }.take(3).mapNotNull { (uid, bytes) ->
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            AppUsageBytes(pkg, label, bytes)
        }
        return DataUsage.Month(device(ConnectivityManager.TYPE_MOBILE), device(ConnectivityManager.TYPE_WIFI), top)
    }

    val month: StateFlow<DataUsage?> = flow {
        while (true) {
            emit(runCatching { read() }.getOrNull())
            delay(60_000)
        }
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}

@Immutable
data class PrivStatus(val enabled: Set<PrivBackend>, val available: Set<PrivBackend>, val rootLevel: RootLevel, val degraded: Set<PrivBackend>) {
    val usable: PrivBackend? get() = listOf(PrivBackend.SHIZUKU, PrivBackend.ROOT, PrivBackend.ADB_TCP).firstOrNull { it in enabled && it in available }
}

@Singleton
class PrivStatusSource @Inject constructor(private val shells: PrivilegedShells, scope: CoroutineScope) {
    suspend fun read(): PrivStatus {
        val cfg = shells.config()
        val available = PrivBackend.entries.filter { b -> b in cfg.enabled && runCatching { shells.available(b) }.getOrDefault(false) }.toSet()
        return PrivStatus(cfg.enabled, available, cfg.rootLevel, shells.degraded.value)
    }

    private val kicks = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun refresh() { kicks.trySend(Unit) }

    val status: StateFlow<PrivStatus?> = flow {
        while (true) {
            emit(runCatching { read() }.getOrNull())
            kotlinx.coroutines.withTimeoutOrNull(10_000) { kicks.receive() }
        }
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}

sealed interface ActionOutcome {
    data object Done : ActionOutcome
    data class OpenSettings(val intent: Intent) : ActionOutcome
    data class Failed(val message: String) : ActionOutcome
}

@Singleton
class QuickActions @Inject constructor(private val shells: PrivilegedShells, private val priv: PrivStatusSource) {
    private suspend fun run(command: String, fallback: Intent, level: RootLevel = RootLevel.CONTROL): ActionOutcome = withContext(Dispatchers.IO) {
        val backend = runCatching { priv.read().usable }.getOrNull() ?: return@withContext ActionOutcome.OpenSettings(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val result = runCatching { shells.exec(backend, command, level, timeoutSec = 15) }.getOrElse { return@withContext ActionOutcome.Failed(it.message ?: "error") }
        if (result.exitCode == 0) ActionOutcome.Done else ActionOutcome.Failed(result.stderr.ifBlank { result.stdout }.take(200))
    }

    suspend fun wifi(on: Boolean) = run("svc wifi ${if (on) "enable" else "disable"}", Intent(Settings.Panel.ACTION_WIFI))
    suspend fun mobileData(on: Boolean) = run("svc data ${if (on) "enable" else "disable"}", Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    suspend fun bluetooth(on: Boolean) = run("svc bluetooth ${if (on) "enable" else "disable"}", Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    suspend fun adbWifi(on: Boolean) = run("settings put global adb_wifi_enabled ${if (on) 1 else 0}", Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    suspend fun batterySaver(on: Boolean) = run("settings put global low_power ${if (on) 1 else 0}", Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    suspend fun autoRotate(on: Boolean) = run("settings put system accelerometer_rotation ${if (on) 1 else 0}", Intent(Settings.ACTION_DISPLAY_SETTINGS))
    suspend fun privateDns(host: String?) = run(
        if (host.isNullOrBlank()) "settings put global private_dns_mode opportunistic" else "settings put global private_dns_mode hostname && settings put global private_dns_specifier ${host.filter { it.isLetterOrDigit() || it == '.' || it == '-' }}",
        Intent("android.settings.WIRELESS_SETTINGS"),
        RootLevel.MODIFY,
    )

    val hotspotSettings: Intent get() = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val vpnSettings: Intent get() = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
