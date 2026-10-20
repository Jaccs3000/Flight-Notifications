package com.flightnotifications.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AirlineCatalogDialog(viewModel: FlightAlertsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aerolíneas nacionales · ${viewModel.airlineCatalog.reviewedAt}") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                viewModel.airlineCatalog.airlines.forEach { Text("• ${it.name} (${it.code})") }
                Text(viewModel.airlineCatalog.source, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Catálogo informativo, no exhaustivo. No filtra la búsqueda ni garantiza tarifas/cobertura; " +
                        "cualquier operador devuelto por SerpApi aparece en los resultados.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
