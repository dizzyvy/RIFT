package com.dizzyvy.sjmusicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Palette = lightColorScheme(
    primary = SJCoral,
    onPrimary = Color.White,
    secondary = SJBlue,
    onSecondary = Color.White,
    tertiary = SJSunshine,
    background = PaperBackground,
    surface = Color(0xFFFFFEFB),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF686D76),
)

@Composable
fun SJMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
