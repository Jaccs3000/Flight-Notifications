package com.flightnotifications.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.flightnotifications.app.presentation.ProbeScreen
import com.flightnotifications.app.presentation.ProbeViewModel
import com.flightnotifications.app.presentation.ProbeViewModelFactory
import com.flightnotifications.app.ui.theme.FlightProbeTheme

class MainActivity : ComponentActivity() {
    private val probeViewModel: ProbeViewModel by viewModels {
        val container = (application as FlightProbeApplication).container
        ProbeViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            FlightProbeTheme {
                ProbeScreen(probeViewModel)
            }
        }
    }
}
