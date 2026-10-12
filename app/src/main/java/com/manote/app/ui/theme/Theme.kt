package com.manote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 浅色配色：暖米白画布 + 深墨绿主要操作 + 砖红错误。
 *
 * 卡片用纯白而非画布色，让「一张张卡片」在米白底上形成层级（参考稿的主要观感来源）。
 */
private val ManoteLightColorScheme = lightColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = OnBrandContainerLight,
    inversePrimary = BrandInkLight,
    secondary = LossLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LossContainerLight,
    onSecondaryContainer = OnLossContainerLight,
    tertiary = GoldLight,
    onTertiary = OnGoldLight,
    tertiaryContainer = DrawContainerLight,
    onTertiaryContainer = OnDrawContainerLight,
    error = WinLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = WinContainerLight,
    onErrorContainer = OnWinContainerLight,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = ChipLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = BrandLight,
    inverseSurface = BrandInkLight,
    inverseOnSurface = Color(0xFFEFF3EF),
    outline = OutlineLight,
    outlineVariant = BorderLight,
    scrim = Color(0xFF000000),
    surfaceBright = SurfaceLight,
    surfaceDim = CanvasDimLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceAltLight,
    surfaceContainer = Color(0xFFF6F1E5),
    surfaceContainerHigh = Color(0xFFF2EDE0),
    surfaceContainerHighest = Color(0xFFEDE7D9),
)

/** 深色配色：同一套语义角色，绿与红提亮以保证对比度。 */
private val ManoteDarkColorScheme = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF04301F),
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = OnBrandContainerDark,
    inversePrimary = BrandInkDark,
    secondary = LossDark,
    onSecondary = Color(0xFF04301F),
    secondaryContainer = LossContainerDark,
    onSecondaryContainer = OnLossContainerDark,
    tertiary = GoldDark,
    onTertiary = OnGoldDark,
    tertiaryContainer = DrawContainerDark,
    onTertiaryContainer = OnDrawContainerDark,
    error = WinDark,
    onError = Color(0xFF3F0B04),
    errorContainer = WinContainerDark,
    onErrorContainer = OnWinContainerDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = ChipDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = BrandDark,
    inverseSurface = Color(0xFFE8ECE8),
    inverseOnSurface = Color(0xFF232B26),
    outline = OutlineDark,
    outlineVariant = BorderDark,
    scrim = Color(0xFF000000),
    surfaceBright = SurfaceAltDark,
    surfaceDim = CanvasDimDark,
    surfaceContainerLowest = CanvasDimDark,
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceAltDark,
    surfaceContainerHighest = Color(0xFF2A332E),
)

/** 圆角：卡片与胶囊整体偏圆润。 */
private val ManoteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 字体：在 Material 3 默认排版上把标题类加粗，让区块标题与数字更醒目。
 *
 * 所有可能承载金额与场数的样式一律开启等宽数字（`tnum`）。
 * 比例数字里 `1` 比 `8` 窄，同一列上「¥1,113.00」会比「¥888.00」短一截，
 * 逐行排下来右边缘参差不齐；等宽数字让金额、场数在列表与表格里严格成列。
 */
private val ManoteTypography: Typography = Typography().run {
    copy(
        headlineLarge = headlineLarge.tabular().copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.tabular().copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.tabular().copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.tabular().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.tabular().copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
    )
}

/** 开启等宽数字。字体缺该特性时系统会忽略，不会让文字变形。 */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 应用主题。规格 3.5 要求主题跟随系统，因此不使用动态取色，也不提供应用内明暗开关。
 */
@Composable
fun ManoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalManoteExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) ManoteDarkColorScheme else ManoteLightColorScheme,
            shapes = ManoteShapes,
            typography = ManoteTypography,
            content = content,
        )
    }
}

/** 取本应用特有的语义色，例如 `MaterialTheme.manote.win`。 */
val MaterialTheme.manote: ManoteExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalManoteExtendedColors.current
