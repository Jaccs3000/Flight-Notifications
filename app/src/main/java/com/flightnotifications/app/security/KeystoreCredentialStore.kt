package com.flightnotifications.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

/** Cifra la credencial con AES-256/GCM y guarda solo IV + texto cifrado en noBackupFilesDir. */
class KeystoreCredentialStore(
    noBackupFilesDir: File,
    private val keyAlias: String = PRODUCTION_KEY_ALIAS,
) : CredentialStore {
    private val encryptedFile = AtomicFile(File(noBackupFilesDir, "serpapi_credential.bin"))

    override suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) {
        encryptedFile.baseFile.exists()
    }

    override suspend fun credentials(): List<StoredApiCredential> = withContext(Dispatchers.IO) {
        val (credentials, isLegacy) = readCredentials()
        if (isLegacy && credentials.isNotEmpty()) writeCredentials(credentials)
        credentials
    }

    override suspend fun save(apiKey: String) = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank())
        writeCredentials(listOf(StoredApiCredential(UUID.randomUUID().toString(), apiKey.trim())))
    }

    override suspend fun add(apiKey: String) = withContext(Dispatchers.IO) {
        val normalized = apiKey.trim()
        require(normalized.isNotBlank())
        val current = readCredentials().first
        if (current.none { it.apiKey == normalized }) {
            writeCredentials(current + StoredApiCredential(UUID.randomUUID().toString(), normalized))
        }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val remaining = readCredentials().first.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            encryptedFile.delete()
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(keyAlias)
        } else {
            writeCredentials(remaining)
        }
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        credentialsFromDisk().firstOrNull()?.apiKey ?: error("CREDENTIAL_UNREADABLE")
    }

    private fun credentialsFromDisk(): List<StoredApiCredential> {
        val (credentials, isLegacy) = readCredentials()
        if (isLegacy && credentials.isNotEmpty()) writeCredentials(credentials)
        return credentials
    }

    private fun readCredentials(): Pair<List<StoredApiCredential>, Boolean> {
        if (!encryptedFile.baseFile.exists()) return emptyList<StoredApiCredential>() to false
        val payload = encryptedFile.readFully()
        require(payload.size > HEADER_SIZE && payload[0] == FORMAT_VERSION) { "CREDENTIAL_UNREADABLE" }
        val ivSize = payload[1].toInt() and 0xff
        require(ivSize == GCM_IV_SIZE && payload.size > HEADER_SIZE + ivSize) { "CREDENTIAL_UNREADABLE" }
        val iv = payload.copyOfRange(HEADER_SIZE, HEADER_SIZE + ivSize)
        val ciphertext = payload.copyOfRange(HEADER_SIZE + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        val bundle = runCatching { Json.decodeFromString<CredentialBundle>(plaintext) }.getOrNull()
        if (bundle != null && bundle.version == BUNDLE_VERSION) {
            require(bundle.keys.all { it.id.isNotBlank() && it.apiKey.isNotBlank() }) { "CREDENTIAL_UNREADABLE" }
            return bundle.keys.map { StoredApiCredential(it.id, it.apiKey) } to false
        }
        require(plaintext.isNotBlank()) { "CREDENTIAL_UNREADABLE" }
        return listOf(StoredApiCredential(UUID.randomUUID().toString(), plaintext)) to true
    }

    private fun writeCredentials(credentials: List<StoredApiCredential>) {
        require(credentials.isNotEmpty())
        val plaintext = Json.encodeToString(CredentialBundle(keys = credentials.map { CredentialValue(it.id, it.apiKey) }))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
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
        const val BUNDLE_VERSION = 2
    }

    @Serializable
    private data class CredentialBundle(
        val version: Int = BUNDLE_VERSION,
        val keys: List<CredentialValue>,
    )

    @Serializable
    private data class CredentialValue(
        val id: String,
        val apiKey: String,
    )
}
