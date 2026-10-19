package com.flightnotifications.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
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

        store.save("SECOND_FAKE_LOCAL_KEY")
        assertArrayEquals("SECOND_FAKE_LOCAL_KEY".toByteArray(), store.read().toByteArray())
        store.delete()
        assertFalse(store.isConfigured())
    }
}
