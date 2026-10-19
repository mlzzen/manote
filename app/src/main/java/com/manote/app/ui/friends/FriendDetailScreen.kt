package com.manote.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.DatedNet
import com.manote.app.domain.TrendPoint
import com.manote.app.ui.components.manoteTextFieldColors
import com.manote.app.ui.components.AnimatedSignedYuan
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.ManoteReadout
import com.manote.app.ui.components.ManoteResultTile
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteShareBar
import com.manote.app.ui.components.NetTrendChart
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.FriendBadge
import com.manote.app.ui.model.FriendDetailUi
import com.manote.app.ui.model.FriendSessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.theme.manote
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 麻友详情（规格 3.4）。
 *
 * 整页按「这个人是谁 → 他的战绩 → 我在他面前打得怎样 → 手气走向 → 每一场」
 * 五层排下来，每层各有一张卡、一个小标题：
 * 头像与昵称占最重的一块深色底，数字按赢 / 输 / 平三色格展开，
 * 走势给坐标，逐场记录按月分组。
 *
 * 牌友列表上挂的标签（最苦主 / 福星 / 牌王）在英雄卡上原样重现——
 * 两页用的是同一处判定，不会出现「列表标了他、详情页却没有」。
 * 菜单提供编辑与归档/取消归档。
 */
