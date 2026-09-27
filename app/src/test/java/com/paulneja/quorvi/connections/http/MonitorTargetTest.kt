package com.paulneja.quorvi.connections.http

import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTargetTest {
    @Test
    fun buildsAndReadsBackEveryKind() {
        val https = MonitorTarget(MonitorKind.HTTPS, "api.example.com", null, "/v1/health")
        assertEquals("https://api.example.com/v1/health", https.url())
        assertEquals(https, MonitorTarget.parse(https.url()))

        val lan = MonitorTarget(MonitorKind.HTTP, "192.168.1.10", 8123, "estado")
        assertEquals("http://192.168.1.10:8123/estado", lan.url())

        val ssh = MonitorTarget(MonitorKind.TCP, "server.lan", null)
        assertEquals("tcp://server.lan:22", ssh.url())
        assertEquals(22, MonitorTarget.parse("tcp://server.lan:22")!!.effectivePort)
    }

    @Test
    fun defaultPortsAreLeftOutOfWebUrls() {
        assertEquals("https://example.org/", MonitorTarget(MonitorKind.HTTPS, "example.org", 443).url())
        assertEquals("https://example.org:8443/", MonitorTarget(MonitorKind.HTTPS, "example.org", 8443).url())
    }

    @Test
    fun probeMeasuresAnOpenPortAndFailsOnAClosedOne() {
        ServerSocket(0).use { server ->
            val ms = MonitorTarget.probe("127.0.0.1", server.localPort, 2_000)
            assertTrue(ms >= 0)
        }
        val closed = ServerSocket(0).use { it.localPort }
        assertTrue(runCatching { MonitorTarget.probe("127.0.0.1", closed, 1_000) }.isFailure)
    }
}
