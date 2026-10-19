package com.manote.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.DayNet
import com.manote.app.domain.MonthSummary
import com.manote.app.ui.components.AnimatedSignedYuan
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManoteHeroChip
import com.manote.app.ui.components.ManoteReadout
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.components.MonthCalendarGrid
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历页：逐月翻看，一天一格。
 *
 * 首页那张月历只回答「这个月怎么样」，再多一步就要换页——所以这里把翻月做成主角：
 * 年与月各自一组箭头，可以只改年、只改月；翻到边界（最早一条记录所在月 / 本月）
 * 箭头会变灰，而不是让用户在一片空白格子里往前翻。
 *
 * 页面结构与首页同一套语言：上面是英雄卡（当月净输赢），中间一张月历卡，
 * 下面是最佳 / 最差单日，最后是这个月的场次；点某一场仍旧进四人详情。
 *
 * 所有数字都由订阅到的全部场次实时算出（规格 2.4），没有第二份月度账本。
 */
@Composable
fun CalendarScreen(
    initialMonth: YearMonth,
    onNavigateBack: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        CalendarViewModel(
            sessionRepository = container.sessionRepository,
            initialMonth = initialMonth,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarContent(
        state = state,
        onSelectMonth = viewModel::select,
        onCurrentMonth = viewModel::goToCurrentMonth,
        onNavigateBack = onNavigateBack,
        onOpenSessionDetail = onOpenSessionDetail,
        modifier = modifier,
    )
}

@Composable
internal fun CalendarContent(
    state: CalendarUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onCurrentMonth: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.calendar_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        actions = {
            // 翻远了想回来时，回「本月」要有一步直达，不必按月倒着数
            if (!state.isCurrentMonth) {
                ManoteTextAction(
                    label = stringResource(R.string.calendar_current_month),
                    onClick = onCurrentMonth,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 首帧还没读到时不画任何东西，避免先闪一句「这个月一场都没记」
            if (state.loading) return@Column

            MonthSwitcher(
                month = state.month,
                canGoEarlier = state.canGoEarlier,
                canGoLater = state.canGoLater,
                onSelectMonth = onSelectMonth,
            )

            MonthHeroCard(summary = state.summary)

            MonthCalendarCard(summary = state.summary, today = state.today)

            if (state.summary.hasSessions) {
                MonthExtremesCard(summary = state.summary)

                ManoteSectionHeader(
                    title = stringResource(R.string.calendar_sessions_title),
                    trailing = {
                        Text(
                            text = stringResource(
                                R.string.calendar_sessions_count_format,
                                state.summary.sessionCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.sessions.forEach { row ->
                        ManoteSessionRow(
                            row = row,
                            onClick = { onOpenSessionDetail(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 年月切换：左边一颗「年」、右边一颗「月」，各自带左右箭头。
 *
 * 拆成两颗而不是一颗「上一月 / 下一月」：想从 2026 年 10 月跳到 2024 年 3 月，
 * 按月倒要按 19 次。年与月分开之后，改年只动年、改月只动月，
 * 而且两颗的边界逻辑是同一个（最早一条记录所在月 ～ 本月），不会各走各的。
 */
@Composable
private fun MonthSwitcher(
    month: YearMonth,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    onSelectMonth: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonthStepper(
            label = stringResource(R.string.calendar_year_format, month.year),
            onEarlier = { onSelectMonth(month.minusYears(1)) },
            onLater = { onSelectMonth(month.plusYears(1)) },
            earlierEnabled = canGoEarlier,
            laterEnabled = canGoLater,
            earlierDescription = stringResource(R.string.calendar_prev_year),
            laterDescription = stringResource(R.string.calendar_next_year),
        )
        MonthStepper(
            label = stringResource(R.string.calendar_month_format, month.monthValue),
            onEarlier = { onSelectMonth(month.minusMonths(1)) },
            onLater = { onSelectMonth(month.plusMonths(1)) },
            earlierEnabled = canGoEarlier,
            laterEnabled = canGoLater,
            earlierDescription = stringResource(R.string.calendar_prev_month),
            laterDescription = stringResource(R.string.calendar_next_month),
        )
    }
}

/** 一颗「‹ 2026 年 ›」：中间是读数，两边是 44dp 高的点击区。 */
@Composable
private fun RowScope.MonthStepper(
    label: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    earlierEnabled: Boolean,
    laterEnabled: Boolean,
    earlierDescription: String,
    laterDescription: String,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = earlierDescription,
            enabled = earlierEnabled,
            onClick = onEarlier,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        StepperArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = laterDescription,
            enabled = laterEnabled,
            onClick = onLater,
        )
    }
}

@Composable
private fun StepperArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // 到头了就把箭头压到几乎看不见：能点与不能点必须在按下去之前就分得清
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 当月净输赢 + 三个读数胶囊。深墨绿底，与其它页的英雄卡同一个位置、同一种材质。 */
@Composable
private fun MonthHeroCard(summary: MonthSummary) {
    val colors = MaterialTheme.manote

    ManoteHeroCard {
        Text(
            text = stringResource(R.string.calendar_hero_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHeroMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 翻月份时这个数从旧值「长」出来，切换才看得出是一次重算而不是重绘
        AnimatedSignedYuan(
            netMinor = summary.netMinor,
            style = MaterialTheme.typography.headlineLarge,
            color = when {
                summary.netMinor > 0L -> colors.heroWin
                summary.netMinor < 0L -> colors.heroLoss
                else -> colors.onHero
            },
        )

        if (!summary.hasSessions) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
            return@ManoteHeroCard
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ManoteHeroChip(
                stringResource(R.string.home_slot_count_format, summary.sessionCount),
            )
            ManoteHeroChip(
                stringResource(R.string.calendar_stat_win_rate) + " ${summary.winRatePercent}%",
            )
            ManoteHeroChip(
                stringResource(R.string.calendar_hero_days_format, summary.playedDayCount),
            )
        }
    }
}

/** 月历卡：一张网格，格子上的数字即当天净额。 */
@Composable
private fun MonthCalendarCard(summary: MonthSummary, today: LocalDate) {
    ManoteCard {
        MonthCalendarGrid(
            month = summary.yearMonth,
            days = summary.days,
            today = today,
            // 比首页那张高一档：这一页只有它一个主角，格子大一点更好读金额
            cellHeight = 48.dp,
        )
    }
}

/**
 * 当月极值：最佳 / 最差单日，外加场均值。
 *
 * 场数、胜率、出勤已经由英雄卡的胶囊给出，这里不重复——
 * 一屏之内同一个数只出现一次。
 */
@Composable
private fun MonthExtremesCard(summary: MonthSummary) {
    ManoteCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            ManoteReadout(
                label = stringResource(R.string.stat_average),
                value = UiText.signedYuan(summary.statistics.averageNetMinor),
                valueColor = signedColor(summary.statistics.averageNetMinor),
                modifier = Modifier.weight(1f),
            )
            DayExtremeReadout(
                label = stringResource(R.string.calendar_best_day),
                day = summary.bestDay,
            )
            DayExtremeReadout(
                label = stringResource(R.string.calendar_worst_day),
                day = summary.worstDay,
            )
        }
    }
}

@Composable
private fun RowScope.DayExtremeReadout(label: String, day: DayNet?) {
    ManoteReadout(
        label = label,
        value = if (day == null) {
            stringResource(R.string.common_unavailable)
        } else {
            UiText.signedYuan(day.netMinor)
        },
        valueColor = if (day == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            signedColor(day.netMinor)
        },
        caption = day?.let {
            UiText.monthDay(it.day) + " · " +
                stringResource(R.string.home_streak_count_format, it.sessionCount)
        },
        modifier = Modifier.weight(1.2f),
    )
}

/** 金额的正负配色：赢红、输绿、持平走中性色，与全应用口径一致。 */
@Composable
private fun signedColor(minor: Long) = when {
    minor > 0L -> MaterialTheme.manote.win
    minor < 0L -> MaterialTheme.manote.loss
    else -> MaterialTheme.colorScheme.onSurface
}
