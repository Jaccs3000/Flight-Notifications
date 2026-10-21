package com.flightnotifications.app.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.FlightAlertResultEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.model.AlertFlightSegment
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.ui.theme.FlightAppColors
import com.flightnotifications.app.ui.theme.FlightProbeTheme
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirlineLogoTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun savedVerifiedAlertDisplaysTheJetSmartLogoAndKeepsItsPrice() {
        val item = verifiedAlert()
        val airports = ColombianAirportCatalog(context)
        compose.setContent {
            FlightProbeTheme {
                Column(Modifier.fillMaxSize().background(FlightAppColors.flightBlue).padding(16.dp)) {
                    SketchAlertCard(item, airports, expired = false, isRunning = false,
                        onOpenDetail = {}, onEdit = {}, onDelete = {}, onSearch = {}, onToggle = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Logo de JetSMART").assertIsDisplayed()
        compose.onNodeWithText("JetSMART").assertIsDisplayed()
        compose.onNodeWithText("Verificado").assertIsDisplayed()
        compose.onNodeWithText("$ 527.640").assertIsDisplayed()
        compose.onAllNodesWithText("JS").assertCountEquals(0)
        screenshot("qa-verified-jetsmart.png")
    }

    @Test
    fun everyBundledLogoDecodesAndUnknownCarriersKeepTheirName() {
        val carriers = listOf("Avianca", "JetSMART", "LATAM Colombia", "Wingo", "SATENA", "Clic Air",
            "Copa Airlines", "American Airlines", "Delta", "United Airlines", "Spirit", "Iberia")
        compose.setContent {
            FlightProbeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        carriers.forEach { AirlineIdentity(it, Modifier.fillMaxWidth(), compact = true) }
                        AirlineIdentity("Aerolínea nueva", Modifier.fillMaxWidth(), compact = true)
                    }
                }
            }
        }
        carriers.forEach { compose.onNodeWithContentDescription("Logo de $it").assertIsDisplayed() }
        compose.onNodeWithContentDescription("Aerolínea Aerolínea nueva").assertIsDisplayed()
        compose.onNodeWithText("Aerolínea nueva").assertIsDisplayed()
        screenshot("qa-airline-logos.png")
    }

    @Test
    fun mixedCarriersDisplayBothLogosWithoutDuplicatingTheSameBrand() {
        compose.setContent {
            FlightProbeTheme {
                Surface {
                    AirlineIdentity("JetSMART / jetsmart / Avianca", Modifier.fillMaxWidth().padding(16.dp), compact = true)
                }
            }
        }
        compose.onAllNodesWithContentDescription("Logo de JetSMART").assertCountEquals(1)
        compose.onNodeWithContentDescription("Logo de Avianca").assertIsDisplayed()
        screenshot("qa-mixed-airlines.png")
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun verifiedAlert(): AlertWithResult {
        val now = System.currentTimeMillis()
        val alert = FlightAlertEntity("logo-qa", "Cartagena a Pereira", "CTG", "PEI", "2027-03-25", "2027-03-28",
            350_000, 12, enabled = true, createdAt = now, updatedAt = now)
        val criteria = "CTG-PEI|2027-03-25|2027-03-28|direct=true"
        val offer = VerifiedRoundTripOffer(
            listOf(AlertFlightSegment("CTG", "PEI", "2027-03-25 09:00", "2027-03-25 10:15", "JetSMART", "JA 100")),
            listOf(AlertFlightSegment("PEI", "CTG", "2027-03-28 11:00", "2027-03-28 12:15", "JetSMART", "JA 101")),
            "JetSMART", "JetSMART", 527_640, "logo-qa", "logo-qa",
        )
        val result = FlightAlertResultEntity(
            alertId = alert.id, status = "VERIFICADO", lastAttemptAt = now,
            attemptCriteria = criteria, attemptMaximumCop = alert.maxTotalCop, networkReceivedAt = now,
            lastError = null, lastVerifiedTotalCop = offer.totalCop, lastVerifiedAt = now,
            verifiedCriteria = criteria, selectedOfferJson = Json.encodeToString(offer),
            airlinesJson = Json.encodeToString(listOf("JetSMART")), candidatesConsidered = 3,
            candidateLimit = 3, explorationLimited = true, accountBalanceAtStart = 100, flightGetsStarted = 4,
        )
        return AlertWithResult(alert, result)
    }
}
