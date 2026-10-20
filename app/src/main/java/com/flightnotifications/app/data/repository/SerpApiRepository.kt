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
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.domain.validation.FlightOptionValidator
import com.flightnotifications.app.security.CredentialStore
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
    private val domesticAirportCodes: Set<String> = emptySet(),
) {
    suspend fun checkAccount(): AccountSnapshot = executionMutex.withLock {
        accountSnapshotWithKey(readApiKey())
    }

    suspend fun localBudget() = budgetRepository.snapshot()

    /** One account check, then a fair, serial batch. Each alert uses 1 initial GET + at most 3 token GETs. */
    suspend fun runAlertBatch(
        alerts: List<FlightAlertEntity>,
        maximumGets: Int = MAX_GETS_PER_BATCH,
        afterResults: suspend (List<AlertSearchResult>) -> Unit = {},
        selectAlerts: (suspend () -> List<FlightAlertEntity>)? = null,
    ): List<AlertSearchResult> = executionMutex.withLock {
        // Select under the same lock as the API calls. A worker may have been queued while a
        // manual review, edit, pause, or deletion changed which alerts are still actionable.
        val batchAlerts = selectAlerts?.invoke() ?: alerts
        if (batchAlerts.isEmpty()) return@withLock emptyList()
        val batchStarted = now().toEpochMilli()
        val apiKey: String
        val account: AccountSnapshot
        try {
            apiKey = readApiKey()
            account = accountSnapshotWithKey(apiKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProbeFailure) {
            logFlightDiagnostic("alert_batch_blocked category=${failure.code.name} flight_gets_started=0")
            val blocked = batchAlerts.map { batchFailure(it, failure.code.name, batchStarted, null, 0) }
            afterResults(blocked)
            return@withLock blocked
        }
        if (!account.accountStatus.equals("Active", ignoreCase = true)) {
            val blocked = batchAlerts.map { batchFailure(it, FailureCode.ACCOUNT_INACTIVE.name, batchStarted, account.conservativeSearchesLeft, 0) }
            afterResults(blocked)
            return@withLock blocked
        }
        var accountRemaining = account.conservativeSearchesLeft
            ?: run {
                val blocked = batchAlerts.map { batchFailure(it, FailureCode.ACCOUNT_INVALID.name, batchStarted, null, 0) }
                afterResults(blocked)
                return@withLock blocked
            }
        var localRemaining: Int
        try {
            val snapshot = budgetRepository.snapshot()
            if (!snapshot.clockTrusted) {
                val blocked = batchAlerts.map { batchFailure(it, FailureCode.CLOCK_UNTRUSTED.name, batchStarted, accountRemaining, 0) }
                afterResults(blocked)
                return@withLock blocked
            }
            localRemaining = snapshot.remainingInWindow
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val blocked = batchAlerts.map { batchFailure(it, FailureCode.LOCAL_QUOTA.name, batchStarted, accountRemaining, 0) }
            afterResults(blocked)
            return@withLock blocked
        }
        val allGets = intArrayOf(0)
        val results = mutableListOf<AlertSearchResult>()
        var batchStopReason: String? = null
        for (alert in batchAlerts) {
            if (batchStopReason != null) {
                results += batchFailure(alert, "BATCH_STOPPED_$batchStopReason", now().toEpochMilli(), accountRemaining, 0)
                continue
            }
            val attemptAt = now().toEpochMilli()
            val criteria = alert.toSearchCriteria()
            val invalid = when {
                criteria.validationError(LocalDate.now(SearchCriteria.COLOMBIA_ZONE)) != null -> FailureCode.INVALID_CRITERIA.name
                domesticAirportCodes.isNotEmpty() && (alert.originIata !in domesticAirportCodes || alert.destinationIata !in domesticAirportCodes) -> FailureCode.INVALID_CRITERIA.name
                LocalDate.now(SearchCriteria.COLOMBIA_ZONE).isAfter(criteria.outboundDate) -> "EXPIRED"
                else -> null
            }
            if (invalid != null) {
                results += batchFailure(alert, invalid, attemptAt, accountRemaining, 0)
                continue
            }
            if (accountRemaining < 2 || localRemaining < 2 || maximumGets - allGets[0] < 2) {
                results += batchFailure(alert, when {
                    accountRemaining < 2 -> FailureCode.QUOTA_INSUFFICIENT.name
                    localRemaining < 2 -> FailureCode.LOCAL_QUOTA.name
                    else -> "BATCH_BUDGET_REACHED"
                }, attemptAt, accountRemaining, 0)
                batchStopReason = if (accountRemaining < 2) FailureCode.QUOTA_INSUFFICIENT.name else if (localRemaining < 2) FailureCode.LOCAL_QUOTA.name else "BATCH_BUDGET_REACHED"
                continue
            }
            var alertGets = 0
            var receivedAt: Long? = null
            var airlines = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
            var outboundParsed = com.flightnotifications.app.domain.model.ParsedFlights(emptyList(), emptySet(), "", null, 0)
            var googleFlightsUrl: String? = null
            val offers = mutableListOf<VerifiedRoundTripOffer>()
            var candidatesConsidered = 0
            var candidatesCompleted = 0
            var candidateCount = 0
            var malformed = 0
            var failure: String? = null
            try {
                val runId = UUID.randomUUID().toString()
                val outboundParameters = searchParameters(criteria, apiKey)
                val outboundRoot = performFlightGet(RequestStage.OUTBOUND, runId, outboundParameters) {
                    allGets[0]++; alertGets++; accountRemaining--; localRemaining--
                }
                receivedAt = now().toEpochMilli()
                outboundParsed = parseSearchResponse(outboundRoot, outboundParameters)
                if (outboundParsed.responseStatus != "Success") throw ProbeFailure(FailureCode.SEARCH_NOT_READY)
                googleFlightsUrl = outboundParsed.googleFlightsUrl
                airlines += outboundParsed.airlinesDetected
                malformed += outboundParsed.malformedOptions
                val currentLocalDate = LocalDate.now(SearchCriteria.COLOMBIA_ZONE)
                val candidateOptions = outboundParsed.options
                    .filter { FlightOptionValidator.isOutbound(it, criteria) && isDomesticOption(it) && !it.departureToken.isNullOrBlank() }
                    .filter { option ->
                        val departure = option.segments.firstOrNull()?.departure?.localDateTime
                        departure != null && (criteria.outboundDate != currentLocalDate || departure.isAfter(LocalDateTime.now(SearchCriteria.COLOMBIA_ZONE)))
                    }
                val orderedCandidates = diverseCandidateOrder(candidateOptions, alert.candidateRotation)
                candidateCount = orderedCandidates.size
                val limit = minOf(MAX_CANDIDATES_PER_ALERT, orderedCandidates.size)
                for (candidate in orderedCandidates.take(limit)) {
                    if (accountRemaining < 1 || localRemaining < 1 || maximumGets - allGets[0] < 1) break
                    candidatesConsidered++
                    val returnParameters = outboundParameters + ("departure_token" to candidate.departureToken!!)
                    val returnRoot = performFlightGet(RequestStage.RETURN, runId, returnParameters) {
                        allGets[0]++; alertGets++; accountRemaining--; localRemaining--
                    }
                    receivedAt = now().toEpochMilli()
                    val returning = parseSearchResponse(returnRoot, returnParameters)
                    googleFlightsUrl = returning.googleFlightsUrl ?: googleFlightsUrl
                    if (returning.responseStatus != "Success") {
                        failure = "SEARCH_NOT_READY"
                        continue
                    }
                    candidatesCompleted++
                    airlines += returning.airlinesDetected
                    malformed += returning.malformedOptions
                    val validReturns = returning.options.filter {
                        FlightOptionValidator.isReturn(it, criteria) && isDomesticOption(it) && FlightOptionValidator.returnAfterOutbound(candidate, it)
                    }
                    val outboundPrice = candidate.priceCop
                    if (candidate.type.isRoundTripType() && outboundPrice != null) {
                        validReturns.forEach { returningOption ->
                            if (returningOption.type.isRoundTripType() && returningOption.priceCop == outboundPrice) {
                                offers += makeVerifiedOffer(alert.id, candidate, returningOption, outboundPrice)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: ProbeFailure) {
                failure = problem.code.name
                if (problem.code in setOf(FailureCode.NETWORK, FailureCode.RATE_LIMITED, FailureCode.FORBIDDEN, FailureCode.INVALID_CREDENTIAL, FailureCode.SERVICE_UNAVAILABLE)) {
                    batchStopReason = problem.code.name
                }
            } catch (_: Exception) {
                failure = FailureCode.INVALID_RESPONSE.name
            }
            val status = when {
                offers.isNotEmpty() -> "VERIFICADO"
                failure != null -> "ERROR"
                outboundParsed.options.isEmpty() && outboundParsed.malformedOptions == 0 -> "SIN_RESULTADOS_DEVUELTOS"
                candidatesConsidered == 0 -> "PARCIAL"
                else -> "NO_VERIFICADO"
            }
            results += AlertSearchResult(
                alertId = alert.id,
                status = status,
                attemptAtMillis = attemptAt,
                networkReceivedAtMillis = receivedAt,
                error = failure ?: if (status == "NO_VERIFICADO") "NO_VERIFIED_TOTAL" else null,
                offers = offers.distinctBy { it.fingerprint }.sortedBy { it.totalCop },
                airlines = airlines.toList(),
                candidatesConsidered = candidatesConsidered,
                candidateLimit = MAX_CANDIDATES_PER_ALERT,
                explorationLimited = candidateCount > candidatesCompleted,
                accountBalanceAtStart = account.conservativeSearchesLeft,
                flightGetsStarted = alertGets,
                googleFlightsUrl = googleFlightsUrl,
            )
            logFlightDiagnostic("alert_review_outcome alert_id=${alert.id.take(8)} status=$status candidates_started=$candidatesConsidered candidates_completed=$candidatesCompleted candidate_count=$candidateCount exploration_limited=${candidateCount > candidatesCompleted} flight_gets_started=$alertGets total_batch_gets=${allGets[0]} error=${failure ?: "none"}")
        }
        afterResults(results)
        results
    }

    /** Ejecuta solo tras el consentimiento de interfaz. No hay reintentos ni polling automáticos. */
    suspend fun runRoundTrip(criteria: SearchCriteria): ProbeOutcome = executionMutex.withLock {
        val validationError = criteria.validationError()
        logFlightDiagnostic("flight_probe_started origin=${criteria.origin} destination=${criteria.destination} outbound_date=${criteria.outboundDate} return_date=${criteria.returnDate} direct_only=${criteria.directOnly} max_gets=$MAX_GETS_PER_PROBE")
        if (validationError != null) {
            logFlightDiagnostic("flight_preflight_failed stage=criteria category=INVALID_CRITERIA reason=${validationError.replace(Regex("[^A-Za-z0-9 _-]"), " ").take(100)}")
            throw ProbeFailure(FailureCode.INVALID_CRITERIA)
        }
        val startedAt = now()
        val apiKey = readApiKey()
        val account = accountSnapshotWithKey(apiKey)
        if (!account.accountStatus.equals("Active", ignoreCase = true)) {
            logFlightDiagnostic("flight_preflight_failed stage=account category=ACCOUNT_INACTIVE")
            throw ProbeFailure(FailureCode.ACCOUNT_INACTIVE)
        }
        val officialCapacity = account.conservativeSearchesLeft
            ?: run {
                logFlightDiagnostic("flight_preflight_failed stage=account category=ACCOUNT_INVALID conservative_capacity=unknown")
                throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
            }
        if (officialCapacity < MAX_GETS_PER_PROBE) {
            logFlightDiagnostic("flight_preflight_failed stage=account category=QUOTA_INSUFFICIENT conservative_capacity=$officialCapacity required=$MAX_GETS_PER_PROBE")
            throw ProbeFailure(FailureCode.QUOTA_INSUFFICIENT)
        }
        val localBudget = budgetRepository.snapshot()
        if (!localBudget.clockTrusted) {
            logFlightDiagnostic("flight_preflight_failed stage=local_budget category=CLOCK_UNTRUSTED")
            throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
        }
        if (localBudget.remainingInWindow < MAX_GETS_PER_PROBE) {
            logFlightDiagnostic("flight_preflight_failed stage=local_budget category=LOCAL_QUOTA remaining=${localBudget.remainingInWindow} required=$MAX_GETS_PER_PROBE")
            throw ProbeFailure(FailureCode.LOCAL_QUOTA)
        }
        logFlightDiagnostic("flight_preflight_ok account_capacity=$officialCapacity local_remaining=${localBudget.remainingInWindow} clock_trusted=true")

        val runId = UUID.randomUUID().toString()
        val outboundParameters = searchParameters(criteria, apiKey)
        val outboundRoot = performFlightGet(RequestStage.OUTBOUND, runId, outboundParameters)
        val outboundParsed = parseSearchResponse(outboundRoot, outboundParameters)
        val outboundCompatible = outboundParsed.options.filter { FlightOptionValidator.isOutbound(it, criteria) }
        logFlightDiagnostic("flight_parse stage=outbound options=${outboundParsed.options.size} malformed=${outboundParsed.malformedOptions} route_date_candidates=${outboundCompatible.size} candidates_with_token=${outboundCompatible.count { !it.departureToken.isNullOrBlank() }} airlines=${outboundParsed.airlinesDetected.size}")
        val outboundCandidate = outboundParsed.options
            .asSequence()
            .filter { FlightOptionValidator.isOutbound(it, criteria) && !it.departureToken.isNullOrBlank() }
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
        val returnRoot = performFlightGet(RequestStage.RETURN, runId, returnParameters)
        val returnParsed = parseSearchResponse(returnRoot, returnParameters)
        val validReturns = returnParsed.options.filter {
            FlightOptionValidator.isReturn(it, criteria) && FlightOptionValidator.returnAfterOutbound(outboundCandidate, it)
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
        logFlightDiagnostic("flight_selection outbound_selected=true outbound_segments=${outboundCandidate.segments.size} outbound_price_present=${outboundCandidate.priceCop != null} return_selected=${selectedReturn != null} return_segments=${selectedReturn?.segments?.size ?: 0}")
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
        onReserved: () -> Unit = {},
    ): JsonObject {
        logFlightDiagnostic("flight_reservation stage=${stage.name.lowercase()} run_id=$runId")
        when (budgetRepository.reserve(stage, runId)) {
            BudgetReservationResult.RESERVED -> logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} status=RESERVED")
            BudgetReservationResult.LIMIT_REACHED -> {
                logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} status=LIMIT_REACHED")
                throw ProbeFailure(FailureCode.LOCAL_QUOTA)
            }
            BudgetReservationResult.CLOCK_UNTRUSTED -> {
                logFlightDiagnostic("flight_reservation_result stage=${stage.name.lowercase()} status=CLOCK_UNTRUSTED")
                throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
            }
        }
        onReserved()
        val safeCriteria = parameters.filterKeys { it !in SENSITIVE_PARAMETER_NAMES }
            .entries.joinToString(",") { "${it.key}=${it.value}" }
        logFlightDiagnostic("flight_request_started stage=${stage.name.lowercase()} endpoint=GET /search.json criteria={$safeCriteria}")
        val startedAt = System.nanoTime()
        val response = networkCall("flight_${stage.name.lowercase()}", flightDiagnosticLog) { flightClient.search(parameters) }
        logFlightDiagnostic(
            "flight_response stage=${stage.name.lowercase()} http=${response.code()} duration_ms=${elapsedMillis(startedAt)} " +
                "error_body_present=${response.errorBody() != null}",
        )
        return responseBody(response)
    }

    private suspend fun accountSnapshotWithKey(apiKey: String): AccountSnapshot {
        logAccountDiagnostic("account_request_started endpoint=GET /account.json api_key_attached=true")
        val startedAt = System.nanoTime()
        val response = networkCall("account", accountDiagnosticLog) { accountClient.account(apiKey) }
        logAccountDiagnostic(
            "account_response http=${response.code()} duration_ms=${elapsedMillis(startedAt)} " +
                "error_body_present=${response.errorBody() != null}",
        )
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

    private suspend fun readApiKey(): String {
        if (!safeCredentialConfigured()) throw ProbeFailure(FailureCode.NO_CREDENTIAL)
        return try {
            credentialStore.read().takeIf(String::isNotBlank) ?: throw ProbeFailure(FailureCode.NO_CREDENTIAL)
        } catch (failure: ProbeFailure) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ProbeFailure(FailureCode.CREDENTIAL_UNREADABLE)
        }
    }

    private suspend fun safeCredentialConfigured(): Boolean = try {
        credentialStore.isConfigured()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

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
            "adults", "travel_class", "currency", "gl", "hl", "stops", "sort_by", "departure_token",
        )
        keysToCheck.forEach { key ->
            val actual = reflected[key]?.let { element -> (element as? JsonPrimitive)?.content }
            val requested = expected[key]
            if (actual != null && requested != null && actual != requested) {
                throw ProbeFailure(FailureCode.INVALID_RESPONSE)
            }
        }
    }

    private fun searchParameters(criteria: SearchCriteria, apiKey: String): Map<String, String> = linkedMapOf(
        "engine" to "google_flights",
        "departure_id" to criteria.origin,
        "arrival_id" to criteria.destination,
        "outbound_date" to criteria.outboundDate.toString(),
        "return_date" to criteria.returnDate.toString(),
        "type" to "1",
        "adults" to "1",
        "travel_class" to "1",
        "currency" to "COP",
        "gl" to "co",
        "hl" to "es",
        "stops" to if (criteria.directOnly) "1" else "0",
        "sort_by" to "2",
        "api_key" to apiKey,
    )

    private suspend fun <T> networkCall(operation: String, log: DiagnosticLog?, call: suspend () -> T): T = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        logDiagnostic(log, "network_failure operation=$operation exception=${error.javaClass.simpleName}")
        throw ProbeFailure(FailureCode.NETWORK)
    } catch (error: HttpException) {
        logDiagnostic(log, "network_failure operation=$operation http=${error.code()}")
        throw failureForHttp(error.code())
    } catch (_: SerializationException) {
        logDiagnostic(log, "network_failure operation=$operation category=SERIALIZATION")
        throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    } catch (error: Exception) {
        logDiagnostic(log, "network_failure operation=$operation exception=${error.javaClass.simpleName}")
        throw ProbeFailure(FailureCode.NETWORK)
    }

    private fun <T> responseBody(response: Response<T>): T {
        if (!response.isSuccessful) throw failureForHttp(response.code())
        return response.body() ?: throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    }

    private fun failureForHttp(status: Int): ProbeFailure = ProbeFailure(
        when (status) {
            401 -> FailureCode.INVALID_CREDENTIAL
            403 -> FailureCode.FORBIDDEN
            429 -> FailureCode.RATE_LIMITED
            in 500..599 -> FailureCode.SERVICE_UNAVAILABLE
            else -> FailureCode.SERVICE_ERROR
        },
    )

    private suspend fun logAccountDiagnostic(event: String) = logDiagnostic(accountDiagnosticLog, event)

    private suspend fun logFlightDiagnostic(event: String) = logDiagnostic(flightDiagnosticLog, event)

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

    private fun FlightAlertEntity.toSearchCriteria(): SearchCriteria = SearchCriteria(
        outboundDate = LocalDate.parse(outboundDate),
        returnDate = LocalDate.parse(returnDate),
        directOnly = directOnly,
        origin = originIata.uppercase(),
        destination = destinationIata.uppercase(),
    )

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

    private fun isDomesticOption(option: com.flightnotifications.app.domain.model.FlightOption): Boolean =
        domesticAirportCodes.isEmpty() || option.segments.all { it.departure.code in domesticAirportCodes && it.arrival.code in domesticAirportCodes }

    private fun makeVerifiedOffer(
        alertId: String,
        outbound: com.flightnotifications.app.domain.model.FlightOption,
        returning: com.flightnotifications.app.domain.model.FlightOption,
        totalCop: Long,
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
        val back = convert(returning)
        val identityData = buildString {
            append(alertId).append("|COP|")
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
            returnAirline = back.mapNotNull { it.airline.takeIf(String::isNotBlank) }.distinct().joinToString(" + ").ifBlank { "Operador no informado" },
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
        const val MAX_CANDIDATES_PER_ALERT = 3
        const val MAX_GETS_PER_ALERT = 1 + MAX_CANDIDATES_PER_ALERT
        const val MAX_GETS_PER_BATCH = 20
        private val SENSITIVE_PARAMETER_NAMES = setOf("api_key", "departure_token", "booking_token", "access_token")
        private val SENSITIVE_RESPONSE_FIELDS = setOf("api_key", "account_email", "account_id", "email", "user_id", "departure_token", "booking_token")
        private val SAFE_FIELD_NAME = Regex("[A-Za-z0-9_.-]{1,50}")
    }
}
