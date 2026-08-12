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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MaxState
import com.example.ui.components.ArcReactorView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val command: String
)

@Composable
fun HomeScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val maxState by viewModel.maxState.collectAsState()
    val lastSpeechText by viewModel.lastSpeechText.collectAsState()
    val queryInput by viewModel.userInputQuery.collectAsState()
    val conversationMessages by viewModel.conversationMessages.collectAsState()
    val isFallbackActive by viewModel.isFallbackActive.collectAsState()
    val fallbackNotice by viewModel.fallbackNotice.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }

    val quickActions = listOf(
        QuickActionItem("Phone Control", "Wi-Fi, Mute, Volume", Icons.Default.PhonelinkSetup, "Turn on Wi-Fi"),
        QuickActionItem("Vision Assist", "Explain Screen", Icons.Default.Visibility, "Explain screen content"),
        QuickActionItem("File Vault", "Create / Read Files", Icons.Default.Folder, "Create note project_plan.txt"),
        QuickActionItem("Web Search", "Live AI Research", Icons.Default.Public, "Search recent technology news"),
        QuickActionItem("Direct Call", "Voice Links", Icons.Default.Call, "Call Pepper"),
        QuickActionItem("Auto Comms", "WhatsApp & Email", Icons.AutoMirrored.Filled.Send, "Open WhatsApp")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact MAX Animated Core
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcReactorView(
                maxState = maxState,
                onClick = { viewModel.toggleVoiceListening() }
            )
        }

        // State Indicator & Interruption Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val stateColor = when (maxState) {
                    MaxState.IDLE -> NeonGreenStatus
                    MaxState.LISTENING -> CyanPrimary
                    MaxState.PROCESSING -> NeonAmberAlert
                    MaxState.EXECUTING -> Color(0xFF00E5FF)
                    MaxState.SPEAKING -> CyanTertiary
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(stateColor, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STATE: ${maxState.name}",
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (maxState == MaxState.SPEAKING || maxState == MaxState.LISTENING) {
                Box(
                    modifier = Modifier
                        .background(NeonRedError, shape = RoundedCornerShape(12.dp))
                        .clickable { viewModel.stopAllAudioAndListening() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STOP",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    text = "Tap Core or say 'Max'",
                    color = TextCyanMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (isFallbackActive && fallbackNotice.isNotEmpty()) {
            Surface(
                color = NeonAmberAlert.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmberAlert),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = fallbackNotice,
                    color = NeonAmberAlert,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Spoken Output Display Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudSurface, shape = RoundedCornerShape(10.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaker",
                    tint = CyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lastSpeechText,
                    color = TextCyanLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recent Conversation Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONVERSATION STREAM",
                color = CyanPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "VIEW FULL CHAT",
                color = CyanSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { showHistoryDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(conversationMessages.takeLast(6)) { msg ->
                ChatBubbleRow(msg = msg)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Actions Grid
        Text(
            text = "QUICK SYSTEM ACTIONS",
            color = CyanPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickActions) { action ->
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .background(HudSurfaceVariant, shape = RoundedCornerShape(10.dp))
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
                        .clickable { viewModel.executePrompt(action.command) }
                        .padding(8.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.title,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = action.title,
                            color = TextCyanLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = action.subtitle,
                            color = TextCyanMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Command Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = {
                    Text(
                        text = "Ask MAX anything...",
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

            Spacer(modifier = Modifier.width(6.dp))

            // Mic Button
            IconButton(
                onClick = { viewModel.toggleVoiceListening() },
                modifier = Modifier
                    .size(44.dp)
                    .background(if (maxState == MaxState.LISTENING) NeonGreenStatus else CyanPrimary, shape = RoundedCornerShape(22.dp))
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
                    .size(44.dp)
                    .background(CyanSecondary, shape = RoundedCornerShape(22.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.Black
                )
            }
        }
    }

    // Full Chat History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Text(
                    text = "FULL CONVERSATION HISTORY",
                    color = CyanPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversationMessages) { msg ->
                        ChatBubbleRow(msg = msg)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("CLOSE", color = CyanPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = HudSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ChatBubbleRow(msg: ChatMessage) {
    val isUser = msg.sender == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (isUser) CyanPrimary.copy(alpha = 0.2f) else HudSurfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp
                    )
                )
                .border(
                    0.5.dp,
                    if (isUser) CyanPrimary else HudBorderCyan,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = msg.sender,
                    color = if (isUser) CyanPrimary else CyanSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    color = TextCyanLight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

