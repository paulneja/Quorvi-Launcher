package com.paulneja.quorvi.core

import com.paulneja.quorvi.core.backup.BackupCrypto
import com.paulneja.quorvi.core.backup.BackupException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val plain = "provider_key_x=sk-secreta y chats".toByteArray()

    @Test
    fun theRightPasswordOpensIt() {
        val sealed = BackupCrypto.encrypt(plain, "una clave larga".toCharArray(), iterations = 1_000)
        assertFalse(String(sealed).contains("sk-secreta"))
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, "una clave larga".toCharArray()))
    }

    @Test
    fun aWrongPasswordOrATamperedFileIsRefused() {
        val sealed = BackupCrypto.encrypt(plain, "una clave larga".toCharArray(), iterations = 1_000)
        expect(BackupException.Reason.WRONG_PASSWORD) { BackupCrypto.decrypt(sealed, "otra clave".toCharArray()) }
        val tampered = sealed.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        expect(BackupException.Reason.WRONG_PASSWORD) { BackupCrypto.decrypt(tampered, "una clave larga".toCharArray()) }
        expect(BackupException.Reason.NOT_A_BACKUP) { BackupCrypto.decrypt("{\"schemaVersion\":1}".toByteArray(), "x".toCharArray()) }
    }

    private fun expect(reason: BackupException.Reason, block: () -> Unit) {
        try { block(); fail("expected $reason") } catch (e: BackupException) { assertEquals(reason, e.reason) }
    }
}
