package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.MaxAccessibilityService
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun ScreenAssistScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAccessibilityActive by remember { mutableStateOf(MaxAccessibilityService.isEnabled()) }
    var screenSummaryText by remember { mutableStateOf("Tap 'Analyze Screen' or 'Read Screen' to inspect current UI.") }
    var searchQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Screen Assist Engine Ready.") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "MAX VISION & SCREEN ASSIST",
                        color = CyanPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isAccessibilityActive) "ACCESSIBILITY ENGINE ACTIVE" else "ACCESSIBILITY SERVICE REQUIRED",
                        color = if (isAccessibilityActive) NeonGreenStatus else NeonAmberAlert,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccessibilityActive) HudSurfaceVariant else CyanPrimary,
                        contentColor = if (isAccessibilityActive) TextCyanLight else androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isAccessibilityActive) "Settings" else "Enable Service",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Screen Assist Controls Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isAccessibilityActive = MaxAccessibilityService.isEnabled()
                    val summary = MaxAccessibilityService.instance?.getScreenTextSummary()
                    if (summary != null) {
                        screenSummaryText = summary
                        statusMessage = "Screen text scanned, Boss!"
                        viewModel.executePrompt("explain screen content")
                    } else {
                        screenSummaryText = "Accessibility service is not enabled. Please enable MAX Screen Assist in Accessibility Settings."
                        statusMessage = "Service disabled."
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = androidx.compose.ui.graphics.Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Explain Screen", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Button(
                onClick = {
                    isAccessibilityActive = MaxAccessibilityService.isEnabled()
                    val summary = MaxAccessibilityService.instance?.getScreenTextSummary()
                    if (summary != null) {
                        screenSummaryText = summary
                        statusMessage = "Reading screen content..."
                        viewModel.executePrompt("read screen text aloud")
                    } else {
                        statusMessage = "Accessibility service required."
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary, contentColor = androidx.compose.ui.graphics.Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Read Aloud", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search/Find Button Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search text/button on screen...", color = TextCyanMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
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
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        val clicked = MaxAccessibilityService.instance?.clickElementByText(searchQuery) ?: false
                        statusMessage = if (clicked) "Clicked element '$searchQuery', Boss!" else "Element '$searchQuery' not found on active window."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreenStatus, contentColor = androidx.compose.ui.graphics.Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Find & Tap", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Message Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudSurfaceVariant, shape = RoundedCornerShape(8.dp))
                .border(0.5.dp, HudBorderCyan.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "> STATUS: $statusMessage",
                color = CyanSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Screen Summary Content Window
        Text(
            text = "PARSED SCREEN ELEMENTS HUD",
            color = CyanPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = screenSummaryText,
                        color = TextCyanLight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
