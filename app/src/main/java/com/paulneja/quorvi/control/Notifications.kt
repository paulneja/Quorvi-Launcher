package com.paulneja.quorvi.control

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveNotification(val key: String, val packageName: String, val title: String, val text: String, val postedAt: Long, val ongoing: Boolean)

@Singleton

class NotificationHub @Inject constructor() {
    private val _active = MutableStateFlow<List<ActiveNotification>>(emptyList())
    val active: StateFlow<List<ActiveNotification>> = _active.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val badges: Flow<Set<String>> get() = kotlinx.coroutines.flow.flow { active.collect { list -> emit(list.filter { !it.ongoing }.map { it.packageName }.toSet()) } }

    fun publish(list: List<ActiveNotification>) {
        _active.value = list.sortedByDescending { it.postedAt }
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _active.value = emptyList()
    }

    var dismiss: ((String) -> Unit)? = null

    fun ensureConnected(context: Context) {
        if (_connected.value || !granted(context)) return
        runCatching { android.service.notification.NotificationListenerService.requestRebind(ComponentName(context, QuorviNotificationListener::class.java)) }
    }

    companion object {
        fun granted(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(context, QuorviNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }
}

@AndroidEntryPoint
class QuorviNotificationListener : NotificationListenerService() {
    @Inject lateinit var hub: NotificationHub
    @Inject lateinit var capsuleTriggers: com.paulneja.quorvi.agent.capsules.CapsuleTriggers

    override fun onListenerConnected() {
        hub.setConnected(true)
        hub.dismiss = { key -> runCatching { cancelNotification(key) } }
        refresh()
    }

    override fun onListenerDisconnected() {
        hub.setConnected(false)
        hub.dismiss = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
        if (!sbn.isOngoing) capsuleTriggers.onNotification(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .map { sbn ->
                val extras = sbn.notification.extras
                ActiveNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
                    postedAt = sbn.postTime,
                    ongoing = sbn.isOngoing,
                )
            }
        hub.publish(list)
    }
}
