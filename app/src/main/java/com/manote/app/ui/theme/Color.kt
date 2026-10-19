package com.manote.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
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

/** 鎏金容器：荣誉标记（牌王 / 第一名）的底色，比纯金色更适合铺一片面积。 */
internal val GoldContainerLight = Color(0xFFF5E8C6)
internal val OnGoldContainerLight = Color(0xFF4A3708)

/**
 * 英雄卡：深墨绿实底，用来托住整页最大的那个数字。
 *
 * 它是全应用唯一一块「深色大面积」，反复出现在各页顶部，形成固定的视觉锚点。
 * 卡内的次级文字用 [OnHeroMutedLight] 而不是半透明白 —— 换主题时不必再算透明度。
 */
internal val HeroSurfaceLight = Color(0xFF14352A)
internal val HeroSurfaceTopLight = Color(0xFF1C4636)
/** 英雄卡的下缘：比卡身再深一档，卡面因此有「受光面 / 背光面」而不是一块平色。 */
internal val HeroSurfaceBottomLight = Color(0xFF0C2419)
internal val OnHeroLight = Color(0xFFFFFFFF)
internal val OnHeroMutedLight = Color(0xFF9FD3B8)
internal val HeroChipLight = Color(0xFF1F5E45)
internal val OnHeroChipLight = Color(0xFFD6F0E2)

/** 画布 / 卡片 / 描边 / 胶囊底色。 */
internal val CanvasLight = Color(0xFFF7F3E9)
internal val CanvasDimLight = Color(0xFFEDE6D5)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceAltLight = Color(0xFFFCF9F1)
internal val BorderLight = Color(0xFFEDE4D2)
internal val OutlineLight = Color(0xFFC9BFA9)
internal val ChipLight = Color(0xFFF1EEE6)

/**
 * 页面背景的晨光：最上缘一层暖杏色，往下穿过一段中性过渡，落回画布色。
 *
 * 平铺一块画布色会让整屏像一张纸。这里给顶端一层**看得出**的暖色
 * （而不是只差两个色阶的灰绿），整页就有了「从窗外照进来」的方向感；
 * 中段的过渡点既不是暖色的延续也不是画布色，把两层光柔和地接上。
 * 最下一段仍然严格等于 [CanvasLight]——底部悬浮导航的外环、编辑页的保存条
 * 都拿画布色贴在那条线上，渐变若在下面还留着色差，它们就会露出边界。
 */
internal val PageTopLight = Color(0xFFFBE9C9)
internal val PageMidLight = Color(0xFFF3F0E7)

/**
 * 右上角那团柔光（参考稿里窗光打在墙上的一角）。
 *
 * 它叠在 [PageTopLight] 的渐变之上，只在右上角有一个很淡的核，向外散尽。
 * 单独一层而不是并进渐变里：径向光斑写不进线性渐变，而这一角暖光
 * 正是整屏看起来「有空气」而不是「一块平色」的原因。
 */
internal val PageGlowLight = Color(0x66FFC76B)

/**
 * 玻璃卡片：底色不是纯白，而是带一点透的白。
 *
 * 卡片背后就是那层晨光，透一点上来，卡片才像一片磨砂玻璃架在页面上，
 * 而不是一块贴上去的白纸。上缘比下缘更白一档，构成极弱的顶光。
 */
internal val GlassLight = Color(0xF4FFFFFF)
internal val GlassBottomLight = Color(0xECFFFDF6)

/** 卡片边缘的高光：一圈纯白细线，是「玻璃」的第二条线索（第一条是透）。 */
internal val GlassBorderLight = Color(0xD9FFFFFF)

/** 卡片投影的颜色：偏暖的褐灰，压在米白底上比纯黑柔和得多。 */
internal val GlassShadowLight = Color(0xFF8A7355)

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

internal val GoldContainerDark = Color(0xFF3C3216)
internal val OnGoldContainerDark = Color(0xFFF3E4B8)

/**
 * 深色主题下的英雄卡比画布略亮一档。
 *
 * 浅色主题靠「深底白字」拉开层级，深色主题里再用更深的底就沉进背景了，
 * 因此改成比画布亮的墨绿，让同一套版式在两个主题下都还看得出是一块独立的卡。
 */
internal val HeroSurfaceDark = Color(0xFF22513C)
internal val HeroSurfaceTopDark = Color(0xFF2C6349)
internal val HeroSurfaceBottomDark = Color(0xFF173A2B)
internal val OnHeroDark = Color(0xFFEAF6EF)
internal val OnHeroMutedDark = Color(0xFF9FD3B8)
internal val HeroChipDark = Color(0xFF27543F)
internal val OnHeroChipDark = Color(0xFFD6F0E2)

