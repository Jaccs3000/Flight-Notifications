package com.flightnotifications.app.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

interface SerpApiService {
    @GET("account.json")
    suspend fun account(@Query("api_key") apiKey: String): Response<JsonObject>

    @GET("search.json")
    suspend fun search(@QueryMap parameters: Map<String, String>): Response<JsonObject>
}

fun interface SerpApiClient {
    suspend fun account(apiKey: String): Response<JsonObject>
}

interface FlightSearchClient {
    suspend fun search(parameters: Map<String, String>): Response<JsonObject>
}

class RetrofitSerpApiClient(
    baseUrl: String = PRODUCTION_BASE_URL,
) : SerpApiClient, FlightSearchClient {
    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .cache(null)
                .build(),
        )
        .addConverterFactory(serpApiJsonConverter())
        .build()
        .create(SerpApiService::class.java)

    override suspend fun account(apiKey: String): Response<JsonObject> = service.account(apiKey)
    override suspend fun search(parameters: Map<String, String>): Response<JsonObject> = service.search(parameters)

    companion object {
        const val PRODUCTION_BASE_URL = "https://serpapi.com/"
    }
}

private fun serpApiJsonConverter() = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}.asConverterFactory("application/json".toMediaType())
