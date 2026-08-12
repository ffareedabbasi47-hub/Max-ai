package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

                // Runtime Permissions Launcher
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Checked permissions
                }

                LaunchedEffect(Unit) {
                    val missing = permissionsToRequest.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

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
                            HudNavDestination.CONTROL -> SystemControlScreen(viewModel = maxViewModel)
                            HudNavDestination.VISION -> ScreenAssistScreen(viewModel = maxViewModel)
                            HudNavDestination.TOOLS -> ToolsTabScreen(viewModel = maxViewModel)
                        }

                    }
                }
            }
        }
    }
}
