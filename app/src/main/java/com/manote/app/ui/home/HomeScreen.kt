package com.manote.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.DatedNet
import com.manote.app.domain.DayNet
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.SessionExtremes
import com.manote.app.domain.StreakStats
import com.manote.app.domain.TimeSlot
import com.manote.app.domain.TimeSlotStatistics
import com.manote.app.domain.TrendPoint
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.AnimatedSignedYuan
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.ManoteReadout
import com.manote.app.ui.components.ManoteResultTile
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.components.ManoteShareBar
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.components.NetTrendChart
import com.manote.app.ui.components.TimeSlotTag
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.todayLabel
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.ManoteTheme
import com.manote.app.ui.theme.manote
import java.time.LocalDate

/**
 * 首页（规格 3.1）。
 *
 * 结构自上而下：英雄卡（累计净输赢）→ 战绩概览 → 累计净额曲线 → 分时段战绩
 * → 连输 / 连赢 → 最近场次 → 记一场。
 *
 * 只有「累计净输赢」一个数字被放大到 headlineLarge 并放进深墨绿底：
 * 全页其余读数都是同一档小字。此前所有读数同字号，满屏平铺，
 * 眼睛没有落点；现在先给结论，再给依据。
 *
 * 所有数字都来自 `session_result` 的实时汇总（规格 2.4），没有记录时显示 0.00 元。
 * 「今日宜忌 / 牌运」属规格外内容，不做。
 */
