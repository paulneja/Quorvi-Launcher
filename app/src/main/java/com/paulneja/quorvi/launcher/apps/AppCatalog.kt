package com.paulneja.quorvi.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PackageRemoved(val packageName: String, val userSerial: Long)

@Singleton
class AppCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconCache,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()
    private val _profiles = MutableStateFlow<List<ProfileState>>(emptyList())
    val profiles: StateFlow<List<ProfileState>> = _profiles.asStateFlow()
    private val _removed = MutableSharedFlow<PackageRemoved>(extraBufferCapacity = 16)
    val removed: SharedFlow<PackageRemoved> = _removed.asSharedFlow()
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            icons.invalidate(packageName)
            _removed.tryEmit(PackageRemoved(packageName, serial(user)))
            refresh()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            icons.invalidate(packageName)
            refresh()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = refresh()

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = refresh()
    }

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
        }
        ContextCompat.registerReceiver(context, profileReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch {
            for (ignored in refreshes) {
                val loaded = load()
                _apps.value = loaded
                launch { icons.preload(loaded) }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshes.trySend(Unit)
    }

    fun serial(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun user(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun setPaused(user: UserHandle, paused: Boolean): Boolean =
        runCatching { userManager.requestQuietModeEnabled(paused, user) }.getOrDefault(false).also { refresh() }

    private fun profileOf(user: UserHandle): Profile {
        if (user == Process.myUserHandle()) return Profile.MAIN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            if (type == UserManager.USER_TYPE_PROFILE_PRIVATE) return Profile.PRIVATE
        }
        return Profile.WORK
    }

    private fun load(): List<AppEntry> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val states = userManager.userProfiles.map { user ->
            ProfileState(user, serial(user), profileOf(user), runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false))
        }
        _profiles.value = states
        return states.flatMap { state ->
            if (state.profile == Profile.PRIVATE && state.paused) return@flatMap emptyList()
            runCatching { launcherApps.getActivityList(null, state.user) }.getOrDefault(emptyList())
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    AppEntry(
                        key = AppKey(info.componentName.packageName, info.componentName.className, state.serial),
                        label = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName },
                        user = state.user,
                        profile = state.profile,
                        paused = state.paused,
                    )
                }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}
