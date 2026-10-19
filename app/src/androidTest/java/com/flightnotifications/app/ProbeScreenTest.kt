package com.flightnotifications.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProbeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsWithNoSecretAndRequiresCredentialBeforeFlightAction() {
        compose.onNodeWithText("Prueba de SerpApi").assertExists()
        compose.onNodeWithText("Ensayo CTG ↔ CLO").assertExists()
        compose.onNodeWithText("Probar vuelo de ida y vuelta").assertIsNotEnabled()
        compose.onNodeWithText("Clave privada").assertExists()
    }
}
