package com.paulneja.quorvi.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(val reason: Reason, message: String? = null) : Exception(message ?: reason.name) {
    enum class Reason { WRONG_PASSWORD, NOT_A_BACKUP, NEWER_VERSION, INCOMPATIBLE }
}

object BackupCrypto {
    private val MAGIC = "QBK1".toByteArray()
    const val ITERATIONS = 310_000
    private const val MAX_ITERATIONS = 5_000_000

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 16 + 4 + 12 + body.size).put(MAGIC).put(salt).putInt(iterations).put(nonce).put(body).array()
    }

    fun isBackup(data: ByteArray): Boolean = data.size > MAGIC.size + 32 && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isBackup(data)) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val b = ByteBuffer.wrap(data, MAGIC.size, data.size - MAGIC.size)
        val salt = ByteArray(16).also { b.get(it) }
        val iterations = b.int
        if (iterations !in 1..MAX_ITERATIONS) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val nonce = ByteArray(12).also { b.get(it) }
        val body = ByteArray(b.remaining()).also { b.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        return try { cipher.doFinal(body) } catch (e: AEADBadTagException) { throw BackupException(BackupException.Reason.WRONG_PASSWORD) }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
