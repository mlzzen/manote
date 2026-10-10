package com.manote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅色配色：中性灰白背景 + 墨绿主要操作 + 红色错误。
 */
private val ManoteLightColorScheme = lightColorScheme(
    primary = InkGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F2D5),
    onPrimaryContainer = Color(0xFF00201A),
    inversePrimary = InkGreenLight,
    secondary = Color(0xFF4B635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF43617B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC7E7FF),
    onTertiaryContainer = Color(0xFF001E31),
    error = ErrorRedLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = NeutralBackgroundLight,
    onBackground = Color(0xFF181C1B),
    surface = NeutralBackgroundLight,
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = InkGreen,
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C3),
    scrim = Color(0xFF000000),
    surfaceBright = NeutralBackgroundLight,
    surfaceDim = Color(0xFFD6DBD8),
    surfaceContainer = Color(0xFFEAEFEB),
    surfaceContainerHigh = Color(0xFFE4E9E6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    surfaceContainerLow = Color(0xFFF0F4F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/**
 * 深色配色：保持同一套语义角色，墨绿转为浅色调以保证对比度。
 */
private val ManoteDarkColorScheme = darkColorScheme(
    primary = InkGreenLight,
    onPrimary = Color(0xFF00382B),
    primaryContainer = InkGreenDark,
    onPrimaryContainer = Color(0xFFA9F2D5),
    inversePrimary = InkGreen,
    secondary = Color(0xFFB1CCC0),
    onSecondary = Color(0xFF1D352C),
    secondaryContainer = Color(0xFF344C42),
    onSecondaryContainer = Color(0xFFCDE9DC),
    tertiary = Color(0xFFABCAE7),
    onTertiary = Color(0xFF113349),
    tertiaryContainer = Color(0xFF2A4A61),
    onTertiaryContainer = Color(0xFFC7E7FF),
    error = ErrorRedDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = NeutralBackgroundDark,
    onBackground = Color(0xFFDFE4E0),
    surface = NeutralBackgroundDark,
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceTint = InkGreenLight,
    inverseSurface = Color(0xFFDFE4E0),
    inverseOnSurface = Color(0xFF2C312F),
    outline = Color(0xFF8A938E),
    outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A38),
    surfaceDim = NeutralBackgroundDark,
    surfaceContainer = Color(0xFF1B201E),
    surfaceContainerHigh = Color(0xFF262B29),
    surfaceContainerHighest = Color(0xFF313634),
    surfaceContainerLow = Color(0xFF181D1B),
    surfaceContainerLowest = Color(0xFF0A0F0D),
)

/**
 * 应用主题。规格 3.5 要求主题跟随系统，因此不使用动态取色，
 * 也不提供应用内明暗切换开关。
 */
@Composable
fun ManoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ManoteDarkColorScheme else ManoteLightColorScheme,
        content = content,
    )
}
