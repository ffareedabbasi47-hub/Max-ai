package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.SystemTelemetry
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudSurface
import com.example.ui.theme.NeonAmberAlert
import com.example.ui.theme.NeonGreenStatus
import com.example.ui.theme.TextCyanLight
import com.example.ui.theme.TextCyanMuted

@Composable
fun SystemStatsHud(
    telemetry: SystemTelemetry,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(HudSurface, shape = RoundedCornerShape(12.dp))
            .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "REAL-TIME TELEMETRY",
                color = CyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // CPU Progress Bar
                val cpuProgress by animateFloatAsState(targetValue = telemetry.cpuUsagePct / 100f, label = "CpuProg")
                GaugeItem(
                    label = "CPU CORE",
                    value = "${telemetry.cpuUsagePct}%",
                    progress = cpuProgress,
                    color = if (telemetry.cpuUsagePct > 80) NeonAmberAlert else CyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // RAM Progress Bar
                val ramPct = if (telemetry.ramTotalMb > 0) (telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.toFloat()) else 0.45f
                val ramProgress by animateFloatAsState(targetValue = ramPct, label = "RamProg")
                GaugeItem(
                    label = "RAM LOAD",
                    value = "${telemetry.ramUsedMb}/${telemetry.ramTotalMb}MB",
                    progress = ramProgress,
                    color = CyanSecondary,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Battery Progress Bar
                val batProgress by animateFloatAsState(targetValue = telemetry.batteryLevel / 100f, label = "BatProg")
                GaugeItem(
                    label = "BATTERY",
                    value = "${telemetry.batteryLevel}%",
                    progress = batProgress,
                    color = NeonGreenStatus,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GaugeItem(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = TextCyanMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = TextCyanLight,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}
