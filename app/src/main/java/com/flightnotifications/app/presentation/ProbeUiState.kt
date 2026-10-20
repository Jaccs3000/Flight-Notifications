package com.flightnotifications.app.presentation

import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.ProbeOutcome
import java.time.LocalDate

data class ProbeUiState(
    val credentialConfigured: Boolean = false,
    val outboundDate: LocalDate = LocalDate.of(2027, 1, 25),
    val returnDate: LocalDate = LocalDate.of(2027, 2, 3),
    val directOnly: Boolean = true,
    val localBudgetLimit: Int = 200,
    val account: AccountSnapshot? = null,
    val accountMessage: String? = null,
    val budget: LocalBudgetSnapshot? = null,
    val outcome: ProbeOutcome? = null,
    val errorMessage: String? = null,
    val isCheckingAccount: Boolean = false,
    val isRunningProbe: Boolean = false,
    val showFlightConfirmation: Boolean = false,
    val isDeletingCredential: Boolean = false,
    val accountDiagnosticLog: String = "",
    val flightDiagnosticLog: String = "",
)
