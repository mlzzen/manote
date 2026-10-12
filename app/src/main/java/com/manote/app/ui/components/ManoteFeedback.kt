package com.manote.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.ManoteMotion
import com.manote.app.ui.theme.manote

/**
 * 按压缩放反馈：按下时整体缩到 [pressedScale]，松手由弹簧弹回。
 *
 * 「这一下点到了」是触屏上最基本的一条反馈线，纯水纹在浅底大卡上几乎看不见，
 * 缩放是更明确的一档。调用方负责创建 [MutableInteractionSource] 并把它同时
 * 交给本修饰符与对应的 `clickable` / `Button`，两边才会看到同一份按压状态。
 *
 * 缩放值在绘制阶段读取（graphicsLayer 块内取 State），动画期间不触发重组。
 */
@Composable
fun Modifier.manotePress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale: State<Float> = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = ManoteMotion.pressSpring(),
        label = "manotePressScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin.Center
    }
}

/**
 * 带符号金额的滚动文本：数字从旧值平滑长到新值，颜色随输赢变化。
 *
 * 用在英雄卡上：净输赢是全应用最重要的一个数，筛选切换、记完一场回来时
 * 让它「长」出来，比直接跳变更能把「变了」这件事讲清楚。
 * 等宽数字（tnum 已在主题里全局开启）保证滚动过程中位数不抖。
 *
 * [color] 缺省时按正负取输赢色，与 [SignedAmount] 同一口径；
 * 落在英雄卡深底上时由调用方传 `heroWin` / `heroLoss` / `onHero` 那一组。
 */
@Composable
fun AnimatedSignedYuan(
    netMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color? = null,
) {
    val colors = MaterialTheme.manote
    val resolvedColor = color ?: when {
        netMinor > 0L -> colors.win
        netMinor < 0L -> colors.loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 动画用 Int 承载「分」：Int 最大约 ¥2,147 万，远超个人账本的量级；
    // 结束帧精确落在目标值上，精度与直接显示一致。
    val animated by animateIntAsState(
        targetValue = netMinor.toInt(),
        animationSpec = tween(ManoteMotion.DURATION_SLOW, easing = ManoteMotion.EASING_STANDARD),
        label = "animatedSignedYuan",
    )
    Text(
        text = UiText.signedYuan(animated.toLong()),
        style = style,
        color = resolvedColor,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 选中态颜色的渐变包装：胶囊、页签这类「二态色」共用同一段时长与曲线。
 * 单独抽出来是因为各处手写 animateColorAsState 时最容易漏掉 easing，
 * 而颜色切换没有 easing 时会显得生硬。
 */
@Composable
fun animateManoteSelectionColor(target: Color): Color = animateColorAsState(
    targetValue = target,
    animationSpec = tween(ManoteMotion.DURATION_NORMAL, easing = ManoteMotion.EASING_STANDARD),
    label = "manoteSelectionColor",
).value
