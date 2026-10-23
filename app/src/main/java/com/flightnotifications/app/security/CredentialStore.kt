package com.flightnotifications.app.security

data class StoredApiCredential(
    val id: String,
    val apiKey: String,
)

interface CredentialStore {
    suspend fun isConfigured(): Boolean
    suspend fun save(apiKey: String)
    suspend fun read(): String
    suspend fun delete()

    /** Returns credentials in the order used for account fallback. */
    suspend fun credentials(): List<StoredApiCredential> =
        if (isConfigured()) listOf(StoredApiCredential("primary", read())) else emptyList()

    /** Adds a credential without replacing the existing one. */
    suspend fun add(apiKey: String) = save(apiKey)

    /** Removes only the credential with this stable local id. */
    suspend fun remove(id: String) = delete()
}
