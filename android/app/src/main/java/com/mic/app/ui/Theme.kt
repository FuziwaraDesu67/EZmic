package com.mic.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ChassisBackground = Color(0xFF0D0E12)
val PanelSurface = Color(0xFF14161C)
val ControlWell = Color(0xFF1A1D24)
val BorderHairline = Color(0xFF222630)
val BorderSubtle = Color(0xFF2B303C)

val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted = Color(0xFF484F58)

val StudioGreen = Color(0xFF10B981)
val StudioAmber = Color(0xFFF59E0B)
val StudioRed = Color(0xFFEF4444)

private val StudioHardwareColors = darkColorScheme(
    primary = StudioGreen,
    background = ChassisBackground,
    surface = PanelSurface,
    error = StudioRed,
    onPrimary = Color(0xFF042F1E),
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MicAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioHardwareColors,
        content = content
    )
}
