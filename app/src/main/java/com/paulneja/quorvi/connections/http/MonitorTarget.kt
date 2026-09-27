package com.paulneja.quorvi.connections.http

import java.net.InetSocketAddress
import java.net.Socket

enum class MonitorKind(val scheme: String, val defaultPort: Int) { HTTP("http", 80), HTTPS("https", 443), TCP("tcp", 22) }

data class MonitorTarget(val kind: MonitorKind, val host: String, val port: Int?, val path: String = "/") {
    val effectivePort: Int get() = port ?: kind.defaultPort

    fun url(): String = buildString {
        append(kind.scheme).append("://").append(host.trim())
        if (kind == MonitorKind.TCP) append(':').append(effectivePort)
        else {
            if (port != null && port != kind.defaultPort) append(':').append(port)
            val p = path.trim().ifBlank { "/" }
            append(if (p.startsWith("/")) p else "/$p")
        }
    }

    companion object {
        fun parse(url: String): MonitorTarget? {
            val m = Regex("^(https?|tcp)://([^/:?#]*)(?::(\\d{1,5}))?(.*)$", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
            val kind = when (m.groupValues[1].lowercase()) { "https" -> MonitorKind.HTTPS; "tcp" -> MonitorKind.TCP; else -> MonitorKind.HTTP }
            val port = m.groupValues[3].toIntOrNull()?.takeIf { it in 1..65535 }
            return MonitorTarget(kind, m.groupValues[2], port, m.groupValues[4].ifBlank { "/" })
        }

        fun probe(host: String, port: Int, timeoutMs: Int = 5_000): Long {
            val start = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            return (System.nanoTime() - start) / 1_000_000
        }
    }
}
