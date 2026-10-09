package com.paulneja.quorvi.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class VoiceRecorder {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    @Volatile private var stopNow = false

    fun finish() { stopNow = true }

    @SuppressLint("MissingPermission")
    suspend fun listen(
        onSpeech: () -> Unit = {},
        gate: SpeechGate = SpeechGate(frameMs = 20),
        giveUpMs: Long = NOTHING_MS,

        duplex: Boolean = false,
    ): ByteArray? = withContext(Dispatchers.IO) {
        stopNow = false
        val frame = RATE / 50
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val source = if (duplex) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val record = AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 2 * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw VoiceException(VoiceException.Reason.FAILED, "microphone unavailable") }
        val effects = if (duplex) listOfNotNull(
            runCatching { android.media.audiofx.AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull(),
            runCatching { android.media.audiofx.NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull(),
        ) else emptyList()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(frame * 2)
        var heard = false
        var filled = 0
        val started = System.nanoTime()
        var lastData = started
        try {
            record.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive()
                if (stopNow) break

                val n = record.read(buf, filled, buf.size - filled, AudioRecord.READ_NON_BLOCKING)
                val now = System.nanoTime()
                if (n <= 0) {
                    val idleMs = (now - lastData) / 1_000_000
                    val totalMs = (now - started) / 1_000_000
                    if (idleMs > STALL_MS || (!heard && totalMs > giveUpMs)) break
                    delay(10)
                    continue
                }
                lastData = now
                filled += n
                if (filled < buf.size) continue
                filled = 0
                out.write(buf, 0, buf.size)
                val level = Wav.level(buf)
                _level.value = (level * 6f).coerceIn(0f, 1f)
                val state = gate.feed(level)
                if (state == SpeechGate.State.SPEAKING && !heard) { heard = true; onSpeech() }
                if (state == SpeechGate.State.DONE || state == SpeechGate.State.NOTHING) break
            }
        } finally {
            runCatching { record.stop() }
            effects.forEach { runCatching { it.release() } }
            record.release()
            _level.value = 0f
        }
        if (!heard && !stopNow) null else Wav.encode(out.toByteArray(), RATE)
    }

    companion object {
        const val RATE = 16_000
        private const val STALL_MS = 1_500L
        private const val NOTHING_MS = 8_000L
    }
}
