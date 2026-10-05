package com.pingwin.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VihtColorScheme = darkColorScheme(
    primary = VihtNeonGreen,
    onPrimary = Color(0xFF07090E),
    primaryContainer = Color(0x3300FF88),
    onPrimaryContainer = VihtNeonGreenLight,

    secondary = VihtNeonCyan,
    onSecondary = Color(0xFF07090E),
    secondaryContainer = Color(0x3300D2FF),
    onSecondaryContainer = Color.White,

    tertiary = VihtElectricPurple,
    onTertiary = Color.White,

    background = VihtBgMain,
    onBackground = VihtTextPrimary,

    surface = VihtBgSurface,
    onSurface = VihtTextPrimary,

    surfaceVariant = VihtBgCard,
    onSurfaceVariant = VihtTextSecondary,

    outline = VihtBorderSubtle,
    outlineVariant = VihtBorderActive,

    error = VihtAccentRed,
    onError = Color.White
)

@Composable
fun PingwinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VihtColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun VihtTheme(
    content: @Composable () -> Unit
) {
    PingwinTheme(content = content)
}
