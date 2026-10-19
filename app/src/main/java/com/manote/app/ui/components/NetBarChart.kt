package com.manote.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manote.app.domain.DatedNet
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote
import java.time.ZoneId

/**
 * 逐场净额柱状图：一根柱子就是一场，向上是赢、向下是输。
 *
 * 与 [NetTrendChart] 的分工：那张图画的是**累计**（越打越厚的总账），
 * 这张画的是**单场**（最近这几场各自打成什么样）。
 * 累计曲线一路向上时，单场柱子仍可能东倒西歪——后者才是「手气」的形状。
 *
 * 纵向不画刻度尺，而是把**这一场的确切金额写在柱子末端**：
 * 十根柱子里要回答的是「上周五赢了多少」，不是「大概是 0 到 500 之间的某个值」，
 * 一个精确的数比一根要对齐刻度的条子直接得多。金额带正负号，
 * 不只靠柱子朝上朝下表达输赢（规格 2.1、3.6）。
 *
 * 柱子从中间那条基线垂下来：基线之上是赢、之下是输，与记账时的直觉一致。
 * 上下两侧各按各自的最大值分配高度，因此 +500 与 -500 会对称地一样长；
 * 持平的场次画一根 2dp 的小墩子——0 也是一个真实结果，不能什么都不画。
 */
@Composable
fun NetBarChart(
    items: List<DatedNet>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    if (items.isEmpty()) return

    val colors = MaterialTheme.manote
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val drawColor = colors.draw
    val scale = remember(items) { BarScale.of(items) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val slot = size.width / items.size
                val barWidth = (slot * BAR_WIDTH_RATIO).coerceAtMost(MAX_BAR_WIDTH.toPx())
                val radius = CornerRadius(barWidth / 2f)
                val zeroY = size.height * scale.baselineFraction
                val stub = 2.dp.toPx()

                // 基线：整条横贯，柱子压在它上面
                drawLine(
                    color = baselineColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.dp.toPx(),
                )

                items.forEachIndexed { index, item ->
                    val centerX = slot * index + slot / 2f
                    val valueY = size.height * scale.endFraction(item.netMinor)
                    val top = minOf(valueY, zeroY)
                    val bottom = maxOf(valueY, zeroY)
                    val barHeight = (bottom - top)
                        .coerceAtLeast(if (item.netMinor == 0L) stub else 1.5f)

                    drawRoundRect(
                        color = when {
                            item.netMinor > 0L -> colors.win
                            item.netMinor < 0L -> colors.loss
                            else -> drawColor
                        },
                        topLeft = Offset(centerX - barWidth / 2f, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius,
                    )
                }
            }

            // 金额与日期分别写在基线**两侧**，各自贴着柱子的那一端：
            // 输的柱子垂在基线下面，金额写在柱底之下、日期就回到基线上方；
            // 赢的柱子立在基线上面，金额在柱顶之上、日期落到基线下方。
            // 两行字永远不会挤在同一侧，也永远贴着它们各自要说明的那一端。
            // 与柱子共用同一个等分槽位，因此不必在 Canvas 里量文字。
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isWin = item.netMinor > 0L
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(
                            text = UiText.compactSignedYuan(item.netMinor),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = when {
                                isWin -> colors.win
                                item.netMinor < 0L -> colors.loss
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .offset(
                                    y = height * scale.endFraction(item.netMinor) +
                                        if (isWin) -AMOUNT_LABEL_ROOM else AMOUNT_LABEL_GAP,
                                ),
                        )

                        Text(
                            // 横轴标签用最短写法（6/2 而不是 06.02）：十根柱子并排时
                            // 每格只有三四个字宽，补零只会把月份挤成省略号
                            text = UiText.monthSlashDay(item.playedAt, zoneId),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                // 金额在上，日期就在基线之下；金额在下，日期就在基线之上
                                .offset(
                                    y = height * scale.baselineFraction +
                                        if (isWin) DATE_LABEL_GAP else -DATE_LABEL_ROOM,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 柱子的宽度占槽位的比例。
 *
 * 比一列文字窄得多：柱子是「一根签」，不是「一块砖」；槽位之间留下的空气
 * 让十根柱子各自可分，也给了金额小字落脚的地方。
 */
private const val BAR_WIDTH_RATIO = 0.26f

/** 柱子最宽 10dp：场次少（比如只有两场）时槽位很宽，柱子不能跟着胖成方块。 */
private val MAX_BAR_WIDTH = 10.dp

/** 赢的金额写在柱顶之上，需要预留一行的高度。 */
private val AMOUNT_LABEL_ROOM = 16.dp

/** 输的金额写在柱底之下，与柱端留 2dp 呼吸。 */
private val AMOUNT_LABEL_GAP = 2.dp

/** 日期写在基线另一侧，同样需要一行的高度。 */
private val DATE_LABEL_ROOM = 15.dp

/** 日期贴基线的那一侧留 2dp：紧贴但不压线。 */
private val DATE_LABEL_GAP = 2.dp

/**
 * 纵向映射：基线在哪个高度、某个金额落在哪个高度。
 *
 * 上下两侧按各自的最大值分配空间（`maxUp + |maxDown|` 为总跨度），
 * 这样一根 +500 与一根 -500 会对称地一样长，不会被画成一长一短。
 *
 * 最大的一根不顶到边：顶端与底端各留一行给金额小字，否则「+26」会被裁掉一半。
 */
private class BarScale(
    private val maxUp: Long,
    private val maxDown: Long,
) {
    /** 基线在整块画面里的位置，0 = 顶端、1 = 底端。 */
    val baselineFraction: Float

    private val span: Float

    init {
        val up = if (maxUp > 0L) maxUp.toFloat() else 0f
        val down = if (maxDown < 0L) -maxDown.toFloat() else 0f
        val total = up + down
        if (total <= 0f) {
            // 全程持平：基线居中，柱子在中间画一根小墩子
            baselineFraction = 0.5f
            span = 1f
        } else {
            // 上下各留出 LABEL_BAND 的固定比例给金额小字
            val usable = 1f - LABEL_BAND * 2f
            val upShare = if (total <= 0f) 0f else up / total
            baselineFraction = LABEL_BAND + usable * upShare
            span = total
        }
    }

    /** 某个金额在画面里的位置，0 = 顶端、1 = 底端。 */
    fun endFraction(netMinor: Long): Float {
        if (span <= 0f) return baselineFraction
        val usable = 1f - LABEL_BAND * 2f
        val offset = netMinor / span * usable
        return (baselineFraction - offset).coerceIn(0f, 1f)
    }

    companion object {
        /** 上下两端留给金额小字的高度占比。 */
        private const val LABEL_BAND = 0.17f

        fun of(items: List<DatedNet>): BarScale = BarScale(
            maxUp = (items.maxOfOrNull { it.netMinor } ?: 0L).coerceAtLeast(0L),
            maxDown = (items.minOfOrNull { it.netMinor } ?: 0L).coerceAtMost(0L),
        )
    }
}
