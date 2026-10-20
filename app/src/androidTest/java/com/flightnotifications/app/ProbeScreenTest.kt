package com.flightnotifications.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProbeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun dismissKeyboard() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun waitForPersistedAlert(name: String) = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FlightProbeApplication
        withTimeout(10_000) {
            app.container.flightAlertRepository.alerts.first { alerts -> alerts.any { it.alert.name == name } }
                .first { it.alert.name == name }
                .alert
        }
    }

    @Test
    fun primaryNavigationOpensActivitySettingsAndDiagnosticsWithoutFlightRequests() {
        compose.onNodeWithText("Mis alertas").assertIsDisplayed()
        compose.onAllNodesWithTag("new-alert").assertCountEquals(1)
        compose.onNodeWithTag("nav-activity").performClick()
        compose.onNodeWithText("Avisos enviados").assertIsDisplayed()
        compose.onNodeWithText("La tarifa más baja de cada revisión.", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        compose.onNodeWithText("SerpApi").assertIsDisplayed()
        compose.onNodeWithTag("open-diagnostics").performScrollTo().performClick()

        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
        compose.onNodeWithTag("check-scheduling").assertIsDisplayed()
        compose.onNodeWithTag("check-scheduling").performClick()
        compose.onNodeWithText("No consume consultas de vuelos.").assertIsDisplayed()
        compose.onNodeWithText("Prueba manual de vuelos").performClick()
        compose.onNodeWithText("Prueba manual de vuelos").assertIsDisplayed()
        compose.onNodeWithTag("start-flight-probe").assertIsNotEnabled()
        compose.onNodeWithTag("log-tab-Vuelos").performClick()
        compose.onNodeWithTag("diagnostic-log-text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun alertCanBeCreatedEditedAndDeletedWithoutChangingSeededExample() {
        val originalName = "UI Test Trip"
        val editedName = "UI Test Updated"

        compose.onNodeWithTag("new-alert").performClick()
        compose.onNodeWithText("Nueva alerta").assertIsDisplayed()
        compose.onNodeWithTag("alert-name").performTextClearance()
        compose.onNodeWithTag("alert-name").performTextInput(originalName)
        compose.onNodeWithTag("alert-name").assertTextContains(originalName)
        dismissKeyboard()
        compose.onNodeWithTag("save-alert").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(originalName, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("alert-form-error").fetchSemanticsNodes().isNotEmpty()
        }
        val createError = compose.onAllNodesWithTag("alert-form-error").fetchSemanticsNodes()
            .firstOrNull()?.let { runCatching { it.config[SemanticsProperties.Text].joinToString() }.getOrNull() }
        check(createError == null) { "Alert form rejected creation: $createError" }
        val createdAlert = waitForPersistedAlert(originalName)

        compose.onNodeWithText(originalName, substring = true).performClick()
        compose.onNodeWithTag("alert-actions").performClick()
        compose.onNodeWithText("Editar alerta").performClick()
        compose.onNodeWithTag("alert-name").performTextClearance()
        compose.onNodeWithTag("alert-name").performTextInput(editedName)
        compose.onNodeWithTag("alert-name").assertTextContains(editedName)
        dismissKeyboard()
        compose.onNodeWithTag("save-alert").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save-alert").fetchSemanticsNodes().isEmpty() }
        val editedAlert = waitForPersistedAlert(editedName)
        check(editedAlert.id == createdAlert.id) { "Editing an alert created a duplicate instead of updating it." }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(editedName, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(editedName, substring = true).assertIsDisplayed()
        compose.onNodeWithText(editedName, substring = true).performClick()
        compose.onNodeWithTag("alert-actions").performClick()
        compose.onNodeWithText("Eliminar alerta").performClick()
        compose.onNodeWithTag("confirm-delete-alert").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(editedName, substring = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Cartagena ↔ Cali — marzo 2027").assertIsDisplayed()
    }

    @Test
    fun diagnosticsKeepsSeparateLogTabsAndManualFlightTestRequiresAKey() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("add-serpapi-key").performClick()
        compose.onNodeWithTag("serpapi-key-dialog-field").assertIsDisplayed()
        compose.onNodeWithTag("cancel-serpapi-key").performClick()
        compose.onAllNodesWithTag("serpapi-key-dialog-field").assertCountEquals(0)
        compose.onNodeWithTag("open-diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("Prueba manual de vuelos").performClick()
        compose.onNodeWithTag("start-flight-probe").assertIsNotEnabled()
        compose.onNodeWithTag("log-tab-Conexión").performClick()
        compose.onNodeWithTag("diagnostic-log-text").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("log-tab-Vuelos").performClick()
        compose.onNodeWithTag("diagnostic-log-text").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin API keys ni tokens.").performScrollTo().assertIsDisplayed()
    }
}
