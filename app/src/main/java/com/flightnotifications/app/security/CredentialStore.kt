package com.flightnotifications.app.security

interface CredentialStore {
    suspend fun isConfigured(): Boolean
    suspend fun save(apiKey: String)
    suspend fun read(): String
    suspend fun delete()
}
