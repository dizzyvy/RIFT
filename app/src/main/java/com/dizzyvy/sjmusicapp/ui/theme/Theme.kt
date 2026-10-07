package com.dizzyvy.sjmusicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Palette = darkColorScheme(
    primary = NanoBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF163B5C),
    onPrimaryContainer = NanoText,
    secondary = NanoPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33205B),
    onSecondaryContainer = NanoText,
    tertiary = NanoGreen,
    onTertiary = Color(0xFF062016),
    background = NanoBackground,
    onBackground = NanoText,
    surface = NanoSurface,
    onSurface = NanoText,
    surfaceVariant = NanoSurfaceRaised,
    onSurfaceVariant = NanoMuted,
    outline = NanoOutline,
)

@Composable
fun SJMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
