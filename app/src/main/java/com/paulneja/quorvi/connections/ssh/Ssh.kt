package com.paulneja.quorvi.connections.ssh

import com.paulneja.quorvi.connections.HostEntity
import com.paulneja.quorvi.core.security.CredentialRef
import com.paulneja.quorvi.core.security.Vault
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider

enum class SshAuth { KEY, PASSWORD }

data class HostKey(val type: String, val fingerprint: String)

sealed class SshException(message: String) : Exception(message) {
    class UnknownHostKey(val key: HostKey) : SshException("Unknown host key ${key.fingerprint}")
    class HostKeyChanged(val expected: String, val actual: HostKey) : SshException("Host key changed: expected $expected, got ${actual.fingerprint}")
    class AuthFailed(message: String) : SshException(message)
    class Unreachable(message: String) : SshException(message)
    class MissingCredential : SshException("No credential stored for this host")
}

data class SshExecResult(val exitCode: Int?, val stdout: String, val stderr: String, val timedOut: Boolean)

object SshCrypto {
    @Volatile private var ready = false

    fun install() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
            SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
            ready = true
        }
    }

    fun fingerprint(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
    }

    fun hostKey(key: PublicKey) = HostKey(KeyType.fromKey(key).toString(), fingerprint(key))

    fun generate(): KeyPair {
        install()
        return KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME).generateKeyPair()
    }

    fun encode(pair: KeyPair): String =
        Base64.getEncoder().encodeToString(pair.private.encoded) + ":" + Base64.getEncoder().encodeToString(pair.public.encoded)

    fun decode(stored: String): KeyPair {
        install()
        val (private, public) = stored.split(":", limit = 2)
        val factory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        return KeyPair(
            factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(public))),
            factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(private))),
        )
    }

    fun authorizedKey(public: PublicKey, comment: String): String {
        val raw = public.encoded.takeLast(32).toByteArray()
        val blob = ByteArrayOutputStream().apply {
            writeSshString("ssh-ed25519".toByteArray())
            writeSshString(raw)
        }.toByteArray()
        return "ssh-ed25519 ${Base64.getEncoder().encodeToString(blob)} $comment"
    }

    private fun ByteArrayOutputStream.writeSshString(bytes: ByteArray) {
        write(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
        write(bytes)
    }
}

class SshCredentials(private val vault: Vault) {
    fun keyRef(hostId: String) = CredentialRef("ssh_key_$hostId")
    fun passwordRef(hostId: String) = CredentialRef("ssh_password_$hostId")

    fun ensureKey(hostId: String): KeyPair {
        vault.get(keyRef(hostId))?.let { return SshCrypto.decode(it) }
        val pair = SshCrypto.generate()
        vault.put(keyRef(hostId), SshCrypto.encode(pair))
        return pair
    }

    fun key(hostId: String): KeyPair? = vault.get(keyRef(hostId))?.let(SshCrypto::decode)
    fun password(hostId: String): String? = vault.get(passwordRef(hostId))
    fun setPassword(hostId: String, password: String) = vault.put(passwordRef(hostId), password)

    fun forget(hostId: String) {
        vault.remove(keyRef(hostId))
        vault.remove(passwordRef(hostId))
    }
}

private class PinnedVerifier(private val pinned: String?) : HostKeyVerifier {
    var seen: HostKey? = null

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val hostKey = SshCrypto.hostKey(key)
        seen = hostKey
        return pinned != null && pinned == hostKey.fingerprint
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}

class SshConnector(private val credentials: SshCredentials, private val connectTimeoutMs: Int = 10_000) {
    suspend fun <T> session(host: HostEntity, block: suspend (SSHClient) -> T): T = withContext(Dispatchers.IO) {
        SshCrypto.install()
        val verifier = PinnedVerifier(host.hostKeyFingerprint)
        val client = SSHClient(DefaultConfig())
        client.connectTimeout = connectTimeoutMs
        client.timeout = 30_000
        client.addHostKeyVerifier(verifier)
        try {
            try {
                client.connect(host.hostname, host.port)
            } catch (e: TransportException) {
                val seen = verifier.seen
                when {
                    seen != null && host.hostKeyFingerprint == null -> throw SshException.UnknownHostKey(seen)
                    seen != null && seen.fingerprint != host.hostKeyFingerprint -> throw SshException.HostKeyChanged(host.hostKeyFingerprint!!, seen)
                    else -> throw SshException.Unreachable(e.message ?: "connection failed")
                }
            } catch (e: java.io.IOException) {
                throw SshException.Unreachable(e.message ?: e::class.simpleName.orEmpty())
            }
            try {
                when (SshAuth.valueOf(host.auth)) {
                    SshAuth.KEY -> {
                        val pair = credentials.key(host.id) ?: throw SshException.MissingCredential()
                        client.authPublickey(host.username, keyProvider(pair))
                    }
                    SshAuth.PASSWORD -> client.authPassword(host.username, credentials.password(host.id) ?: throw SshException.MissingCredential())
                }
            } catch (e: UserAuthException) {
                throw SshException.AuthFailed(e.message ?: "authentication failed")
            }
            block(client)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    suspend fun probe(host: HostEntity): HostKey = try {
        session(host.copy(hostKeyFingerprint = null)) { error("unreachable") }
    } catch (e: SshException.UnknownHostKey) {
        e.key
    }

    suspend fun exec(host: HostEntity, command: String, timeoutSec: Long = 60, maxBytes: Int = 256 * 1024): SshExecResult = session(host) { client ->
        client.startSession().use { session ->
            val cmd = session.exec(command)
            val out = Collector(cmd.inputStream, maxBytes)
            val err = Collector(cmd.errorStream, maxBytes / 4)
            runInterruptible { cmd.join(timeoutSec, TimeUnit.SECONDS) }
            val timedOut = cmd.isOpen
            if (timedOut) runCatching { cmd.close() }
            out.join(); err.join()
            SshExecResult(cmd.exitStatus, out.text(), err.text(), timedOut)
        }
    }

    fun shell(host: HostEntity, columns: Int = 80, rows: Int = 24, term: String = "xterm-256color", input: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                session(host) { client ->
                    client.startSession().use { session ->
                        session.allocatePTY(term, columns, rows, 0, 0, emptyMap())
                        val shell = session.startShell()
                        val writer = launch(Dispatchers.IO) { pump(input, shell.outputStream) }
                        val buffer = ByteArray(8192)
                        val stream = shell.inputStream
                        while (true) {
                            val n = runInterruptible { stream.read(buffer) }
                            if (n < 0) break
                            trySend(String(buffer, 0, n, Charsets.UTF_8))
                        }
                        writer.cancel()
                    }
                }
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    private suspend fun pump(input: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>, out: OutputStream) {
        for (bytes in input) {
            out.write(bytes)
            out.flush()
        }
    }

    private fun keyProvider(pair: KeyPair) = object : KeyProvider {
        override fun getPrivate(): PrivateKey = pair.private
        override fun getPublic(): PublicKey = pair.public
        override fun getType(): KeyType = KeyType.ED25519
    }

    private class Collector(private val input: InputStream, private val limit: Int) {
        private val buffer = ByteArrayOutputStream()
        private val thread = Thread {
            val chunk = ByteArray(8192)
            runCatching {
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    val room = limit - buffer.size()
                    if (room > 0) buffer.write(chunk, 0, minOf(room, n))
                }
            }
        }.apply { isDaemon = true; start() }

        fun join() = thread.join(5_000)
        fun text() = buffer.toString(Charsets.UTF_8.name())
    }
}
