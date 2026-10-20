package com.flightnotifications.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FlightColors {
    val Background = Color(0xFFF6F8FA)
    val Surface = Color(0xFFFFFFFF)
    val Primary = Color(0xFF116879)
    val PrimaryDark = Color(0xFF0C5968)
    val OnSurface = Color(0xFF142632)
    val SecondaryText = Color(0xFF60727D)
    val Outline = Color(0xFFE0E6EB)
    val SoftTeal = Color(0xFFE3F2F3)
    val Success = Color(0xFF26744A)
    val SuccessSurface = Color(0xFFE5F3EA)
    val Error = Color(0xFF9D3535)
}

private val FlightScheme = lightColorScheme(
    primary = FlightColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FlightColors.SoftTeal,
    onPrimaryContainer = FlightColors.PrimaryDark,
    secondary = FlightColors.PrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = FlightColors.SoftTeal,
    onSecondaryContainer = FlightColors.PrimaryDark,
    tertiary = FlightColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = FlightColors.SuccessSurface,
    onTertiaryContainer = FlightColors.Success,
    background = FlightColors.Background,
    surface = FlightColors.Surface,
    onSurface = FlightColors.OnSurface,
    onSurfaceVariant = FlightColors.SecondaryText,
    surfaceVariant = Color(0xFFF0F3F6),
    surfaceTint = FlightColors.Primary,
    outline = FlightColors.Outline,
    outlineVariant = FlightColors.Outline,
    inversePrimary = Color(0xFF80CBD2),
    error = FlightColors.Error,
)

private val FlightTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val FlightShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun FlightProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlightScheme,
        typography = FlightTypography,
        shapes = FlightShapes,
        content = content,
    )
}
