package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.remote.FlightSearchClient
import com.flightnotifications.app.data.remote.SerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.data.local.DiagnosticLog
import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.AlertFlightSegment
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.ProbeOutcome
import com.flightnotifications.app.domain.model.RequestStage
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.model.FlexibleFlightDates
import com.flightnotifications.app.domain.model.FlightDatePair
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.domain.validation.FlightOptionValidator
import com.flightnotifications.app.security.CredentialStore
import com.flightnotifications.app.security.StoredApiCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.security.MessageDigest
import java.util.UUID
import retrofit2.HttpException
import retrofit2.Response

enum class FailureCode {
    NO_CREDENTIAL,
    CREDENTIAL_UNREADABLE,
    NETWORK,
    INVALID_CREDENTIAL,
    FORBIDDEN,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    SERVICE_ERROR,
    ACCOUNT_INVALID,
    ACCOUNT_INACTIVE,
    QUOTA_INSUFFICIENT,
    LOCAL_QUOTA,
    CLOCK_UNTRUSTED,
    INVALID_CRITERIA,
    INVALID_RESPONSE,
    SEARCH_NOT_READY,
}

class ProbeFailure(val code: FailureCode) : Exception(code.name)

data class SavedAccountCheck(
    val credentialId: String,
    val maskedKey: String,
    val account: AccountSnapshot? = null,
    val failure: FailureCode? = null,
)

private data class AccountPoolEntry(
    val credential: StoredApiCredential,
    val account: AccountSnapshot?,
    val failure: FailureCode?,
    var remaining: Int,
)

