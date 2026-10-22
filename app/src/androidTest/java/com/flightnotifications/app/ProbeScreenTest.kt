package com.flightnotifications.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.isOn
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.KeyEvent
import com.flightnotifications.app.presentation.FlightAlertsViewModel
import com.flightnotifications.app.presentation.FlightAlertsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ProbeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun dismissKeyboard() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test
    fun primaryScreensAndEmptyStatesRender() {
        compose.onNodeWithText("Mis alertas").assertIsDisplayed()
        compose.onNodeWithText("Actividad").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Cuenta y conexión").assertIsDisplayed()
        compose.onNodeWithText("Presupuesto de consultas").assertIsDisplayed()
        compose.onNodeWithText("Herramientas y opciones").performScrollTo()
    }

    @Test
    fun airportAutocompleteSaveAndDeleteRefreshesTheAlertsList() {
        compose.onNodeWithTag("new-alert").performClick()
        compose.onNodeWithTag("alert-origin").performTextInput("car")
        compose.onNodeWithText("CARTAGENA DE INDIAS · CTG").performClick()
        compose.onNodeWithTag("alert-destination").performTextInput("CLO")
        compose.onNodeWithText("SANTIAGO DE CALI · CLO").performClick()
        compose.onNodeWithText("ALFONSO BONILLA ARAGON").assertIsDisplayed()

        compose.onNodeWithTag("alert-maximum").performScrollTo().performTextClearance()
        compose.onNodeWithTag("alert-maximum").performTextInput("567891")
        dismissKeyboard()
        compose.onNodeWithTag("alert-frequency").performScrollTo().performClick()
        compose.onNodeWithText("Cada 12 horas").performClick()
        compose.onNodeWithTag("save-alert").performScrollTo().performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("$ 567.891").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("$ 567.891").assertIsDisplayed()
        compose.onNodeWithText("Cada 12 h").assertIsDisplayed()
        assertTrue("La alerta recién guardada debe aparecer encendida", compose.onAllNodes(isOn()).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("Cartagena - Rafael Núñez (CTG)")[0].assertIsDisplayed()
        compose.onAllNodesWithText("Cali - Alfonso Bonilla Aragón (CLO)")[0].assertIsDisplayed()

        compose.onAllNodesWithText("Ver resultados")[0].performClick()
        compose.onNodeWithText("Último resultado").assertIsDisplayed()
        compose.onNodeWithText("Aún no hay resultados").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription("Más opciones")[0].performClick()
        compose.onNodeWithText("Eliminar alerta").performClick()
        compose.onNodeWithTag("confirm-delete-alert").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("$ 567.891").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Mis alertas").assertIsDisplayed()
    }

    @Test
    fun oneWayAlertCollectsEachMinorAgeAndInfantSeatingChoice() {
        val container = (compose.activity.application as FlightProbeApplication).container
        var savedAlertId: String? = null
        val existingAlertIds = runBlocking(Dispatchers.IO) { container.flightAlertRepository.currentAlerts().map { it.alert.id }.toSet() }
        try {
            compose.onNodeWithTag("new-alert").performClick()
            compose.onNodeWithTag("trip-one-way").performClick()
            compose.onNodeWithTag("trip-one-way").assertIsSelected()
            compose.onNodeWithTag("trip-round-trip").assertIsNotSelected()
            compose.onAllNodes(hasTestTag("alert-date-return")).assertCountEquals(0)
            compose.onNodeWithTag("adult-plus").performScrollTo().performClick()

            compose.onNodeWithTag("add-minor").performScrollTo().performClick()
            compose.onNodeWithTag("minor-age").performTextInput("7")
            compose.onNodeWithTag("minor-age").performImeAction()
            compose.onNodeWithTag("confirm-minor-age").performClick()
            compose.onNodeWithText("Niño de 7 años").assertIsDisplayed()

            compose.onNodeWithTag("add-minor").performScrollTo().performClick()
            compose.onNodeWithTag("minor-age").performTextInput("0")
            compose.onNodeWithTag("minor-age").performImeAction()
            compose.onNodeWithTag("infant-lap").assertIsDisplayed().performClick()
            compose.onNodeWithTag("confirm-minor-age").performClick()
            compose.onNodeWithText("Bebé de 0 años · en brazos").assertIsDisplayed()

            compose.onNodeWithTag("alert-origin").performScrollTo().performTextInput("CTG")
            compose.onNodeWithText("CARTAGENA DE INDIAS · CTG").performClick()
            compose.onNodeWithTag("alert-destination").performTextInput("CLO")
            compose.onNodeWithText("SANTIAGO DE CALI · CLO").performClick()
            compose.onNodeWithTag("save-alert").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                runBlocking(Dispatchers.IO) {
                    container.flightAlertRepository.currentAlerts().any { it.alert.id !in existingAlertIds && it.alert.returnDate.isBlank() }
                }
            }
            val saved = runBlocking(Dispatchers.IO) {
                container.flightAlertRepository.currentAlerts().first { it.alert.id !in existingAlertIds && it.alert.returnDate.isBlank() }.alert
            }
            savedAlertId = saved.id
            assertTrue(saved.enabled)
            assertTrue(saved.adults18Plus == 2)
            assertTrue(saved.minorPassengersJson.contains("\"age\":7"))
            assertTrue(saved.minorPassengersJson.contains("\"age\":0"))
            assertTrue(saved.minorPassengersJson.contains("\"infantSeating\":\"lap\""))
            compose.onNodeWithText("Solo ida", substring = true).assertIsDisplayed()
            compose.onNodeWithText("2 adultos · 1 niño · 1 bebé").assertIsDisplayed()
        } finally {
            savedAlertId?.let { id -> runBlocking(Dispatchers.IO) { container.flightAlertRepository.remove(id) } }
        }
    }

    @Test
    fun alertCanBePausedAndResumedRepeatedlyWhileFlightRequestsHoldTheirLock() {
        val container = (compose.activity.application as FlightProbeApplication).container
        val outbound = LocalDate.now().plusDays(30)
        val alert = container.flightAlertRepository.newAlert(
            name = "Prueba de alternancia",
            origin = "CTG",
            destination = "CLO",
            outbound = outbound,
            returning = outbound.plusDays(3),
            maximum = 500_000,
            frequency = 24,
            direct = true,
            enabled = true,
        )
        runBlocking(Dispatchers.IO) { container.flightAlertRepository.save(alert) }

        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, FlightAlertsViewModelFactory(container))
            .get(FlightAlertsViewModel::class.java)
        runBlocking(Dispatchers.IO) { container.executionMutex.lock() }
        try {
            repeat(20) {
                compose.activity.runOnUiThread { viewModel.setEnabled(alert.id, false) }
                awaitPersistedToggle(container, viewModel, alert.id, expectedEnabled = false)

                compose.activity.runOnUiThread { viewModel.setEnabled(alert.id, true) }
                awaitPersistedToggle(container, viewModel, alert.id, expectedEnabled = true)
            }
        } finally {
            runBlocking(Dispatchers.IO) {
                container.executionMutex.unlock()
                container.flightAlertRepository.remove(alert.id)
            }
            compose.activity.runOnUiThread { store.clear() }
        }
    }

    private fun awaitPersistedToggle(
        container: com.flightnotifications.app.di.AppContainer,
        viewModel: FlightAlertsViewModel,
        alertId: String,
        expectedEnabled: Boolean,
    ) {
        try {
            compose.waitUntil(10_000) {
                val pending = alertId in viewModel.state.value.pendingToggleIds
                val persisted = runBlocking(Dispatchers.IO) {
                    container.flightAlertRepository.find(alertId)?.enabled
                }
                !pending && persisted == expectedEnabled
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val persisted = runBlocking(Dispatchers.IO) { container.flightAlertRepository.find(alertId)?.enabled }
            throw AssertionError(
                "Toggle $alertId expected=$expectedEnabled persisted=$persisted pending=${alertId in viewModel.state.value.pendingToggleIds} error=${viewModel.state.value.error}",
                failure,
            )
        }
    }
}
