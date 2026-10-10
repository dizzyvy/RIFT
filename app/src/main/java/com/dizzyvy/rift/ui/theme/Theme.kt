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

private const val SECONDARY_TEXT_ALPHA = 1f

val ShrikhandFontFamily = FontFamily(
    Font(R.font.shrikhand_regular, weight = FontWeight.Normal),
)

val LibreFranklinFontFamily = FontFamily(
    Font(R.font.libre_franklin_regular, weight = FontWeight.Normal),
    Font(R.font.libre_franklin_medium, weight = FontWeight.Medium),
    Font(R.font.libre_franklin_semibold, weight = FontWeight.SemiBold),
    Font(R.font.libre_franklin_bold, weight = FontWeight.Bold),
)

private val baseTypography = Typography()

private fun TextStyle.withFontFamily(
    fontFamily: FontFamily,
    fontWeight: FontWeight = this.fontWeight ?: FontWeight.Normal,
    fontSynthesis: FontSynthesis = this.fontSynthesis ?: FontSynthesis.All,
): TextStyle = copy(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSynthesis = fontSynthesis,
)

private val RiftTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.withFontFamily(LibreFranklinFontFamily),
    displayMedium = baseTypography.displayMedium.withFontFamily(LibreFranklinFontFamily),
    displaySmall = baseTypography.displaySmall.withFontFamily(LibreFranklinFontFamily),
    headlineLarge = baseTypography.headlineLarge.withFontFamily(LibreFranklinFontFamily, FontWeight.SemiBold),
    headlineMedium = baseTypography.headlineMedium.withFontFamily(LibreFranklinFontFamily, FontWeight.SemiBold),
    headlineSmall = baseTypography.headlineSmall.withFontFamily(LibreFranklinFontFamily, FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.withFontFamily(LibreFranklinFontFamily),
    titleMedium = baseTypography.titleMedium.withFontFamily(LibreFranklinFontFamily),
    titleSmall = baseTypography.titleSmall.withFontFamily(LibreFranklinFontFamily),
    bodyLarge = baseTypography.bodyLarge.withFontFamily(LibreFranklinFontFamily),
    bodyMedium = baseTypography.bodyMedium.withFontFamily(LibreFranklinFontFamily),
    bodySmall = baseTypography.bodySmall.withFontFamily(LibreFranklinFontFamily),
    labelLarge = baseTypography.labelLarge.withFontFamily(LibreFranklinFontFamily),
    labelMedium = baseTypography.labelMedium.withFontFamily(LibreFranklinFontFamily),
    labelSmall = baseTypography.labelSmall.withFontFamily(LibreFranklinFontFamily),
)

object RiftPalette {
    val surface = Color(0xFFFAF9F7)
    val lavender = Color(0xFFEEEAFA)
    val periwinkle = Color(0xFF8D8CCF)
    val dustyRose = Color(0xFFD3A2B5)
    val sage = Color(0xFFB7CBB8)
    val butter = Color(0xFFF0DDA4)
    val charcoal = Color(0xFF282832)
    val border = Color(0xFFE7E3E8)
    val darkBackground = Color(0xFF17171C)
    val darkSurface = Color(0xFF23232B)
    val darkLavender = Color(0xFF2A2A38)
    val darkPrimary = Color(0xFFEDEBF2)
    val darkSecondary = Color(0xFFA9A6B5)
    val darkAccent = Color(0xFFABA8F0)

    val lightBackground = surface
    val lightSurface = Color(0xFFFFFFFF)
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
    background = RiftPalette.surface,
    surface = RiftPalette.lightSurface,
    onBackground = RiftPalette.charcoal,
    onSurface = RiftPalette.charcoal,
    primary = RiftPalette.periwinkle,
    onPrimary = RiftPalette.charcoal,
    secondary = RiftPalette.dustyRose,
    onSecondary = RiftPalette.charcoal,
    tertiary = RiftPalette.sage,
    onTertiary = RiftPalette.charcoal,
    outline = RiftPalette.border,
)

private val darkRoles = ThemeRoles(
    background = RiftPalette.darkBackground,
    surface = RiftPalette.darkSurface,
    onBackground = RiftPalette.darkPrimary,
    onSurface = RiftPalette.darkPrimary,
    primary = RiftPalette.darkAccent,
    onPrimary = RiftPalette.darkBackground,
    secondary = RiftPalette.dustyRose,
    onSecondary = RiftPalette.darkBackground,
    tertiary = RiftPalette.sage,
    onTertiary = RiftPalette.darkBackground,
    outline = RiftPalette.darkSecondary,
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
    val dimOnSurface = if (isDark) RiftPalette.darkSecondary else Color(0xFF5F5D68)
    val palette = if (isDark) {
        darkColorScheme(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = RiftPalette.darkLavender,
            onPrimaryContainer = roles.onSurface,
            inversePrimary = roles.secondary,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = RiftPalette.darkLavender,
            onSecondaryContainer = roles.onSurface,
            tertiary = roles.tertiary,
            onTertiary = roles.onTertiary,
            tertiaryContainer = RiftPalette.darkLavender,
            onTertiaryContainer = roles.onSurface,
            background = roles.background,
            onBackground = roles.onBackground,
            surface = roles.surface,
            onSurface = roles.onSurface,
            surfaceVariant = RiftPalette.darkLavender,
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
            primaryContainer = RiftPalette.lavender,
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
            surfaceVariant = RiftPalette.lavender,
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
    val headingStyle = style.copy(fontFamily = LibreFranklinFontFamily, fontWeight = FontWeight.SemiBold)
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
