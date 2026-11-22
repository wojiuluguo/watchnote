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

/** 夜墨 Ink Nocturne：暗色底 + 单强调色。per the 2026-11 design study. */
val ACCENT_THEMES = listOf(
    AccentTheme("朱砂", Color(0xFFFF6A4D), Color(0xFF5C2A1F), Color(0xFFF3B7A6), Color(0xFF3A0F07)),
    AccentTheme("素白", Color(0xFFF5F1E8), Color(0xFF3A363D), Color(0xFFD8D2C6), Color(0xFF262229)),
    AccentTheme("青色", Color(0xFF9FE8D8), Color(0xFF1F4E45), Color(0xFFB9D8D0), Color(0xFF10332C)),
    AccentTheme("琥珀", Color(0xFFFFC46B), Color(0xFF5C3A22), Color(0xFFE8C6B0), Color(0xFF3A2110)),
    AccentTheme("蓝色", Color(0xFFA8C7FA), Color(0xFF27477A), Color(0xFFC5D6F0), Color(0xFF10203D))
)

/**
 * 用任意一个颜色派生整套强调色：primary 就是它本身，
 * 容器色压暗、secondary 提亮、onPrimary 按亮度自动选深墨 / 浅米白。
 * 用于「更多颜色」（中国传统色）里挑出来的颜色。
 */
fun accentFromColor(color: Color): AccentTheme {
    val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    // 玄黑一类的极暗色直接当强调色，在黑底上等于看不见；只对 lum < 0.26 的色往白里提一点（保色相），
    // 提到 0.32 左右刚好能在纯黑背景上看清。其它颜色一律原样使用。
    val primary = if (lum < 0.26f) mix(color, Color.White, (0.32f - lum) / (1f - lum)) else color
    val ink = if (lum > 0.45f) Color(0xFF1A1310) else Color(0xFFF5F1E8)
    return AccentTheme(
        name = "自定义",
        primary = primary,
        primaryContainer = mix(primary, Color.Black, 0.62f),
        secondary = mix(primary, Color.White, 0.42f),
        onPrimary = ink
    )
}

private fun mix(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
}

private val base = ColorScheme(
    primary = Color(0xFFFF6A4D),
    primaryDim = Color(0xFFD9563E),
    onPrimary = Color(0xFF3A0F07),
    primaryContainer = Color(0xFF5C2A1F),
    onPrimaryContainer = Color(0xFFFFD9CF),
    secondary = Color(0xFFFFC46B),
    secondaryDim = Color(0xFFD9A659),
    onSecondary = Color(0xFF3A2A0E),
    secondaryContainer = Color(0xFF3B2F1B),
    onSecondaryContainer = Color(0xFFFFE6C2),
    tertiary = Color(0xFF7BD88F),
    tertiaryDim = Color(0xFF63B374),
    onTertiary = Color(0xFF10331A),
    tertiaryContainer = Color(0xFF1E3B26),
    onTertiaryContainer = Color(0xFFD4F3DB),
    error = Color(0xFFFF5A5A),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFD4D4),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F1E8),
    onSurface = Color(0xFFF5F1E8),
    onSurfaceVariant = Color(0xFFA8A29A),
    outline = Color(0xFF6E6A64),
    outlineVariant = Color(0xFF2E2E33),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF2A2A2E)
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
fun WristNoteTheme(accentIndex: Int = 0, customColor: Int = 0, content: @Composable () -> Unit) {
    val accent = if (customColor != 0) {
        accentFromColor(Color(customColor.toLong() and 0xFFFFFFFFL))
    } else {
        ACCENT_THEMES.getOrElse(accentIndex) { ACCENT_THEMES[0] }
    }
    MaterialTheme(colorScheme = schemeFor(accent), content = content)
}
