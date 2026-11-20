package com.mic.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Figma Stealth Obsidian & Vibrant Mint Palette
val ChassisBackground = Color(0xFF0C0E11)
val PanelSurface = Color(0xFF16191D)
val ControlWell = Color(0xFF1E2227)
val BorderHairline = Color(0xFF212529)
val BorderSubtle = Color(0xFF282D35)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E959E)
val TextMuted = Color(0xFF5A626A)

val StudioMint = Color(0xFF2EE59D)
val StudioGreen = Color(0xFF2EE59D)
val StudioAmber = Color(0xFFF59E0B)
val StudioRed = Color(0xFFEF4444)
val StudioDarkMintBg = Color(0xFF0B281E)

private val StudioHardwareColors = darkColorScheme(
    primary = StudioMint,
    background = ChassisBackground,
    surface = PanelSurface,
    error = StudioRed,
    onPrimary = Color(0xFF091E16),
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
