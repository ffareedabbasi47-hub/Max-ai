package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudSurface
import com.example.ui.theme.TextCyanMuted
import com.example.ui.viewmodel.MaxViewModel

private enum class ToolSubTab(val label: String, val icon: ImageVector) {
    FILES("FILES", Icons.Default.Folder),
    COMMS("AUTO COMMS", Icons.AutoMirrored.Filled.Chat),
    CALLS("SECRETARY", Icons.AutoMirrored.Filled.PhoneCallback),
    SETTINGS("SETTINGS", Icons.Default.Settings)
}

@Composable
fun ToolsTabScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ToolSubTab.SETTINGS) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Sub-tab Navigation Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(HudSurface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolSubTab.entries.forEach { subTab ->
                    val isSelected = subTab == selectedTab
                    val color = if (isSelected) CyanPrimary else TextCyanMuted

                    Row(
                        modifier = Modifier
                            .clickable { selectedTab = subTab }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = subTab.icon,
                            contentDescription = subTab.label,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = subTab.label,
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Sub-tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                ToolSubTab.FILES -> FileManagerScreen(viewModel = viewModel)
                ToolSubTab.COMMS -> CommunicationScreen(viewModel = viewModel)
                ToolSubTab.CALLS -> CallSecretaryScreen(viewModel = viewModel)
                ToolSubTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