internal val CanvasDark = Color(0xFF121614)
internal val CanvasDimDark = Color(0xFF0C100E)
internal val SurfaceDark = Color(0xFF1B211D)
internal val SurfaceAltDark = Color(0xFF232B26)
internal val BorderDark = Color(0xFF2C3833)
internal val OutlineDark = Color(0xFF3C4A43)
internal val ChipDark = Color(0xFF252E29)

/**
 * 深色主题下的顶光：上缘比画布亮半档、偏墨绿，往下沉回画布。
 *
 * 浅色主题靠「深底白字」拉开层级，深色主题里一块纯 #121614 的底则沉得发死；
 * 顶部亮半档既补上了同样的「光从上面来」，也不会亮到抢走英雄卡的对比。
 */
internal val PageTopDark = Color(0xFF1D2E25)
internal val PageMidDark = Color(0xFF161D19)
internal val PageGlowDark = Color(0x3D3E8F68)

/** 深色主题的玻璃卡：比画布亮一档，同样带透，让背后的顶光渗上来一点。 */
internal val GlassDark = Color(0xF7212A24)
internal val GlassBottomDark = Color(0xF01D2521)
internal val GlassBorderDark = Color(0x29FFFFFF)
internal val GlassShadowDark = Color(0xFF000000)

internal val TextPrimaryDark = Color(0xFFE8ECE8)
internal val TextSecondaryDark = Color(0xFF9AA39C)
internal val TextTertiaryDark = Color(0xFF7A837C)

// ————————————————————— 两个主题共用（深底上的颜色） —————————————————————

/**
 * 英雄卡上的标签底色（最苦主 / 福星 / 牌王）。
 *
 * 与 [WinDark] 被借去当英雄卡的亮色是同一个理由：列表上那三个标签都是「浅底深字」，
 * 压在深墨绿上要么糊进卡面（深色主题的容器色本就偏暗，品牌容器色更是与卡面几乎同色），
 * 要么与卡面糊成一片。英雄卡在两个主题下都是深底，需要的也就是同一组亮色，
 * 因此这里只写一份，不按主题各配一套。
 */
internal val HeroBadgeNeutral = Color(0xFF4A3F22)
internal val OnHeroBadgeNeutral = Color(0xFFF0E4C0)
internal val HeroBadgeGold = Color(0xFF54401A)
internal val OnHeroBadgeGold = Color(0xFFF7E3B0)
internal val HeroBadgeBrand = Color(0xFF22523C)
internal val OnHeroBadgeBrand = Color(0xFFD6F0E2)

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
    /** 荣誉标记的底色（牌王 / 第一名），比 [gold] 更适合铺面积。 */
    val goldContainer: Color,
    val onGoldContainer: Color,
    /** 英雄卡：各页顶部那块深墨绿，托住全页最大的数字。 */
    val heroSurface: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroChip: Color,
    val onHeroChip: Color,
    /** 英雄卡的纵向渐变顶端色：比 [heroSurface] 亮半档，给卡面一层「光照」。 */
    val heroSurfaceTop: Color,
    /** 英雄卡的纵向渐变底端色：比 [heroSurface] 深一档，给卡面一层「暗角」。 */
    val heroSurfaceBottom: Color,
    /**
     * 英雄卡里的输赢三色。
     *
     * 不能直接用 [win] / [loss]：那两个是给浅底配的深色，压在深墨绿上几乎看不见。
     * 这里固定取提亮后的一档，两个主题共用——英雄卡在两个主题下都是深底，
     * 需要的也就是同一组亮色。
     */
    val heroWin: Color,
    val heroLoss: Color,
    val heroDraw: Color,
    /**
     * 英雄卡上的标签胶囊（最苦主 / 福星 / 牌王）。
     *
     * 同 [heroWin] 的道理：列表上那套浅底容器色压在深墨绿上看不见，这里另给一组。
     */
    val heroBadgeNeutral: Color,
    val onHeroBadgeNeutral: Color,
    val heroBadgeGold: Color,
    val onHeroBadgeGold: Color,
    val heroBadgeBrand: Color,
    val onHeroBadgeBrand: Color,
    /**
     * 整页背景的晨光渐变。
     *
     * 页面一律由 [com.manote.app.ui.components.ManoteScaffold] 铺这一道，
     * 各页自己不画背景色——否则「页面底色」会有好几份说法。
     * 最下一段刻意停在画布色上：底部那颗悬浮按钮用画布色描了一圈外环，
     * 底端只要与画布差一点，那圈环就会露出来。
     */
    val pageBackground: Brush,
    /**
     * 叠在 [pageBackground] 之上、右上角那团柔光的**颜色**。
     *
     * 这里只存一个颜色而不是 Brush：光斑的圆心要落在屏幕右上方之外（按实际尺寸算），
     * 而 [Brush.radialGradient] 的圆心是像素单位、建 Brush 时还不知道尺寸。
     * 真正的径向渐变由 [com.manote.app.ui.components.ManoteScaffold] 在绘制阶段按
     * 画布大小现场构造，这里只负责给出那一层光的色相与浓度。
     */
    val pageGlow: Color,
    /** 卡片（[com.manote.app.ui.components.ManoteCard] 与场次行）的玻璃底，比页面那层更亮也更透。 */
    val cardSurface: Brush,
    /** 卡片边缘那圈高光细线。 */
    val glassBorder: Color,
    /** 卡片投影色。浅色主题取偏暖的褐灰，深色主题只能用黑。 */
    val shadowColor: Color,
)

