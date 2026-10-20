package com.flightnotifications.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.flightnotifications.app.presentation.ProbeScreen
import com.flightnotifications.app.presentation.ProbeViewModel
import com.flightnotifications.app.presentation.ProbeViewModelFactory
import com.flightnotifications.app.presentation.FlightAlertsViewModel
import com.flightnotifications.app.presentation.FlightAlertsViewModelFactory
import com.flightnotifications.app.ui.theme.FlightProbeTheme

class MainActivity : ComponentActivity() {
    private val probeViewModel: ProbeViewModel by viewModels {
        val container = (application as FlightProbeApplication).container
        ProbeViewModelFactory(container)
    }
    private val flightAlertsViewModel: FlightAlertsViewModel by viewModels {
        val container = (application as FlightProbeApplication).container
        FlightAlertsViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flightAlertsViewModel.setNotificationOpenAlert(intent.getStringExtra(EXTRA_ALERT_ID))
        setContent {
            FlightProbeTheme {
                ProbeScreen(probeViewModel, flightAlertsViewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        flightAlertsViewModel.setNotificationOpenAlert(intent.getStringExtra(EXTRA_ALERT_ID))
    }

    companion object {
        const val EXTRA_ALERT_ID = "flight_alert_id"
    }
}
