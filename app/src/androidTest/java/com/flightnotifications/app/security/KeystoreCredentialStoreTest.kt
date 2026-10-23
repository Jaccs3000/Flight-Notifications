package com.flightnotifications.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreTest {
    @Test
    fun fakeCredentialIsEncryptedReadableReplaceableAndDeletable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedDirectory = File(context.noBackupFilesDir, "credential-store-instrumentation-test").apply { mkdirs() }
        val store = KeystoreCredentialStore(isolatedDirectory, "serpapi-instrumentation-test-v1")
        val fakeSecret = "FAKE_LOCAL_TEST_KEY_12345"

        store.delete()
        assertFalse(store.isConfigured())
        store.save(fakeSecret)
        assertTrue(store.isConfigured())
        val encryptedBytes = isolatedDirectory.resolve("serpapi_credential.bin").readBytes()
        assertNotEquals(fakeSecret, encryptedBytes.toString(Charsets.UTF_8))
        assertArrayEquals(fakeSecret.toByteArray(), store.read().toByteArray())

        // Recreate the old encrypted single-key payload and verify it migrates without losing the key.
        val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = androidKeyStore.getKey("serpapi-instrumentation-test-v1", null)
        val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val legacyPayload = byteArrayOf(1, legacyCipher.iv.size.toByte()) + legacyCipher.iv + legacyCipher.doFinal(fakeSecret.toByteArray())
        isolatedDirectory.resolve("serpapi_credential.bin").writeBytes(legacyPayload)
        val migrated = store.credentials()
        assertEquals(1, migrated.size)
        assertArrayEquals(fakeSecret.toByteArray(), migrated.single().apiKey.toByteArray())
        assertNotEquals(legacyPayload.toList(), isolatedDirectory.resolve("serpapi_credential.bin").readBytes().toList())

        store.add("SECOND_FAKE_LOCAL_KEY")
        store.add("THIRD_FAKE_LOCAL_KEY")
        store.add("SECOND_FAKE_LOCAL_KEY") // Duplicate keys from one account are kept only once.
        val accounts = store.credentials()
        assertEquals(3, accounts.size)
        assertArrayEquals(fakeSecret.toByteArray(), accounts.first().apiKey.toByteArray())
        assertArrayEquals(fakeSecret.toByteArray(), store.read().toByteArray()) // Legacy primary remains first.
        assertFalse(encryptedBytes.toString(Charsets.UTF_8).contains("SECOND_FAKE_LOCAL_KEY"))

        store.remove(accounts.first().id)
        assertArrayEquals("SECOND_FAKE_LOCAL_KEY".toByteArray(), store.read().toByteArray())
        assertEquals(2, store.credentials().size)
        store.remove(store.credentials().first().id)
        store.remove(store.credentials().first().id)
        store.delete()
        assertFalse(store.isConfigured())
    }
}
