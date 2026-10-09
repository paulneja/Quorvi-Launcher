package com.paulneja.quorvi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.paulneja.quorvi.core.settings.VoiceConfig
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class VoicePlayer(private val context: Context, private val api: VoiceApi, private val config: () -> VoiceConfig) {
    private fun speed(): Float = config().speed.coerceIn(0.7f, 2f)

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queue = Channel<Deferred<Speech?>>(Channel.UNLIMITED)
    private var worker: Job? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)
    private var track: AudioTrack? = null

    fun say(sentence: String, synth: (suspend () -> Speech)? = null) {
        if (sentence.isBlank()) return
        if (worker?.isActive != true) start()
        pending.incrementAndGet()
        _speaking.value = true
        _error.value = null
        val job = scope.async { runCatching { synth?.invoke() ?: api.speak(sentence, config()) }.onFailure { _error.value = it.message }.getOrNull() }

        queue.trySend(job)
    }

    suspend fun awaitDone() { speaking.first { !it } }

    fun stop() {
        scope.cancel()
        runCatching { track?.pause(); track?.flush(); track?.release() }
        track = null
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        queue = Channel(Channel.UNLIMITED)
        worker = null
        pending.set(0)
        _level.value = 0f
        _speaking.value = false
    }

    private fun start() {
        val q = queue
        worker = scope.launch {
            for (next in q) {
                next.await()?.let { play(it) }
                if (pending.decrementAndGet() <= 0) { pending.set(0); _level.value = 0f; _speaking.value = false }
            }
        }
    }

    private suspend fun play(speech: Speech) {
        speech.pcm?.let { return playPcm(it) }
        speech.encoded?.let { playEncoded(it) }
    }

    private suspend fun playPcm(pcm: Pcm) {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val format = AudioFormat.Builder().setSampleRate(pcm.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val min = AudioTrack.getMinBufferSize(pcm.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(format).setBufferSizeInBytes(maxOf(min, pcm.sampleRate / 5)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = t
        try {
            speed().takeIf { it != 1f }?.let { s -> runCatching { t.playbackParams = android.media.PlaybackParams().setSpeed(s).setPitch(1f) } }
            t.play()
            val chunk = pcm.sampleRate / 25 * 2
            var at = 0
            while (at < pcm.data.size) {
                currentCoroutineContext().ensureActive()
                val n = minOf(chunk, pcm.data.size - at)
                _level.value = (Wav.level(pcm.data, at, n) * 5f).coerceIn(0f, 1f)
                t.write(pcm.data, at, n)
                at += n
            }

            val frames = pcm.data.size / 2
            while (t.playbackHeadPosition < frames && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                currentCoroutineContext().ensureActive()
                kotlinx.coroutines.delay(20)
            }
        } finally {
            runCatching { t.stop(); t.release() }
            if (track === t) track = null
        }
    }

    private suspend fun playEncoded(bytes: ByteArray) {
        val file = File.createTempFile("speech", ".audio", context.cacheDir).apply { writeBytes(bytes) }
        val player = MediaPlayer()
        try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { runCatching { player.stop() } }
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                player.setDataSource(file.path)
                player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                player.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                player.prepare()
                speed().takeIf { it != 1f }?.let { s -> runCatching { player.playbackParams = android.media.PlaybackParams().setSpeed(s).setPitch(1f) } }
                _level.value = 0.5f
                player.start()
            }
        } finally {
            player.release()
            file.delete()
        }
    }
}
