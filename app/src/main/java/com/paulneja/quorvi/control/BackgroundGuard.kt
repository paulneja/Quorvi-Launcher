package com.paulneja.quorvi.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.paulneja.quorvi.core.settings.QuorviSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class BackgroundGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: QuorviSettings,
    private val shells: PrivilegedShells,
) {
    val unrestricted: Boolean get() = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @Suppress("BatteryLife")
    fun exemptionIntent(): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val protection get() = settings.backgroundProtection

    suspend fun setProtection(on: Boolean): String {
        val cfg = settings.privileged()
        val backend = listOf(PrivBackend.SHIZUKU, PrivBackend.ROOT).firstOrNull { it in cfg.enabled && shells.available(it) }
            ?: throw PrivUnavailable(context.getString(com.paulneja.quorvi.R.string.bg_needs_priv))
        val pkg = context.packageName
        val commands = if (on) listOf(
            "dumpsys deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg WAKE_LOCK allow",
            "am set-standby-bucket $pkg active",
        ) else listOf(
            "dumpsys deviceidle whitelist -$pkg",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default",
            "cmd appops set $pkg RUN_IN_BACKGROUND default",
            "am set-standby-bucket $pkg working_set",
        )
        val r = shells.exec(backend, commands.joinToString("; "), RootLevel.CONTROL, timeoutSec = 30)
        settings.setBackgroundProtection(on)
        if (on && backend == PrivBackend.ROOT) protectWorkProcess()
        return if (r.exitCode == 0) backend.name else "${backend.name}: ${r.stderr.take(200)}"
    }

    suspend fun protectWorkProcess() {
        if (!settings.backgroundProtection.first()) return
        val cfg = settings.privileged()
        if (PrivBackend.ROOT !in cfg.enabled) return
        runCatching { shells.exec(PrivBackend.ROOT, "for p in $(pidof ${context.packageName}:work); do echo -800 > /proc/\$p/oom_score_adj; done", RootLevel.CONTROL, timeoutSec = 15) }
    }
}
