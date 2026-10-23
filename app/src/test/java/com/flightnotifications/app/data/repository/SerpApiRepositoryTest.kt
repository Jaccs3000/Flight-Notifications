package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.remote.RetrofitSerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.DiagnosticLog
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.RequestStage
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.security.CredentialStore
import com.flightnotifications.app.security.StoredApiCredential
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.nio.file.Files
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
    fun validatesAnUnstoredApiKeyWithoutStartingFlightSearches() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))

        val account = repository().checkAccount("FAKE_UNSTORED_ACCOUNT_KEY")

        assertEquals(500, account.totalSearchesLeft)
        assertEquals(1, server.requestCount)
        assertTrue(takeLine().contains("/account.json?api_key=FAKE_UNSTORED_ACCOUNT_KEY"))
        assertFalse(takeLineWasRequested())
    }

    @Test
    fun alertBatchSkipsEmptyInvalidAndHourlyExhaustedAccountsInOrder() = runBlocking {
        val credentials = listOf(
            StoredApiCredential("empty", "FAKE_EMPTY_KEY"),
            StoredApiCredential("invalid", "FAKE_INVALID_KEY"),
            StoredApiCredential("hourly", "FAKE_HOURLY_KEY"),
            StoredApiCredential("ready", "FAKE_READY_KEY"),
        )
        server.enqueue(jsonResponse(accountJson().replace("\"plan_searches_left\":500", "\"plan_searches_left\":0").replace("\"total_searches_left\":500", "\"total_searches_left\":0")))
        server.enqueue(jsonResponse("""{"error":"invalid api key"}""", code = 401))
        server.enqueue(jsonResponse(accountJson().replace("\"this_hour_searches\":0", "\"this_hour_searches\":100")))
        server.enqueue(jsonResponse(accountJson()))
        val oneWayResponse = fixture("outbound.json")
            .replace("\"return_date\": \"2027-03-28\",", "")
            .replace("\"type\": 1,", "\"type\": 2,")
            .replace("\"type\": \"Round trip\"", "\"type\": \"One way\"")
        server.enqueue(jsonResponse(oneWayResponse))

        val result = repository(credentials = credentials).runAlertBatch(listOf(alert().copy(returnDate = ""))).single()

        assertEquals(5, server.requestCount)
        val requests = List(5) { takeLine() }
        assertTrue(requests[0].contains("api_key=FAKE_EMPTY_KEY"))
        assertTrue(requests[1].contains("api_key=FAKE_INVALID_KEY"))
        assertTrue(requests[2].contains("api_key=FAKE_HOURLY_KEY"))
        assertTrue(requests[3].contains("api_key=FAKE_READY_KEY"))
        assertTrue(requests[4].contains("/search.json"))
        assertTrue(requests[4].contains("api_key=FAKE_READY_KEY"))
        assertEquals(1, result.flightGetsStarted)
        assertEquals(100, result.accountBalanceAtStart)
    }

    @Test
    fun manualRoundTripSelectsOneAccountWithEnoughCapacityForBothLegs() = runBlocking {
        val credentials = listOf(
            StoredApiCredential("low", "FAKE_LOW_BALANCE_KEY"),
            StoredApiCredential("ready", "FAKE_READY_KEY"),
        )
        server.enqueue(jsonResponse(accountJson().replace("\"plan_searches_left\":500", "\"plan_searches_left\":1").replace("\"total_searches_left\":500", "\"total_searches_left\":1")))
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(fixture("outbound.json")))
        server.enqueue(jsonResponse(fixture("return.json")))

        repository(credentials = credentials).runRoundTrip(
            SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), true),
        )

        assertEquals(4, server.requestCount)
        val requests = List(4) { takeLine() }
        assertTrue(requests[0].contains("api_key=FAKE_LOW_BALANCE_KEY"))
        assertTrue(requests[1].contains("api_key=FAKE_READY_KEY"))
        assertTrue(requests[2].contains("api_key=FAKE_READY_KEY"))
        assertTrue(requests[3].contains("api_key=FAKE_READY_KEY"))
    }

    @Test
    fun alertBatchMovesToNextAccountWhenAFlightRequestReportsRateLimit() = runBlocking {
        val credentials = listOf(
            StoredApiCredential("first", "FAKE_FIRST_KEY"),
            StoredApiCredential("second", "FAKE_SECOND_KEY"),
        )
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse("""{"error":"rate limit"}""", code = 429))
        val oneWayResponse = fixture("outbound.json")
            .replace("\"return_date\": \"2027-03-28\",", "")
            .replace("\"type\": 1,", "\"type\": 2,")
            .replace("\"type\": \"Round trip\"", "\"type\": \"One way\"")
        server.enqueue(jsonResponse(oneWayResponse))
        val alerts = listOf(alert().copy(returnDate = ""), alert().copy(returnDate = ""))

        val results = repository(credentials = credentials).runAlertBatch(alerts)

        assertEquals(4, server.requestCount)
        val requests = List(4) { takeLine() }
        assertTrue(requests[2].contains("api_key=FAKE_FIRST_KEY"))
        assertTrue(requests[3].contains("api_key=FAKE_SECOND_KEY"))
        assertEquals(FailureCode.RATE_LIMITED.name, results[0].error)
        assertEquals("VERIFICADO", results[1].status)
    }

    @Test
    fun savedAccountCheckMasksEveryKey() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(accountJson()))
        val credentials = listOf(
            StoredApiCredential("one", "SECRET_KEY_ONE"),
            StoredApiCredential("two", "SECRET_KEY_TWO"),
        )

        val accounts = repository(credentials = credentials).checkSavedAccounts()

        assertEquals(2, accounts.size)
        assertEquals("••••_ONE", accounts[0].maskedKey)
        assertEquals("••••_TWO", accounts[1].maskedKey)
        assertTrue(accounts.all { it.failure == null && it.account != null })
        assertFalse(accounts.any { it.maskedKey.contains("SECRET") })
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
    fun alertBatchSearchesAnInternationalRouteWithoutAColombianAirportFilter() = runBlocking {
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(
            """{"search_metadata":{"status":"Success"},"best_flights":[{"flights":[{"departure_airport":{"id":"SYD","name":"Sydney Airport","date":"2027-03-25","time":"00:00"},"arrival_airport":{"id":"LAX","name":"Los Angeles International Airport","date":"2027-03-24","time":"23:00"},"duration":1020,"airline":"Test Air","flight_number":"TA100"}],"price":950000,"type":"One way"}],"other_flights":[]}""",
        ))
        val zones = mapOf("SYD" to ZoneId.of("Australia/Sydney"), "LAX" to ZoneId.of("America/Los_Angeles"))
        val result = repository(airportTimeZoneFor = zones::get).runAlertBatch(
            listOf(alert().copy(originIata = "SYD", destinationIata = "LAX", outboundDate = "2027-03-25", returnDate = "", maxTotalCop = 1_500_000)),
        ).single()

        assertEquals("VERIFICADO", result.status)
        assertEquals(950_000L, result.offers.single().totalCop)
        assertTrue(takeLine().contains("/account.json"))
        val flightRequest = takeLine()
        assertTrue(flightRequest.contains("departure_id=SYD"))
        assertTrue(flightRequest.contains("arrival_id=LAX"))
        assertEquals(1, result.flightGetsStarted)
    }

    @Test
    fun oneWaySearchUsesOneFlightGetAndMapsPassengersToSerpApiCategories() = runBlocking {
        server.enqueue(jsonResponse(accountJson().replace("\"total_searches_left\":500", "\"total_searches_left\":1").replace("\"plan_searches_left\":500", "\"plan_searches_left\":1")))
        val oneWayResponse = fixture("outbound.json")
            .replace("\"return_date\": \"2027-03-28\",\n", "")
            .replace("\"type\": 1", "\"type\": 2")
            .replace("\"adults\": 1", "\"adults\": 3")
            .replace("\"Round trip\"", "\"One way\"")
        server.enqueue(jsonResponse(oneWayResponse))
        val budget = FakeBudget()
        val alert = alert().copy(
            returnDate = "",
            adults18Plus = 2,
            minorPassengersJson = Json.encodeToString(
                listOf(MinorPassenger(17), MinorPassenger(8), MinorPassenger(0, "lap"), MinorPassenger(1, "seat")),
            ),
        )

        val result = repository(budget).runAlertBatch(listOf(alert)).single()

        assertEquals("VERIFICADO", result.status)
        assertEquals(1, result.flightGetsStarted)
        assertEquals(1, budget.reserved.size)
        assertEquals(2, server.requestCount) // one account check + one one-way search
        assertTrue(takeLine().contains("/account.json"))
        val flightRequest = takeLine()
        assertTrue(flightRequest.contains("type=2"))
        assertFalse(flightRequest.contains("return_date="))
        assertTrue(flightRequest.contains("adults=3")) // two 18+ adults + the 17-year-old API adult category
        assertTrue(flightRequest.contains("children=1"))
        assertTrue(flightRequest.contains("infants_on_lap=1"))
        assertTrue(flightRequest.contains("infants_in_seat=1"))
        assertTrue(result.offers.isNotEmpty())
        assertTrue(result.offers.all { it.returning.isEmpty() })
    }

    @Test
    fun flexibleOneWaySearchChecksEveryDatePairWithinItsRequestBudget() = runBlocking {
        val firstDay = "2027-03-25"
        val nextDay = "2027-03-26"
        val firstResponse = fixture("outbound.json")
            .replace("2027-03-25", firstDay)
            .replace("\"return_date\": \"2027-03-28\",\n", "")
            .replace("\"type\": 1", "\"type\": 2")
            .replace("\"Round trip\"", "\"One way\"")
        val nextResponse = fixture("outbound.json")
            .replace("2027-03-25", nextDay)
            .replace("\"return_date\": \"2027-03-28\",\n", "")
            .replace("\"type\": 1", "\"type\": 2")
            .replace("\"Round trip\"", "\"One way\"")
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(nextResponse))
        server.enqueue(jsonResponse(firstResponse))

        val result = repository().runAlertBatch(
            listOf(
                alert().copy(
                    returnDate = "",
                    flexibleDateSearch = true,
                    outboundDateEnd = nextDay,
                    returnDateEnd = "",
                    datePairRotation = 1,
                ),
            ),
        ).single()

        assertEquals("VERIFICADO", result.status)
        assertEquals(2, result.flightGetsStarted)
        assertEquals(1, result.datePairIndex)
        assertEquals(2, result.datePairCount)
        assertTrue(result.datePairScanned)
        assertFalse(result.explorationLimited)
        assertEquals(listOf(1, 0), result.datePairScannedIndices)
        assertEquals(2, result.offers.map { it.outbound.first().departureLocal?.take(10) }.distinct().size)
        assertTrue(takeLine().contains("/account.json"))
        val requests = listOf(takeLine(), takeLine())
        assertTrue(requests[0].contains("outbound_date=$nextDay"))
        assertTrue(requests[1].contains("outbound_date=$firstDay"))
        assertTrue(requests.all { !it.contains("return_date=") })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun flexibleRoundTripSearchUsesTheSelectedExactOutboundAndReturnDates() = runBlocking {
        val selectedOutbound = "2027-03-26"
        val selectedReturn = "2027-03-28"
        val outboundResponse = fixture("outbound.json")
            .substringBefore("  \"other_flights\": [")
            .plus("  \"other_flights\": []\n}")
            .replace("2027-03-25", selectedOutbound)
        val returnResponse = fixture("return.json")
            .replace("2027-03-25", selectedOutbound)
            .replace("opaque-unknown-token", "opaque-wingo-token")
            .replace("340000", "320000")

        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(outboundResponse))
        server.enqueue(jsonResponse(returnResponse))

        val result = repository().runAlertBatch(
            listOf(
                alert().copy(
                    flexibleDateSearch = true,
                    outboundDateEnd = "2027-03-26",
                    returnDateEnd = "2027-03-29",
                    datePairRotation = 2,
                ),
            ),
            maximumGets = 2,
        ).single()

        assertEquals("VERIFICADO", result.status)
        assertEquals(2, result.flightGetsStarted)
        assertEquals(2, result.datePairIndex)
        assertEquals(4, result.datePairCount)
        assertTrue(result.datePairScanned)
        assertEquals(1, result.offers.size)
        assertEquals(selectedOutbound, result.offers.single().outbound.single().departureLocal?.take(10))
        assertEquals(selectedReturn, result.offers.single().returning.single().departureLocal?.take(10))

        assertTrue(takeLine().contains("/account.json"))
        val outboundRequest = takeLine()
        val returnRequest = takeLine()
        assertTrue(outboundRequest.contains("outbound_date=$selectedOutbound"))
        assertTrue(outboundRequest.contains("return_date=$selectedReturn"))
        assertTrue(returnRequest.contains("outbound_date=$selectedOutbound"))
        assertTrue(returnRequest.contains("return_date=$selectedReturn"))
        assertTrue(returnRequest.contains("departure_token=opaque-wingo-token"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun flexibleRoundTripRangeScansEveryAffordableDatePairAndNeverExceedsNineFlightGets() = runBlocking {
        val pairs = listOf(
            "2027-03-25" to "2027-03-28",
            "2027-03-25" to "2027-03-29",
            "2027-03-26" to "2027-03-28",
            "2027-03-26" to "2027-03-29",
        )
        val budget = FakeBudget()
        val directory = Files.createTempDirectory("flexible-rt-range-log").toFile()
        val flightLog = DiagnosticLog(directory.resolve("flight.log"))
        server.enqueue(jsonResponse(accountJson()))
        pairs.forEachIndexed { index, (outbound, returning) ->
            server.enqueue(
                jsonResponse(
                    fixture("outbound.json")
                        .replace("2027-03-25", outbound)
                        .replace("2027-03-28", returning),
                ),
            )
            val tokens = if (index == 0) {
                listOf("opaque-unknown-token" to 300_000, "opaque-wingo-token" to 320_000)
            } else {
                listOf("opaque-unknown-token" to 300_000)
            }
            tokens.forEach { (token, price) ->
                server.enqueue(
                    jsonResponse(
                        fixture("return.json")
                            .replace("2027-03-25", outbound)
                            .replace("2027-03-28", returning)
                            .replace("opaque-unknown-token", token)
                            .replace("340000", price.toString()),
                    ),
                )
            }
        }

        try {
            val client = RetrofitSerpApiClient(server.url("/").toString())
            val repository = SerpApiRepository(
                credentialStore = FakeCredentialStore(),
                accountClient = client,
                flightClient = client,
                budgetRepository = budget,
                responseParser = SerpApiJsonParser(),
                executionMutex = Mutex(),
                flightDiagnosticLog = flightLog,
            )
            val result = repository.runAlertBatch(
                listOf(
                    alert().copy(
                        flexibleDateSearch = true,
                        outboundDateEnd = "2027-03-26",
                        returnDateEnd = "2027-03-29",
                    ),
                ),
            ).single()

            assertEquals("VERIFICADO", result.status)
            assertEquals("Flight GET cap should cover four date pairs; result=$result", 9, result.flightGetsStarted)
            assertEquals(9, budget.reserved.size)
            assertEquals(listOf(0, 1, 2, 3), result.datePairScannedIndices)
            assertEquals(4, result.datePairCount)
            assertEquals(5, result.candidatesCompleted)
            assertEquals(listOf(1, 2, 3), result.datePairIncompleteIndices)
            assertTrue(result.explorationLimited)
            assertEquals(10, server.requestCount) // one account check + exactly the nine-GET flight cap
        } finally {
            directory.deleteRecursively()
        }
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
    fun alertBatchChecksEveryReturnedCandidateInACompleteSearch() = runBlocking {
        val thirdCandidate = """{"flights":[{"departure_airport":{"id":"CTG","time":"2027-03-25 12:00"},"arrival_airport":{"id":"CLO","time":"2027-03-25 13:30"},"airline":"Avianca","flight_number":"AV 300"}],"price":310000,"type":"Round trip","departure_token":"opaque-third-token"}"""
        val outbound = fixture("outbound.json").replace("\"other_flights\": [", "\"other_flights\": [$thirdCandidate,")
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(outbound))
        server.enqueue(jsonResponse(fixture("return.json")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-third-token").replace("340000", "310000")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-wingo-token").replace("340000", "320000")))

        val result = repository().runAlertBatch(listOf(alert())).single()

        assertEquals(5, server.requestCount) // one free account check + one outbound + three return lookups
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
    fun duplicateVisibleItinerariesAreDeduplicatedBeforeBillableReturnLookups() = runBlocking {
        val duplicate = """{"flights":[{"departure_airport":{"id":"CTG","time":"2027-03-25 08:00"},"arrival_airport":{"id":"CLO","time":"2027-03-25 09:30"},"airline":"Wingo","flight_number":"P5 7001"}],"price":320000,"type":"Round trip","departure_token":"opaque-wingo-duplicate-token"}"""
        val outbound = fixture("outbound.json").replace("  \"other_flights\": [", "  \"other_flights\": [$duplicate,")
        val directory = Files.createTempDirectory("candidate-dedup-log").toFile()
        val flightLog = DiagnosticLog(directory.resolve("flight.log"))
        server.enqueue(jsonResponse(accountJson()))
        server.enqueue(jsonResponse(outbound))
        server.enqueue(jsonResponse(fixture("return.json").replace("340000", "300000")))
        server.enqueue(jsonResponse(fixture("return.json").replace("opaque-unknown-token", "opaque-wingo-token").replace("340000", "320000")))
        val client = RetrofitSerpApiClient(server.url("/").toString())
        val repository = SerpApiRepository(
            credentialStore = FakeCredentialStore(),
            accountClient = client,
            flightClient = client,
            budgetRepository = FakeBudget(),
            responseParser = SerpApiJsonParser(),
            executionMutex = Mutex(),
            flightDiagnosticLog = flightLog,
        )

        try {
            val result = repository.runAlertBatch(listOf(alert())).single()

            assertEquals(3, result.flightGetsStarted)
            assertEquals(2, result.candidatesConsidered)
            assertEquals(2, result.candidatesCompleted)
            assertTrue(flightLog.text.value.contains("duplicate_candidates_removed=1"))
            assertEquals(4, server.requestCount)
        } finally {
            directory.deleteRecursively()
        }
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

    @Test
    fun alertBatchRotatesDeferredAlertsSoTheSameAlertIsNotStarvedByTheGetCap() = runBlocking {
        val oneWayResponse = fixture("outbound.json")
            .replace("\"return_date\": \"2027-03-28\",\n", "")
            .replace("\"type\": 1,", "\"type\": 2,")
            .replace("\"adults\": 1,", "\"adults\": 2,")
            .replace("\"Round trip\"", "\"One way\"")
        server.enqueue(jsonResponse(accountJson()))
        repeat(9) { server.enqueue(jsonResponse(oneWayResponse)) }
        server.enqueue(jsonResponse(accountJson()))
        repeat(9) { server.enqueue(jsonResponse(oneWayResponse)) }
        var secondBatchAlerts = emptyList<FlightAlertEntity>()
        val alerts = (0 until 10).map { index ->
            alert().copy(id = "fair-$index", returnDate = "", returnDateEnd = "")
        }

        val firstBatch = repository().runAlertBatch(alerts, maximumGets = 9) { results ->
            secondBatchAlerts = alerts.map { alert ->
                if (results.any { it.alertId == alert.id && it.flightGetsStarted > 0 }) {
                    alert.copy(candidateRotation = alert.candidateRotation + 1)
                } else alert
            }
        }
        val secondBatch = repository().runAlertBatch(secondBatchAlerts, maximumGets = 9)

        assertEquals(0, firstBatch.single { it.alertId == "fair-0" }.flightGetsStarted)
        assertEquals(9, firstBatch.sumOf { it.flightGetsStarted })
        assertEquals(
            "The alert deferred in the first batch should receive a flight GET in the next batch.",
            1,
            secondBatch.single { it.alertId == "fair-0" }.flightGetsStarted,
        )
        assertEquals(9, secondBatch.sumOf { it.flightGetsStarted })
        assertEquals(20, server.requestCount)
    }

    @Test
    fun fullSearchLogCorrelatesRequestsReportsPassengerMappingAndRedactsAccountData() = runBlocking {
        val directory = Files.createTempDirectory("flight-search-log-test").toFile()
        try {
            val flightLog = DiagnosticLog(directory.resolve("flight.log"))
            val oneWayResponse = fixture("outbound.json")
                .replace("\"return_date\": \"2027-03-28\",\n", "")
                .replace("\"type\": 1,", "\"type\": 2,")
                .replace("\"adults\": 1,", "\"adults\": 3,")
                .replace("\"Round trip\"", "\"One way\"")
            server.enqueue(jsonResponse(accountJson()))
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("serpapi-search-id", "provider-search-123")
                    .body(oneWayResponse)
                    .build(),
            )
            val alert = alert().copy(
                returnDate = "",
                adults18Plus = 2,
                minorPassengersJson = Json.encodeToString(listOf(MinorPassenger(7), MinorPassenger(12))),
            )
            val client = RetrofitSerpApiClient(server.url("/").toString())
            val repository = SerpApiRepository(
                credentialStore = FakeCredentialStore(),
                accountClient = client,
                flightClient = client,
                budgetRepository = FakeBudget(),
                responseParser = SerpApiJsonParser(),
                executionMutex = Mutex(),
                flightDiagnosticLog = flightLog,
            )

            val result = repository.runAlertBatch(listOf(alert), source = "manual")
            val text = flightLog.text.value

            assertEquals("VERIFICADO", result.single().status)
            assertTrue(text.contains("flight_search_started search_id="))
            assertTrue(text.contains("source=manual"))
            assertTrue(text.contains("provider_counts_adults=3 children=1"))
            assertTrue(text.contains("minors=7,12"))
            assertTrue(text.contains("account_pool_status account=Cuenta-1"))
            assertTrue(text.contains("date_pair_started search_id="))
            assertTrue(text.contains("flight_request request_id="))
            assertTrue(text.contains("account=Cuenta-1"))
            assertTrue(text.contains("provider_search_id=provider-search-123"))
            assertTrue(text.contains("response_bytes="))
            assertTrue(text.contains("flight_search_finished search_id="))
            assertTrue(text.contains("api_key=[REDACTED]"))
            assertFalse(text.contains("FAKE_TEST_KEY"))
            assertFalse(text.contains("must-not-be-mapped@example.invalid"))
            assertFalse(text.contains("must-not-be-mapped"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun repository(
        budget: FakeBudget = FakeBudget(),
        airportTimeZoneFor: (String) -> ZoneId? = { null },
        credentials: List<StoredApiCredential> = listOf(StoredApiCredential("primary", "FAKE_TEST_KEY")),
    ): SerpApiRepository {
        val client = RetrofitSerpApiClient(server.url("/").toString())
        return SerpApiRepository(
            credentialStore = FakeCredentialStore(credentials),
            accountClient = client,
            flightClient = client,
            budgetRepository = budget,
            responseParser = SerpApiJsonParser(),
            executionMutex = Mutex(),
            airportTimeZoneFor = airportTimeZoneFor,
        )
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
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

    private class FakeCredentialStore(
        private val keys: List<StoredApiCredential> = listOf(StoredApiCredential("primary", "FAKE_TEST_KEY")),
    ) : CredentialStore {
        override suspend fun isConfigured() = keys.isNotEmpty()
        override suspend fun save(apiKey: String) = Unit
        override suspend fun read() = keys.first().apiKey
        override suspend fun delete() = Unit
        override suspend fun credentials() = keys
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