class SerpApiRepository(
    private val credentialStore: CredentialStore,
    private val accountClient: SerpApiClient,
    private val flightClient: FlightSearchClient,
    private val budgetRepository: FlightRequestBudget,
    private val responseParser: SerpApiJsonParser,
    private val executionMutex: Mutex,
    private val accountDiagnosticLog: DiagnosticLog? = null,
    private val flightDiagnosticLog: DiagnosticLog? = null,
    private val now: () -> Instant = Instant::now,
    private val airportTimeZoneFor: (String) -> ZoneId? = { null },
) {
    suspend fun checkAccount(): AccountSnapshot = executionMutex.withLock {
        val credential = readCredentials().firstOrNull() ?: throw ProbeFailure(FailureCode.NO_CREDENTIAL)
        accountSnapshotWithKey(credential.apiKey)
    }

    /** Validates an unsaved key through the provider's account endpoint only. */
    suspend fun checkAccount(apiKey: String): AccountSnapshot = executionMutex.withLock {
        if (apiKey.isBlank()) throw ProbeFailure(FailureCode.NO_CREDENTIAL)
        accountSnapshotWithKey(apiKey.trim())
    }

    /** Refreshes all configured accounts without exposing their raw keys to presentation code. */
    suspend fun checkSavedAccounts(): List<SavedAccountCheck> = executionMutex.withLock {
        val credentials = readCredentials()
        credentials.mapIndexed { index, credential ->
            try {
                SavedAccountCheck(
                    credentialId = credential.id,
                    maskedKey = maskApiKey(credential.apiKey),
                    account = accountSnapshotWithKey(credential.apiKey, accountAlias = "Cuenta-${index + 1}"),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                SavedAccountCheck(credential.id, maskApiKey(credential.apiKey), failure = failure.code)
            }
        }
    }

    suspend fun localBudget() = budgetRepository.snapshot()

    /** One account check, then a fair, serial batch. Round trips can inspect up to 8 return candidates. */
    suspend fun runAlertBatch(
        alerts: List<FlightAlertEntity>,
        maximumGets: Int = MAX_GETS_PER_BATCH,
        source: String = "unspecified",
        afterResults: suspend (List<AlertSearchResult>) -> Unit = {},
    ): List<AlertSearchResult> = executionMutex.withLock {
        if (alerts.isEmpty()) return@withLock emptyList()
        val searchId = UUID.randomUUID().toString()
        val batchStartedNanos = System.nanoTime()
        beginFlightCapture("flight_search_started search_id=$searchId source=$source type=alert_batch alerts=${alerts.size} max_flight_gets=$maximumGets")
        alerts.forEach { alert ->
            val minors = runCatching {
                kotlinx.serialization.json.Json.decodeFromString<List<com.flightnotifications.app.domain.model.MinorPassenger>>(alert.minorPassengersJson)
            }.getOrDefault(emptyList())
            val passengerCounts = com.flightnotifications.app.domain.model.PassengerSelection.counts(alert.adults18Plus, minors)
            val originZone = airportTimeZoneFor(alert.originIata)?.id ?: SearchCriteria.COLOMBIA_ZONE.id
            logFlightDiagnostic(
                "alert_criteria search_id=$searchId alert_id=${alert.id} route=${alert.originIata}->${alert.destinationIata} origin_zone=$originZone " +
                    "outbound=${alert.outboundDate}..${alert.outboundDateEnd.ifBlank { alert.outboundDate }} " +
                    "return=${alert.returnDate.ifBlank { "one_way" }}..${alert.returnDateEnd.ifBlank { alert.returnDate.ifBlank { "one_way" } }} " +
                    "flexible=${alert.flexibleDateSearch} passengers_adults_18_plus=${alert.adults18Plus} minors=${minors.joinToString(",") { it.age.toString() }} " +
                    "provider_counts_adults=${passengerCounts.adults} children=${passengerCounts.children} infants_in_seat=${passengerCounts.infantsInSeat} infants_on_lap=${passengerCounts.infantsOnLap} " +
                    "direct_only=${alert.directOnly} max_total_cop=${alert.maxTotalCop} frequency_hours=${alert.frequencyHours}",
            )
        }
        val batchStarted = now().toEpochMilli()
        val accounts: MutableList<AccountPoolEntry>
        try {
            accounts = readAccountPool(includeFlightCapture = true).toMutableList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProbeFailure) {
            logFlightDiagnostic("alert_batch_blocked category=${failure.code.name} flight_gets_started=0")
            val blocked = alerts.map { batchFailure(it, failure.code.name, batchStarted, null, 0) }
            afterResults(blocked)
            logFlightDiagnostic("flight_search_finished search_id=$searchId source=$source result=blocked error=${failure.code.name} duration_ms=${elapsedMillis(batchStartedNanos)} flight_gets_started=0")
            return@withLock blocked
        }
        if (accounts.none { it.account?.accountStatus.equals("Active", ignoreCase = true) }) {
            val failure = poolFailure(accounts)
            logFlightDiagnostic("alert_batch_blocked category=${failure.name} flight_gets_started=0 accounts=${accounts.size}")
            val blocked = alerts.map { batchFailure(it, failure.name, batchStarted, null, 0) }
            afterResults(blocked)
            logFlightDiagnostic("flight_search_finished search_id=$searchId source=$source result=blocked error=${failure.name} duration_ms=${elapsedMillis(batchStartedNanos)} flight_gets_started=0")
            return@withLock blocked
        }
        val startingBalance = accounts.maxOfOrNull { it.remaining.takeIf { value -> it.account?.accountStatus.equals("Active", ignoreCase = true) } ?: 0 }
        val searchBudget: MutableSearchBudget
        try {
            val snapshot = budgetRepository.snapshot()
            if (!snapshot.clockTrusted) {
                val blocked = alerts.map { batchFailure(it, FailureCode.CLOCK_UNTRUSTED.name, batchStarted, startingBalance, 0) }
                afterResults(blocked)
                logFlightDiagnostic("flight_search_finished search_id=$searchId source=$source result=blocked error=CLOCK_UNTRUSTED duration_ms=${elapsedMillis(batchStartedNanos)} flight_gets_started=0")
                return@withLock blocked
            }
            searchBudget = MutableSearchBudget(snapshot.remainingInWindow)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val blocked = alerts.map { batchFailure(it, FailureCode.LOCAL_QUOTA.name, batchStarted, startingBalance, 0) }
            afterResults(blocked)
            logFlightDiagnostic("flight_search_finished search_id=$searchId source=$source result=blocked error=LOCAL_QUOTA duration_ms=${elapsedMillis(batchStartedNanos)} flight_gets_started=0")
            return@withLock blocked
        }
        // Give alerts that received a flight GET in the previous batch lower priority.
        // With more due alerts than the global GET cap, this rotates the deferred alert
        // toward a position that can receive a request in the next batch.
        val orderedAlerts = alerts.sortedByDescending { it.candidateRotation }
        val results = mutableListOf<AlertSearchResult>()
        var batchStopReason: String? = null
        for ((alertIndex, alert) in orderedAlerts.withIndex()) {
            if (batchStopReason != null) {
                results += batchFailure(alert, "BATCH_STOPPED_$batchStopReason", now().toEpochMilli(), startingBalance, 0)
                continue
            }
            val attemptAt = now().toEpochMilli()
            val originZone = airportTimeZoneFor(alert.originIata) ?: SearchCriteria.COLOMBIA_ZONE
            val todayAtOrigin = now().atZone(originZone).toLocalDate()
            val datePairs = alert.datePairs(todayAtOrigin)
            if (datePairs.isEmpty()) {
                val expired = todayAtOrigin.isAfter(LocalDate.parse(alert.outboundDateEnd.ifBlank { alert.outboundDate }))
                results += batchFailure(alert, if (expired) "EXPIRED" else FailureCode.INVALID_CRITERIA.name, attemptAt, startingBalance, 0)
                continue
            }
            val datePairCount = datePairs.size
            val startIndex = if (alert.flexibleDateSearch) Math.floorMod(alert.datePairRotation, datePairCount) else 0
            val orderedPairIndices = if (alert.flexibleDateSearch) {
                (startIndex until datePairCount).toList() + (0 until startIndex).toList()
            } else listOf(0)
            val laterAlertReservations = orderedAlerts.drop(alertIndex + 1).sumOf { laterAlert ->
                if (laterAlert.returnDate.isBlank()) 1 else 2
            }
            val scan = scanAlertDatePairs(
                searchId = searchId,
                alert = alert,
                datePairs = datePairs,
                orderedPairIndices = orderedPairIndices,
                originZone = originZone,
                accounts = accounts,
                budget = searchBudget,
                maximumGets = maximumGets,
                laterAlertReservations = laterAlertReservations,
                attemptAt = attemptAt,
            )
            results += scan.result
            if (scan.batchStopReason != null) batchStopReason = scan.batchStopReason
        }
        afterResults(results)
        logFlightDiagnostic(
            "flight_search_finished search_id=$searchId source=$source result=complete duration_ms=${elapsedMillis(batchStartedNanos)} " +
                "alerts=${results.size} flight_gets_started=${results.sumOf { it.flightGetsStarted }} statuses=${results.groupingBy { it.status }.eachCount().toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }}",
        )
        results
    }

    /** Ejecuta solo tras el consentimiento de interfaz. No hay reintentos ni polling automáticos. */
    suspend fun runRoundTrip(criteria: SearchCriteria): ProbeOutcome = executionMutex.withLock {
        val searchId = UUID.randomUUID().toString()
        beginFlightCapture("flight_search_started search_id=$searchId source=manual_probe type=diagnostic_probe")
        val originZone = airportTimeZoneFor(criteria.origin) ?: SearchCriteria.COLOMBIA_ZONE
        val validationError = criteria.validationError(now().atZone(originZone).toLocalDate())
        logFlightDiagnostic(
            "flight_probe_started search_id=$searchId origin=${criteria.origin} destination=${criteria.destination} outbound_date=${criteria.outboundDate} " +
                "return_date=${criteria.returnDate} adults=${criteria.adults18Plus} minors=${criteria.minors} direct_only=${criteria.directOnly} max_gets=$MAX_GETS_PER_PROBE",
        )
        if (validationError != null) {
            logFlightDiagnostic("flight_preflight_failed stage=criteria category=INVALID_CRITERIA reason=${validationError.replace(Regex("[^A-Za-z0-9 _-]"), " ").take(100)}")
            throw ProbeFailure(FailureCode.INVALID_CRITERIA)
        }
        val startedAt = now()
        val accountPool = readAccountPool(includeFlightCapture = true)
        val selectedAccount = accountPool.firstOrNull {
            it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining >= MAX_GETS_PER_PROBE
        }
        if (selectedAccount == null) {
            val failure = poolFailure(accountPool)
            logFlightDiagnostic("flight_preflight_failed stage=account category=${failure.name} accounts=${accountPool.size} required=$MAX_GETS_PER_PROBE")
            throw ProbeFailure(failure)
        }
        val accountAlias = "Cuenta-${accountPool.indexOf(selectedAccount) + 1}"
        val apiKey = selectedAccount.credential.apiKey
        val account = selectedAccount.account!!
        val officialCapacity = selectedAccount.remaining
        val localBudget = budgetRepository.snapshot()
        if (!localBudget.clockTrusted) {
            logFlightDiagnostic("flight_preflight_failed stage=local_budget category=CLOCK_UNTRUSTED")
            throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
        }
        if (localBudget.remainingInWindow < MAX_GETS_PER_PROBE) {
            logFlightDiagnostic("flight_preflight_failed stage=local_budget category=LOCAL_QUOTA remaining=${localBudget.remainingInWindow} required=$MAX_GETS_PER_PROBE")
            throw ProbeFailure(FailureCode.LOCAL_QUOTA)
        }
        logFlightDiagnostic("flight_preflight_ok account=$accountAlias account_capacity=$officialCapacity local_remaining=${localBudget.remainingInWindow} clock_trusted=true")

        val runId = searchId
        val outboundParameters = searchParameters(criteria, apiKey)
        val outboundRoot = performFlightGet(RequestStage.OUTBOUND, runId, outboundParameters, searchId = searchId, accountAlias = accountAlias)
        val outboundParsed = parseSearchResponse(outboundRoot, outboundParameters)
        val outboundCompatible = outboundParsed.options.filter { FlightOptionValidator.isOutbound(it, criteria, airportTimeZoneFor) }
        logFlightDiagnostic("flight_parse stage=outbound options=${outboundParsed.options.size} malformed=${outboundParsed.malformedOptions} route_date_candidates=${outboundCompatible.size} candidates_with_token=${outboundCompatible.count { !it.departureToken.isNullOrBlank() }} airlines=${outboundParsed.airlinesDetected.size}")
        val outboundCandidate = outboundParsed.options
            .asSequence()
            .filter { FlightOptionValidator.isOutbound(it, criteria, airportTimeZoneFor) && !it.departureToken.isNullOrBlank() }
            .sortedBy { it.priceCop ?: Long.MAX_VALUE }
            .firstOrNull()

        if (outboundCandidate == null) {
            logFlightDiagnostic("flight_probe_outcome status=${if (outboundParsed.options.isEmpty() && outboundParsed.malformedOptions == 0) "SIN_RESULTADOS_DEVUELTOS" else "PARCIAL"} flight_gets_started=1 reason=NO_VALID_OUTBOUND_TOKEN")
            val detail = when {
                outboundParsed.options.isEmpty() && outboundParsed.malformedOptions == 0 ->
                    "La fuente no devolvió opciones de vuelo para estos criterios."
                outboundParsed.options.isEmpty() ->
                    "La respuesta incluyó opciones que no pudieron interpretarse; no se consideró que la ruta carezca de vuelos."
                else ->
                    "No se encontró una salida compatible con token para consultar el regreso."
            }
            return@withLock ProbeOutcome(
                criteria = criteria,
                accountAtStart = account,
                outbound = null,
                returnOptions = emptyList(),
                selectedReturn = null,
                airlinesDetected = outboundParsed.airlinesDetected,
                googleFlightsUrl = outboundParsed.googleFlightsUrl,
                startedAt = startedAt,
                completedAt = now(),
                flightGetsStarted = 1,
                statusLabel = if (outboundParsed.options.isEmpty() && outboundParsed.malformedOptions == 0) {
                    "SIN_RESULTADOS_DEVUELTOS"
                } else {
                    "PARCIAL"
                },
                detail = detail,
                malformedOptions = outboundParsed.malformedOptions,
            )
        }

        val returnParameters = outboundParameters + ("departure_token" to outboundCandidate.departureToken!!)
        val returnRoot = performFlightGet(RequestStage.RETURN, runId, returnParameters, searchId = searchId, accountAlias = accountAlias)
        val returnParsed = parseSearchResponse(returnRoot, returnParameters)
        val validReturns = returnParsed.options.filter {
            FlightOptionValidator.isReturn(it, criteria, airportTimeZoneFor) && FlightOptionValidator.returnAfterOutbound(outboundCandidate, it)
        }
        logFlightDiagnostic("flight_parse stage=return options=${returnParsed.options.size} malformed=${returnParsed.malformedOptions} valid_returns=${validReturns.size} airlines=${returnParsed.airlinesDetected.size} selected_return_price_present=${validReturns.any { it.priceCop != null }}")
        val matchingRoundTripReturns = if (
            outboundCandidate.type.isRoundTripType() && outboundCandidate.priceCop != null
        ) {
            validReturns.filter {
                it.type.isRoundTripType() && it.priceCop == outboundCandidate.priceCop
            }
        } else {
            emptyList()
        }
        val selectedReturn = matchingRoundTripReturns.minByOrNull { it.priceCop ?: Long.MAX_VALUE }
            ?: validReturns.minByOrNull { it.priceCop ?: Long.MAX_VALUE }
        val verifiedRoundTripPriceCop = matchingRoundTripReturns.firstOrNull()?.priceCop
        logFlightDiagnostic("flight_selection outbound=${summarizeOption(outboundCandidate)} return=${selectedReturn?.let(::summarizeOption) ?: "none"}")
        logFlightDiagnostic("flight_fare_check outbound_type_round_trip=${outboundCandidate.type.isRoundTripType()} return_type_round_trip=${selectedReturn?.type.isRoundTripType()} outbound_price_present=${outboundCandidate.priceCop != null} return_price_present=${selectedReturn?.priceCop != null} prices_match=${verifiedRoundTripPriceCop != null} total_price_status=${if (verifiedRoundTripPriceCop != null) "VERIFIED" else "UNVERIFIED"}")
        logFlightDiagnostic("flight_probe_outcome status=${when { selectedReturn == null -> "PARCIAL"; verifiedRoundTripPriceCop != null -> "VERIFICADO"; else -> "NO_VERIFICADO" }} flight_gets_started=2 valid_returns=${validReturns.size}")
        val airlines = (outboundParsed.airlinesDetected + returnParsed.airlinesDetected)
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)

        val detail = when {
            validReturns.isEmpty() && returnParsed.options.isEmpty() && returnParsed.malformedOptions == 0 ->
                "La fuente no devolvió opciones de regreso asociadas a la salida seleccionada."
            validReturns.isEmpty() && returnParsed.options.isEmpty() ->
                "La respuesta de regreso incluyó opciones que no pudieron interpretarse."
            validReturns.isEmpty() ->
                "Se recibieron opciones de regreso, pero ninguna superó la validación de ruta, fecha y secuencia."
            verifiedRoundTripPriceCop != null ->
                "La ida y el regreso asociado están marcados como Round trip y SerpApi devolvió el mismo importe para ambos. Se informa como tarifa total observada; puede cambiar al reservar."
            else ->
                "Itinerario de ida y regreso detectado. No se pudo confirmar una tarifa total: los tipos o importes de las respuestas no coinciden."
        }
        ProbeOutcome(
            criteria = criteria,
            accountAtStart = account,
            outbound = outboundCandidate,
            returnOptions = validReturns,
            selectedReturn = selectedReturn,
            airlinesDetected = airlines,
            googleFlightsUrl = returnParsed.googleFlightsUrl ?: outboundParsed.googleFlightsUrl,
            startedAt = startedAt,
            completedAt = now(),
            flightGetsStarted = 2,
            statusLabel = when {
                selectedReturn == null -> "PARCIAL"
                verifiedRoundTripPriceCop != null -> "VERIFICADO"
                else -> "NO_VERIFICADO"
            },
            detail = detail,
            malformedOptions = outboundParsed.malformedOptions + returnParsed.malformedOptions,
            verifiedRoundTripPriceCop = verifiedRoundTripPriceCop,
        )
    }

    private suspend fun performFlightGet(
        stage: RequestStage,
        runId: String,
        parameters: Map<String, String>,
        searchId: String? = null,
        alertId: String? = null,
        datePairIndex: Int? = null,
        candidateIndex: Int? = null,
        accountAlias: String? = null,
        budget: MutableSearchBudget? = null,
        selectedAccount: AccountPoolEntry? = null,
        onReserved: () -> Unit = {},
    ): JsonObject {
        val context = buildString {
            searchId?.let { append(" search_id=$it") }
            alertId?.let { append(" alert_id=$it") }
            datePairIndex?.let { append(" pair=${it + 1}") }
            candidateIndex?.let { append(" candidate=${it + 1}") }
            accountAlias?.let { append(" account=$it") }
        }
        logFlightDiagnostic("flight_reservation stage=${stage.name.lowercase()} run_id=$runId$context")
        when (budgetRepository.reserve(stage, runId)) {
            BudgetReservationResult.RESERVED -> logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} run_id=$runId$context status=RESERVED")
            BudgetReservationResult.LIMIT_REACHED -> {
                logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} run_id=$runId$context status=LIMIT_REACHED")
                throw ProbeFailure(FailureCode.LOCAL_QUOTA)
            }
            BudgetReservationResult.CLOCK_UNTRUSTED -> {
                logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} run_id=$runId$context status=CLOCK_UNTRUSTED")
                throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
            }
        }
        budget?.let {
            it.localRemaining = (it.localRemaining - 1).coerceAtLeast(0)
            it.totalGets++
        }
        selectedAccount?.let { it.remaining = (it.remaining - 1).coerceAtLeast(0) }
        onReserved()
        val safeCriteria = parameters.toSortedMap().entries.joinToString(",") { (key, value) ->
            "$key=${if (key.equals("api_key", ignoreCase = true)) "[REDACTED]" else value}"
        }
        val requestId = UUID.randomUUID().toString()
        logFlightPayload(
            "flight_request request_id=$requestId run_id=$runId$context method=GET endpoint=/search.json stage=${stage.name.lowercase()} query={$safeCriteria}",
            "",
        )
        val startedAt = System.nanoTime()
        val response = try {
            networkCall("flight_${stage.name.lowercase()}", flightDiagnosticLog) { flightClient.search(parameters) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProbeFailure) {
            logFlightDiagnostic(
                "flight_response request_id=$requestId run_id=$runId$context stage=${stage.name.lowercase()} result=transport_or_http_error " +
                    "error=${failure.code.name} duration_ms=${elapsedMillis(startedAt)}",
            )
            throw failure
        }
        val responsePayload = response.body()?.toString()
            ?: runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val wireDetails = wireDetails(response)
        logFlightPayload(
            "flight_response request_id=$requestId run_id=$runId$context method=GET endpoint=/search.json stage=${stage.name.lowercase()} http=${response.code()} " +
                "duration_ms=${elapsedMillis(startedAt)} response_bytes=${responsePayload.toByteArray(Charsets.UTF_8).size} " +
                "provider_search_id=${response.headers()["serpapi-search-id"] ?: "none"} protocol=${response.raw().protocol} $wireDetails " +
                "body=${if (responsePayload.isBlank()) "<empty>" else "below"}",
            responsePayload,
        )
        if (!response.isSuccessful) {
            val failure = failureForHttp(response.code(), responsePayload)
            logFlightDiagnostic("flight_http_error request_id=$requestId run_id=$runId$context http=${response.code()} category=${failure.code.name}")
            throw failure
        }
        return response.body() ?: throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    }

    private suspend fun accountSnapshotWithKey(
        apiKey: String,
        includeFlightCapture: Boolean = false,
        accountAlias: String? = null,
    ): AccountSnapshot {
        val accountContext = accountAlias?.let { " account=$it" }.orEmpty()
        logAccountDiagnostic("account_request_started endpoint=GET /account.json api_key_attached=true$accountContext")
        if (includeFlightCapture) logFlightPayload("account_request endpoint=GET /account.json query={api_key=[REDACTED]}$accountContext", "")
        val startedAt = System.nanoTime()
        val response = networkCall(
            "account",
            accountDiagnosticLog,
            captureLog = if (includeFlightCapture) flightDiagnosticLog else null,
        ) { accountClient.account(apiKey) }
        val responsePayload = response.body()?.toString()
            ?: runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        logAccountDiagnostic(
            "account_response http=${response.code()} duration_ms=${elapsedMillis(startedAt)}$accountContext " +
                "error_body_present=${response.code() !in 200..299 && responsePayload.isNotBlank()}",
        )
        if (includeFlightCapture) {
            logFlightPayload(
                "account_response endpoint=GET /account.json http=${response.code()}$accountContext " +
                    "duration_ms=${elapsedMillis(startedAt)} protocol=${response.raw().protocol} ${wireDetails(response)} " +
                    "body=${if (responsePayload.isBlank()) "<empty>" else "below"}",
                responsePayload,
            )
        }
        val body = responseBody(response)
        logAccountDiagnostic("account_response_fields=${safeResponseFieldNames(body)}")
        return try {
            parseAccount(body, now()).also { account ->
                logAccountDiagnostic("account_parse_ok status=${account.accountStatus}")
            }
        } catch (failure: ProbeFailure) {
            logAccountDiagnostic("account_parse_failed category=${failure.code.name}")
            throw failure
        } catch (_: Exception) {
            logAccountDiagnostic("account_parse_failed category=UNEXPECTED_FORMAT")
            throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        }
    }

    private suspend fun readCredentials(): List<StoredApiCredential> = try {
        credentialStore.credentials().also { credentials ->
            if (credentials.isEmpty()) throw ProbeFailure(FailureCode.NO_CREDENTIAL)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ProbeFailure) {
        throw failure
    } catch (_: Exception) {
        throw ProbeFailure(FailureCode.CREDENTIAL_UNREADABLE)
    }

    private suspend fun readAccountPool(includeFlightCapture: Boolean = false): List<AccountPoolEntry> =
        readCredentials().mapIndexed { index, credential ->
            val alias = "Cuenta-${index + 1}"
            try {
                val account = accountSnapshotWithKey(credential.apiKey, includeFlightCapture, alias)
                logFlightDiagnostic(
                    "account_pool_status account=$alias status=${account.accountStatus} " +
                        "searches_left=${account.conservativeSearchesLeft ?: "unknown"} checked_at=${account.checkedAt}",
                )
                AccountPoolEntry(
                    credential = credential,
                    account = account,
                    failure = null,
                    remaining = account.conservativeSearchesLeft ?: 0,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                logFlightDiagnostic("account_pool_status account=$alias status=unavailable error=${failure.code.name}")
                AccountPoolEntry(credential, account = null, failure = failure.code, remaining = 0)
            }
        }

    private fun poolFailure(accounts: List<AccountPoolEntry>): FailureCode {
        if (accounts.isEmpty()) return FailureCode.NO_CREDENTIAL
        val active = accounts.filter { it.account?.accountStatus.equals("Active", ignoreCase = true) }
        if (active.any { it.remaining > 0 }) return FailureCode.QUOTA_INSUFFICIENT
        if (active.isNotEmpty()) return FailureCode.QUOTA_INSUFFICIENT
        if (accounts.all { it.account != null }) return FailureCode.ACCOUNT_INACTIVE
        return listOf(
            FailureCode.RATE_LIMITED,
            FailureCode.NETWORK,
            FailureCode.SERVICE_UNAVAILABLE,
            FailureCode.FORBIDDEN,
            FailureCode.INVALID_CREDENTIAL,
            FailureCode.ACCOUNT_INVALID,
            FailureCode.SERVICE_ERROR,
        ).firstOrNull { wanted -> accounts.any { it.failure == wanted } }
            ?: accounts.firstNotNullOfOrNull { it.failure }
            ?: FailureCode.ACCOUNT_INVALID
    }

    private fun maskApiKey(apiKey: String): String = "••••${apiKey.takeLast(4)}"

    private fun parseAccount(body: JsonObject, checkedAt: Instant): AccountSnapshot {
        if (body["error"] != null) throw ProbeFailure(FailureCode.INVALID_CREDENTIAL)
        val status = body.stringValue("account_status") ?: throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        val planLeft = body.nonNegativeInt("plan_searches_left", required = false)
        val totalLeft = body.nonNegativeInt("total_searches_left", required = true)
        val hourlyLimit = body.nonNegativeInt("account_rate_limit_per_hour", required = true)
        val thisHour = body.nonNegativeInt("this_hour_searches", required = true)
        if (hourlyLimit!! < thisHour!!) throw ProbeFailure(FailureCode.ACCOUNT_INVALID)

        val extraCredits = body.nonNegativeInt("extra_credits", required = false)
        val monthlyLimit = body.nonNegativeInt("searches_per_month", required = false)
        val monthlyUsage = body.nonNegativeInt("this_month_usage", required = false)
        val renewalRaw = body.stringValue("plan_renewal_date")
        val renewalDate = renewalRaw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (renewalRaw != null && renewalDate == null) {
            throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        }
        if (planLeft != null && totalLeft!! < planLeft && (extraCredits ?: 0) == 0) {
            throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        }
        return AccountSnapshot(
            accountStatus = status,
            planSearchesLeft = planLeft,
            totalSearchesLeft = totalLeft,
            searchesPerMonth = monthlyLimit,
            thisMonthUsage = monthlyUsage,
            extraCredits = extraCredits,
            hourlyLimit = hourlyLimit,
            thisHourSearches = thisHour,
            planRenewalDate = renewalDate,
            checkedAt = checkedAt,
        )
    }

    private suspend fun parseSearchResponse(root: JsonObject, expected: Map<String, String>) = try {
        verifyReflectedCriteria(root, expected)
        responseParser.parse(root)
    } catch (failure: ProbeFailure) {
        logFlightDiagnostic("flight_parse_failed stage=${if ("departure_token" in expected) "return" else "outbound"} category=${failure.code.name}")
        throw failure
    } catch (error: IllegalArgumentException) {
        logFlightDiagnostic("flight_parse_failed stage=${if ("departure_token" in expected) "return" else "outbound"} category=${error.message?.takeIf { it in setOf("SEARCH_NOT_READY", "SERVICE_ERROR") } ?: "INVALID_RESPONSE"}")
        when (error.message) {
            "SEARCH_NOT_READY" -> throw ProbeFailure(FailureCode.SEARCH_NOT_READY)
            "SERVICE_ERROR" -> throw ProbeFailure(FailureCode.SERVICE_ERROR)
            else -> throw ProbeFailure(FailureCode.INVALID_RESPONSE)
        }
    } catch (error: Exception) {
        logFlightDiagnostic("flight_parse_failed stage=${if ("departure_token" in expected) "return" else "outbound"} category=INVALID_RESPONSE exception=${error.javaClass.simpleName}")
        throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    }

    private fun verifyReflectedCriteria(root: JsonObject, expected: Map<String, String>) {
        val reflected = root["search_parameters"] as? JsonObject ?: return
        val keysToCheck = listOf(
            "engine", "departure_id", "arrival_id", "outbound_date", "return_date", "type",
            "adults", "children", "infants_in_seat", "infants_on_lap", "travel_class", "currency", "gl", "hl", "stops", "sort_by", "departure_token",
            "no_cache", "deep_search", "show_hidden",
        )
        keysToCheck.forEach { key ->
            val actual = reflected[key]?.let { element -> (element as? JsonPrimitive)?.content }
            val requested = expected[key]
            if (actual != null && requested != null && actual != requested) {
                throw ProbeFailure(FailureCode.INVALID_RESPONSE)
            }
        }
    }

    private fun searchParameters(criteria: SearchCriteria, apiKey: String): Map<String, String> {
        val passengers = com.flightnotifications.app.domain.model.PassengerSelection.counts(criteria.adults18Plus, criteria.minors)
        return buildMap {
            put("engine", "google_flights")
            put("departure_id", criteria.origin)
            put("arrival_id", criteria.destination)
            put("outbound_date", criteria.outboundDate.toString())
            criteria.returnDate?.let { put("return_date", it.toString()) }
            put("type", if (criteria.returnDate == null) "2" else "1")
            put("adults", passengers.adults.toString())
            put("children", passengers.children.toString())
            put("infants_in_seat", passengers.infantsInSeat.toString())
            put("infants_on_lap", passengers.infantsOnLap.toString())
            put("travel_class", "1")
            put("currency", "COP")
            put("gl", "co")
            put("hl", "es")
            put("stops", if (criteria.directOnly) "1" else "0")
            put("sort_by", "2")
            put("no_cache", "true")
            put("deep_search", "true")
            put("show_hidden", "true")
            put("api_key", apiKey)
        }
    }

    private suspend fun <T> networkCall(
        operation: String,
        log: DiagnosticLog?,
        captureLog: DiagnosticLog? = null,
        call: suspend () -> T,
    ): T = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        val event = "network_failure operation=$operation exception=${error.javaClass.simpleName} message=${error.message.orEmpty()}"
        logDiagnostic(log, event)
        if (captureLog !== log) logDiagnostic(captureLog, event)
        throw ProbeFailure(FailureCode.NETWORK)
    } catch (error: HttpException) {
        val event = "network_failure operation=$operation http=${error.code()} message=${error.message.orEmpty()}"
        logDiagnostic(log, event)
        if (captureLog !== log) logDiagnostic(captureLog, event)
        throw failureForHttp(error.code())
    } catch (_: SerializationException) {
        val event = "network_failure operation=$operation category=SERIALIZATION"
        logDiagnostic(log, event)
        if (captureLog !== log) logDiagnostic(captureLog, event)
        throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    } catch (error: Exception) {
        val event = "network_failure operation=$operation exception=${error.javaClass.simpleName} message=${error.message.orEmpty()}"
        logDiagnostic(log, event)
        if (captureLog !== log) logDiagnostic(captureLog, event)
        throw ProbeFailure(FailureCode.NETWORK)
    }

    private fun <T> responseBody(response: Response<T>): T {
        if (!response.isSuccessful) throw failureForHttp(response.code())
        return response.body() ?: throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    }

    private fun failureForHttp(status: Int, body: String = ""): ProbeFailure = ProbeFailure(
        when (status) {
            401 -> FailureCode.INVALID_CREDENTIAL
            403 -> FailureCode.FORBIDDEN
            429 -> FailureCode.RATE_LIMITED
            400, 402 -> if (QUOTA_ERROR.containsMatchIn(body)) FailureCode.QUOTA_INSUFFICIENT else FailureCode.SERVICE_ERROR
            in 500..599 -> FailureCode.SERVICE_UNAVAILABLE
            else -> FailureCode.SERVICE_ERROR
        },
    )

    private suspend fun logAccountDiagnostic(event: String) = logDiagnostic(accountDiagnosticLog, event)

    private suspend fun logFlightDiagnostic(event: String) = logDiagnostic(flightDiagnosticLog, event)

    private suspend fun beginFlightCapture(event: String) {
        try {
            flightDiagnosticLog?.beginCapture(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Logging must never stop a flight search.
        }
    }

    private suspend fun logFlightPayload(event: String, payload: String) {
        try {
            flightDiagnosticLog?.recordPayload(event, payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Logging must never stop a flight search.
        }
    }

    private fun summarizeOffer(offer: VerifiedRoundTripOffer): String =
        "${offer.totalCop}:${offer.outboundAirline}/${offer.returnAirline}:" +
            "${offer.outbound.joinToString(",") { "${it.fromIata}-${it.toIata}@${it.departureLocal ?: "?"}/${it.airline} ${it.flightNumber}" }}:" +
            "${offer.returning.joinToString(",") { "${it.fromIata}-${it.toIata}@${it.departureLocal ?: "?"}/${it.airline} ${it.flightNumber}" }}"

    private fun summarizeOption(option: com.flightnotifications.app.domain.model.FlightOption): String =
        "price=${option.priceCop ?: "unknown"} type=${option.type ?: "unknown"} " +
            "segments=${option.segments.joinToString(",") { segment ->
                "${segment.departure.code}-${segment.arrival.code}@${segment.departure.localDateTime ?: "?"}/${segment.airline ?: "?"} ${segment.flightNumber ?: "?"}"
            }}"

    private fun <T> wireDetails(response: Response<T>): String =
        "request_headers=${safeHeaders(response.raw().request.headers)} response_headers=${safeHeaders(response.raw().headers)}"

    private fun safeHeaders(headers: okhttp3.Headers): String = headers.names()
        .sorted()
        .filterNot { name ->
            name.equals("authorization", ignoreCase = true) ||
                name.equals("cookie", ignoreCase = true) ||
                name.equals("set-cookie", ignoreCase = true) ||
                name.equals("x-api-key", ignoreCase = true)
        }
        .joinToString(",", prefix = "{", postfix = "}") { name ->
            "$name=${headers.values(name).joinToString("|")}"
        }

    private suspend fun logDiagnostic(log: DiagnosticLog?, event: String) {
        try {
            log?.record(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Diagnostics must never prevent an account or flight request from being handled.
        }
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L

    private fun String?.isRoundTripType(): Boolean = this?.equals("Round trip", ignoreCase = true) == true
    private fun String?.isOneWayType(): Boolean = this?.equals("One way", ignoreCase = true) == true

    private fun FlightAlertEntity.datePairs(todayAtOrigin: LocalDate): List<FlightDatePair> {
        val outboundStart = LocalDate.parse(outboundDate)
        val outboundEnd = if (flexibleDateSearch) LocalDate.parse(outboundDateEnd.ifBlank { outboundDate }) else outboundStart
        val returnStart = returnDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        val returnEnd = if (flexibleDateSearch && returnStart != null) {
            LocalDate.parse(returnDateEnd.ifBlank { returnDate })
        } else returnStart
        return FlexibleFlightDates.pairs(outboundStart, outboundEnd, returnStart, returnEnd, todayAtOrigin)
    }

    private fun FlightAlertEntity.toSearchCriteria(pair: FlightDatePair): SearchCriteria = SearchCriteria(
        outboundDate = pair.outbound,
        returnDate = pair.returning,
        directOnly = directOnly,
        origin = originIata.uppercase(),
        destination = destinationIata.uppercase(),
        adults18Plus = adults18Plus,
        minors = kotlinx.serialization.json.Json.decodeFromString(minorPassengersJson),
    )

    private data class MutableSearchBudget(var localRemaining: Int, var totalGets: Int = 0)

    private data class AlertPairScan(val result: AlertSearchResult, val batchStopReason: String?)

    private suspend fun scanAlertDatePairs(
        searchId: String,
        alert: FlightAlertEntity,
        datePairs: List<FlightDatePair>,
        orderedPairIndices: List<Int>,
        originZone: ZoneId,
        accounts: MutableList<AccountPoolEntry>,
        budget: MutableSearchBudget,
        maximumGets: Int,
        laterAlertReservations: Int,
        attemptAt: Long,
    ): AlertPairScan {
        val getsAtStart = budget.totalGets
        val offers = mutableListOf<VerifiedRoundTripOffer>()
        val airlines = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        val attemptedIndices = mutableListOf<Int>()
        val scannedIndices = mutableListOf<Int>()
        val incompleteIndices = mutableListOf<Int>()
        val aliasesUsed = linkedSetOf<String>()
        var accountBalanceAtStart: Int? = null
        var receivedAt: Long? = null
        var googleFlightsUrl: String? = null
        var candidatesConsidered = 0
        var candidatesCompleted = 0
        var candidateFailures = 0
        var candidateCountTotal = 0
        var duplicateCandidatesRemoved = 0
        var duplicateOffersRemoved = 0
        var candidateLimitUsed = 0
        var malformed = 0
        var outboundOptionsTotal = 0
        var firstFailure: String? = null
        var batchStopReason: String? = null

        for ((position, pairIndex) in orderedPairIndices.withIndex()) {
            if (batchStopReason != null) break
            val pair = datePairs[pairIndex]
            val criteria = alert.toSearchCriteria(pair)
            val invalidCriteria = criteria.validationError(now().atZone(originZone).toLocalDate())
            if (invalidCriteria != null) {
                attemptedIndices += pairIndex
                firstFailure = firstFailure ?: FailureCode.INVALID_CRITERIA.name
                logFlightDiagnostic("date_pair_skipped search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} reason=INVALID_CRITERIA")
                continue
            }

            val minimumGets = if (criteria.returnDate == null) 1 else 2
            var pairAccount = accounts.firstOrNull {
                it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining >= minimumGets
            }
            if (pairAccount == null) {
                firstFailure = firstFailure ?: poolFailure(accounts).name
                logFlightDiagnostic("date_pair_deferred search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} reason=${poolFailure(accounts).name}")
                break
            }
            val availableGets = minOf(
                pairAccount.remaining,
                budget.localRemaining,
                MAX_GETS_PER_ALERT - (budget.totalGets - getsAtStart),
                maximumGets - budget.totalGets - laterAlertReservations,
            ).coerceAtLeast(0)
            val remainingPairs = orderedPairIndices.size - position
            val pairSlots = minOf(remainingPairs, availableGets / minimumGets)
            if (pairSlots <= 0) {
                val reason = when {
                    budget.localRemaining < minimumGets -> FailureCode.LOCAL_QUOTA.name
                    maximumGets - budget.totalGets - laterAlertReservations < minimumGets -> "BATCH_BUDGET_REACHED"
                    else -> poolFailure(accounts).name
                }
                logFlightDiagnostic("date_pair_deferred search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} reason=$reason remaining_gets=$availableGets")
                firstFailure = firstFailure ?: reason
                break
            }
            val allocatedGets = availableGets / pairSlots + if (availableGets % pairSlots > 0) 1 else 0
            val accountIndex = accounts.indexOf(pairAccount).coerceAtLeast(0)
            var accountAlias = "Cuenta-${accountIndex + 1}"
            val runId = UUID.randomUUID().toString()
            attemptedIndices += pairIndex
            aliasesUsed += accountAlias
            if (accountBalanceAtStart == null) accountBalanceAtStart = pairAccount.remaining
            logFlightDiagnostic(
                "date_pair_started search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} " +
                    "outbound=${pair.outbound} return=${pair.returning ?: "one_way"} allocated_gets=$allocatedGets account=$accountAlias",
            )
            var outboundParameters = searchParameters(criteria, pairAccount.credential.apiKey)
            val outboundParsed = try {
                val root = performFlightGet(
                    stage = RequestStage.OUTBOUND,
                    runId = runId,
                    parameters = outboundParameters,
                    searchId = searchId,
                    alertId = alert.id,
                    datePairIndex = pairIndex,
                    accountAlias = accountAlias,
                    budget = budget,
                    selectedAccount = pairAccount,
                )
                receivedAt = now().toEpochMilli()
                parseSearchResponse(root, outboundParameters).also {
                    if (it.responseStatus != "Success") throw ProbeFailure(FailureCode.SEARCH_NOT_READY)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                firstFailure = firstFailure ?: failure.code.name
                logFlightDiagnostic(
                    "date_pair_outbound_failed search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} error=${failure.code.name}",
                )
                if (failure.code in setOf(FailureCode.RATE_LIMITED, FailureCode.FORBIDDEN, FailureCode.INVALID_CREDENTIAL)) {
                    pairAccount.remaining = 0
                    if (accounts.none { it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining > 0 }) {
                        batchStopReason = failure.code.name
                    } else {
                        logFlightDiagnostic("account_fallback_available search_id=$searchId alert_id=${alert.id} failed_account=$accountAlias next_account_available=true")
                    }
                } else if (failure.code == FailureCode.QUOTA_INSUFFICIENT) {
                    pairAccount.remaining = 0
                    if (accounts.none { it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining > 0 }) {
                        batchStopReason = failure.code.name
                    }
                } else if (failure.code in setOf(FailureCode.NETWORK, FailureCode.SERVICE_UNAVAILABLE)) {
                    batchStopReason = failure.code.name
                }
                continue
            } catch (failure: Exception) {
                firstFailure = firstFailure ?: FailureCode.INVALID_RESPONSE.name
                logFlightDiagnostic("date_pair_outbound_failed search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} error=INVALID_RESPONSE cause=${failure.javaClass.simpleName}")
                continue
            }

            scannedIndices += pairIndex
            googleFlightsUrl = outboundParsed.googleFlightsUrl ?: googleFlightsUrl
            airlines += outboundParsed.airlinesDetected
            val pairMalformedBefore = malformed
            malformed += outboundParsed.malformedOptions
            outboundOptionsTotal += outboundParsed.options.size
            val routeValid = outboundParsed.options.filter { FlightOptionValidator.isOutbound(it, criteria, airportTimeZoneFor) }
            val tokenValid = if (criteria.returnDate == null) routeValid else routeValid.filter { !it.departureToken.isNullOrBlank() }
            val reviewInstant = now()
            val timeValid = tokenValid.filter { option ->
                val departure = option.segments.firstOrNull()?.departure?.localDateTime ?: return@filter false
                criteria.outboundDate != reviewInstant.atZone(originZone).toLocalDate() || departure.atZone(originZone).toInstant().isAfter(reviewInstant)
            }
            val uniqueCandidates = timeValid.distinctBy(::candidateFingerprint)
            val removedDuplicates = timeValid.size - uniqueCandidates.size
            duplicateCandidatesRemoved += removedDuplicates
            val orderedCandidates = diverseCandidateOrder(uniqueCandidates, alert.candidateRotation)
            candidateCountTotal += orderedCandidates.size
            val candidateLimit = if (criteria.returnDate == null) {
                minOf(MAX_CANDIDATES_PER_ALERT, orderedCandidates.size)
            } else {
                minOf(MAX_CANDIDATES_PER_ALERT, orderedCandidates.size, (allocatedGets - 1).coerceAtLeast(0))
            }
            candidateLimitUsed = maxOf(candidateLimitUsed, candidateLimit)
            logFlightDiagnostic(
                "outbound_candidates search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} " +
                    "raw=${outboundParsed.options.size} route_date_valid=${routeValid.size} missing_departure_token=${routeValid.size - tokenValid.size} " +
                    "time_valid=${timeValid.size} unique_candidates=${orderedCandidates.size} duplicate_candidates_removed=$removedDuplicates " +
                    "malformed=${outboundParsed.malformedOptions} candidate_limit=$candidateLimit " +
                    "rotation=${alert.candidateRotation} ordered=${orderedCandidates.mapIndexed { index, option -> "${index + 1}:${summarizeOption(option)}" }.joinToString(";").ifBlank { "none" }}",
            )
            val pairFailuresBefore = candidateFailures
            val offersBeforePair = offers.size
            var pairCompleted = 0
            var pairStarted = 0
            var pairHasMissingPrice = false
            for ((candidateIndex, candidate) in orderedCandidates.take(candidateLimit).withIndex()) {
                candidatesConsidered++
                pairStarted++
                if (criteria.returnDate == null) {
                    if (candidate.type.isOneWayType() && candidate.priceCop != null) {
                        pairCompleted++
                        candidatesCompleted++
                        offers += makeVerifiedOffer(alert.id, candidate, null, candidate.priceCop, criteria)
                    } else {
                        pairHasMissingPrice = true
                        logFlightDiagnostic("candidate_rejected search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} candidate=${candidateIndex + 1} reason=INVALID_ONE_WAY_PRICE_OR_TYPE")
                    }
                    continue
                }
                val returnParameters = outboundParameters + ("departure_token" to candidate.departureToken.orEmpty())
                try {
                    val returnRoot = performFlightGet(
                        stage = RequestStage.RETURN,
                        runId = runId,
                        parameters = returnParameters,
                        searchId = searchId,
                        alertId = alert.id,
                        datePairIndex = pairIndex,
                        candidateIndex = candidateIndex,
                        accountAlias = accountAlias,
                        budget = budget,
                        selectedAccount = pairAccount,
                    )
                    receivedAt = now().toEpochMilli()
                    val returning = parseSearchResponse(returnRoot, returnParameters)
                    if (returning.responseStatus != "Success") throw ProbeFailure(FailureCode.SEARCH_NOT_READY)
                    pairCompleted++
                    candidatesCompleted++
                    airlines += returning.airlinesDetected
                    malformed += returning.malformedOptions
                    val routeValidReturns = returning.options.filter { FlightOptionValidator.isReturn(it, criteria, airportTimeZoneFor) }
                    val chronologicalReturns = routeValidReturns.filter { FlightOptionValidator.returnAfterOutbound(candidate, it) }
                    val outboundPrice = candidate.priceCop
                    val matchingPrices = chronologicalReturns.filter { it.type.isRoundTripType() && outboundPrice != null && it.priceCop == outboundPrice }
                    val eligible = if (candidate.type.isRoundTripType() && outboundPrice != null) matchingPrices else emptyList()
                    eligible.forEach { returningOption -> offers += makeVerifiedOffer(alert.id, candidate, returningOption, outboundPrice!!, criteria) }
                    logFlightDiagnostic(
                        "return_candidate_summary search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} candidate=${candidateIndex + 1} " +
                            "http_result=success raw=${returning.options.size} route_date_valid=${routeValidReturns.size} chronological=${chronologicalReturns.size} " +
                            "matching_total_fare=${matchingPrices.size} offers_added=${eligible.size} malformed=${returning.malformedOptions}",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ProbeFailure) {
                    candidateFailures++
                    firstFailure = firstFailure ?: failure.code.name
                    logFlightDiagnostic(
                        "return_candidate_failed search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} " +
                            "candidate=${candidateIndex + 1} error=${failure.code.name} account=$accountAlias",
                    )
                    if (failure.code in setOf(FailureCode.RATE_LIMITED, FailureCode.FORBIDDEN, FailureCode.INVALID_CREDENTIAL)) {
                        pairAccount?.remaining = 0
                        val replacement = accounts.firstOrNull {
                            it !== pairAccount && it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining > 0
                        }
                        if (replacement == null) {
                            batchStopReason = failure.code.name
                            break
                        }
                        pairAccount = replacement
                        val replacementIndex = accounts.indexOf(replacement).coerceAtLeast(0)
                        accountAlias = "Cuenta-${replacementIndex + 1}"
                        aliasesUsed += accountAlias
                        outboundParameters = searchParameters(criteria, pairAccount.credential.apiKey)
                        logFlightDiagnostic("account_fallback_selected search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} account=$accountAlias cause=${failure.code.name}")
                    } else if (failure.code == FailureCode.QUOTA_INSUFFICIENT) {
                        pairAccount?.remaining = 0
                        val replacement = accounts.firstOrNull {
                            it !== pairAccount && it.account?.accountStatus.equals("Active", ignoreCase = true) && it.remaining > 0
                        }
                        if (replacement == null) {
                            batchStopReason = failure.code.name
                            break
                        }
                        pairAccount = replacement
                        val replacementIndex = accounts.indexOf(replacement).coerceAtLeast(0)
                        accountAlias = "Cuenta-${replacementIndex + 1}"
                        outboundParameters = searchParameters(criteria, pairAccount.credential.apiKey)
                        logFlightDiagnostic("account_fallback_selected search_id=$searchId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} account=$accountAlias cause=${failure.code.name}")
                    }
                    if (failure.code in setOf(FailureCode.NETWORK, FailureCode.SERVICE_UNAVAILABLE)) {
                        batchStopReason = failure.code.name
                        break
                    }
                } catch (failure: Exception) {
                    candidateFailures++
                    firstFailure = firstFailure ?: FailureCode.INVALID_RESPONSE.name
                    logFlightDiagnostic("return_candidate_failed search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} candidate=${candidateIndex + 1} error=INVALID_RESPONSE cause=${failure.javaClass.simpleName}")
                }
            }
            val pairIncomplete = orderedCandidates.size > pairCompleted || pairFailuresBefore < candidateFailures || malformed > pairMalformedBefore || pairHasMissingPrice
            if (pairIncomplete) incompleteIndices += pairIndex
            logFlightDiagnostic(
                "date_pair_finished search_id=$searchId run_id=$runId alert_id=${alert.id} pair=${pairIndex + 1}/${datePairs.size} " +
                    "candidates_available=${orderedCandidates.size} candidates_started=$pairStarted candidates_completed=$pairCompleted " +
                    "candidate_failures=${candidateFailures - pairFailuresBefore} offers_added=${offers.size - offersBeforePair} " +
                    "incomplete=$pairIncomplete gets=${budget.totalGets}",
            )
        }

        val uniqueOffers = offers.distinctBy { it.fingerprint }
        duplicateOffersRemoved = offers.size - uniqueOffers.size
        val sortedOffers = uniqueOffers.sortedBy { it.totalCop }
        val flexibleIncomplete = alert.flexibleDateSearch && (scannedIndices.distinct().size < datePairs.size || incompleteIndices.isNotEmpty())
        val candidateIncomplete = incompleteIndices.isNotEmpty()
        val status = when {
            sortedOffers.isNotEmpty() -> "VERIFICADO"
            scannedIndices.isEmpty() && firstFailure != null -> "ERROR"
            batchStopReason != null || candidateFailures > 0 -> "ERROR"
            scannedIndices.isNotEmpty() && outboundOptionsTotal == 0 && malformed == 0 -> "SIN_RESULTADOS_DEVUELTOS"
            candidatesConsidered == 0 && firstFailure != null -> "ERROR"
            else -> "NO_VERIFICADO"
        }
        val alertGets = budget.totalGets - getsAtStart
        val result = AlertSearchResult(
            alertId = alert.id,
            status = status,
            attemptAtMillis = attemptAt,
            networkReceivedAtMillis = receivedAt,
            error = firstFailure ?: if (status == "NO_VERIFICADO") "NO_VERIFIED_TOTAL" else null,
            offers = sortedOffers,
            airlines = airlines.toList(),
            candidatesConsidered = candidatesConsidered,
            candidateLimit = candidateLimitUsed,
            explorationLimited = flexibleIncomplete || candidateIncomplete,
            accountBalanceAtStart = accountBalanceAtStart,
            flightGetsStarted = alertGets,
            googleFlightsUrl = googleFlightsUrl,
            datePairIndex = orderedPairIndices.firstOrNull() ?: 0,
            datePairCount = datePairs.size,
            datePairScanned = scannedIndices.isNotEmpty(),
            candidateExplorationLimited = candidateIncomplete,
            datePairScannedIndices = scannedIndices.distinct(),
            datePairAttemptedIndices = attemptedIndices.distinct(),
            datePairIncompleteIndices = incompleteIndices.distinct(),
            candidatesCompleted = candidatesCompleted,
            candidateFailures = candidateFailures,
            accountAlias = aliasesUsed.joinToString(", ").ifBlank { null },
        )
        logFlightDiagnostic(
            "alert_review_outcome search_id=$searchId alert_id=${alert.id} status=$status " +
                "date_pairs_attempted=${attemptedIndices.distinct().joinToString(",").ifBlank { "none" }} " +
                "date_pairs_scanned=${scannedIndices.distinct().joinToString(",").ifBlank { "none" }} " +
                "date_pairs_incomplete=${incompleteIndices.distinct().joinToString(",").ifBlank { "none" }} total_date_pairs=${datePairs.size} " +
                "candidates_started=$candidatesConsidered candidates_completed=$candidatesCompleted candidate_failures=$candidateFailures " +
                "candidate_count=$candidateCountTotal duplicate_candidates_removed=$duplicateCandidatesRemoved duplicate_offers_removed=$duplicateOffersRemoved " +
                "malformed=$malformed flight_gets_started=$alertGets batch_gets=${budget.totalGets} " +
                "accounts=${aliasesUsed.joinToString(",").ifBlank { "none" }} error=${firstFailure ?: "none"} " +
                "offers=${sortedOffers.joinToString(";") { summarizeOffer(it) }.ifBlank { "none" }}",
        )
        return AlertPairScan(result, batchStopReason)
    }

    private fun batchFailure(
        alert: FlightAlertEntity,
        category: String,
        at: Long,
        accountBalance: Int?,
        gets: Int,
    ) = AlertSearchResult(
        alertId = alert.id,
        status = if (category == "EXPIRED") "EXPIRED" else "ERROR",
        attemptAtMillis = at,
        networkReceivedAtMillis = null,
        error = category,
        offers = emptyList(),
        airlines = emptyList(),
        candidatesConsidered = 0,
        candidateLimit = MAX_CANDIDATES_PER_ALERT,
        explorationLimited = false,
        accountBalanceAtStart = accountBalance,
        flightGetsStarted = gets,
        datePairIndex = if (alert.flexibleDateSearch) {
            val count = alert.datePairs(LocalDate.parse(alert.outboundDate)).size.coerceAtLeast(1)
            Math.floorMod(alert.datePairRotation, count)
        } else 0,
        datePairCount = if (alert.flexibleDateSearch) alert.datePairs(LocalDate.parse(alert.outboundDate)).size.coerceAtLeast(1) else 1,
    )

    private fun diverseCandidateOrder(options: List<com.flightnotifications.app.domain.model.FlightOption>, rotation: Int): List<com.flightnotifications.app.domain.model.FlightOption> {
        val sorted = options.sortedBy { it.priceCop ?: Long.MAX_VALUE }
        val firstByCarrier = sorted.groupBy { it.carriers.firstOrNull()?.canonicalCarrier().orEmpty() }
            .toSortedMap()
            .values
            .map { it.first() }
        val selected = firstByCarrier.toSet()
        val ordered = firstByCarrier + sorted.filterNot { it in selected }
        if (ordered.isEmpty()) return emptyList()
        val offset = Math.floorMod(rotation, ordered.size)
        return ordered.drop(offset) + ordered.take(offset)
    }

    /** Tokens can differ for the same visible itinerary; checking it twice would waste a billable GET. */
    private fun candidateFingerprint(option: com.flightnotifications.app.domain.model.FlightOption): String = buildString {
        append(option.type.orEmpty()).append('|').append(option.priceCop ?: "unknown").append('|')
        option.segments.forEach { segment ->
            append(segment.departure.code).append('>')
                .append(segment.arrival.code).append('@')
                .append(segment.departure.localDateTime ?: "unknown").append('/')
                .append(segment.arrival.localDateTime ?: "unknown").append(':')
                .append(segment.airline.orEmpty().trim().lowercase()).append(':')
                .append(segment.flightNumber.orEmpty().trim().uppercase()).append(';')
        }
    }

    private fun makeVerifiedOffer(
        alertId: String,
        outbound: com.flightnotifications.app.domain.model.FlightOption,
        returning: com.flightnotifications.app.domain.model.FlightOption?,
        totalCop: Long,
        criteria: SearchCriteria,
    ): VerifiedRoundTripOffer {
        fun convert(option: com.flightnotifications.app.domain.model.FlightOption) = option.segments.map { segment ->
            AlertFlightSegment(
                fromIata = segment.departure.code,
                toIata = segment.arrival.code,
                departureLocal = segment.departure.localDateTime?.toString(),
                arrivalLocal = segment.arrival.localDateTime?.toString(),
                airline = segment.airline.orEmpty().trim(),
                flightNumber = segment.flightNumber.orEmpty().trim(),
            )
        }
        val out = convert(outbound)
        val back = returning?.let(::convert).orEmpty()
        val identityData = buildString {
            append(alertId).append("|COP|")
            append(if (criteria.returnDate == null) "one-way" else "round-trip").append('|')
            append(criteria.adults18Plus).append('|').append(criteria.minors.joinToString(",") { "${it.age}:${it.infantSeating.orEmpty()}" }).append('|')
            (out + back).forEach { segment ->
                append(segment.fromIata.uppercase()).append('>').append(segment.toIata.uppercase()).append('|')
                append(segment.departureLocal.orEmpty()).append('|').append(segment.arrivalLocal.orEmpty()).append('|')
                append(segment.airline.canonicalCarrier()).append('|')
                append(segment.flightNumber.uppercase().replace(Regex("\\s+"), "")).append(';')
            }
        }
        val identity = "sha256:${sha256(identityData)}"
        val fingerprint = "sha256:${sha256("$identity|$totalCop")}"
        return VerifiedRoundTripOffer(
            outbound = out,
            returning = back,
            outboundAirline = out.mapNotNull { it.airline.takeIf(String::isNotBlank) }.distinct().joinToString(" + ").ifBlank { "Operador no informado" },
            returnAirline = back.mapNotNull { it.airline.takeIf(String::isNotBlank) }.distinct().joinToString(" + "),
            totalCop = totalCop,
            itineraryIdentity = identity,
            fingerprint = fingerprint,
        )
    }

    private fun String.canonicalCarrier(): String = trim().uppercase().replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun safeResponseFieldNames(body: JsonObject): String = body.keys
        .filter { field ->
            val normalized = field.lowercase()
            normalized !in SENSITIVE_RESPONSE_FIELDS && field.matches(SAFE_FIELD_NAME)
        }
        .sorted()
        .joinToString(",")
        .ifBlank { "none" }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeUnless { it == "null" }

    private fun JsonObject.nonNegativeInt(key: String, required: Boolean): Int? {
        val element = this[key]
        if (element == null) {
            if (required) throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
            return null
        }
        val number = (element as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        if (number < 0) throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        return number
    }

    companion object {
        const val MAX_GETS_PER_PROBE = 2
        const val MAX_CANDIDATES_PER_ALERT = 8
        const val MAX_GETS_PER_ALERT = 1 + MAX_CANDIDATES_PER_ALERT
        const val MAX_GETS_PER_BATCH = 20
        private val SENSITIVE_RESPONSE_FIELDS = setOf("api_key", "account_email", "account_id", "email", "user_id", "departure_token", "booking_token")
        private val QUOTA_ERROR = Regex("(?i)(out of (?:searches|credits)|(?:search|credit)[ -]?(?:quota|limit|balance).{0,40}(?:exhaust|limit|zero|reached)|no (?:searches|credits) left)")
        private val SAFE_FIELD_NAME = Regex("[A-Za-z0-9_.-]{1,50}")
    }
}
