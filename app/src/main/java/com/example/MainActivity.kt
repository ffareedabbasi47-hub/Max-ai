package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.example.system.MaxWakeService
import com.example.ui.components.HudBottomNav
import com.example.ui.components.HudHeader
import com.example.ui.components.HudNavDestination
import com.example.ui.screens.*
import com.example.ui.theme.HudBackground
import com.example.ui.theme.MAXTheme
import com.example.ui.viewmodel.MaxViewModel

class MainActivity : ComponentActivity() {

    private var maxViewModelInstance: MaxViewModel? = null

    // A wake intent can arrive in onCreate before the ViewModel exists; hold it and deliver once.
    private var pendingWake = false
    private var pendingGreeting = false

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MaxWakeService.ACTION_WAKE_WORD_DETECTED) {
                // De-duplicated inside the ViewModel (the wake service also sends an activity intent).
                maxViewModelInstance?.onWakeDetected(
                    greeting = intent.getBooleanExtra(MaxWakeService.EXTRA_GREETING, false)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            wakeReceiver,
            IntentFilter(MaxWakeService.ACTION_WAKE_WORD_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        intent?.let { handleIntent(it) }

        setContent {
            MAXTheme {
                val maxViewModel: MaxViewModel = viewModel()
                maxViewModelInstance = maxViewModel

                LaunchedEffect(Unit) {
                    if (pendingWake) {
                        pendingWake = false
                        maxViewModel.onWakeDetected(greeting = pendingGreeting)
                    }
                }

                val telemetry by maxViewModel.systemTelemetry.collectAsState()
                var currentDestination by remember { mutableStateOf(HudNavDestination.HOME) }

                // Check accessibility status when resumed
                val context = LocalContext.current
                DisposableEffect(Unit) {
                    maxViewModel.checkAccessibilityStatus(context)
                    onDispose {}
                }

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
                ) { _ ->
                    // Whatever was granted just now might be exactly what wake word was waiting for.
                    maybeAutoStartWake(context, maxViewModel)
                }

                LaunchedEffect(Unit) {
                    val missing = permissionsToRequest.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    } else {
                        // Nothing to ask for — permissions were already granted earlier, so this is
                        // the only place auto-start would otherwise be triggered from.
                        maybeAutoStartWake(context, maxViewModel)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Root-cause fix for "Settings shows wake word ACTIVE but it never actually starts": that
     * label used to be a local UI boolean with no link to the real service, defaulting to true on
     * every screen open. The service was never auto-started, so on a fresh app launch (or after
     * the OS kills it) "Max"/"Hey Max" did nothing despite the screen claiming otherwise. This
     * restarts the REAL service on launch if the user had it on and MAX actually has what it needs.
     */
    private fun maybeAutoStartWake(context: Context, viewModel: MaxViewModel) {
        val prefs = context.getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("wake_service_enabled", false)) return
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasMic && hasNotif) viewModel.toggleBackgroundWakeService(context, true, announce = false)
        // If permission is still missing, stay quiet here — the Settings screen's real-time
        // status (WakeStatus) tells the user why the moment they open it, instead of an
        // unexplained failure sound on every app launch.
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("WAKE_WORD_TRIGGERED", false)) {
            // Consume the extra so a re-delivered intent (rotation, recents) can't re-trigger.
            intent.removeExtra("WAKE_WORD_TRIGGERED")
            val greeting = intent.getBooleanExtra(MaxWakeService.EXTRA_GREETING, false)
            intent.removeExtra(MaxWakeService.EXTRA_GREETING)
            val vm = maxViewModelInstance
            if (vm != null) {
                vm.onWakeDetected(greeting = greeting)
            } else {
                pendingWake = true
                pendingGreeting = greeting
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(wakeReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}

