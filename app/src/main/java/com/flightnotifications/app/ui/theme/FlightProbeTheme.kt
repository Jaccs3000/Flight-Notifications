package com.flightnotifications.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val ProbeColors = lightColorScheme(
    primary = Color(0xFF116879),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF124B57),
    primaryContainer = Color(0xFFE3F2F3),
    secondary = Color(0xFF60727D),
    secondaryContainer = Color(0xFFE9EEF1),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    onSurface = Color(0xFF142632),
    onSurfaceVariant = Color(0xFF60727D),
    outline = Color(0xFFE0E6EB),
    outlineVariant = Color(0xFFE0E6EB),
    tertiary = Color(0xFF26744A),
    tertiaryContainer = Color(0xFFE5F3EA),
    error = Color(0xFF9D3535),
)

@Composable
fun FlightProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProbeColors,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
