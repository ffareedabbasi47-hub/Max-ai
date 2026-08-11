package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.SystemTelemetry
import com.example.ui.components.HudBottomNav
import com.example.ui.components.HudHeader
import com.example.ui.components.HudNavDestination
import com.example.ui.screens.*
import com.example.ui.theme.HudBackground
import com.example.ui.theme.MAXTheme
import com.example.ui.viewmodel.MaxViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MAXTheme {
                val maxViewModel: MaxViewModel = viewModel()
                val telemetry by maxViewModel.systemTelemetry.collectAsState()

                var currentDestination by remember { mutableStateOf(HudNavDestination.HOME) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HudBackground),
                    topBar = {
                        HudHeader(telemetry = telemetry)
                    },
                    bottomBar = {
                        HudBottomNav(
                            currentDestination = currentDestination,
                            onNavigate = { currentDestination = it }
                        )
                    },
                    containerColor = HudBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(HudBackground)
                    ) {
                        when (currentDestination) {
                            HudNavDestination.HOME -> HomeScreen(viewModel = maxViewModel)
                            HudNavDestination.SYSTEM -> SystemControlScreen(viewModel = maxViewModel)
                            HudNavDestination.COMMS -> CommunicationScreen(viewModel = maxViewModel)
                            HudNavDestination.FILES -> FileManagerScreen(viewModel = maxViewModel)
                            HudNavDestination.CALLS -> CallSecretaryScreen(viewModel = maxViewModel)
                            HudNavDestination.SETTINGS -> SettingsScreen(viewModel = maxViewModel)
                        }
                    }
                }
            }
        }
    }
}
