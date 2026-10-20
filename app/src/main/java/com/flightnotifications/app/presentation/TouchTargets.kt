package com.flightnotifications.app.presentation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun Modifier.primaryButtonTarget(): Modifier = heightIn(min = 52.dp)

internal fun Modifier.secondaryButtonTarget(): Modifier = heightIn(min = 48.dp)

internal fun Modifier.chipTarget(): Modifier = heightIn(min = 48.dp)
