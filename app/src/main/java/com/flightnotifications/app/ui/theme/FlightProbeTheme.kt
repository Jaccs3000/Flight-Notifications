package com.flightnotifications.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProbeColors = lightColorScheme(
    primary = Color(0xFF008D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF7EE),
    onPrimaryContainer = Color(0xFF075A58),
    secondary = Color(0xFF657486),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0F7),
    onSecondaryContainer = Color(0xFF23344D),
    background = Color(0xFF0753D7),
    onBackground = Color.White,
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172442),
    surfaceVariant = Color(0xFFF0F4F8),
    onSurfaceVariant = Color(0xFF536579),
    outline = Color(0xFF9FAEBD),
    outlineVariant = Color(0xFFD9E1E9),
    tertiary = Color(0xFF16845B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F6ED),
    onTertiaryContainer = Color(0xFF145B3D),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBEDEC),
    onErrorContainer = Color(0xFF5F1411),
)

private val ProbeTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

object FlightAppColors {
    val flightBlue = Color(0xFF0753D7)
    val flightBlueDark = Color(0xFF0648C7)
    val ink = Color(0xFF172442)
    val teal = Color(0xFF008D91)
    val tealDark = Color(0xFF08746F)
    val mintHeader = Color(0xFFD9F7EC)
    val mintIcon = Color(0xFFBDEBDD)
    val paleBlue = Color(0xFFE9F6FC)
    val paleLavender = Color(0xFFF1EDFA)
    val paleYellow = Color(0xFFFFF5DC)
    val warningContainer = Color(0xFFFFF2D5)
    val onWarningContainer = Color(0xFF6D531D)
}

@Composable
fun FlightProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProbeColors,
        typography = ProbeTypography,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
