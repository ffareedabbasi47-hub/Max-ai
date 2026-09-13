package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun CallSecretaryScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    var dialNumber by remember { mutableStateOf("") }
    var activeCallSim by remember { mutableStateOf(false) }

    val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CALL SECRETARY & VIRTUAL DIALER",
            color = CyanPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dial Display Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dialNumber.ifEmpty { "Enter phone number..." },
                    color = if (dialNumber.isNotEmpty()) TextCyanLight else TextCyanMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                if (dialNumber.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = CyanPrimary,
                        modifier = Modifier.clickable {
                            dialNumber = dialNumber.dropLast(1)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Keypad Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(200.dp)
        ) {
            items(digits) { digit ->
                KeypadButton(
                    digit = digit,
                    onClick = { dialNumber += digit }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Place Call Action Button
        Button(
            onClick = {
                if (dialNumber.isNotEmpty()) {
                    viewModel.executePrompt("call $dialNumber")
                    activeCallSim = true
                } else {
                    viewModel.executePrompt("call Pepper Potts")
                    dialNumber = "+1 (555) 019-2831"
                    activeCallSim = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreenStatus, contentColor = Color.Black),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Call, contentDescription = "Call")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "PLACE COMMS LINK VIA MAX", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Call Secretary Assistant Transcript Monitor
        Text(
            text = "SECRETARY ASSISTANT LIVE TRANSCRIPTION",
            color = TextCyanMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                        contentDescription = "Secretary",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeCallSim) "COMMUNICATION LINK ACTIVE" else "SECRETARY STANDBY MODE",
                        color = if (activeCallSim) NeonGreenStatus else TextCyanMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        TranscriptBubble(speaker = "Caller", text = "Hello, is Mr. Stark available for a quick inquiry?", isCaller = true)
                    }
                    item {
                        TranscriptBubble(speaker = "MAX Secretary", text = "Greetings. Mr. Stark is currently unavailable. I am MAX, his AI Assistant. May I log a structured message or redirect your query?", isCaller = false)
                    }
                    item {
                        TranscriptBubble(speaker = "Caller", text = "Please let him know the flight stabilization telemetry is ready for review.", isCaller = true)
                    }
                    item {
                        TranscriptBubble(speaker = "MAX Secretary", text = "Message logged and transcribed to Document Vault. Protocol finalized.", isCaller = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    digit: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(HudSurface, shape = CircleShape)
            .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = TextCyanLight,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TranscriptBubble(
    speaker: String,
    text: String,
    isCaller: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isCaller) Alignment.Start else Alignment.End
    ) {
        Text(
            text = speaker,
            color = if (isCaller) CyanSecondary else NeonGreenStatus,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .background(if (isCaller) HudSurfaceVariant else Color(0xFF07213D), shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = text,
                color = TextCyanLight,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
