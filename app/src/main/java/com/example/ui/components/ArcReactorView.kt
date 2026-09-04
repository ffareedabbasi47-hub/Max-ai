package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MaxState
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.NeonAmberAlert
import com.example.ui.theme.NeonGreenStatus
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorView(
    maxState: MaxState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorAnim")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OuterRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "InnerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (maxState == MaxState.SPEAKING || maxState == MaxState.LISTENING) 500 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseCore"
    )

    val coreColor = when (maxState) {
        MaxState.IDLE -> CyanPrimary
        MaxState.LISTENING -> NeonGreenStatus
        MaxState.PROCESSING -> NeonAmberAlert
        MaxState.SPEAKING -> CyanTertiary
        MaxState.EXECUTING -> Color(0xFF00E5FF)
    }

    Box(
        modifier = modifier
            .size(130.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 12.dp.toPx()

            // 1. Outer Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = radius * 1.2f
                ),
                center = center,
                radius = radius * 1.1f
            )

            // 2. Outer Segmented HUD Ring
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = coreColor.copy(alpha = 0.4f),
                    center = center,
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 8 Arc segments
                for (i in 0 until 8) {
                    val angle = i * 45f
                    drawArc(
                        color = coreColor,
                        startAngle = angle,
                        sweepAngle = 25f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Counter-rotating Inner Tech Ring
            rotate(innerRotation, pivot = center) {
                val innerRadius = radius * 0.72f
                drawCircle(
                    color = coreColor.copy(alpha = 0.6f),
                    center = center,
                    radius = innerRadius,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Triangular Arc notches
                for (i in 0 until 12) {
                    val angle = i * 30f * (Math.PI / 180f)
                    val x1 = center.x + innerRadius * cos(angle).toFloat()
                    val y1 = center.y + innerRadius * sin(angle).toFloat()
                    val x2 = center.x + (innerRadius - 10.dp.toPx()) * cos(angle).toFloat()
                    val y2 = center.y + (innerRadius - 10.dp.toPx()) * sin(angle).toFloat()

                    drawLine(
                        color = coreColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // 4. Center Core Energy Orb
            val coreRadius = radius * 0.42f * pulseScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, coreColor, coreColor.copy(alpha = 0.2f)),
                    center = center,
                    radius = coreRadius
                ),
                center = center,
                radius = coreRadius
            )

            // Core highlight ring
            drawCircle(
                color = Color.White,
                center = center,
                radius = coreRadius * 0.5f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center HUD State Label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MAX",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = maxState.name,
                color = coreColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
