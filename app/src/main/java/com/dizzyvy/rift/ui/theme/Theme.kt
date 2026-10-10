package com.dizzyvy.rift.ui.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.dizzyvy.rift.R

private const val SECONDARY_TEXT_ALPHA = 0.7f

val ShrikhandFontFamily = FontFamily(
    Font(R.font.shrikhand_regular, weight = FontWeight.Normal),
)

val LibreFranklinFontFamily = FontFamily(
    Font(R.font.libre_franklin_regular, weight = FontWeight.Normal),
    Font(R.font.libre_franklin_medium, weight = FontWeight.Medium),
    Font(R.font.libre_franklin_semibold, weight = FontWeight.SemiBold),
    Font(R.font.libre_franklin_bold, weight = FontWeight.Bold),
)

private val RiftTypography = Typography(defaultFontFamily = LibreFranklinFontFamily)

object RiftPalette {
    val coral = Color(0xFFFF7E7E)
    val orange = Color(0xFFFFA259)
    val yellow = Color(0xFFFFCB56)
    val cream = Color(0xFFFFEDB9)
    val warmBrown = Color(0xFF2B1B17)

    val lightBackground = cream
    val lightSurface = Color(0xFFFFF4D2)
    val darkBackground = Color(0xFF231416)
    val darkSurface = Color(0xFF33201F)
}

private data class ThemeRoles(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val outline: Color,
)

private val lightRoles = ThemeRoles(
    background = RiftPalette.lightBackground,
    surface = RiftPalette.lightSurface,
    onBackground = RiftPalette.warmBrown,
    onSurface = RiftPalette.warmBrown,
    primary = RiftPalette.coral,
    onPrimary = RiftPalette.warmBrown,
    secondary = RiftPalette.orange,
    onSecondary = RiftPalette.warmBrown,
    tertiary = RiftPalette.yellow,
    onTertiary = RiftPalette.warmBrown,
    outline = RiftPalette.warmBrown,
)

private val darkRoles = ThemeRoles(
    background = RiftPalette.darkBackground,
    surface = RiftPalette.darkSurface,
    onBackground = RiftPalette.cream,
    onSurface = RiftPalette.cream,
    primary = RiftPalette.coral,
    onPrimary = RiftPalette.warmBrown,
    secondary = RiftPalette.orange,
    onSecondary = RiftPalette.warmBrown,
    tertiary = RiftPalette.yellow,
    onTertiary = RiftPalette.warmBrown,
    outline = RiftPalette.cream.copy(alpha = 0.7f),
)

@Composable
fun RiftTheme(
    mode: String = "system",
    content: @Composable () -> Unit,
) {
    val isDark = when (mode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val roles = if (isDark) darkRoles else lightRoles
    val dimOnSurface = roles.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    val palette = if (isDark) {
        darkColorScheme(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.surface,
            onPrimaryContainer = roles.onSurface,
            inversePrimary = roles.secondary,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = roles.surface,
            onSecondaryContainer = roles.onSurface,
            tertiary = roles.tertiary,
            onTertiary = roles.onTertiary,
            tertiaryContainer = roles.surface,
            onTertiaryContainer = roles.onSurface,
            background = roles.background,
            onBackground = roles.onBackground,
            surface = roles.surface,
            onSurface = roles.onSurface,
            surfaceVariant = roles.surface,
            onSurfaceVariant = dimOnSurface,
            surfaceTint = roles.primary,
            inverseSurface = roles.onSurface,
            inverseOnSurface = roles.background,
            error = roles.tertiary,
            onError = roles.onTertiary,
            errorContainer = roles.surface,
            onErrorContainer = roles.onSurface,
            outline = roles.outline,
            outlineVariant = roles.outline,
            scrim = roles.background,
        )
    } else {
        lightColorScheme(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.surface,
            onPrimaryContainer = roles.onSurface,
            inversePrimary = roles.secondary,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = roles.surface,
            onSecondaryContainer = roles.onSurface,
            tertiary = roles.tertiary,
            onTertiary = roles.onTertiary,
            tertiaryContainer = roles.surface,
            onTertiaryContainer = roles.onSurface,
            background = roles.background,
            onBackground = roles.onBackground,
            surface = roles.surface,
            onSurface = roles.onSurface,
            surfaceVariant = roles.surface,
            onSurfaceVariant = dimOnSurface,
            surfaceTint = roles.primary,
            inverseSurface = roles.onSurface,
            inverseOnSurface = roles.background,
            error = roles.tertiary,
            onError = roles.onTertiary,
            errorContainer = roles.surface,
            onErrorContainer = roles.onSurface,
            outline = roles.outline,
            outlineVariant = roles.outline,
            scrim = roles.background,
        )
    }
    MaterialTheme(colorScheme = palette, typography = RiftTypography, content = content)
}

@Composable
fun ShrikhandHeading(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    autoSize: TextAutoSize? = null,
) {
    val headingStyle = style.copy(
        fontFamily = ShrikhandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSynthesis = FontSynthesis.None,
        fontSize = if (style.fontSize.value < 20f) 20.sp else style.fontSize,
    )
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        style = headingStyle,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        autoSize = autoSize,
    )
}

@Composable
fun RiftTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

@Composable
fun RiftOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

@Composable
fun RiftBackgroundBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(listOf(colors.background, colors.surface, colors.background))
}
