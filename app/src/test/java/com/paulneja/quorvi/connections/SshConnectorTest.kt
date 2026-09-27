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
}
