package com.manote.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manote.app.domain.TrendPoint
import com.manote.app.ui.theme.manote

/**
 * 累计净额走势折线图。
 *
 * 纵轴以 0 为参照：曲线画在 0 线之上表示累计赢钱，之下表示累计输钱，
 * 因此 0 线是一条固定的虚线，曲线越过它就代表由赢转输（或反过来）。
 *
 * 只有一个点时画一个圆点而不是一条线——那一点是一个真实结果，
 * 画成线会凭空多出一个并不存在的「第二个点」。
 */
@Composable
fun NetTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
) {
    if (points.isEmpty()) return

    val colors = MaterialTheme.manote
    val last = points.last().cumulativeMinor
    // 终点为正用红（赢），为负用绿（输），持平走中性色：与全应用的红赢绿输一致
    val lineColor: Color = when {
        last > 0L -> colors.win
        last < 0L -> colors.loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val dotRingColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val values = points.map { it.cumulativeMinor.toFloat() }
        // 纵轴范围必须含 0（否则 0 线无从画起）
        val rawLow = minOf(values.minOrNull() ?: 0f, 0f)
        val rawHigh = maxOf(values.maxOrNull() ?: 0f, 0f)
        // 全程持平时（连着几场都打平）上下各撑开一点，
        // 让曲线落在图中央，而不是贴着上边缘或下边缘。
        val flat = rawHigh - rawLow == 0f
        val lo = if (flat) rawLow - 1f else rawLow
        val hi = if (flat) rawHigh + 1f else rawHigh
        val range = hi - lo

        val inset = 12.dp.toPx()
        val usable = (size.height - inset * 2).coerceAtLeast(1f)
        val xStep = if (points.size > 1) size.width / (points.size - 1) else 0f

        fun xOf(index: Int): Float =
            if (points.size > 1) index * xStep else size.width / 2f

        fun yOf(value: Float): Float = inset + (hi - value) / range * usable

        val zeroY = yOf(0f)

        // 0 线：虚线，让「赢 / 输的分界」一眼可辨
        drawLine(
            color = zeroLineColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                0f,
            ),
        )

        if (points.size == 1) {
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(xOf(0), yOf(values[0])))
            return@Canvas
        }

        val linePath = Path()
        points.forEachIndexed { index, _ ->
            val x = xOf(index)
            val y = yOf(values[index])
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // 曲线与 0 线之间的面积填充：涨跌方向比一条细线更容易看出来
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(xOf(points.lastIndex), zeroY)
            lineTo(xOf(0), zeroY)
            close()
        }
        drawPath(path = areaPath, color = lineColor.copy(alpha = AREA_ALPHA))

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 终点：加一圈底色描边，压在虚线上也能看清
        val lastPoint = Offset(xOf(points.lastIndex), yOf(values.last()))
        drawCircle(color = dotRingColor, radius = 5.dp.toPx(), center = lastPoint)
        drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = lastPoint)
    }
}

/** 面积填充的不透明度：够看出方向，又不至于盖住 0 线与终点。 */
private const val AREA_ALPHA = 0.18f
