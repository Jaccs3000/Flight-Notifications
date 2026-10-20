package com.flightnotifications.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
    fun startsWithNoSecretAndRequiresCredentialBeforeFlightAction() {
        compose.onNodeWithText("Alertas de vuelos").assertExists()
        compose.onNodeWithText("Configuración y diagnóstico").assertExists()
        compose.onNodeWithText("Ensayo CTG ↔ CLO").assertExists()
        compose.onNodeWithText("2027-01-25").assertExists()
        compose.onNodeWithText("2027-02-03").assertExists()
        compose.onNodeWithText("Probar vuelo de ida y vuelta").assertIsNotEnabled()
        compose.onNodeWithText("Clave privada").assertExists()
        compose.onNodeWithText("Registro de conexión").assertExists()
        compose.onNodeWithText("Registro de búsqueda de vuelos").assertExists()
        compose.onAllNodesWithText("Copiar").assertCountEquals(2)
        compose.onAllNodesWithText("Limpiar").assertCountEquals(2)
    }

    @Test
    fun alertFormCanCreateEditAndDeleteWithoutChangingTheSeededExample() {
        val originalName = "UI Test Trip"
        val editedName = "UI Test Updated"

        compose.onNodeWithTag("new-alert").performClick()
        compose.onNodeWithTag("alert-name").performTextInput(originalName)
        compose.onNodeWithTag("alert-origin").performTextInput("CTG")
        compose.onNodeWithTag("alert-destination").performTextInput("CLO")
        compose.onNodeWithTag("alert-name").assertTextContains(originalName)
        compose.onNodeWithTag("alert-origin").assertTextContains("CTG")
        compose.onNodeWithTag("alert-destination").assertTextContains("CLO")
        dismissKeyboard()
        compose.onNodeWithTag("save-alert").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(originalName).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("alert-form-error").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("alert-feedback-message").fetchSemanticsNodes().isNotEmpty()
        }
        val createdNodes = compose.onAllNodesWithText(originalName).fetchSemanticsNodes()
        if (createdNodes.isEmpty()) {
            val formError = compose.onAllNodesWithTag("alert-form-error").fetchSemanticsNodes()
                .firstOrNull()?.let { runCatching { it.config[SemanticsProperties.Text].joinToString() }.getOrNull() }
            val feedback = compose.onAllNodesWithTag("alert-feedback-message").fetchSemanticsNodes()
                .firstOrNull()?.let { runCatching { it.config[SemanticsProperties.Text].joinToString() }.getOrNull() }
            throw AssertionError("Expected saved alert; formError=$formError feedback=$feedback")
        }
        assertEquals(1, createdNodes.size)

        compose.onAllNodesWithText("Editar")[0].performClick()
        compose.onNodeWithTag("alert-name").performTextClearance()
        compose.onNodeWithTag("alert-name").performTextInput(editedName)
        dismissKeyboard()
        compose.onNodeWithTag("save-alert").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(editedName).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(originalName).assertCountEquals(0)

        compose.onAllNodesWithText("Eliminar")[0].performClick()
        compose.onNodeWithTag("confirm-delete-alert").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(editedName).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Cartagena ↔ Cali — marzo 2027").assertExists()
    }
}
