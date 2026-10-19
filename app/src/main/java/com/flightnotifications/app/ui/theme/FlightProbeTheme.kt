package com.flightnotifications.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ProbeColors = lightColorScheme(
    primary = Color(0xFF205C70),
    onPrimary = Color.White,
    secondary = Color(0xFF557A69),
    background = Color(0xFFF5F7F8),
    surface = Color.White,
    onSurface = Color(0xFF182126),
    error = Color(0xFF9D3535),
)

@Composable
fun FlightProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ProbeColors, content = content)
}