@Composable
fun FriendDetailScreen(
    playerId: String,
    onNavigateBack: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel(key = playerId) { container, _ ->
        FriendDetailViewModel(
            playerRepository = container.playerRepository,
            sessionRepository = container.sessionRepository,
            playerId = playerId,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendDetailEvent.Failed -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    val detail = state.detail

    ManoteScaffold(
        title = detail?.nickname ?: stringResource(R.string.friend_detail_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        actions = {
            if (detail != null) {
                FriendDetailMenu(
                    archived = detail.archived,
                    working = state.working,
                    onEdit = viewModel::requestEdit,
                    onToggleArchive = viewModel::requestArchiveToggle,
                )
            }
        },
    ) { innerPadding ->
        if (detail == null) {
            // 首帧还没读到时不要误报「已不存在」
            if (!state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.friend_detail_missing_title),
                        description = stringResource(R.string.friend_detail_missing_desc),
                    )
                }
            }
            return@ManoteScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FriendProfileCard(detail = detail)

            ManoteSectionHeader(title = stringResource(R.string.friend_detail_stats_title))
            FriendStatisticsCard(detail = detail)

            ManoteSectionHeader(
                title = stringResource(R.string.friend_detail_together_title),
                trailing = {
                    if (detail.commonSessionCount > 0) {
                        ManotePill(
                            text = stringResource(
                                R.string.friend_stat_together_sessions_format,
                                detail.commonSessionCount,
                            ),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            verticalPadding = 3.dp,
                        )
                    }
                },
            )
            FriendTogetherCard(detail = detail)

            ManoteSectionHeader(title = stringResource(R.string.friend_detail_trend_title))
            FriendTrendCard(trend = detail.trend)

            ManoteSectionHeader(
                title = stringResource(R.string.friend_detail_sessions_title),
                trailing = {
                    if (detail.sessions.isNotEmpty()) {
                        ManotePill(
                            text = stringResource(R.string.friends_stat_format, detail.sessions.size),
                            style = MaterialTheme.typography.labelSmall,
                            verticalPadding = 3.dp,
                        )
                    }
                },
            )

            if (detail.sessions.isEmpty()) {
                ManoteCard {
                    Text(
                        text = stringResource(R.string.friend_detail_no_sessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FriendSessionList(
                    rows = detail.sessions,
                    onOpenSession = onOpenSessionDetail,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (detail != null && state.editVisible) {
        EditFriendDialog(
            initialNickname = detail.nickname,
            initialNote = detail.note,
            onDismiss = viewModel::dismissEdit,
            onConfirm = viewModel::saveEdit,
        )
    }

    if (detail != null && state.archiveConfirmVisible) {
        ArchiveToggleDialog(
            archived = detail.archived,
            onDismiss = viewModel::dismissArchiveToggle,
            onConfirm = viewModel::confirmArchiveToggle,
        )
    }
}

/**
 * 昵称 + 备注 + 牌友列表上的那几个标签 + 两个量级胶囊 + 一条迷你走势。
 *
 * 这块用英雄卡而不是白卡：本页的主角是「这个人」，头像与昵称值得占全页最重的一层底。
 * 卡上不放任何被下面统计卡重复的金额——他所有的数都在那张卡里；
 * 这里只放两个「他是个什么量级的对手」的胶囊（场数、胜率），
 * 以及一条只给方向的迷你走势，和首页英雄卡是同一个节奏。
 * 标签也不放数字，说的是「他对我意味着什么」，正属于这张卡在讲的事。
 */
@Composable
private fun FriendProfileCard(detail: FriendDetailUi) {
    val colors = MaterialTheme.manote
    val statistics = detail.statistics

    ManoteHeroCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = detail.nickname,
                size = 60.dp,
                containerColor = colors.heroChip,
                contentColor = colors.onHeroChip,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.nickname,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail.note.ifBlank { stringResource(R.string.common_unavailable) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (detail.badges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            // 与列表同一个顺序（按枚举声明序），同一个标签在两页的排列一致；
            // 深底上另配一组亮色，见 FriendBadgeChip 的 onHero
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FriendBadge.entries.forEach { badge ->
                    if (badge in detail.badges) {
                        FriendBadgeChip(badge = badge, onHero = true)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroChip(
                stringResource(R.string.friends_stat_format, statistics.sessionCount),
            )
            if (statistics.sessionCount > 0) {
                HeroChip(
                    stringResource(R.string.home_stat_win_rate) + " " +
                        "${UiText.percent(statistics.winCount, statistics.sessionCount)}%",
                )
            }
        }

        // 只在画得出走势时出现：一个点画不成线，卡上就只留那两个胶囊
        if (detail.trend.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            NetTrendChart(
                points = detail.trend,
                height = 52.dp,
                lineColorOverride = colors.onHeroMuted,
                dotRingColorOverride = colors.heroSurface,
                areaAlpha = 0.22f,
                showZeroLine = false,
                verticalInset = 4.dp,
            )
        }

        if (detail.archived) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.friend_detail_archived_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onHeroMuted,
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
 * 这位麻友的累计战绩：净输赢（唯一放大的那个数）、赢 / 输 / 平三色格与占比条、
 * 场均与单场极值。全部实时来自 `session_result`。
 *
 * 场数放在标题行右侧的徽标里（与首页「战绩概览」的「总牌局」同一个位置）：
 * 它是这一组数字的量级说明，不是一个与净额并列的指标。
 * 净输赢在英雄卡上不再出现一次——本页的主语是这个人，不是我的总账。
 */
@Composable
private fun FriendStatisticsCard(detail: FriendDetailUi) {
    val colors = MaterialTheme.manote
    val statistics = detail.statistics

    ManoteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.friend_stat_net),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedSignedYuan(
                    netMinor = statistics.netMinor,
                    style = MaterialTheme.typography.headlineMedium,
                    color = signedColor(statistics.netMinor),
                )
            }
            SessionsBadge(count = statistics.sessionCount)
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                // 输的那一栏给负值：金额与上面的短条说的是同一个方向
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
            ManoteReadout(
                label = stringResource(R.string.stat_average),
                value = UiText.signedYuan(statistics.averageNetMinor),
                valueColor = signedColor(statistics.averageNetMinor),
                modifier = Modifier.weight(1f),
            )
            SessionExtremeReadout(
                label = stringResource(R.string.stat_best_win),
                extreme = detail.extremes.bestWin,
                modifier = Modifier.weight(1f),
            )
            SessionExtremeReadout(
                label = stringResource(R.string.stat_worst_loss),
                extreme = detail.extremes.worstLoss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 「N 场 / 场数」小徽标，与首页「战绩概览」右侧的「总牌局」同款。
 *
 * 场数是这一组数字的量级说明，放在标题旁比让它单独占一格读数更贴近它的角色。
 */
@Composable
private fun SessionsBadge(count: Int) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.friends_stat_format, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.friend_stat_sessions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        )
    }
}

/**
 * 单场极值：金额 + 「哪一天」。
 *
 * 没有这样的场次（他从没赢过 / 从没输过）时显示占位符而不是 ¥0.00——
 * 「有过一场刚好持平」和「根本没赢过」是两回事。
 * 日期小字给出来，否则一个孤零零的金额无从追溯；这与首页的单场极值同一口径。
 */
@Composable
private fun SessionExtremeReadout(
    label: String,
    extreme: DatedNet?,
    modifier: Modifier = Modifier,
) {
    ManoteReadout(
        label = label,
        value = if (extreme == null) {
            stringResource(R.string.common_unavailable)
        } else {
            UiText.signedYuan(extreme.netMinor)
        },
        valueColor = if (extreme == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            signedColor(extreme.netMinor)
        },
        caption = extreme?.let { UiText.monthDay(it.playedAt) },
        modifier = modifier,
    )
}

/**
 * 同场时「我」的表现：只统计他也在桌的那些场。
 *
 * 两个数放在一块浅一档米色底上，与上面「他的战绩」明确分开——
 * 这一组的主语是我，不是他；和「最苦主 / 福星」用的是同一个口径。
 * 从没同场过时不显示 ¥0.00 而是给出说明，否则那会被读成「同场打平」。
 */
@Composable
private fun FriendTogetherCard(detail: FriendDetailUi) {
    val hasCommon = detail.commonSessionCount > 0

    ManoteCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            TogetherValue(
                label = stringResource(R.string.friend_stat_together_net),
                minor = detail.myNetTogetherMinor,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            TogetherValue(
                label = stringResource(R.string.friend_stat_together_average),
                minor = detail.myAverageTogetherMinor,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (hasCommon) {
                    R.string.friend_detail_together_note
                } else {
                    R.string.friend_detail_together_empty
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TogetherValue(
    label: String,
    minor: Long?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = minor?.let { UiText.signedYuan(it) } ?: stringResource(R.string.common_unavailable),
            style = MaterialTheme.typography.titleLarge,
            color = minor?.let { signedColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 金额的正负配色：赢红、输绿、持平的走中性色，与首页概览口径一致。 */
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
 * 这位牌友的累计净额走势。
 *
 * 只画他自己的跨场累计，回答的是「他最近手气怎么样」；
 * 两人之间的相对输赢另有口径（见同场卡与牌友列表的「最苦主」），不混在一条线里。
 * 图上方那行给出区间（首场 – 末场）与点数，图下方是本页已有的三个读数——
 * 一条没有坐标参照的曲线只能看出方向，看不出「从什么时候到什么时候」。
 */
@Composable
private fun FriendTrendCard(trend: List<TrendPoint>) {
    ManoteCard {
        if (trend.size < 2) {
            Text(
                text = stringResource(R.string.friend_detail_trend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManoteCard
        }

        val first = trend.first().playedAt
        val last = trend.last().playedAt
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = UiText.monthDay(first) + " – " + UiText.monthDay(last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.friends_stat_format, trend.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        NetTrendChart(points = trend)

        Spacer(modifier = Modifier.height(12.dp))

        val latest = trend.last().cumulativeMinor
        val highest = trend.maxOf { it.cumulativeMinor }
        val lowest = trend.minOf { it.cumulativeMinor }
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
    }
}

/**
 * 逐场记录：按月分组，每组一个「这个月我与他各自打成什么样」的表头。
 *
 * 分组后列表不再是一条无限长的同质流；月份本身也是一条信息——
 * 「连续两个月都在输」和「隔了半年才打一场」在平铺的列表里看不出来。
 * 表头右侧是该月这位麻友的净额合计，与本页其它地方同一个正负配色。
 */
@Composable
private fun FriendSessionList(
    rows: List<FriendSessionRowUi>,
    onOpenSession: (String) -> Unit,
) {
    // 数据本身已按时间倒序，groupBy 保持插入顺序，因此月份也是从新到旧
    val groups = remember(rows) {
        rows.groupBy { YearMonth.from(UiText.toLocalDate(it.playedAt)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (month, monthRows) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = UiText.monthLabel(month),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.friends_stat_format, monthRows.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                SignedAmount(
                    netMinor = monthRows.sumOf { it.netMinor },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            monthRows.forEach { row ->
                FriendSessionCard(
                    row = row,
                    onClick = { onOpenSession(row.sessionId) },
                )
            }
        }
    }
}

/**
 * 逐场一行：日期块、标题、这位麻友的位置，以及他与本人各自的本场结果（规格 3.4）。
 *
 * 日期块按**这位麻友**这一场的输赢上色。一列排下来先看到的是红 / 绿 / 米色的
 * 日期方块，滚动的过程中不必逐行去读右边的金额就能知道这段手气如何；
 * 颜色只是加速器，金额与正负号仍然同时在场。
 */
@Composable
private fun FriendSessionCard(row: FriendSessionRowUi, onClick: () -> Unit) {
    ManoteCard(
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SessionDateTile(playedAt = row.playedAt, netMinor = row.netMinor)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 「麻将对局」只是占位默认名，没起过标题就不显示，避免每一行都重复
                if (row.hasCustomTitle) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                ManotePill(
                    text = seatLabel(row.seat),
                    shape = MaterialTheme.shapes.extraSmall,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    horizontalPadding = 6.dp,
                    verticalPadding = 1.dp,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(
                    netMinor = row.netMinor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.friend_session_self_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    SignedAmount(
                        netMinor = row.selfNetMinor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** 日期块：「05 / 10月」两行，底色按这一场这位麻友的输赢取。 */
@Composable
private fun SessionDateTile(playedAt: Long, netMinor: Long) {
    val colors = MaterialTheme.manote
    val containerColor: Color
    val contentColor: Color
    when {
        netMinor > 0L -> {
            containerColor = colors.winContainer
            contentColor = colors.onWinContainer
        }

        netMinor < 0L -> {
            containerColor = colors.lossContainer
            contentColor = colors.onLossContainer
        }

        else -> {
            containerColor = colors.drawContainer
            contentColor = colors.onDrawContainer
        }
    }
    val date = remember(playedAt) { UiText.toLocalDate(playedAt) }

    Column(
        modifier = Modifier
            .width(46.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = String.format(Locale.US, "%02d", date.dayOfMonth),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
        Text(
            // 月份名跟随设备语言：中文「10月」，英文「Oct」，不再写死数字月份
            text = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 详情顶栏菜单：编辑、归档或取消归档。 */
@Composable
private fun FriendDetailMenu(
    archived: Boolean,
    working: Boolean,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = !working,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_edit)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(
                            if (archived) R.string.action_unarchive else R.string.action_archive,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (archived) Icons.Filled.Refresh else Icons.Filled.Delete,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleArchive()
                },
            )
        }
    }
}

/** 编辑昵称与备注。改名不改写历史场次里已保存的快照（规格 2.2、T12）。 */
@Composable
private fun EditFriendDialog(
    initialNickname: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (nickname: String, note: String) -> Unit,
) {
    var nickname by remember { mutableStateOf(initialNickname) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.friend_edit_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.friend_nickname_label)) },
                    shape = MaterialTheme.shapes.small,
                    colors = manoteTextFieldColors(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.friend_note_label)) },
                    placeholder = { Text(text = stringResource(R.string.friend_note_hint)) },
                    shape = MaterialTheme.shapes.small,
                    colors = manoteTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nickname.isNotBlank(),
                onClick = { onConfirm(nickname, note) },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 归档 / 取消归档确认。归档必须说明历史仍然保留（规格 3.4）。 */
@Composable
private fun ArchiveToggleDialog(
    archived: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (archived) R.string.friend_unarchive_title else R.string.friend_archive_title,
                ),
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (archived) {
                        R.string.friend_unarchive_message
                    } else {
                        R.string.friend_archive_message
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(
                        if (archived) R.string.action_unarchive else R.string.action_archive,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
