package com.manote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manote.app.domain.DayNet
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 月历网格：一天一格，格子上写当天的净输赢。
 *
 * 赢的天铺淡红底、输的天铺淡绿底、持平的天铺米黄底 —— 与全应用的红赢绿输同一套。
 * **没打过的那天保持透明**：把空格子也铺上一层「0 元」的颜色，
 * 会让「这天没打」看起来像「这天打平」，而这是两件事。
 *
 * 恒画 6 行。2 月从周一开始时其实 4 行就够，但如果行数随月份变，
 * 切换月份时下面的内容会上下跳一下；留一行空白换来位置的稳定，值。
 *
 * 星期名固定中文（周一起），不跟设备语言走：界面文案虽然是两套，
 * 但日期格子只有 40dp 宽，`M T W T F S S` 与 `一 二 三` 混排反而更乱，
 * 也与 [com.manote.app.ui.model.todayLabel] 里星期名的取舍保持一致。
 */
@Composable
fun MonthCalendarGrid(
    month: YearMonth,
    days: Map<LocalDate, DayNet>,
    modifier: Modifier = Modifier,
    /** 高亮用的「今天」；不在本月时不画圈。 */
    today: LocalDate? = null,
    cellHeight: Dp = 42.dp,
) {
    val weeks = remember(month) { weeksOf(month) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight),
                        )
                    } else {
                        DayCell(
                            date = date,
                            net = days[date],
                            isToday = date == today,
                            cellHeight = cellHeight,
                        )
                    }
                }
            }
        }
    }
}

/** 一天的格子：日号在上、当天净额在下。 */
@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    net: DayNet?,
    isToday: Boolean,
    cellHeight: Dp,
) {
    val colors = MaterialTheme.manote
    val shape = MaterialTheme.shapes.extraSmall
    val won = net != null && net.netMinor > 0L
    val lost = net != null && net.netMinor < 0L

    val container: Color = when {
        won -> colors.winContainer
        lost -> colors.lossContainer
        net != null -> colors.drawContainer
        else -> Color.Transparent
    }
    val content: Color = when {
        won -> colors.onWinContainer
        lost -> colors.onLossContainer
        net != null -> colors.onDrawContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(cellHeight)
            .clip(shape)
            .background(container)
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
            if (net != null) {
                Text(
                    text = UiText.compactSignedYuan(net.netMinor),
                    // 比 labelSmall 再小一档：格子里要塞下「+1,239」这类五六个字符
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

/**
 * 把一个月摊成 6×7 的格子，周一开头，不足处补 null。
 *
 * 补到 42 格而不是「凑够整周就停」：行数固定，切换月份时网格高度不动。
 */
private fun weeksOf(month: YearMonth): List<List<LocalDate?>> {
    val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = ArrayList<LocalDate?>(TOTAL_CELLS)
    repeat(lead) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size < TOTAL_CELLS) cells.add(null)
    return cells.chunked(DAYS_PER_WEEK)
}

private const val DAYS_PER_WEEK = 7
private const val TOTAL_CELLS = DAYS_PER_WEEK * 6

/** `一 二 三 四 五 六 日`，固定中文（见文件头注释）。 */
private val WEEKDAY_LABELS: List<String> = (1..DAYS_PER_WEEK).map { day ->
    DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.CHINA)
}
