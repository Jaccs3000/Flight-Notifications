package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.remote.RetrofitSerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.RequestStage
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.security.CredentialStore
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex

class SerpApiRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun accountCheckCallsOnlyTheFreeAccountEndpoint() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        val repository = repository()

        val account = repository.checkAccount()

        assertEquals(500, account.totalSearchesLeft)
        assertEquals(100, account.conservativeSearchesLeft)
        assertEquals(1, server.requestCount)
        assertTrue(takeLine().contains("/account.json?api_key=FAKE_TEST_KEY"))
        assertFalse(takeLineWasRequested())
    }

    @Test
    fun manualRoundTripUsesAtMostTwoGetsAndKeepsAllOperators() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        server.enqueue(jsonResponse(fixture("return.json")))
        val budget = FakeBudget()
        val repository = repository(budget)

        val result = repository.runRoundTrip(
            SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true),
        )

        assertEquals(3, server.requestCount) // 1 account call plus at most 2 flight GETs
        val accountLine = takeLine()
        val outboundLine = takeLine()
        val returnLine = takeLine()
        assertTrue(accountLine.contains("/account.json"))
        assertTrue(outboundLine.contains("/search.json"))
        assertTrue(outboundLine.contains("departure_id=CTG"))
        assertTrue(outboundLine.contains("arrival_id=CLO"))
        assertTrue(outboundLine.contains("stops=1"))
        assertFalse(outboundLine.contains("airline"))
        assertFalse(outboundLine.contains("max_price"))
        assertTrue(returnLine.contains("departure_token=opaque-unknown-token"))
        assertTrue(result.airlinesDetected.contains("Wingo"))
        assertTrue(result.airlinesDetected.contains("Aerolínea Desconocida"))
        assertTrue(result.airlinesDetected.contains("Operador Nuevo"))
        assertTrue(result.wingoDetected)
        assertEquals("Aerolínea Desconocida", result.outbound?.carriers?.first())
        assertNotNull(result.selectedReturn)
        assertEquals("NO_VERIFICADO", result.statusLabel)
        assertEquals(2, result.flightGetsStarted)
        assertEquals(2, budget.reserved.size)
        assertEquals(budget.reserved[0].second, budget.reserved[1].second)
    }

    @Test
    fun noTokenStopsAfterTheOutboundRequest() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse("""{"search_metadata":{"status":"Success"},"best_flights":[],"other_flights":[]}"""))
        val repository = repository()

        val result = repository.runRoundTrip(
            SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true),
        )

        assertEquals(2, server.requestCount)
        assertEquals(1, result.flightGetsStarted)
        assertEquals("SIN_RESULTADOS_DEVUELTOS", result.statusLabel)
    }

    @Test
    fun malformedOnlyCandidatesAreNotReportedAsNoFlights() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(
            jsonResponse(
                """{"search_metadata":{"status":"Success"},"best_flights":[],"other_flights":[{"flights":[{"airline":"Operador parcial"}]}]}""",
            ),
        )

        val result = repository().runRoundTrip(
            SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true),
        )

        assertEquals("PARCIAL", result.statusLabel)
        assertEquals(1, result.malformedOptions)
        assertTrue(result.airlinesDetected.contains("Operador parcial"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun untrustedLocalClockStopsBeforeAnyFlightRequest() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        val repository = repository(FakeBudget(clockTrusted = false))

        val error = runCatching {
            repository.runRoundTrip(
                SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true),
            )
        }.exceptionOrNull()

        assertEquals(FailureCode.CLOCK_UNTRUSTED, (error as ProbeFailure).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun failedHttpResponseIsNotRetried() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("ignored error body").build())
        val repository = repository()

        val error = runCatching { repository.checkAccount() }.exceptionOrNull()

        assertEquals(FailureCode.SERVICE_UNAVAILABLE, (error as ProbeFailure).code)
        assertEquals(1, server.requestCount)
    }

    private fun repository(budget: FakeBudget = FakeBudget()): SerpApiRepository {
        val client = RetrofitSerpApiClient(server.url("/").toString())
        return SerpApiRepository(
            credentialStore = FakeCredentialStore(),
            accountClient = client,
            flightClient = client,
            budgetRepository = budget,
            responseParser = SerpApiJsonParser(),
            executionMutex = Mutex(),
        )
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun accountJson() = """{
        "account_status":"Active",
        "plan_searches_left":500,
        "total_searches_left":500,
        "searches_per_month":1000,
        "this_month_usage":500,
        "extra_credits":0,
        "account_rate_limit_per_hour":100,
        "this_hour_searches":0,
        "plan_renewal_date":"2027-04-01",
        "account_email":"must-not-be-mapped@example.invalid",
        "account_id":"must-not-be-mapped"
    }"""

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private fun takeLine(): String = server.takeRequest(2, TimeUnit.SECONDS)?.requestLine
        ?: error("Expected a local mock request")

    private fun takeLineWasRequested(): Boolean = server.takeRequest(100, TimeUnit.MILLISECONDS) != null

    private class FakeCredentialStore : CredentialStore {
        override suspend fun isConfigured() = true
        override suspend fun save(apiKey: String) = Unit
        override suspend fun read() = "FAKE_TEST_KEY"
        override suspend fun delete() = Unit
    }

    private class FakeBudget(private val clockTrusted: Boolean = true) : FlightRequestBudget {
        val reserved = mutableListOf<Pair<RequestStage, String>>()
        override suspend fun snapshot() = LocalBudgetSnapshot(
            usedInWindow = reserved.size,
            remainingInWindow = 200 - reserved.size,
            windowEndsAt = Instant.parse("2026-11-18T00:00:00Z"),
            clockTrusted = clockTrusted,
        )

        override suspend fun reserve(stage: RequestStage, runId: String): BudgetReservationResult {
            if (reserved.size >= 200) return BudgetReservationResult.LIMIT_REACHED
            reserved += stage to runId
            return BudgetReservationResult.RESERVED
        }
    }
}
