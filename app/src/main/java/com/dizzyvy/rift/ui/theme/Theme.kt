package com.dizzyvy.rift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver

private fun accentColor(name: String): Color = when (name.lowercase()) {
    "red" -> Color(0xFFD92338)
    "orange" -> Color(0xFFE66B1E)
    "yellow", "gold" -> Color(0xFFF2C230)
    "green" -> Color(0xFF54A96A)
    "blue" -> Color(0xFF208BCE)
    "purple" -> Color(0xFF8758B8)
    "pink" -> Color(0xFFD95791)
    "silver" -> Color(0xFF9AA4AE)
    "graphite" -> Color(0xFF454B54)
    "chromatic", "cyan" -> NanoCyan
    else -> RiftPink
}

@Composable
fun RiftTheme(
    mode: String = "light",
    accent: String = "Coral",
    content: @Composable () -> Unit,
) {
    val resolvedMode = when (mode.lowercase()) {
        "system" -> if (isSystemInDarkTheme()) "dark" else "light"
        else -> mode.lowercase()
    }
    val accentColor = if (resolvedMode == "light" && accent.lowercase() in setOf("chromatic", "cyan")) {
        Color(0xFF006B78)
    } else {
        accentColor(accent)
    }
    val darkAccentLabel = accent.lowercase() in setOf("gold", "yellow", "orange", "silver")
    val palette = when (resolvedMode) {
        "nano" -> darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = NanoSurfaceRaised,
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
        "dark" -> darkColorScheme(
            primary = accentColor,
            onPrimary = if (darkAccentLabel) Ink else Color.White,
            secondary = RiftBlue,
            onSecondary = Color.White,
            tertiary = RiftGold,
            background = Color(0xFF121212),
            surface = Color(0xFF1C1C1E),
            onSurface = Color(0xFFF4F4F4),
            onSurfaceVariant = Color(0xFFB5B5B8),
        )
        "amoled" -> darkColorScheme(
            primary = accentColor,
            onPrimary = if (darkAccentLabel) Ink else Color.White,
            secondary = RiftBlue,
            onSecondary = Color.White,
            tertiary = RiftGold,
            background = Color.Black,
            surface = Color.Black,
            onSurface = Color(0xFFF4F4F4),
            onSurfaceVariant = Color(0xFFB5B5B8),
        )
        else -> lightColorScheme(
            primary = accentColor,
            onPrimary = if (darkAccentLabel) Ink else Color.White,
            secondary = RiftBlue,
            onSecondary = Color.White,
            tertiary = RiftGold,
            background = RiftBackground,
            surface = Color(0xFFFFFEFB),
            onSurface = Ink,
            onSurfaceVariant = Color(0xFF686D76),
        )
    }
    MaterialTheme(colorScheme = palette, content = content)
}

@Composable
fun RiftBackgroundBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    val background = colors.background
    return Brush.verticalGradient(
        listOf(
            colors.primary.copy(alpha = 0.055f).compositeOver(background),
            background,
            colors.secondary.copy(alpha = 0.035f).compositeOver(background),
        ),
    )
}
