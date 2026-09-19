package com.paulneja.quorvi.launcher.live

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.paulneja.quorvi.control.QuorviNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

@Immutable
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String?,
    val art: ImageBitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val updatedAt: Long,
) {
    fun positionAt(elapsedRealtime: Long): Long =
        if (!playing) positionMs else (positionMs + ((elapsedRealtime - updatedAt) * speed).toLong()).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
}

@Immutable
sealed interface MediaState {
    data object NeedsAccess : MediaState
    data object Idle : MediaState
    data class Active(val now: NowPlaying) : MediaState
}

data class SessionInfo(val index: Int, val playing: Boolean, val hasMetadata: Boolean)

object MediaPick {
    fun pick(sessions: List<SessionInfo>): Int? =
        sessions.firstOrNull { it.playing && it.hasMetadata }?.index ?: sessions.firstOrNull { it.hasMetadata }?.index
}

@Singleton
class MediaSessions @Inject constructor(@ApplicationContext private val context: Context, scope: CoroutineScope) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, QuorviNotificationListener::class.java)
    private var current: MediaController? = null

    private fun scaled(bitmap: Bitmap?): ImageBitmap? {
        bitmap ?: return null
        val side = 256
        val scale = side.toFloat() / maxOf(bitmap.width, bitmap.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        return out.asImageBitmap()
    }

    private fun describe(c: MediaController): NowPlaying? {
        val meta = c.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val state = c.playbackState
        return NowPlaying(
            packageName = c.packageName,
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = scaled(meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = state?.position ?: 0L,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            updatedAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
        )
    }

    val state: StateFlow<MediaState> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
        fun publish(controllers: List<MediaController>) {
            val index = MediaPick.pick(controllers.mapIndexed { i, c -> SessionInfo(i, c.playbackState?.state == PlaybackState.STATE_PLAYING, c.metadata != null) })
            current = index?.let { controllers[it] }
            trySend(current?.let(::describe)?.let { MediaState.Active(it) } ?: MediaState.Idle)
        }
        fun watch(controllers: List<MediaController>) {
            callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
            callbacks.clear()
            controllers.forEach { c ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controllers)
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controllers)
                    override fun onSessionDestroyed() = publish(controllers - c)
                }
                c.registerCallback(cb, handler)
                callbacks[c] = cb
            }
            publish(controllers)
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }
        val ok = runCatching {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            watch(manager.getActiveSessions(component))
        }.isSuccess
        if (!ok) {
            trySend(MediaState.NeedsAccess)
            launch {
                while (true) {
                    kotlinx.coroutines.delay(2_000)
                    val retry = runCatching {
                        manager.addOnActiveSessionsChangedListener(listener, component, handler)
                        watch(manager.getActiveSessions(component))
                    }.isSuccess
                    if (retry) break
                }
            }
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), MediaState.Idle)

    fun playPause() = current?.let { c -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() }
    fun next() = current?.transportControls?.skipToNext()
    fun previous() = current?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = current?.transportControls?.seekTo(ms.coerceAtLeast(0))
    fun open() = current?.sessionActivity?.let { runCatching { it.send() } }
}
