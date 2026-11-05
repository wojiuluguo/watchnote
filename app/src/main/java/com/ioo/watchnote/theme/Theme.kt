package com.ioo.watchnote

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

data class AccentTheme(
    val name: String,
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val onPrimary: Color
)

val ACCENT_THEMES = listOf(
    AccentTheme("紫色", Color(0xFFC8B6FF), Color(0xFF493575), Color(0xFFCAC2DC), Color(0xFF2F1D5B)),
    AccentTheme("素白", Color(0xFFE7E2E9), Color(0xFF3A363D), Color(0xFFCAC5CE), Color(0xFF262229)),
    AccentTheme("青色", Color(0xFF9FE8D8), Color(0xFF1F4E45), Color(0xFFB9D8D0), Color(0xFF10332C)),
    AccentTheme("琥珀", Color(0xFFFFC9A3), Color(0xFF5C3A22), Color(0xFFE8C6B0), Color(0xFF3A2110)),
    AccentTheme("蓝色", Color(0xFFA8C7FA), Color(0xFF27477A), Color(0xFFC5D6F0), Color(0xFF10203D))
)

private fun mix(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
}

private val base = ColorScheme(
    primary = Color(0xFFC8B6FF),
    primaryDim = Color(0xFFB29AF7),
    onPrimary = Color(0xFF2F1D5B),
    primaryContainer = Color(0xFF493575),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCAC2DC),
    secondaryDim = Color(0xFFAEA6BF),
    onSecondary = Color(0xFF312E3B),
    secondaryContainer = Color(0xFF484451),
    onSecondaryContainer = Color(0xFFE7E0F4),
    tertiary = Color(0xFFFFB2C7),
    tertiaryDim = Color(0xFFE595AC),
    onTertiary = Color(0xFF511D31),
    tertiaryContainer = Color(0xFF6D3348),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFE9E1EC),
    onSurface = Color(0xFFE9E1EC),
    onSurfaceVariant = Color(0xFFCBC3CE),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF4A454E),
    surfaceContainerLow = Color(0xFF1B181D),
    surfaceContainer = Color(0xFF211E23),
    surfaceContainerHigh = Color(0xFF2C292E)
)

private fun schemeFor(accent: AccentTheme): ColorScheme {
    return ColorScheme(
        primary = accent.primary,
        primaryDim = mix(accent.primary, Color.Black, 0.18f),
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = mix(accent.primary, Color.White, 0.72f),
        secondary = accent.secondary,
        secondaryDim = mix(accent.secondary, Color.Black, 0.18f),
        onSecondary = accent.onPrimary,
        secondaryContainer = mix(accent.primaryContainer, Color.Black, 0.26f),
        onSecondaryContainer = mix(accent.secondary, Color.White, 0.62f),
        tertiary = base.tertiary,
        tertiaryDim = base.tertiaryDim,
        onTertiary = base.onTertiary,
        tertiaryContainer = base.tertiaryContainer,
        onTertiaryContainer = base.onTertiaryContainer,
        error = base.error,
        onError = base.onError,
        errorContainer = base.errorContainer,
        onErrorContainer = base.onErrorContainer,
        background = base.background,
        onBackground = base.onBackground,
        onSurface = base.onSurface,
        onSurfaceVariant = base.onSurfaceVariant,
        outline = base.outline,
        outlineVariant = base.outlineVariant,
        surfaceContainerLow = base.surfaceContainerLow,
        surfaceContainer = base.surfaceContainer,
        surfaceContainerHigh = base.surfaceContainerHigh
    )
}

@Composable
fun WristNoteTheme(accentIndex: Int = 0, content: @Composable () -> Unit) {
    val accent = ACCENT_THEMES.getOrElse(accentIndex) { ACCENT_THEMES[0] }
    MaterialTheme(colorScheme = schemeFor(accent), content = content)
}
