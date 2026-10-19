package com.flightnotifications.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra la credencial con AES-256/GCM y guarda solo IV + texto cifrado en noBackupFilesDir. */
class KeystoreCredentialStore(
    noBackupFilesDir: File,
    private val keyAlias: String = PRODUCTION_KEY_ALIAS,
) : CredentialStore {
    private val encryptedFile = AtomicFile(File(noBackupFilesDir, "serpapi_credential.bin"))

    override suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) { encryptedFile.baseFile.exists() }

    override suspend fun save(apiKey: String) = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val output = encryptedFile.startWrite()
        try {
            output.write(FORMAT_VERSION.toInt())
            output.write(iv.size)
            output.write(iv)
            output.write(encrypted)
            encryptedFile.finishWrite(output)
        } catch (error: Exception) {
            encryptedFile.failWrite(output)
            throw error
        }
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        val payload = encryptedFile.readFully()
        require(payload.size > HEADER_SIZE && payload[0] == FORMAT_VERSION) { "CREDENTIAL_UNREADABLE" }
        val ivSize = payload[1].toInt() and 0xff
        require(ivSize == GCM_IV_SIZE && payload.size > HEADER_SIZE + ivSize) { "CREDENTIAL_UNREADABLE" }
        val iv = payload.copyOfRange(HEADER_SIZE, HEADER_SIZE + ivSize)
        val ciphertext = payload.copyOfRange(HEADER_SIZE + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        encryptedFile.delete()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.deleteEntry(keyAlias)
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = getExistingKeyOrNull()
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey = getExistingKeyOrNull() ?: error("CREDENTIAL_KEY_MISSING")

    private fun getExistingKeyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val PRODUCTION_KEY_ALIAS = "serpapi-api-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val HEADER_SIZE = 2
    }
}
