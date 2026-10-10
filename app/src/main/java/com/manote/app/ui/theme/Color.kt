package com.manote.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 配色常量。
 *
 * 规格 3.6 要求：中性灰白背景、墨绿主要操作、红色错误，输赢同时用文字与符号表达。
 * 在此基础上按参考稿把灰白调整为偏暖的米白，并补上「赢 / 输 / 持平 / 金字」四个
 * Material 3 没有的语义色 —— 它们放在 [ManoteExtendedColors] 里，
 * 不占用 M3 的 error 角色（error 仍然只表示真正的错误）。
 *
 * 全部为具名色值，界面代码不得再出现字面量颜色。
 */

// ——————————————————————————————— 浅色 ———————————————————————————————

/** 品牌墨绿：比「输」的绿更深更暗，避免两种绿混淆。 */
internal val BrandInkLight = Color(0xFF14352A)

/** 浅色底上可见的主操作绿。 */
internal val BrandLight = Color(0xFF1F5E45)
internal val BrandContainerLight = Color(0xFFDCEBE2)
internal val OnBrandContainerLight = Color(0xFF0C2B1E)

/** 赢：砖红。 */
internal val WinLight = Color(0xFFB93A2B)
internal val WinContainerLight = Color(0xFFF6DCD6)
internal val OnWinContainerLight = Color(0xFF4A120A)

/** 输：中绿。 */
internal val LossLight = Color(0xFF2F7A55)
internal val LossContainerLight = Color(0xFFD9EBE0)
internal val OnLossContainerLight = Color(0xFF0E3323)

/** 持平：米黄。 */
internal val DrawLight = Color(0xFFA9801F)
internal val DrawContainerLight = Color(0xFFF0E4C0)
internal val OnDrawContainerLight = Color(0xFF3E2F06)

/** 金黄点缀（徽标、排名第一）。 */
internal val GoldLight = Color(0xFFD9B85C)
internal val OnGoldLight = Color(0xFF33270A)

/** 画布 / 卡片 / 描边 / 胶囊底色。 */
internal val CanvasLight = Color(0xFFF8F4EA)
internal val CanvasDimLight = Color(0xFFEFE9DA)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceAltLight = Color(0xFFFCF9F1)
internal val BorderLight = Color(0xFFEDE4D2)
internal val OutlineLight = Color(0xFFC9BFA9)
internal val ChipLight = Color(0xFFF1ECE0)

/** 文本三级。 */
internal val TextPrimaryLight = Color(0xFF1B211D)
internal val TextSecondaryLight = Color(0xFF7C837B)
internal val TextTertiaryLight = Color(0xFFA6ACA4)

// ——————————————————————————————— 深色 ———————————————————————————————

internal val BrandInkDark = Color(0xFF1B4131)

internal val BrandDark = Color(0xFF7CCFA6)
internal val BrandContainerDark = Color(0xFF1F4433)
internal val OnBrandContainerDark = Color(0xFFD6F0E2)

internal val WinDark = Color(0xFFF0927F)
internal val WinContainerDark = Color(0xFF4A1C14)
internal val OnWinContainerDark = Color(0xFFFBD9D1)

internal val LossDark = Color(0xFF77C9A1)
internal val LossContainerDark = Color(0xFF17392A)
internal val OnLossContainerDark = Color(0xFFCFEFDD)

internal val DrawDark = Color(0xFFE0C071)
internal val DrawContainerDark = Color(0xFF3C3216)
internal val OnDrawContainerDark = Color(0xFFF3E4B8)

internal val GoldDark = Color(0xFFD9B85C)
internal val OnGoldDark = Color(0xFF33270A)

internal val CanvasDark = Color(0xFF121614)
internal val CanvasDimDark = Color(0xFF0C100E)
internal val SurfaceDark = Color(0xFF1B211D)
internal val SurfaceAltDark = Color(0xFF232B26)
internal val BorderDark = Color(0xFF2C3833)
internal val OutlineDark = Color(0xFF3C4A43)
internal val ChipDark = Color(0xFF252E29)

internal val TextPrimaryDark = Color(0xFFE8ECE8)
internal val TextSecondaryDark = Color(0xFF9AA39C)
internal val TextTertiaryDark = Color(0xFF7A837C)

/**
 * Material 3 配色之外、本应用特有的语义色。
 *
 * 输赢一律同时提供「容器色 + 文字色」，配合 [ResultBadge] 里的文字标签，
 * 保证不依赖颜色单独表达输赢（规格 2.1、3.6）。
 */
@Immutable
data class ManoteExtendedColors(
    /** 赢。 */
    val win: Color,
    val winContainer: Color,
    val onWinContainer: Color,
    /** 输。 */
    val loss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    /** 持平。 */
    val draw: Color,
    val drawContainer: Color,
    val onDrawContainer: Color,
    /** 列表里的小结果标签：浅色用实色白字，深色用柔和底色深字，两种主题都保证可读。 */
    val winBadge: Color,
    val onWinBadge: Color,
    val lossBadge: Color,
    val onLossBadge: Color,
    val drawBadge: Color,
    val onDrawBadge: Color,
    /** 金黄点缀。 */
    val gold: Color,
    val onGold: Color,
)

internal val LightExtendedColors = ManoteExtendedColors(
    win = WinLight,
    winContainer = WinContainerLight,
    onWinContainer = OnWinContainerLight,
    loss = LossLight,
    lossContainer = LossContainerLight,
    onLossContainer = OnLossContainerLight,
    draw = DrawLight,
    drawContainer = DrawContainerLight,
    onDrawContainer = OnDrawContainerLight,
    winBadge = WinLight,
    onWinBadge = Color(0xFFFFFFFF),
    lossBadge = LossLight,
    onLossBadge = Color(0xFFFFFFFF),
    drawBadge = DrawContainerLight,
    onDrawBadge = OnDrawContainerLight,
    gold = GoldLight,
    onGold = OnGoldLight,
)

internal val DarkExtendedColors = ManoteExtendedColors(
    win = WinDark,
    winContainer = WinContainerDark,
    onWinContainer = OnWinContainerDark,
    loss = LossDark,
    lossContainer = LossContainerDark,
    onLossContainer = OnLossContainerDark,
    draw = DrawDark,
    drawContainer = DrawContainerDark,
    onDrawContainer = OnDrawContainerDark,
    winBadge = WinContainerDark,
    onWinBadge = OnWinContainerDark,
    lossBadge = LossContainerDark,
    onLossBadge = OnLossContainerDark,
    drawBadge = DrawContainerDark,
    onDrawBadge = OnDrawContainerDark,
    gold = GoldDark,
    onGold = OnGoldDark,
)

internal val LocalManoteExtendedColors = staticCompositionLocalOf { LightExtendedColors }
