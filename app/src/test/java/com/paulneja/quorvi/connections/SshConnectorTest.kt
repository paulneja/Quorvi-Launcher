package com.paulneja.quorvi.connections

import com.paulneja.quorvi.connections.ssh.Docker
import com.paulneja.quorvi.connections.ssh.DockerAction
import com.paulneja.quorvi.connections.ssh.HostProbe
import com.paulneja.quorvi.connections.ssh.SshConnector
import com.paulneja.quorvi.connections.ssh.SshCredentials
import com.paulneja.quorvi.connections.ssh.SshCrypto
import com.paulneja.quorvi.connections.ssh.SshException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellCommandFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SshConnectorTest {
    private val vault = MemoryVault()
    private val credentials = SshCredentials(vault)
    private lateinit var server: SshServer
    private lateinit var connector: SshConnector

    @Before
    fun start() {
        connector = SshConnector(credentials)
        val key = credentials.ensureKey("h1").public
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(Files.createTempFile("hostkey", ".ser").also { Files.delete(it) })
            setPublickeyAuthenticator { user, offered, _ -> user == "nexo" && offered == key }
            setPasswordAuthenticator { user, password, _ -> user == "nexo" && password == "secret" }
            commandFactory = ProcessShellCommandFactory.INSTANCE
            start()
        }
    }

    @After
    fun stop() = server.stop(true)

    private fun host(pin: String? = null, auth: String = "KEY", id: String = "h1") =
        HostEntity(id = id, name = "test", hostname = "127.0.0.1", port = server.port, username = "nexo", auth = auth, hostKeyFingerprint = pin, createdAt = 0)

    @Test
    fun unknownHostKeyIsNeverTrustedSilently() = runBlocking {
        try {
            connector.exec(host(), "echo hi")
            fail("connected without a pinned key")
        } catch (e: SshException.UnknownHostKey) {
            assertTrue(e.key.fingerprint.startsWith("SHA256:"))
        }
    }

    @Test
    fun pinnedKeyRunsCommandsWithAnEd25519Key() = runBlocking {
        val key = connector.probe(host())
        val result = connector.exec(host(key.fingerprint), "echo hola && exit 3")
        assertEquals("hola\n", result.stdout)
        assertEquals(3, result.exitCode)
        assertFalse(result.timedOut)
    }

    @Test
    fun changedHostKeyIsRefused() = runBlocking {
        try {
            connector.exec(host("SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), "echo hi")
            fail("connected to a host whose key changed")
        } catch (e: SshException.HostKeyChanged) {
            assertEquals("SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", e.expected)
        }
    }

    @Test
    fun passwordAuthAndItsFailure() = runBlocking {
        val pin = connector.probe(host()).fingerprint
        credentials.setPassword("p1", "secret")
        assertEquals("ok\n", connector.exec(host(pin, "PASSWORD", "p1"), "echo ok").stdout)
        credentials.setPassword("p2", "wrong")
        try {
            connector.exec(host(pin, "PASSWORD", "p2"), "echo ok")
            fail("wrong password accepted")
        } catch (e: SshException.AuthFailed) {
        }
    }

    @Test
    fun authorizedKeyLineMatchesTheStoredKey() {
        val pair = credentials.ensureKey("h1")
        val line = SshCrypto.authorizedKey(pair.public, "nexo@phone")
        val parsed = AuthorizedKeyEntry.parseAuthorizedKeyEntry(line).resolvePublicKey(null, emptyMap(), null)
        assertEquals(pair.public, parsed)
        assertTrue(line.startsWith("ssh-ed25519 ") && line.endsWith(" nexo@phone"))
    }

    @Test
    fun healthProbeParsesARealLinuxHost() = runBlocking {
        val pin = connector.probe(host()).fingerprint
        val health = HostProbe.parse(connector.exec(host(pin), HostProbe.COMMAND).stdout)
        assertTrue(health.kernel.startsWith("Linux"))
        assertTrue((health.cpus ?: 0) > 0)
        assertTrue((health.memTotal ?: 0) > 0)
        assertTrue((health.uptimeSec ?: 0) > 0)
    }

    @Test
    fun dockerCommandsRejectInjection() {
        assertEquals("docker restart web_1", Docker.action(DockerAction.RESTART, "web_1"))
        listOf("a;rm -rf /", "\$(id)", "-rm", "a b").forEach { bad ->
            try { Docker.action(DockerAction.STOP, bad); fail("accepted $bad") } catch (e: IllegalArgumentException) { }
        }
        val parsed = Docker.parse("""{"ID":"abc","Names":"web","Image":"nginx","State":"running","Status":"Up 2 hours"}""" + "\nnoise\n")
        assertEquals(listOf("web"), parsed.map { it.name })
    }
}
