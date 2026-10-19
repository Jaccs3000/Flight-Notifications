package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.remote.FlightSearchClient
import com.flightnotifications.app.data.remote.SerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.ProbeOutcome
import com.flightnotifications.app.domain.model.RequestStage
import com.flightnotifications.app.domain.model.SearchCriteria
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
import java.util.UUID
import retrofit2.HttpException
import retrofit2.Response

enum class FailureCode {
    NO_CREDENTIAL,
    CREDENTIAL_UNREADABLE,
    NETWORK,
    INVALID_CREDENTIAL,
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
    private val now: () -> Instant = Instant::now,
) {
    suspend fun checkAccount(): AccountSnapshot = executionMutex.withLock {
        accountSnapshotWithKey(readApiKey())
    }

    suspend fun localBudget() = budgetRepository.snapshot()

    /** Ejecuta solo tras el consentimiento de interfaz. No hay reintentos ni polling automáticos. */
    suspend fun runRoundTrip(criteria: SearchCriteria): ProbeOutcome = executionMutex.withLock {
        criteria.validationError()?.let { throw ProbeFailure(FailureCode.INVALID_CRITERIA) }
        val startedAt = now()
        val apiKey = readApiKey()
        val account = accountSnapshotWithKey(apiKey)
        if (!account.accountStatus.equals("Active", ignoreCase = true)) {
            throw ProbeFailure(FailureCode.ACCOUNT_INACTIVE)
        }
        val officialCapacity = account.conservativeSearchesLeft
            ?: throw ProbeFailure(FailureCode.ACCOUNT_INVALID)
        if (officialCapacity < MAX_GETS_PER_PROBE) throw ProbeFailure(FailureCode.QUOTA_INSUFFICIENT)
        val localBudget = budgetRepository.snapshot()
        if (!localBudget.clockTrusted) throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
        if (localBudget.remainingInWindow < MAX_GETS_PER_PROBE) {
            throw ProbeFailure(FailureCode.LOCAL_QUOTA)
        }

        val runId = UUID.randomUUID().toString()
        val outboundParameters = searchParameters(criteria, apiKey)
        val outboundRoot = performFlightGet(RequestStage.OUTBOUND, runId, outboundParameters)
        val outboundParsed = parseSearchResponse(outboundRoot, outboundParameters)
        val outboundCandidate = outboundParsed.options
            .asSequence()
            .filter { FlightOptionValidator.isOutbound(it, criteria) && !it.departureToken.isNullOrBlank() }
            .sortedBy { it.priceCop ?: Long.MAX_VALUE }
            .firstOrNull()

        if (outboundCandidate == null) {
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
        val selectedReturn = validReturns.sortedBy { it.priceCop ?: Long.MAX_VALUE }.firstOrNull()
        val airlines = (outboundParsed.airlinesDetected + returnParsed.airlinesDetected)
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)

        val detail = when {
            validReturns.isEmpty() && returnParsed.options.isEmpty() && returnParsed.malformedOptions == 0 ->
                "La fuente no devolvió opciones de regreso asociadas a la salida seleccionada."
            validReturns.isEmpty() && returnParsed.options.isEmpty() ->
                "La respuesta de regreso incluyó opciones que no pudieron interpretarse."
            validReturns.isEmpty() ->
                "Se recibieron opciones de regreso, pero ninguna superó la validación de ruta, fecha y secuencia."
            else ->
                "Itinerario de ida y regreso detectado. El precio total permanece sin verificar hasta contrastar la respuesta real y su semántica."
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
            statusLabel = if (selectedReturn == null) "PARCIAL" else "NO_VERIFICADO",
            detail = detail,
            malformedOptions = outboundParsed.malformedOptions + returnParsed.malformedOptions,
        )
    }

    private suspend fun performFlightGet(
        stage: RequestStage,
        runId: String,
        parameters: Map<String, String>,
    ): JsonObject {
        when (budgetRepository.reserve(stage, runId)) {
            BudgetReservationResult.RESERVED -> Unit
            BudgetReservationResult.LIMIT_REACHED -> throw ProbeFailure(FailureCode.LOCAL_QUOTA)
            BudgetReservationResult.CLOCK_UNTRUSTED -> throw ProbeFailure(FailureCode.CLOCK_UNTRUSTED)
        }
        val response = networkCall { flightClient.search(parameters) }
        return responseBody(response)
    }

    private suspend fun accountSnapshotWithKey(apiKey: String): AccountSnapshot {
        val response = networkCall { accountClient.account(apiKey) }
        val body = responseBody(response)
        return try {
            parseAccount(body, now())
        } catch (failure: ProbeFailure) {
            throw failure
        } catch (_: Exception) {
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

    private fun parseSearchResponse(root: JsonObject, expected: Map<String, String>) = try {
        verifyReflectedCriteria(root, expected)
        responseParser.parse(root)
    } catch (failure: ProbeFailure) {
        throw failure
    } catch (error: IllegalArgumentException) {
        when (error.message) {
            "SEARCH_NOT_READY" -> throw ProbeFailure(FailureCode.SEARCH_NOT_READY)
            "SERVICE_ERROR" -> throw ProbeFailure(FailureCode.SERVICE_ERROR)
            else -> throw ProbeFailure(FailureCode.INVALID_RESPONSE)
        }
    } catch (_: Exception) {
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

    private suspend fun <T> networkCall(call: suspend () -> T): T = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        throw ProbeFailure(FailureCode.NETWORK)
    } catch (error: HttpException) {
        throw failureForHttp(error.code())
    } catch (_: SerializationException) {
        throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    } catch (_: Exception) {
        throw ProbeFailure(FailureCode.NETWORK)
    }

    private fun <T> responseBody(response: Response<T>): T {
        if (!response.isSuccessful) throw failureForHttp(response.code())
        return response.body() ?: throw ProbeFailure(FailureCode.INVALID_RESPONSE)
    }

    private fun failureForHttp(status: Int): ProbeFailure = ProbeFailure(
        when (status) {
            401, 403 -> FailureCode.INVALID_CREDENTIAL
            429 -> FailureCode.RATE_LIMITED
            in 500..599 -> FailureCode.SERVICE_UNAVAILABLE
            else -> FailureCode.SERVICE_ERROR
        },
    )

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeUnless { it == "null" }

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
    }
}
