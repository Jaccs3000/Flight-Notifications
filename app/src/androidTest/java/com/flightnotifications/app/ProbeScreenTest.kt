package com.flightnotifications.app

import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.KeyEvent
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
        compose.onNodeWithText("Activa").assertIsDisplayed()
        compose.onAllNodesWithText("Cartagena - Rafael Nuñez (CTG)")[0].assertIsDisplayed()
        compose.onAllNodesWithText("Cali - Alfonso Bonilla Aragon (CLO)")[0].assertIsDisplayed()

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
}
