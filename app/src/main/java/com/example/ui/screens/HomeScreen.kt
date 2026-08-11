package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CommandLogEntity
import com.example.data.model.MaxState
import com.example.ui.components.ArcReactorView
import com.example.ui.components.SystemStatsHud
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun HomeScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val maxState by viewModel.maxState.collectAsState()
    val lastSpeechText by viewModel.lastSpeechText.collectAsState()
    val queryInput by viewModel.userInputQuery.collectAsState()
    val telemetry by viewModel.systemTelemetry.collectAsState()
    val logs by viewModel.commandLogs.collectAsState()

    val quickCommands = listOf(
        "System Diagnostic",
        "Open WhatsApp",
        "Turn on Wi-Fi",
        "Create file alpha.txt",
        "Draft email to Stark",
        "Call Pepper Potts",
        "Search quantum AI news"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // System Stats Bar
        SystemStatsHud(telemetry = telemetry)

        Spacer(modifier = Modifier.height(12.dp))

        // Arc Reactor HUD Core
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ArcReactorView(
                maxState = maxState,
                onClick = { viewModel.toggleVoiceListening() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Spoken Output Display Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaker",
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lastSpeechText,
                    color = TextCyanLight,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Command Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCommands) { cmd ->
                Box(
                    modifier = Modifier
                        .background(HudSurfaceVariant, shape = RoundedCornerShape(20.dp))
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                        .clickable { viewModel.executePrompt(cmd) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cmd,
                        color = TextCyanLight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Command Prompt Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = {
                    Text(
                        text = "Command MAX or speak...",
                        color = TextCyanMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = HudBorderCyan,
                    focusedTextColor = TextCyanLight,
                    unfocusedTextColor = TextCyanLight,
                    focusedContainerColor = HudSurface,
                    unfocusedContainerColor = HudSurface
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Mic Button
            IconButton(
                onClick = { viewModel.toggleVoiceListening() },
                modifier = Modifier
                    .size(48.dp)
                    .background(if (maxState == MaxState.LISTENING) NeonGreenStatus else CyanPrimary, shape = RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = if (maxState == MaxState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = { viewModel.executePrompt(queryInput) },
                modifier = Modifier
                    .size(48.dp)
                    .background(CyanSecondary, shape = RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Command Log History Stream
        Text(
            text = "COMMAND LOG HISTORY",
            color = CyanPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs) { log ->
                LogItemRow(log = log)
            }
        }
    }
}

@Composable
private fun LogItemRow(log: CommandLogEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudSurface, shape = RoundedCornerShape(8.dp))
            .border(0.5.dp, HudBorderCyan.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "> ${log.prompt}",
                    color = TextCyanLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = log.actionType,
                    color = CyanSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = log.response,
                color = TextCyanMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
