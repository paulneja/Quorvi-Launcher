package com.paulneja.quorvi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

class Pcm(val data: ByteArray, val sampleRate: Int)

object Wav {
    fun encode(pcm: ByteArray, sampleRate: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }

    fun decode(bytes: ByteArray): Pcm? {
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        var rate = 0
        var channels = 0
        var bits = 0
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4)
            // Streamed WAVs write 0 or 0xFFFFFFFF as the data size: it then runs to the end of the file.
            val declared = b.getInt(at + 4).toLong() and 0xFFFFFFFFL
            val start = at + 8
            if (id == "fmt ") {
                channels = b.getShort(start + 2).toInt()
                rate = b.getInt(start + 4)
                bits = b.getShort(start + 14).toInt()
            } else if (id == "data") {
                if (rate == 0 || channels != 1 || bits != 16) return null
                val end = if (declared == 0L || start + declared > bytes.size) bytes.size else (start + declared).toInt()
                return Pcm(bytes.copyOfRange(start, end), rate)
            }
            at = start + declared.toInt() + (declared.toInt() and 1)
        }
        return null
    }

    fun rateOf(mime: String, default: Int = 24_000): Int = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: default

    fun level(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size): Float {
        var sum = 0.0
        var n = 0
        var i = offset
        while (i + 1 < offset + length) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort() / 32768.0
            sum += s * s; n++; i += 2
        }
        return if (n == 0) 0f else kotlin.math.sqrt(sum / n).toFloat()
    }
}
