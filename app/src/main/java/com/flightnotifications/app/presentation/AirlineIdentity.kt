package com.flightnotifications.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.annotation.DrawableRes
import com.flightnotifications.app.R
import java.text.Normalizer
import java.util.Locale

internal data class AirlineBrand(
    val name: String,
    val code: String?,
    @DrawableRes val logo: Int?,
    val color: Color,
)

@Composable
internal fun AirlineIdentity(
    airline: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    prominent: Boolean = false,
    centered: Boolean = false,
    logoSize: Dp? = null,
) {
    val brands = remember(airline) { airlineBrands(airline) }
    val badgeSize = logoSize ?: if (compact) 28.dp else 32.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            brands.forEach { brand ->
                Surface(
                    modifier = Modifier.size(if (brands.size > 1) badgeSize * 0.8f else badgeSize),
                    shape = if (brand.logo != null) RoundedCornerShape(8.dp) else CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    if (brand.logo != null) {
                        Image(
                            painter = painterResource(brand.logo),
                            contentDescription = "Logo de ${brand.name}",
                            modifier = Modifier.fillMaxSize().padding(3.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(AppIcon.Flight, "Aerolínea ${brand.name}", modifier = Modifier.size(18.dp), tint = brand.color)
                        }
                    }
                }
            }
        }
        Text(
            text = airline,
            modifier = if (centered) Modifier else Modifier.weight(1f),
            color = if (brands.size == 1) brands.first().color else MaterialTheme.colorScheme.onSurface,
            style = when {
                prominent && brands.size > 1 -> MaterialTheme.typography.titleMedium
                prominent -> MaterialTheme.typography.titleLarge
                compact -> MaterialTheme.typography.bodySmall
                else -> MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (prominent) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = if (centered || prominent) 2 else 1,
            textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun airlineBrands(airline: String): List<AirlineBrand> = airline.split('/')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .ifEmpty { listOf("Aerolínea") }
    .map(::airlineBrand)
    .distinctBy { it.code ?: normalizeAirlineName(it.name) }

private fun normalizeAirlineName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

private fun airlineBrand(name: String): AirlineBrand {
    val normalized = normalizeAirlineName(name)
    fun brand(code: String, @DrawableRes logo: Int, color: Long) = AirlineBrand(name, code, logo, Color(color))
    return when {
        "avianca" in normalized || normalized == "av" -> brand("AV", R.drawable.airline_av, 0xFFC8102E)
        "jetsmart" in normalized || normalized in setOf("ja", "j6", "jz") -> brand("JA", R.drawable.airline_ja, 0xFF18284D)
        "latam" in normalized || normalized == "la" -> brand("LA", R.drawable.airline_la, 0xFF4B1C70)
        "wingo" in normalized || normalized == "p5" -> brand("P5", R.drawable.airline_p5, 0xFFB00068)
        "satena" in normalized || normalized == "9r" -> brand("9R", R.drawable.airline_9r, 0xFF075A9C)
        "clic" in normalized || "easyfly" in normalized || normalized == "ve" -> brand("VE", R.drawable.airline_ve, 0xFF59358C)
        "copa" in normalized || normalized == "cm" -> brand("CM", R.drawable.airline_cm, 0xFF07529A)
        "american" in normalized || normalized == "aa" -> brand("AA", R.drawable.airline_aa, 0xFF1764A0)
        "delta" in normalized || normalized == "dl" -> brand("DL", R.drawable.airline_dl, 0xFFAE1C3F)
        "united" in normalized || normalized == "ua" -> brand("UA", R.drawable.airline_ua, 0xFF145CA8)
        "spirit" in normalized || normalized == "nk" -> brand("NK", R.drawable.airline_nk, 0xFF665300)
        "iberia" in normalized || normalized == "ib" -> brand("IB", R.drawable.airline_ib, 0xFFB51E2E)
        else -> {
            val hue = (normalized.hashCode().toLong().and(0x7FFFFFFFL) % 360L).toFloat()
            AirlineBrand(name, null, null, Color.hsv(hue, 0.72f, 0.55f))
        }
    }
}
