package com.paulneja.quorvi.connections

import com.paulneja.quorvi.core.net.NetGuard
import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetGuardTest {
    private fun at(ip: String): (String) -> List<InetAddress> = { listOf(InetAddress.getByName(ip)) }

    @Test
    fun credentialsOnlyTravelOverHttpInsideTheLocalNetwork() {
        assertTrue(NetGuard.secureEnough("https://bridge.example.com", at("93.184.216.34")))
        assertFalse(NetGuard.secureEnough("http://bridge.example.com", at("93.184.216.34")))
        assertTrue(NetGuard.secureEnough("http://192.168.1.50:8787", at("192.168.1.50")))
        assertTrue(NetGuard.secureEnough("http://box.tailnet.ts.net", at("100.101.102.103")))
        assertFalse(NetGuard.secureEnough("http://100.20.1.1", at("100.20.1.1")))
        assertFalse(NetGuard.secureEnough("ftp://x", at("192.168.1.1")))
    }
}
