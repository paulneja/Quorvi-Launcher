package com.paulneja.quorvi.control

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(val packageName: String, val title: String, val artist: String?, val playing: Boolean)

@Singleton
class DeviceControls @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    private val torchId: String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    init {
        runCatching {
            camera.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) _torch.value = enabled
                }
            }, null)
        }
    }

    val hasTorch: Boolean get() = torchId != null

    fun toggleTorch() {
        torchId?.let { id -> runCatching { camera.setTorchMode(id, !_torch.value) } }
    }

    fun volume(): Pair<Int, Int> = audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).toInt(), 0) }
    }

    fun adjustVolume(up: Boolean) = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)

    val canWriteSettings: Boolean get() = Settings.System.canWrite(context)

    fun brightness(): Int = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)

    fun setBrightness(value: Int) {
        if (!canWriteSettings) return
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
    }

    val canSetDnd: Boolean get() = notifications.isNotificationPolicyAccessGranted

    fun dndOn(): Boolean = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun toggleDnd() {
        if (!canSetDnd) return
        notifications.setInterruptionFilter(if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun controllers(): List<MediaController> = runCatching {
        context.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(context, QuorviNotificationListener::class.java))
    }.getOrDefault(emptyList())

    fun media(): MediaInfo? = controllers().firstOrNull()?.let { c ->
        val meta = c.metadata
        MediaInfo(
            c.packageName,
            meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: c.packageName,
            meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
            c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun mediaPlayPause() = controllers().firstOrNull()?.let { c -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() }
    fun mediaNext() = controllers().firstOrNull()?.transportControls?.skipToNext()
    fun mediaPrevious() = controllers().firstOrNull()?.transportControls?.skipToPrevious()

    companion object {
        val INTERNET_PANEL = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        val BLUETOOTH = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        val NOTIFICATION_ACCESS = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val DND_ACCESS = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        fun writeSettings(context: Context) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
    }
}
