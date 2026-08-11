package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HudColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = HudSurfaceVariant,
    onPrimaryContainer = TextCyanLight,
    secondary = CyanSecondary,
    onSecondary = Color.Black,
    tertiary = CyanTertiary,
    onTertiary = Color.Black,
    background = HudBackground,
    onBackground = TextCyanLight,
    surface = HudSurface,
    onSurface = TextCyanLight,
    surfaceVariant = HudSurfaceVariant,
    onSurfaceVariant = TextCyanMuted,
    outline = HudBorderCyan
)

@Composable
fun MAXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = Typography,
        content = content
    )
}

