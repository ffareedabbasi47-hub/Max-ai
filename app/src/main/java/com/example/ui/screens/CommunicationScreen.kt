package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AutoReplyEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun CommunicationScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val replies by viewModel.autoReplyList.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var inputSender by remember { mutableStateOf("") }
    var inputPlatform by remember { mutableStateOf("WHATSAPP") }
    var inputMessage by remember { mutableStateOf("") }
    var inputResponse by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUTONOMOUS COMMS & CHAT HUB",
                color = CyanPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "New Rule", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "INCOMING MESSAGES & AUTONOMOUS DRAFTS",
            color = TextCyanMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(replies) { item ->
                ReplyCard(
                    reply = item,
                    onDispatch = {
                        viewModel.dispatchAutoReply(item.id, item.sender, item.platform, item.generatedReply)
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "Configure Auto-Reply Rule",
                    color = CyanPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputSender,
                        onValueChange = { inputSender = it },
                        label = { Text("Sender Name / Contact", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inputPlatform == "WHATSAPP",
                            onClick = { inputPlatform = "WHATSAPP" },
                            label = { Text("WhatsApp") }
                        )
                        FilterChip(
                            selected = inputPlatform == "EMAIL",
                            onClick = { inputPlatform = "EMAIL" },
                            label = { Text("Email") }
                        )
                    }

                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        label = { Text("Incoming Trigger Message", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        )
                    )

                    OutlinedTextField(
                        value = inputResponse,
                        onValueChange = { inputResponse = it },
                        label = { Text("Autonomous AI Response Draft", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputSender.isNotBlank()) {
                            viewModel.createAutoReply(inputSender, inputPlatform, inputMessage, inputResponse)
                            showCreateDialog = false
                            inputSender = ""
                            inputMessage = ""
                            inputResponse = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Save Rule", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextCyanMuted)
                }
            },
            containerColor = HudSurface
        )
    }
}

@Composable
private fun ReplyCard(
    reply: AutoReplyEntity,
    onDispatch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudSurface, shape = RoundedCornerShape(12.dp))
            .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reply.platform == "WHATSAPP") Icons.AutoMirrored.Filled.Chat else Icons.Default.Email,
                        contentDescription = reply.platform,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reply.sender,
                        color = TextCyanLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = reply.status,
                    color = if (reply.status == "SENT") NeonGreenStatus else NeonAmberAlert,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "INCOMING: \"${reply.incomingMessage}\"",
                color = TextCyanMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "MAX AUTO-REPLY: \"${reply.generatedReply}\"",
                color = CyanSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onDispatch() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Dispatch ${reply.platform}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
