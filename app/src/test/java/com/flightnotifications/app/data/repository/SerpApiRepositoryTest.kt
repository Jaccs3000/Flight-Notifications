package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.remote.RetrofitSerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.RequestStage
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.security.CredentialStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger
import retrofit2.Response
import com.flightnotifications.app.data.remote.SerpApiClient
import com.flightnotifications.app.data.remote.FlightSearchClient

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
    fun executionMutexSerializesConcurrentAccountPreflights() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val body = Json.parseToJsonElement(accountJson()) as JsonObject
        val fakeAccountClient = SerpApiClient {
            val currentActive = active.incrementAndGet()
            maximumActive.updateAndGet { previous -> maxOf(previous, currentActive) }
            val callIndex = calls.incrementAndGet()
            try {
                if (callIndex == 1) {
                    started.complete(Unit)
                    release.await()
                }
                Response.success(body)
            } finally {
                active.decrementAndGet()
            }
        }
        val fakeFlightClient = object : FlightSearchClient {
            override suspend fun search(parameters: Map<String, String>): Response<JsonObject> =
                error("Account checks must not call the flight endpoint")
        }
        val repository = SerpApiRepository(
            credentialStore = FakeCredentialStore(),
            accountClient = fakeAccountClient,
            flightClient = fakeFlightClient,
            budgetRepository = FakeBudget(),
            responseParser = SerpApiJsonParser(),
            executionMutex = Mutex(),
        )

        coroutineScope {
            val first = async { repository.checkAccount() }
            started.await()
            val second = async { repository.checkAccount() }
            yield()
            assertEquals("second call waits for the first preflight", 1, calls.get())
            release.complete(Unit)
            assertEquals(500, first.await().totalSearchesLeft)
            assertEquals(500, second.await().totalSearchesLeft)
        }

        assertEquals(2, calls.get())
        assertEquals(1, maximumActive.get())
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
    fun matchingRoundTripPricesForTokenAssociatedReturnAreReportedAsVerifiedTotal() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        val returnJson = fixture("return.json").replace("\"price\": 340000", "\"price\": 300000")
        server.enqueue(jsonResponse(returnJson))
        val repository = repository()

        val result = repository.runRoundTrip(
            SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true),
        )

        assertEquals("VERIFICADO", result.statusLabel)
        assertEquals(300000L, result.verifiedRoundTripPriceCop)
        assertEquals(300000L, result.outbound?.priceCop)
        assertEquals(300000L, result.selectedReturn?.priceCop)
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

    @Test
    fun mapsAuthorizationRateLimitAndServerHttpFailuresWithoutRetrying() = runBlocking {
        val cases = listOf(
            401 to FailureCode.INVALID_CREDENTIAL,
            403 to FailureCode.FORBIDDEN,
            429 to FailureCode.RATE_LIMITED,
            500 to FailureCode.SERVICE_UNAVAILABLE,
            599 to FailureCode.SERVICE_UNAVAILABLE,
            400 to FailureCode.SERVICE_ERROR,
        )

        cases.forEach { (http, expected) ->
            server.enqueue(MockResponse.Builder().code(http).body("ignored error body").build())
            val failure = runCatching { repository().checkAccount() }.exceptionOrNull()
            assertEquals("HTTP $http", expected, (failure as ProbeFailure).code)
        }

        assertEquals(cases.size, server.requestCount)
    }

    @Test
    fun flightSearchHttpErrorsAreReservedOnceAndNeverRetried() = runBlocking {
        val cases = listOf(
            401 to FailureCode.INVALID_CREDENTIAL,
            403 to FailureCode.FORBIDDEN,
            429 to FailureCode.RATE_LIMITED,
            503 to FailureCode.SERVICE_UNAVAILABLE,
        )
        val criteria = SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), directOnly = true)

        cases.forEachIndexed { index, (http, expected) ->
            server.enqueue(jsonResponse(accountJson()))
            server.enqueue(MockResponse.Builder().code(http).body("ignored").build())
            val budget = FakeBudget()
            val failure = runCatching { repository(budget).runRoundTrip(criteria) }.exceptionOrNull()

            assertEquals("flight HTTP $http", expected, (failure as ProbeFailure).code)
            assertEquals("flight HTTP $http reservations", 1, budget.reserved.size)
            assertEquals("flight HTTP $http does not retry", (index + 1) * 2, server.requestCount)
        }
    }

    @Test
    fun malformedAccountBalancesAndDatesFailClosed() = runBlocking {
        val invalidBodies = listOf(
            accountJson().replace("\"total_searches_left\":500,", ""),
            accountJson().replace("\"total_searches_left\":500", "\"total_searches_left\":-1"),
            accountJson().replace("\"account_rate_limit_per_hour\":100", "\"account_rate_limit_per_hour\":-1"),
            accountJson().replace("\"this_hour_searches\":0", "\"this_hour_searches\":101"),
            accountJson().replace("\"plan_renewal_date\":\"2027-04-01\"", "\"plan_renewal_date\":\"not-a-date\""),
        )

        invalidBodies.forEach { body ->
            server.enqueue(jsonResponse(body))
            val failure = runCatching { repository().checkAccount() }.exceptionOrNull()
            assertEquals(FailureCode.ACCOUNT_INVALID, (failure as ProbeFailure).code)
        }

        server.enqueue(jsonResponse(accountJson().replace("\"account_status\":\"Active\"", "\"account_status\":false")))
        val invalidStatus = runCatching { repository().checkAccount() }.exceptionOrNull()
        assertEquals(FailureCode.ACCOUNT_INVALID, (invalidStatus as ProbeFailure).code)
        assertEquals(invalidBodies.size + 1, server.requestCount)
    }

    @Test
    fun inactiveAccountAndMalformedServiceBodyStartNoFlightGets() = runBlocking {
        server.enqueue(jsonResponse(accountJson().replace("\"account_status\":\"Active\"", "\"account_status\":\"Inactive\"")))
        val inactive = repository().runAlertBatch(listOf(alert())).single()

        assertEquals(FailureCode.ACCOUNT_INACTIVE.name, inactive.error)
        assertEquals(0, inactive.flightGetsStarted)
        assertEquals(1, server.requestCount)

        server.enqueue(jsonResponse("{\"error\":\"Invalid API key\"}"))
        val rejected = repository().runAlertBatch(listOf(alert())).single()
        assertEquals(FailureCode.INVALID_CREDENTIAL.name, rejected.error)
        assertEquals(0, rejected.flightGetsStarted)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun alertBatchChecksAccountOnceAndEvaluatesEveryDiverseTokenCandidate() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        server.enqueue(jsonResponse(fixture("return.json").replace("340000", "300000")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-wingo-token").replace("340000", "320000")))
        val budget = FakeBudget()
        val repository = repository(budget)

        val result = repository.runAlertBatch(listOf(alert()))

        assertEquals(4, server.requestCount) // one free account check + one initial search + two token searches
        val accountRequest = takeLine()
        val outboundRequest = takeLine()
        val firstReturnRequest = takeLine()
        val secondReturnRequest = takeLine()
        assertTrue(accountRequest.contains("/account.json"))
        assertTrue(outboundRequest.contains("/search.json"))
        assertTrue(outboundRequest.contains("departure_id=CTG"))
        assertTrue(outboundRequest.contains("adults=1"))
        assertTrue(firstReturnRequest.contains("departure_token=opaque-unknown-token"))
        assertTrue(secondReturnRequest.contains("departure_token=opaque-wingo-token"))
        assertEquals(1, result.size)
        assertEquals("VERIFICADO", result.single().status)
        assertEquals(listOf(300000L, 320000L), result.single().offers.map { it.totalCop })
        assertEquals(3, result.single().flightGetsStarted)
        assertEquals(2, result.single().candidatesConsidered)
        assertFalse(result.single().explorationLimited)
        assertEquals(3, budget.reserved.size)
    }

    @Test
    fun candidateRotationChangesWhichCarrierIsExploredFirst() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-wingo-token").replace("340000", "320000")))
        server.enqueue(jsonResponse(fixture("return.json").replace("340000", "300000")))

        val result = repository().runAlertBatch(listOf(alert().copy(candidateRotation = 1))).single()

        assertEquals("VERIFICADO", result.status)
        assertEquals(4, server.requestCount)
        val account = takeLine()
        val outbound = takeLine()
        val firstReturn = takeLine()
        val secondReturn = takeLine()
        assertTrue(account.contains("/account.json"))
        assertTrue(outbound.contains("/search.json"))
        assertTrue(firstReturn.contains("departure_token=opaque-wingo-token"))
        assertTrue(secondReturn.contains("departure_token=opaque-unknown-token"))
    }

    @Test
    fun alertBatchCapsCandidateExplorationAtThreeAndReportsACompleteBoundedSearch() = runBlocking {
        val thirdCandidate = """{"flights":[{"departure_airport":{"id":"CTG","time":"2027-03-25 12:00"},"arrival_airport":{"id":"CLO","time":"2027-03-25 13:30"},"airline":"Avianca","flight_number":"AV 300"}],"price":310000,"type":"Round trip","departure_token":"opaque-third-token"}"""
        val outbound = fixture("outbound.json").replace("\"other_flights\": [", "\"other_flights\": [$thirdCandidate,")
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(outbound))
        server.enqueue(jsonResponse(fixture("return.json")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-third-token").replace("340000", "310000")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-wingo-token").replace("340000", "320000")))

        val result = repository().runAlertBatch(listOf(alert())).single()

        assertEquals(5, server.requestCount) // one free account check + one outbound + three bounded return GETs
        assertEquals("VERIFICADO", result.status)
        assertEquals(3, result.candidatesConsidered)
        assertEquals(3, result.candidateLimit)
        assertFalse(result.explorationLimited)
        assertEquals(4, result.flightGetsStarted)
        assertEquals(listOf(310_000L, 320_000L), result.offers.map { it.totalCop })
        val requests = (1..5).map { takeLine() }
        assertTrue(requests[2].contains("departure_token=opaque-unknown-token"))
        assertTrue(requests[3].contains("departure_token=opaque-third-token"))
        assertTrue(requests[4].contains("departure_token=opaque-wingo-token"))
    }

    @Test
    fun alertBatchMarksCandidateExplorationPartialWhenTokenRequestFails() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        server.enqueue(MockResponse.Builder().code(503).body("ignored").build())

        val result = repository().runAlertBatch(listOf(alert())).single()

        assertEquals(3, server.requestCount)
        assertEquals("ERROR", result.status)
        assertEquals(FailureCode.SERVICE_UNAVAILABLE.name, result.error)
        assertEquals(1, result.candidatesConsidered)
        assertTrue(result.explorationLimited)
        assertEquals(2, result.flightGetsStarted)
    }

    @Test
    fun alertBatchWithInsufficientOfficialQuotaStartsZeroFlightGets() = runBlocking {
        server.enqueue(jsonResponse(accountJson().replace("\"total_searches_left\":500", "\"total_searches_left\":1").replace("\"plan_searches_left\":500", "\"plan_searches_left\":1")))
        val budget = FakeBudget()
        val repository = repository(budget)

        val result = repository.runAlertBatch(listOf(alert()))

        assertEquals(1, server.requestCount)
        assertEquals("ERROR", result.single().status)
        assertEquals(FailureCode.QUOTA_INSUFFICIENT.name, result.single().error)
        assertEquals(0, result.single().flightGetsStarted)
        assertTrue(budget.reserved.isEmpty())
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

    private fun alert() = FlightAlertEntity(
        id = UUID.randomUUID().toString(),
        name = "Cartagena - Cali",
        originIata = "CTG",
        destinationIata = "CLO",
        outboundDate = "2027-03-25",
        returnDate = "2027-03-28",
        maxTotalCop = 450_000,
        frequencyHours = 24,
        directOnly = true,
        enabled = false,
        createdAt = 1L,
        updatedAt = 1L,
    )

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
