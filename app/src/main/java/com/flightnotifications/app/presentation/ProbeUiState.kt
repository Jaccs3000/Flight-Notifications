package com.flightnotifications.app.presentation

import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.ProbeOutcome
import java.time.LocalDate

data class ProbeUiState(
    val credentialConfigured: Boolean = false,
    val outboundDate: LocalDate = LocalDate.of(2027, 3, 25),
    val returnDate: LocalDate = LocalDate.of(2027, 3, 28),
    val directOnly: Boolean = true,
    val account: AccountSnapshot? = null,
    val accountMessage: String? = null,
    val budget: LocalBudgetSnapshot? = null,
    val outcome: ProbeOutcome? = null,
    val errorMessage: String? = null,
    val isCheckingAccount: Boolean = false,
    val isRunningProbe: Boolean = false,
    val showFlightConfirmation: Boolean = false,
    val isDeletingCredential: Boolean = false,
)
