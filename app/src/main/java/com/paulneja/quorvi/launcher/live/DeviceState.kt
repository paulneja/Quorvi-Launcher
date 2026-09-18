package com.paulneja.quorvi.launcher.live

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.paulneja.quorvi.control.DeviceControls
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private const val KEEP_ALIVE_MS = 5_000L

@Immutable
data class BatteryInfo(val percent: Int, val charging: Boolean, val plugged: Boolean, val temperatureC: Float?)

object BatteryParse {
    fun from(level: Int, scale: Int, status: Int, plugged: Int, tempTenths: Int): BatteryInfo? {
        if (level < 0 || scale <= 0) return null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(
            percent = (level * 100f / scale).toInt().coerceIn(0, 100),
            charging = charging,
            plugged = plugged != 0,
            temperatureC = if (tempTenths > 0) tempTenths / 10f else null,
        )
    }

    fun from(intent: Intent?): BatteryInfo? = intent?.let {
        from(
            it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            it.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            it.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
        )
    }
}

@Singleton
class BatteryState @Inject constructor(@ApplicationContext private val context: Context, scope: CoroutineScope) {
    private fun sticky(): Intent? = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

    val info: StateFlow<BatteryInfo?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { trySend(BatteryParse.from(intent)) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), BatteryParse.from(sticky()))
}

@Immutable
data class SystemInfo(val storageUsed: Long, val storageTotal: Long, val ramUsed: Long, val ramTotal: Long) {
    val storageFraction: Float get() = if (storageTotal > 0) storageUsed.toFloat() / storageTotal else 0f
    val ramFraction: Float get() = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f

    companion object {
        fun of(storageFree: Long, storageTotal: Long, ramAvailable: Long, ramTotal: Long) =
            SystemInfo((storageTotal - storageFree).coerceAtLeast(0), storageTotal, (ramTotal - ramAvailable).coerceAtLeast(0), ramTotal)
    }
}

@Singleton
class SystemStats @Inject constructor(@ApplicationContext private val context: Context, scope: CoroutineScope) {
    private fun read(): SystemInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return SystemInfo.of(stat.availableBytes, stat.totalBytes, memory.availMem, memory.totalMem)
    }

    val info: StateFlow<SystemInfo?> = flow {
        while (true) {
            emit(runCatching { read() }.getOrNull())
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), null)
}

@Immutable
data class NetState(
    val wifiEnabled: Boolean,
    val wifiConnected: Boolean,
    val cellularActive: Boolean,
    val mobileDataEnabled: Boolean?,
    val vpn: Boolean,
    val bluetoothEnabled: Boolean?,
    val airplane: Boolean,
    val privateDnsMode: String?,
    val privateDnsHost: String?,
    val adbWifi: Boolean?,
    val operator: String?,
)

@Singleton
class Connectivity @Inject constructor(@ApplicationContext private val context: Context, scope: CoroutineScope) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter

    @android.annotation.SuppressLint("MissingPermission")
    fun snapshot(): NetState {
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val resolver = context.contentResolver
        return NetState(
            wifiEnabled = runCatching { wifi.isWifiEnabled }.getOrDefault(false),
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellularActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            mobileDataEnabled = runCatching { telephony.isDataEnabled }.getOrNull(),
            vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            bluetoothEnabled = runCatching { bluetooth?.isEnabled }.getOrNull(),
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            privateDnsMode = runCatching { Settings.Global.getString(resolver, "private_dns_mode") }.getOrNull(),
            privateDnsHost = runCatching { Settings.Global.getString(resolver, "private_dns_specifier") }.getOrNull()?.takeIf { it.isNotBlank() },
            adbWifi = runCatching { Settings.Global.getInt(resolver, "adb_wifi_enabled") == 1 }.getOrNull(),
            operator = runCatching { telephony.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() },
        )
    }

    val state: StateFlow<NetState> = callbackFlow {
        fun push() { trySend(snapshot()) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = push()
            override fun onLost(network: Network) = push()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = push()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = push()
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = push()
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        context.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        push()
        awaitClose {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), snapshot())
}

@Immutable
data class DeviceSettingsInfo(
    val brightness: Float,
    val volume: Float,
    val dnd: Boolean,
    val torch: Boolean,
    val hasTorch: Boolean,
    val autoRotate: Boolean,
    val batterySaver: Boolean,
    val canWriteSettings: Boolean,
    val canSetDnd: Boolean,
)

@Singleton
class DeviceSettingsState @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controls: DeviceControls,
    scope: CoroutineScope,
) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun snapshot(torch: Boolean = controls.torch.value): DeviceSettingsInfo {
        val (vol, max) = controls.volume()
        return DeviceSettingsInfo(
            brightness = (controls.brightness() / 255f).coerceIn(0f, 1f),
            volume = if (max > 0) vol.toFloat() / max else 0f,
            dnd = runCatching { controls.dndOn() }.getOrDefault(false),
            torch = torch,
            hasTorch = controls.hasTorch,
            autoRotate = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
            batterySaver = power.isPowerSaveMode,
            canWriteSettings = controls.canWriteSettings,
            canSetDnd = notifications.isNotificationPolicyAccessGranted,
        )
    }

    private val changes = callbackFlow {
        fun push() { trySend(Unit) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = push()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = push()
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        push()
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    val state: StateFlow<DeviceSettingsInfo> = combine(changes, controls.torch) { _, torch -> snapshot(torch) }
        .stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), snapshot())
}
