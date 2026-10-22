package com.flightnotifications.app.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightnotifications.app.ui.theme.FlightAppColors
import com.flightnotifications.app.ui.theme.FlightProbeTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun narrowFiltersKeepTheirLabelsCountsAndExclusiveSelection() {
        compose.setContent {
            var selected by remember { mutableStateOf(AlertListFilter.ACTIVE) }
            FlightProbeTheme {
                Column(Modifier.fillMaxSize().background(FlightAppColors.flightBlue).padding(20.dp)) {
                    AlertStatusFilters(selected, 12, 102, 114, onSelect = { selected = it }, modifier = Modifier.width(320.dp))
                }
            }
        }
        listOf("Activas", "Inactivas", "Todas", "12", "102", "114").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("filter-ACTIVE").assertIsSelected().assertHeightIsAtLeast(48.dp)
        screenshot("qa-filters-active.png")
        compose.onNodeWithTag("filter-INACTIVE").performClick().assertIsSelected()
        compose.onNodeWithTag("filter-ACTIVE").assertIsNotSelected()
        screenshot("qa-filters-inactive.png")
        compose.onNodeWithTag("filter-ALL").performClick().assertIsSelected()
        compose.onNodeWithTag("filter-INACTIVE").assertIsNotSelected()
        screenshot("qa-filters-all.png")
    }

    @Test
    fun bottomNavigationKeepsOneSelectedDestinationAndAllLabelsVisible() {
        compose.setContent {
            var selected by remember { mutableStateOf(MainTab.ALERTS) }
            FlightProbeTheme {
                Column(Modifier.fillMaxSize().background(FlightAppColors.flightBlue)) {
                    Spacer(Modifier.weight(1f))
                    FlightBottomNavigation(selected, onSelect = { selected = it })
                }
            }
        }
        compose.onNodeWithTag("nav-ALERTS").assertIsSelected()
        listOf("Alertas", "Actividad", "Ajustes").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        screenshot("qa-navigation-alerts.png")
        compose.onNodeWithTag("nav-ACTIVITY").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-ALERTS").assertIsNotSelected()
        screenshot("qa-navigation-activity.png")
        compose.onNodeWithTag("nav-SETTINGS").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-ACTIVITY").assertIsNotSelected()
        screenshot("qa-navigation-settings.png")
    }

    @Test
    fun compactAlertSwitchHandlesRepeatedActivationAndPauseWithAFullTouchTarget() {
        compose.setContent {
            var enabled by remember { mutableStateOf(false) }
            FlightProbeTheme {
                CompactAlertSwitch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    modifier = Modifier.testTag("compact-alert-switch"),
                )
            }
        }

        val switch = compose.onNodeWithTag("compact-alert-switch")
        switch.assertIsOff().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        repeat(40) { iteration ->
            switch.performClick()
            if (iteration % 2 == 0) switch.assertIsOn() else switch.assertIsOff()
        }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
