package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.policy.PolicyEngine
import com.paulneja.quorvi.agent.policy.Verdict
import com.paulneja.quorvi.control.PowerHooks
import com.paulneja.quorvi.control.RootLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedTest {
    @Test
    fun relaxShellLiftsTheHardLimits() {
        val strict = PolicyEngine()
        assertEquals(Verdict.DENY, strict.shell(listOf("su", "-c", "id")).verdict)
        assertEquals(Verdict.DENY, strict.shell(listOf("rm", "-rf", "/sdcard")).verdict)
        assertTrue(strict.shell(listOf("git", "push")).publish)

        val relaxed = PolicyEngine(relaxShell = true)
        assertEquals(Verdict.ALLOW, relaxed.shell(listOf("su", "-c", "id")).verdict)
        assertEquals(Verdict.ALLOW, relaxed.shell(listOf("rm", "-rf", "/sdcard")).verdict)
        assertEquals(Verdict.ALLOW, relaxed.shell(listOf("git", "push")).verdict)
        assertFalse(relaxed.shell(listOf("git", "push")).publish)
    }

    @Test
    fun relaxNetworkAllowsLocalTargets() {
        val strict = PolicyEngine(resolver = { listOf(java.net.InetAddress.getByName("192.168.1.5")) })
        assertEquals(Verdict.DENY, strict.network("http://192.168.1.5/admin").verdict)
        val relaxed = PolicyEngine(relaxNetwork = true, resolver = { listOf(java.net.InetAddress.getByName("192.168.1.5")) })
        assertEquals(Verdict.ALLOW, relaxed.network("http://192.168.1.5/admin").verdict)
    }

    @Test
    fun rootLevelsAreOrdered() {
        assertTrue(RootLevel.EXPERIMENTAL.allows(RootLevel.MODIFY))
        assertTrue(RootLevel.MODIFY.allows(RootLevel.EXEC))
        assertFalse(RootLevel.CONTROL.allows(RootLevel.MODIFY))
        assertFalse(RootLevel.NONE.allows(RootLevel.READ))
    }

    @Test
    fun powerHooksRenderAndRejectInjection() {
        val dns = PowerHooks.byId("private_dns")!!
        assertEquals(RootLevel.MODIFY, dns.level)
        assertTrue(dns.render(mapOf("host" to "dns.adguard-dns.com")).contains("dns.adguard-dns.com"))
        val density = PowerHooks.byId("density")!!
        assertEquals("wm density 420", density.render(emptyMap()))
        listOf("a; rm -rf /", "x`id`", "y\$(reboot)", "z'quote").forEach { bad ->
            try { PowerHooks.byId("setprop")!!.render(mapOf("prop" to "p", "value" to bad)); error("accepted $bad") }
            catch (e: IllegalArgumentException) {}
        }
        assertTrue(PowerHooks.byId("selinux_permissive")!!.level == RootLevel.EXPERIMENTAL)
    }
}