@Composable
fun HomeScreen(
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        HomeViewModel(container.playerRepository, container.sessionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onSelectTab = onSelectTab,
        onRecordSession = onRecordSession,
        onOpenSessionDetail = onOpenSessionDetail,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 顶栏上的日期。默认取设备时区的今天，预览与测试传入固定日期。 */
    today: LocalDate = LocalDate.now(),
) {
    ManoteScaffold(
        // 首页的标题就是应用名：品牌标记出现在四个页签顶栏的同一位置，
        // 翻页时那个方块不动，只有它右边的字在变。
        title = stringResource(R.string.app_name),
        branded = true,
        modifier = modifier,
        actions = {
            ManotePill(
                text = todayLabel(today),
                modifier = Modifier.padding(end = 6.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                verticalPadding = 5.dp,
            )
        },
        bottomBar = {
            ManoteBottomBar(
                current = ManoteTab.HOME,
                onSelect = onSelectTab,
                onNewSession = onRecordSession,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                statistics = state.statistics,
                trend = state.trend,
                loading = state.loading,
            )

            // 一场都没记时不显示概览、走势与分时段战绩，避免三行全是「—」的空表；
            // 空态已经由英雄卡上的 0.00 与「最近场次」里的说明覆盖了。
            if (state.statistics.sessionCount > 0) {
                ManoteSectionHeader(title = stringResource(R.string.home_overview_title))
                BreakdownCard(statistics = state.statistics, extremes = state.extremes)

                ManoteSectionHeader(title = stringResource(R.string.home_trend_title))
                NetTrendCard(trend = state.trend)

                ManoteSectionHeader(title = stringResource(R.string.home_slot_title))
                SlotStatisticsCard(slots = state.slotStatistics)

                ManoteSectionHeader(title = stringResource(R.string.home_streak_title))
                StreakCard(stats = state.streak)
            }

            ManoteSectionHeader(
                title = stringResource(R.string.home_recent_title),
                trailing = {
                    ManoteTextAction(
                        label = stringResource(R.string.action_view_all),
                        onClick = { onSelectTab(ManoteTab.SESSIONS) },
                    )
                },
            )

            if (state.recentSessions.isEmpty()) {
                // 首帧还没有读到场次时不显示空态，避免闪一下「暂无记录」
                if (!state.loading) {
                    NoRecentSessions()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.recentSessions.forEach { row ->
                        ManoteSessionRow(
                            row = row,
                            onClick = { onOpenSessionDetail(row.id) },
                        )
                    }
                }
            }

            ManotePrimaryButton(
                text = stringResource(R.string.action_record_session),
                onClick = onRecordSession,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * 英雄卡：累计净输赢 + 三个读数胶囊 + 迷你走势。
 *
 * 它是全页唯一一块深底，也是唯一把金额放大到 headlineLarge 的地方。
 * 迷你走势用卡内的薄荷色而非红绿：深墨绿底上红绿都会糊掉，
 * 且这一条曲线只是「大致的方向」，精确的读数由下面那张曲线卡负责。
 */
@Composable
private fun HeroCard(
    statistics: PlayerStatistics,
    trend: List<TrendPoint>,
    loading: Boolean,
) {
    val colors = MaterialTheme.manote

    ManoteHeroCard {
        Text(
            text = stringResource(R.string.home_stat_net),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHeroMuted,
        )
        Spacer(modifier = Modifier.height(2.dp))
        // 记完一场回到首页，这个数是从旧值「长」出来的：变化本身也是一条信息
        AnimatedSignedYuan(
            netMinor = statistics.netMinor,
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onHero,
        )

        if (statistics.sessionCount == 0) {
            // 首帧还没读到场次时不写死一句「还没有记录」，避免闪一下
            if (!loading) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_overview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted,
                )
            }
            return@ManoteHeroCard
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroChip(stringResource(R.string.home_hero_sessions_format, statistics.sessionCount))
            HeroChip(
                stringResource(R.string.home_stat_win_rate) + " " +
                    "${UiText.percent(statistics.winCount, statistics.sessionCount)}%",
            )
            HeroChip(
                stringResource(R.string.home_stat_net_wins) + " " +
                    "${statistics.winCount - statistics.lossCount}",
            )
        }

        // 只有一个点时画不出走势，卡上就只留读数
        if (trend.size >= 2) {
            Spacer(modifier = Modifier.height(10.dp))
            NetTrendChart(
                points = trend,
                height = 46.dp,
                lineColorOverride = colors.onHeroMuted,
                dotRingColorOverride = colors.heroSurface,
                areaAlpha = 0.22f,
                showZeroLine = false,
                verticalInset = 4.dp,
            )
        }
    }
}

/** 英雄卡里的小胶囊。底色比卡身亮一档，在深绿上是一块浅浅的牌子。 */
@Composable
private fun HeroChip(text: String) {
    val colors = MaterialTheme.manote
    ManotePill(
        text = text,
        containerColor = colors.heroChip,
        contentColor = colors.onHeroChip,
        style = MaterialTheme.typography.labelSmall,
        verticalPadding = 4.dp,
    )
}

/**
 * 战绩概览：胜 / 负 / 平 三色格 + 占比条 + 场均、单场赢最多 / 输最多。
 *
 * 本页的净输赢已经由英雄卡承担，这里不再重复——同一屏上出现两次同一个数字，
 * 读者会先去找它们的差别，而它们并没有差别。
 */
@Composable
private fun BreakdownCard(
    statistics: PlayerStatistics,
    extremes: SessionExtremes,
) {
    val colors = MaterialTheme.manote

    ManoteCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManoteResultTile(
                label = stringResource(R.string.result_win),
                count = statistics.winCount,
                amountMinor = statistics.winMinor,
                containerColor = colors.winContainer,
                contentColor = colors.onWinContainer,
                accentColor = colors.win,
                modifier = Modifier.weight(1f),
            )
            ManoteResultTile(
                label = stringResource(R.string.result_loss),
                count = statistics.lossCount,
                amountMinor = -statistics.lossMinor,
                containerColor = colors.lossContainer,
                contentColor = colors.onLossContainer,
                accentColor = colors.loss,
                modifier = Modifier.weight(1f),
            )
            ManoteResultTile(
                label = stringResource(R.string.result_draw),
                count = statistics.drawCount,
                amountMinor = 0L,
                containerColor = colors.drawContainer,
                contentColor = colors.onDrawContainer,
                accentColor = colors.draw,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        ManoteShareBar(
            winCount = statistics.winCount,
            lossCount = statistics.lossCount,
            drawCount = statistics.drawCount,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Metric(
                label = stringResource(R.string.stat_average),
                value = UiText.signedYuan(statistics.averageNetMinor),
                valueColor = signedColor(statistics.averageNetMinor),
                modifier = Modifier.weight(1f),
            )
            SessionExtremeReadout(
                label = stringResource(R.string.stat_best_win),
                extreme = extremes.bestWin,
            )
            SessionExtremeReadout(
                label = stringResource(R.string.stat_worst_loss),
                extreme = extremes.worstLoss,
            )
        }
    }
}

/**
 * 单场极值读数：金额 + 「哪一场」。
 *
 * 没有这样的场次（从没赢过 / 从没输过）时整块显示占位符，
 * 而不是 ¥0.00——那会被读成「有过一场刚好持平的记录」，是另一个事实。
 * 日期小字与「最佳 / 最差单日」一样给出来，否则一个孤零零的金额无从追溯。
 */
@Composable
private fun RowScope.SessionExtremeReadout(label: String, extreme: DatedNet?) {
    Metric(
        label = label,
        value = if (extreme == null) {
            stringResource(R.string.common_unavailable)
        } else {
            UiText.signedYuan(extreme.netMinor)
        },
        valueColor = if (extreme == null) mutedColor() else signedColor(extreme.netMinor),
        caption = extreme?.let { UiText.monthDay(it.playedAt) },
        modifier = Modifier.weight(1f),
    )
}

/**
 * 分时段战绩：早场 / 下午场 / 晚场各自的场数、胜率与场均。
 *
 * 表头与三行数据共用同一组列权重，右对齐后严格成列。
 * 时段名沿用列表里那个品牌色标签，于是「下午场」在列表、详情与本表里是同一种符号。
 * 某段没有记录时显示「—」而不是 0% / ¥0.00：
 * 「这一段没打过」和「打了但持平」必须能一眼分开（规格 3.6 的信息诚实原则）。
 */
@Composable
private fun SlotStatisticsCard(slots: List<TimeSlotStatistics>) {
    ManoteCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(SLOT_NAME_WEIGHT))
            SlotColumnHeader(
                text = stringResource(R.string.home_slot_count),
                weight = SLOT_COUNT_WEIGHT,
            )
            SlotColumnHeader(
                text = stringResource(R.string.home_stat_win_rate),
                weight = SLOT_METRIC_WEIGHT,
            )
            SlotColumnHeader(
                text = stringResource(R.string.stat_average),
                weight = SLOT_METRIC_WEIGHT,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        slots.forEach { slot ->
            SlotStatRow(slot = slot)
        }
    }
}

@Composable
private fun RowScope.SlotColumnHeader(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun SlotStatRow(slot: TimeSlotStatistics) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val placeholder = stringResource(R.string.common_unavailable)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(SLOT_NAME_WEIGHT)) {
            TimeSlotTag(slot = slot.slot)
        }
        SlotValue(
            text = if (slot.isEmpty) placeholder else stringResource(
                R.string.home_slot_count_format,
                slot.sessionCount,
            ),
            color = muted,
            weight = SLOT_COUNT_WEIGHT,
        )
        SlotValue(
            text = if (slot.isEmpty) placeholder else "${slot.winRatePercent}%",
            color = if (slot.isEmpty) muted else MaterialTheme.colorScheme.onSurface,
            weight = SLOT_METRIC_WEIGHT,
        )
        SlotValue(
            text = if (slot.isEmpty) placeholder else UiText.signedYuan(slot.averageNetMinor),
            color = if (slot.isEmpty) muted else signedColor(slot.averageNetMinor),
            weight = SLOT_METRIC_WEIGHT,
        )
    }
}

@Composable
private fun RowScope.SlotValue(text: String, color: Color, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

/** 列宽权重：时段名稍宽，「场数」最窄，两个指标等宽。 */
private const val SLOT_NAME_WEIGHT = 1.0f
private const val SLOT_COUNT_WEIGHT = 0.7f
private const val SLOT_METRIC_WEIGHT = 0.9f

/**
 * 累计净额曲线：本人跨场的累计盈亏走势，外加最新 / 区间最高 / 区间最低三个读数。
 *
 * 英雄卡上那条迷你走势只给方向，这里给坐标：0 线标出赢输分界，三个读数给出具体数值。
 * 只有一个点时画不出「走势」，这里给出说明而不是画一条毫无意义的直线。
 */
@Composable
private fun NetTrendCard(trend: List<TrendPoint>) {
    ManoteCard {
        if (trend.size < 2) {
            Text(
                text = stringResource(R.string.home_trend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManoteCard
        }

        NetTrendChart(points = trend)

        Spacer(modifier = Modifier.height(12.dp))

        val values = trend.map { it.cumulativeMinor }
        val latest = values.last()
        val highest = values.maxOrNull() ?: 0L
        val lowest = values.minOrNull() ?: 0L
        Row(modifier = Modifier.fillMaxWidth()) {
            ManoteReadout(
                label = stringResource(R.string.trend_latest),
                value = UiText.signedYuan(latest),
                valueColor = signedColor(latest),
                modifier = Modifier.weight(1f),
            )
            ManoteReadout(
                label = stringResource(R.string.trend_highest),
                value = UiText.signedYuan(highest),
                valueColor = signedColor(highest),
                modifier = Modifier.weight(1f),
            )
            ManoteReadout(
                label = stringResource(R.string.trend_lowest),
                value = UiText.signedYuan(lowest),
                valueColor = signedColor(lowest),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_trend_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 连输 / 连赢记录：当前连胜与连输、历史最长连胜与连输，以及最佳 / 最差单日。
 *
 * 「当前」取的是最后一场往前的连续段：今天输了、昨天赢了，当前连输就是 1 场。
 * 持平会把两条连续段同时切断，因此 0 不会混进连胜或连输里（见 [StreakStats]）。
 */
@Composable
private fun StreakCard(stats: StreakStats) {
    val colors = MaterialTheme.manote

    ManoteCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            ManoteReadout(
                label = stringResource(R.string.home_streak_current_win),
                value = streakCount(stats.currentWinStreak),
                valueColor = if (stats.currentWinStreak > 0) colors.win else mutedColor(),
                modifier = Modifier.weight(1f),
            )
            ManoteReadout(
                label = stringResource(R.string.home_streak_current_loss),
                value = streakCount(stats.currentLossStreak),
                valueColor = if (stats.currentLossStreak > 0) colors.loss else mutedColor(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ManoteReadout(
                label = stringResource(R.string.home_streak_longest_win),
                value = streakCount(stats.longestWinStreak),
                valueColor = if (stats.longestWinStreak > 0) colors.win else mutedColor(),
                modifier = Modifier.weight(1f),
            )
            ManoteReadout(
                label = stringResource(R.string.home_streak_longest_loss),
                value = streakCount(stats.longestLossStreak),
                valueColor = if (stats.longestLossStreak > 0) colors.loss else mutedColor(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DayReadout(
                label = stringResource(R.string.home_streak_best_day),
                day = stats.bestDay,
            )
            DayReadout(
                label = stringResource(R.string.home_streak_worst_day),
                day = stats.worstDay,
            )
        }
    }
}

/** 「N 场」。单独抽出来是因为 stringResource 只能在 @Composable 上下文里调用，不能塞进普通 lambda。 */
@Composable
private fun streakCount(count: Int): String =
    stringResource(R.string.home_streak_count_format, count)

/** 单日读数：金额 + 「哪一天 · 几场」。一天都还没记时整块显示占位符。 */
@Composable
private fun RowScope.DayReadout(label: String, day: DayNet?) {
    ManoteReadout(
        label = label,
        value = if (day == null) {
            stringResource(R.string.common_unavailable)
        } else {
            UiText.signedYuan(day.netMinor)
        },
        valueColor = if (day == null) mutedColor() else signedColor(day.netMinor),
        modifier = Modifier.weight(1f),
        caption = if (day == null) {
            null
        } else {
            UiText.monthDay(day.day) + " · " +
                stringResource(R.string.home_streak_count_format, day.sessionCount)
        },
    )
}

@Composable
private fun mutedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** 金额的正负配色：赢红、输绿、持平的走中性色，与列表和详情的口径一致。 */
@Composable
private fun signedColor(minor: Long): Color {
    val colors = MaterialTheme.manote
    return when {
        minor > 0L -> colors.win
        minor < 0L -> colors.loss
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/**
 * 一栏读数：标签在上、数值在下，可再挂一行小字说明（例如单场极值的日期）。
 *
 * 本页三栏并排的三处读数（场均 / 单场极值）都用它。
 * 字号取 titleSmall 而不是 titleMedium：三道栏在窄屏上只剩约 90dp，
 * 用 titleMedium 时「−¥1,239.00」会被挤掉尾数。
 * 与 [ManoteReadout] 的区别只有一点：它额外支持 caption。
 */
@Composable
private fun Metric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    /** 数值下方的一行小字，例如单场极值的日期。 */
    caption: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
        )
        if (caption != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NoRecentSessions() {
    ManoteCard {
        ManoteEmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.home_empty_title),
            description = stringResource(R.string.home_empty_desc),
        )
    }
}

@Preview(name = "首页 有记录", showBackground = true)
@Composable
private fun HomeContentPreview() {
    ManoteTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(
                loading = false,
                statistics = PlayerStatistics(
                    sessionCount = 10,
                    winCount = 1,
                    lossCount = 9,
                    drawCount = 0,
                    winMinor = 2_600L,
                    lossMinor = 123_900L,
                ),
                slotStatistics = TimeSlotStatistics.of(
                    mapOf(
                        TimeSlot.MORNING to listOf(600L, -200L, 0L),
                        TimeSlot.EVENING to listOf(-1_500L, -2_300L, 900L, -400L),
                    ),
                ),
                trend = listOf(
                    TrendPoint(1_788_000_000_000L, -2_600L, -2_600L),
                    TrendPoint(1_789_000_000_000L, 900L, -1_700L),
                    TrendPoint(1_790_000_000_000L, 2_600L, 900L),
                    TrendPoint(1_791_000_000_000L, -200L, 700L),
                    TrendPoint(1_792_000_000_000L, -4_100L, -3_400L),
                ),
                streak = StreakStats(
                    currentWinStreak = 0,
                    currentLossStreak = 1,
                    longestWinStreak = 2,
                    longestLossStreak = 3,
                    bestDay = DayNet(LocalDate.of(2026, 10, 3), 4_200L, 2),
                    worstDay = DayNet(LocalDate.of(2026, 10, 5), -3_100L, 2),
                ),
                // 与上面 trend 里的点同源：+2_600 是最高的一场，-4_100 是最低的一场
                extremes = SessionExtremes(
                    bestWin = DatedNet(playedAt = 1_790_000_000_000L, netMinor = 2_600L),
                    worstLoss = DatedNet(playedAt = 1_792_000_000_000L, netMinor = -4_100L),
                ),
                recentSessions = listOf(
                    SessionRowUi(
                        id = "1",
                        title = "麻将对局",
                        note = "",
                        playedAt = 1_791_000_000_000L,
                        timeSlot = TimeSlot.EVENING,
                        selfNetMinor = -200L,
                        opponentNames = listOf("次小", "次建米", "麻米建"),
                        searchHaystack = "麻将对局\n次小\n次建米\n麻米建",
                    ),
                    SessionRowUi(
                        id = "2",
                        title = "周五晚局",
                        note = "",
                        playedAt = 1_790_000_000_000L,
                        timeSlot = TimeSlot.AFTERNOON,
                        selfNetMinor = 2_600L,
                        opponentNames = listOf("阿强", "小美"),
                        searchHaystack = "周五晚局\n阿强\n小美",
                    ),
                ),
            ),
            onSelectTab = {},
            onRecordSession = {},
            onOpenSessionDetail = {},
            today = LocalDate.of(2026, 10, 12),
        )
    }
}

@Preview(name = "首页 无记录", showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    ManoteTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(loading = false),
            onSelectTab = {},
            onRecordSession = {},
            onOpenSessionDetail = {},
            today = LocalDate.of(2026, 10, 12),
        )
    }
}
