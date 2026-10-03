package com.paulneja.quorvi.labs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object DemoMediaSession {
    private var session: MediaSession? = null
    private var track = 0
    private val tracks = listOf(Triple("Neon light", "Quorvi Demo", 0xFF7B5CFF.toInt()), Triple("Terminal sunset", "The Shells", 0xFFFF7A59.toInt()), Triple("Liquid Glass", "Night Orchestra", 0xFF2EC4B6.toInt()))

    val active: Boolean get() = session != null

    fun start(context: Context) {
        if (session != null) return
        val s = MediaSession(context, "nexo-demo")
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = state(PlaybackState.STATE_PLAYING)
            override fun onPause() = state(PlaybackState.STATE_PAUSED)
            override fun onSkipToNext() { track = (track + 1) % tracks.size; metadata(); state(PlaybackState.STATE_PLAYING, 0) }
            override fun onSkipToPrevious() { track = (track + tracks.size - 1) % tracks.size; metadata(); state(PlaybackState.STATE_PLAYING, 0) }
            override fun onSeekTo(pos: Long) = state(current?.state ?: PlaybackState.STATE_PLAYING, pos)
        })
        session = s
        metadata()
        state(PlaybackState.STATE_PLAYING, 0)
        s.isActive = true
    }

    fun stop() { session?.release(); session = null }

    private var current: PlaybackState? = null

    private fun position(): Long = current?.let { c -> if (c.state == PlaybackState.STATE_PLAYING) c.position + (SystemClock.elapsedRealtime() - c.lastPositionUpdateTime) else c.position } ?: 0

    private fun state(state: Int, pos: Long = position()) {
        val p = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(state, pos, 1f, SystemClock.elapsedRealtime()).build()
        current = p
        session?.setPlaybackState(p)
    }

    private fun metadata() {
        val (title, artist, color) = tracks[track]
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 214_000)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, art(color))
                .build(),
        )
    }

    private fun art(color: Int): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, color, 0xFF101420.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(128f, 128f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x55FFFFFF })
        c.drawCircle(128f, 128f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF101420.toInt() })
        return b
    }
}

@Composable
fun MediaLab() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(DemoMediaSession.active) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Starts a MediaSession with metadata, artwork and seek, without playing audio, to try the media widget.")
        Button(onClick = { if (on) DemoMediaSession.stop() else DemoMediaSession.start(context.applicationContext); on = DemoMediaSession.active }) {
            Text(if (on) "Stop demo session" else "Start demo session")
        }
    }
}
