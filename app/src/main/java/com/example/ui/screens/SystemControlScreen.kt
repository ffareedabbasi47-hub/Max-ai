package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.InstalledAppInfo
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun SystemControlScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.installedApps.collectAsState()
    val telemetry by viewModel.systemTelemetry.collectAsState()
    var appFilter by remember { mutableStateOf("") }

    val filteredApps = remember(apps, appFilter) {
        if (appFilter.isBlank()) apps else apps.filter { it.appName.contains(appFilter, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "SYSTEM & PHONE CONTROL HUB",
            color = CyanPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Telemetry Gauges
        com.example.ui.components.SystemStatsHud(telemetry = telemetry)

        Spacer(modifier = Modifier.height(10.dp))

        // System Toggles Panel

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleCard(
                title = "Wi-Fi Panel",
                icon = Icons.Default.Wifi,
                onClick = { viewModel.executePrompt("turn on Wi-Fi") },
                modifier = Modifier.weight(1f)
            )
            ToggleCard(
                title = "Bluetooth",
                icon = Icons.Default.Bluetooth,
                onClick = { viewModel.executePrompt("toggle Bluetooth") },
                modifier = Modifier.weight(1f)
            )
            ToggleCard(
                title = "Silent Mode",
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                onClick = { viewModel.executePrompt("mute phone") },
                modifier = Modifier.weight(1f)
            )
            ToggleCard(
                title = "Settings HUD",
                icon = Icons.Default.Settings,
                onClick = { viewModel.executePrompt("open settings") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Filter for Installed Apps
        OutlinedTextField(
            value = appFilter,
            onValueChange = { appFilter = it },
            placeholder = {
                Text(
                    text = "Search installed apps...",
                    color = TextCyanMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CyanPrimary
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = HudBorderCyan,
                focusedTextColor = TextCyanLight,
                unfocusedTextColor = TextCyanLight,
                focusedContainerColor = HudSurface,
                unfocusedContainerColor = HudSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "INSTALLED APPLICATION LAUNCHER (${filteredApps.size})",
            color = TextCyanMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Installed Applications Launcher Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredApps) { app ->
                AppGridCard(
                    app = app,
                    onLaunch = { viewModel.executePrompt("open ${app.appName}") }
                )
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(HudSurface, shape = RoundedCornerShape(12.dp))
            .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = CyanPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = TextCyanLight,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppGridCard(
    app: InstalledAppInfo,
    onLaunch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(HudSurface, shape = RoundedCornerShape(10.dp))
            .border(0.5.dp, HudBorderCyan.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .clickable { onLaunch() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = app.appName,
                tint = CyanSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.appName,
                color = TextCyanLight,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
