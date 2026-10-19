package com.manote.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * 全应用动效令牌：时长、缓动曲线与弹簧规格。
 *
 * 动效只在「帮助理解」的地方出现：页面切换（从哪里来、回到了哪里）、
 * 按压反馈（这一下点到了）、数字滚动（新数从旧数长出来）、列表重排（谁挪了位置）。
 * 所有动效一律取这里的常量，不得在页面里散落手写的 tween 数值与曲线 ——
 * 参数散了，各处的手感就会各自为政。
 */
object ManoteMotion {

    /** 快：退场、次要元素淡出。退场要比进场快，把位置让出来。 */
    const val DURATION_FAST = 120

    /** 标准：颜色渐变、胶囊选中态、图标微缩放。 */
    const val DURATION_NORMAL = 220

    /** 慢：子页面进场、数字滚动。 */
    const val DURATION_SLOW = 320

    /** 标准缓动：起步快、收尾缓。进场与大部分状态切换用它。 */
    val EASING_STANDARD = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 退场缓动：先慢后快地离开，与标准缓动配成一对。 */
    val EASING_EXIT = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /**
     * 按压反馈的弹簧：略带回弹，松手时「弹起来」的手感来自它。
     * 回弹太强会像果冻，这里取偏阻尼的一档。
     */
    fun pressSpring(): SpringSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 列表项重排（筛选、搜索后换位）的弹簧：比按压更柔，位置感优先。 */
    fun placementSpring(): SpringSpec<IntOffset> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
