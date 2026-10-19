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
import androidx.compose.ui.unit.sp

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

/**
 * 圆角：卡片与胶囊整体偏圆润。
 *
 * 参考稿的把控点是大面积圆角（卡片接近 24dp）+ 顶部大标题，
 * 圆角一小，整页立刻显得像表格。这里把每一档都提上去，
 * 但保持逐档递增，避免「所有东西一样圆」而失去层级。
 */
private val ManoteShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 层次用的一套高度令牌。
 *
 * 全应用只有三档：控件（按钮、胶囊）几乎贴地，卡片浮起来一层，
 * 悬浮导航要压在内容之上。把高度收成三个数，翻十页看到的「什么在什么上面」
 * 才是同一套关系；各处自己填 elevation 时，同一层的卡片会差出好几 dp。
 */
object ManoteElevation {
    /** 顶栏的圆形按钮、分段控件的选中段。只给一点点，够把边线托起来。 */
    val control = 2.dp

    /** 内容卡片与场次行。 */
    val card = 8.dp

    /** 悬浮导航条：它必须明显高于任何内容。 */
    val floatingBar = 18.dp
}

/**
 * 根页面顶栏的大标题。
 *
 * 比 `titleLarge` 大一档、更粗、字距收紧一点：参考稿里页面名就是整屏的第一行，
 * 用常规标题字号时它会淹没在下面那堆卡片标题里。中文没有西文那样的字距问题，
 * 但 30sp 的字紧排一点更成块，不会散成五个独立方块。
 */
val ManoteScreenTitleStyle: TextStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.8).sp,
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
