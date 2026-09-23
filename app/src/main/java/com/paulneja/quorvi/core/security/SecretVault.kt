package com.paulneja.quorvi.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@JvmInline
value class CredentialRef(val name: String)

interface Vault {
    fun get(ref: CredentialRef): String?
    fun put(ref: CredentialRef, value: String)
    fun remove(ref: CredentialRef)
    fun has(ref: CredentialRef): Boolean = get(ref) != null
}

@Singleton
class SecretVault @Inject constructor(@ApplicationContext context: Context) : Vault {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }

    private fun file(ref: CredentialRef) = File(dir, ref.name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    override fun get(ref: CredentialRef): String? = file(ref).takeIf { it.isFile }?.let { f -> runCatching { decrypt(f.readText()) }.getOrNull() }

    override fun put(ref: CredentialRef, value: String) {
        val target = file(ref)
        val temp = File(dir, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(encrypt(value).toByteArray())
            out.fd.sync()
        }
        check(temp.renameTo(target)) { "Could not persist credential" }
    }

    override fun remove(ref: CredentialRef) {
        file(ref).delete()
    }

    override fun has(ref: CredentialRef): Boolean = file(ref).isFile

    fun names(): List<String> = dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".tmp") }.map { it.name }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        val GITHUB = CredentialRef("github_oauth")
        val BRIDGE_CODE = CredentialRef("bridge_pairing_code")

        fun provider(id: String) = CredentialRef("provider_key_$id")

        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "nexo-vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
