package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

enum class HudNavDestination(val label: String, val icon: ImageVector) {
    HOME("HOME", Icons.Default.GraphicEq),
    CONTROL("CONTROL", Icons.Default.PhonelinkSetup),
    VISION("VISION", Icons.Default.Visibility),
    TOOLS("TOOLS", Icons.Default.Build)
}

@Composable
fun HudBottomNav(
    currentDestination: HudNavDestination,
    onNavigate: (HudNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(HudSurface, shape = RoundedCornerShape(16.dp))
            .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudNavDestination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                val color = if (isSelected) CyanPrimary else TextCyanMuted

                Column(
                    modifier = Modifier
                        .clickable { onNavigate(destination) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = destination.label,
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

