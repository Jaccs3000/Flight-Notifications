package com.flightnotifications.app.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsOff
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertTextIsSingleLine("JetSMART")
        compose.onNodeWithText("$ 527.640").assertIsDisplayed()
        compose.onNodeWithText("Ida").assertIsDisplayed()
        compose.onNodeWithText("09:00").assertIsDisplayed()
        compose.onNodeWithText("Regreso").assertIsDisplayed()
        compose.onNodeWithText("11:00").assertIsDisplayed()
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

    @Test
    fun compactCardKeepsItsDatesAndOfferReadableWithoutOverlapping() {
        renderCard(320, 1f, verifiedAlert())
        screenshot("qa-card-320.png")
        assertCardLayout()
    }

    @Test
    fun regularCardAlignsTheOfferAndBothActionButtons() {
        renderCard(353, 1f, verifiedAlert())
        screenshot("qa-card-353.png")
        assertCardLayout(directLabel = "Vuelos directos")
        assertTextFits("Cartagena - Rafael Núñez (CTG)")
        assertTextFits("Pereira - Matecaña (PEI)")
        assertCardMatchesSketchHeaderAndOfferColumns()
    }

    @Test
    fun largerTextAndMixedAirlinesKeepPricesDatesAndControlsVisible() {
        renderCard(320, 1.3f, verifiedAlert(price = 1_527_640, outboundCarrier = "Avianca", returnCarrier = "LATAM", destination = "CLO"))
        screenshot("qa-card-large-text.png")
        assertCardLayout("$ 1.527.640", "Avianca / LATAM", fontScale = 1.3f)
        compose.onNodeWithContentDescription("Logo de Avianca").assertIsDisplayed()
        compose.onNodeWithContentDescription("Logo de LATAM").assertIsDisplayed()
    }

    @Test
    fun cardWithoutAnOfferKeepsThePendingStatusAndActionsVisible() {
        renderCard(320, 1f, verifiedAlert().copy(result = null))
        compose.onNodeWithText("Aún no hay una tarifa verificada").assertIsDisplayed()
        compose.onNodeWithTag("alert-offer-logo-qa", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("alert-review-logo-qa").assertIsDisplayed()
        compose.onNodeWithTag("alert-results-logo-qa").assertIsDisplayed()
        assertTextFits("25–28 mar 2027")
        screenshot("qa-card-pending.png")
    }

    @Test
    fun rearrangedCardStillDispatchesEachActionToItsOwnCallback() {
        val item = verifiedAlert()
        val airports = ColombianAirportCatalog(context)
        var reviews = 0
        var details = 0
        var edits = 0
        var deletions = 0
        compose.setContent {
            var enabled by remember { mutableStateOf(true) }
            FlightProbeTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    SketchAlertCard(item.copy(alert = item.alert.copy(enabled = enabled)), airports, expired = false, isRunning = false,
                        onOpenDetail = { details++ }, onEdit = { edits++ }, onDelete = { deletions++ },
                        onSearch = { reviews++ }, onToggle = { enabled = it })
                }
            }
        }
        compose.onNodeWithTag("toggle-alert-logo-qa").performClick().assertIsOff()
        compose.onNodeWithTag("alert-review-logo-qa").performClick()
        compose.onNodeWithTag("alert-results-logo-qa").performClick()
        compose.onNodeWithTag("alert-menu-logo-qa").performClick()
        compose.onNodeWithText("Editar alerta").performClick()
        compose.onNodeWithTag("alert-menu-logo-qa").performClick()
        compose.onNodeWithText("Eliminar alerta").performClick()
        compose.runOnIdle {
            assertEquals(1, reviews)
            assertEquals(1, details)
            assertEquals(1, edits)
            assertEquals(1, deletions)
        }
    }

    private fun renderCard(widthDp: Int, fontScale: Float, item: AlertWithResult) {
        val airports = ColombianAirportCatalog(context)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FlightProbeTheme {
                    Column(Modifier.fillMaxSize().background(FlightAppColors.flightBlue).padding(20.dp)) {
                        Column(Modifier.width(widthDp.dp)) {
                            SketchAlertCard(item, airports, expired = false, isRunning = false,
                                onOpenDetail = {}, onEdit = {}, onDelete = {}, onSearch = {}, onToggle = {})
                        }
                    }
                }
            }
        }
    }

    private fun assertCardLayout(
        priceText: String = "$ 527.640",
        airlineText: String = "JetSMART",
        fontScale: Float = 1f,
        directLabel: String = "Vuelos directos",
    ) {
        val price = bounds("alert-price-logo-qa")
        val airline = bounds("alert-airline-logo-qa")
        val outboundTime = bounds("alert-outbound-time-logo-qa")
        val returnTime = bounds("alert-return-time-logo-qa")
        val dates = bounds("alert-dates-logo-qa")
        val details = bounds("alert-details-logo-qa")
        val offer = bounds("alert-offer-logo-qa")
        val direct = bounds("alert-direct-logo-qa")
        val reviewMeta = bounds("alert-review-meta-logo-qa")
        assertTrue("La aerolínea debe quedar en la franja superior", airline.bottom <= details.top)
        assertTrue("Los horarios deben quedar debajo del precio", price.bottom <= outboundTime.top)
        assertTrue("Los horarios deben quedar debajo del precio", price.bottom <= returnTime.top)
        assertEquals(outboundTime.top, returnTime.top, 1f)
        assertTrue("La etiqueta de vuelos debe quedar arriba de Ver resultados", direct.bottom <= bounds("alert-results-logo-qa").top + 1f)
        assertEquals("Vuelos directos y última revisión deben compartir línea", reviewMeta.center.y, direct.center.y, 1f)
        assertTrue("Las fechas y la oferta no deben superponerse", details.right <= offer.left || details.bottom <= offer.top)
        assertTrue("Los horarios deben quedar dentro del panel de la oferta", offer.left <= outboundTime.left && outboundTime.right <= offer.right && offer.left <= returnTime.left && returnTime.right <= offer.right)
        assertEquals(bounds("alert-route-icon-logo-qa").left, bounds("alert-date-icon-logo-qa").left, 1f)
        assertEquals(bounds("alert-route-logo-qa").left, bounds("alert-date-text-logo-qa").left, 1f)
        assertEquals(bounds("toggle-alert-logo-qa").center.y, bounds("alert-menu-logo-qa").center.y, 1f)
        assertAlignedOrStacked(bounds("alert-budget-logo-qa"), bounds("alert-frequency-logo-qa"))
        assertAlignedOrStacked(bounds("alert-review-logo-qa"), bounds("alert-results-logo-qa"))
        listOf("25–28 mar 2027", "4 días · 1 adulto", "Tu máximo", "Revisión automática", "$ 350.000", "Cada 12 h", "Ver resultados", priceText, airlineText, "Ida", "Regreso", "09:00", "11:00").forEach(::assertTextFits)
        assertTextIsSingleLine(airlineText)
        if (fontScale <= 1.15f) assertTextFits(directLabel)
        listOf("Escalas permitidas", "Última tarifa observada · requiere nueva verificación", "Verificado").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        listOf("alert-airline", "alert-details", "alert-dates", "alert-offer", "alert-direct", "alert-budget", "alert-frequency", "alert-review", "alert-results").forEach {
            compose.onNodeWithTag("$it-logo-qa", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun assertCardMatchesSketchHeaderAndOfferColumns() {
        val card = bounds("alert-card-logo-qa")
        val header = bounds("alert-header-logo-qa")
        val details = bounds("alert-details-logo-qa")
        val offer = bounds("alert-offer-logo-qa")
        assertEquals("La franja de aerolínea debe llegar a los bordes de la tarjeta", card.left, header.left, 1f)
        assertEquals("La franja de aerolínea debe llegar a los bordes de la tarjeta", card.right, header.right, 1f)
        assertEquals("La franja debe empezar arriba de la tarjeta", card.top, header.top, 1f)
        assertTrue("La ruta y las fechas deben quedar a la izquierda de la oferta", details.right <= offer.left)
        assertTrue("La ruta y la oferta deben compartir el mismo panel", details.top < offer.bottom && offer.top < details.bottom)
        compose.onNodeWithText("Vuelos directos").assertIsDisplayed()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertAlignedOrStacked(first: Rect, second: Rect) {
        if (first.bottom <= second.top) {
            assertEquals(first.left, second.left, 1f)
            assertEquals(first.right, second.right, 1f)
        } else {
            assertTrue("Los elementos no deben superponerse", first.right <= second.left)
            assertEquals(first.top, second.top, 1f)
            assertEquals(first.bottom, second.bottom, 1f)
        }
    }

    private fun assertTextFits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Cached paragraphs can be wider than the final node even when every glyph fits.
        // Compare the glyph-run widths rather than cached paragraph alignment offsets.
        val lineWidths = layouts.map { layout ->
            "width=${layout.size.width},height=${layout.size.height},exceeded=${layout.multiParagraph.didExceedMaxLines},lines=" +
                (0 until layout.lineCount).joinToString { line ->
                    "${layout.getLineRight(line) - layout.getLineLeft(line)}/${layout.size.width},bottom=${layout.getLineBottom(line)},ellipsis=${layout.isLineEllipsized(line)}"
                }
        }
        assertTrue("Texto recortado: $text; medición: $lineWidths", layouts.isNotEmpty() && layouts.all { layout ->
            !layout.multiParagraph.didExceedMaxLines && (0 until layout.lineCount).all { line ->
                !layout.isLineEllipsized(line) &&
                    layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f &&
                    layout.getLineBottom(line) <= layout.size.height + 1f
            }
        })
    }

    private fun assertTextIsSingleLine(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$text debe ocupar una sola línea", layouts.isNotEmpty() && layouts.all { it.lineCount == 1 })
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun verifiedAlert(price: Long = 527_640, outboundCarrier: String = "JetSMART", returnCarrier: String = outboundCarrier, destination: String = "PEI"): AlertWithResult {
        val now = System.currentTimeMillis()
        val alert = FlightAlertEntity("logo-qa", "Ruta de prueba", "CTG", destination, "2027-03-25", "2027-03-28",
            350_000, 12, enabled = true, createdAt = now, updatedAt = now)
        val criteria = "CTG-$destination|2027-03-25|2027-03-28|direct=true"
        val offer = VerifiedRoundTripOffer(
            listOf(AlertFlightSegment("CTG", destination, "2027-03-25 09:00", "2027-03-25 10:15", outboundCarrier, "QA 100")),
            listOf(AlertFlightSegment(destination, "CTG", "2027-03-28 11:00", "2027-03-28 12:15", returnCarrier, "QA 101")),
            outboundCarrier, returnCarrier, price, "logo-qa", "logo-qa",
        )
        val result = FlightAlertResultEntity(
            alertId = alert.id, status = "VERIFICADO", lastAttemptAt = now,
            attemptCriteria = criteria, attemptMaximumCop = alert.maxTotalCop, networkReceivedAt = now,
            lastError = null, lastVerifiedTotalCop = offer.totalCop, lastVerifiedAt = now,
            verifiedCriteria = criteria, selectedOfferJson = Json.encodeToString(offer),
            airlinesJson = Json.encodeToString(listOf(outboundCarrier, returnCarrier).distinct()), candidatesConsidered = 3,
            candidateLimit = 3, explorationLimited = true, accountBalanceAtStart = 100, flightGetsStarted = 4,
        )
        return AlertWithResult(alert, result)
    }
}