/**
 * 三道背景在这里只写一次，两个主题各一份。
 *
 * 页面那道用一个中间停靠点（0.34）而不是从头到尾线性过渡：光要在第一屏内就落回画布，
 * 页面下半部分必须**严格等于**画布色——底部悬浮按钮的外环、编辑页的保存条
 * 都用画布色贴在同一条线上，渐变若在下面还留着一点点色差，它们就会显出边界。
 * 卡片那道只有两个停靠点：卡片本来就小，多一个停靠点只会让四边看起来不均匀。
 */
private val PageBackgroundLight = Brush.verticalGradient(
    0f to PageTopLight,
    0.34f to PageMidLight,
    0.74f to CanvasLight,
    1f to CanvasLight,
)
private val CardSurfaceLight = Brush.verticalGradient(
    0f to GlassLight,
    1f to GlassBottomLight,
)

/**
 * 右上角的柔光参数：圆心落在屏幕右上方之外，半径取到屏宽的一倍多。
 *
 * 圆心放到可见区域以外，页面上就只看得到光的「尾巴」——没有一道明确的圆边，
 * 只有一层从中间往右上渐渐变暖的空气。两个数字都由 Scaffold 在绘制时取用。
 */
internal const val PAGE_GLOW_CENTER_X = 0.84f
internal const val PAGE_GLOW_CENTER_Y = 0.04f
internal const val PAGE_GLOW_RADIUS_RATIO = 1.05f

private val PageBackgroundDark = Brush.verticalGradient(
    0f to PageTopDark,
    0.34f to PageMidDark,
    0.74f to CanvasDark,
    1f to CanvasDark,
)
private val CardSurfaceDark = Brush.verticalGradient(
    0f to GlassDark,
    1f to GlassBottomDark,
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
    goldContainer = GoldContainerLight,
    onGoldContainer = OnGoldContainerLight,
    heroSurface = HeroSurfaceLight,
    onHero = OnHeroLight,
    onHeroMuted = OnHeroMutedLight,
    heroChip = HeroChipLight,
    onHeroChip = OnHeroChipLight,
    heroSurfaceTop = HeroSurfaceTopLight,
    heroSurfaceBottom = HeroSurfaceBottomLight,
    heroWin = WinDark,
    heroLoss = LossDark,
    heroDraw = DrawDark,
    heroBadgeNeutral = HeroBadgeNeutral,
    onHeroBadgeNeutral = OnHeroBadgeNeutral,
    heroBadgeGold = HeroBadgeGold,
    onHeroBadgeGold = OnHeroBadgeGold,
    heroBadgeBrand = HeroBadgeBrand,
    onHeroBadgeBrand = OnHeroBadgeBrand,
    pageBackground = PageBackgroundLight,
    pageGlow = PageGlowLight,
    cardSurface = CardSurfaceLight,
    glassBorder = GlassBorderLight,
    shadowColor = GlassShadowLight,
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
    goldContainer = GoldContainerDark,
    onGoldContainer = OnGoldContainerDark,
    heroSurface = HeroSurfaceDark,
    onHero = OnHeroDark,
    onHeroMuted = OnHeroMutedDark,
    heroChip = HeroChipDark,
    onHeroChip = OnHeroChipDark,
    heroSurfaceTop = HeroSurfaceTopDark,
    heroSurfaceBottom = HeroSurfaceBottomDark,
    heroWin = WinDark,
    heroLoss = LossDark,
    heroDraw = DrawDark,
    heroBadgeNeutral = HeroBadgeNeutral,
    onHeroBadgeNeutral = OnHeroBadgeNeutral,
    heroBadgeGold = HeroBadgeGold,
    onHeroBadgeGold = OnHeroBadgeGold,
    heroBadgeBrand = HeroBadgeBrand,
    onHeroBadgeBrand = OnHeroBadgeBrand,
    pageBackground = PageBackgroundDark,
    pageGlow = PageGlowDark,
    cardSurface = CardSurfaceDark,
    glassBorder = GlassBorderDark,
    shadowColor = GlassShadowDark,
)

internal val LocalManoteExtendedColors = staticCompositionLocalOf { LightExtendedColors }
