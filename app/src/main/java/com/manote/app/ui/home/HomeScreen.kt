package com.manote.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.PlayerStatistics
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.components.MahjongTile
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteHeaderCard
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.ManoteTheme
import com.manote.app.ui.theme.manote

/**
 * 首页（规格 3.1）。
 *
 * 显示本人昵称、累计赢钱、累计输钱、净输赢与已记录场数，主体为最近场次与「记一场」。
 * 所有数字都来自 `session_result` 的实时汇总（规格 2.4），没有记录时显示 0.00 元。
 *
 * 规格明确「首版不实现图表」，因此参考稿里的趋势折线图不在这里实现；
 * 「今日宜忌 / 牌运」属规格外内容，同样不做。
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
) {
    ManoteScaffold(
        title = stringResource(R.string.nav_home),
        modifier = modifier,
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
            ProfileHeader(
                nickname = state.selfNickname,
                sessionCount = state.statistics.sessionCount,
            )

            OverviewCard(statistics = state.statistics, loading = state.loading)

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

/** 深墨绿头图卡：昵称 + 「麻记 · 已记录 N 场」。 */
@Composable
private fun ProfileHeader(
    nickname: String?,
    sessionCount: Int,
) {
    val colors = MaterialTheme.manote
    ManoteHeaderCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = nickname.orEmpty(),
                size = 52.dp,
                containerColor = colors.onHeader.copy(alpha = 0.16f),
                contentColor = colors.onHeader,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nickname.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.home_subtitle_format,
                        stringResource(R.string.app_name),
                        sessionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onHeaderMuted,
                    maxLines = 1,
                )
            }
            MahjongTile(
                glyph = stringResource(R.string.home_mahjong_glyph),
                modifier = Modifier.size(width = 34.dp, height = 46.dp),
                faceColor = colors.onHeader.copy(alpha = 0.14f),
                glyphColor = colors.onHeader,
            )
        }
    }
}

/** 战绩概览：胜 / 负 / 平 三色格 + 占比条 + 净胜、胜率、净胜场。 */
@Composable
private fun OverviewCard(statistics: PlayerStatistics, loading: Boolean) {
    val colors = MaterialTheme.manote

    ManoteCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.home_overview_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TotalSessionsBadge(count = statistics.sessionCount)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (statistics.sessionCount == 0) {
            // 首帧还没读到场次时不显示「还没有可统计的场次」，避免闪一下空态
            if (!loading) {
                Text(
                    text = stringResource(R.string.home_overview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ManoteCard
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.result_win),
                count = statistics.winCount,
                amountMinor = statistics.winMinor,
                containerColor = colors.winContainer,
                contentColor = colors.onWinContainer,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.result_loss),
                count = statistics.lossCount,
                amountMinor = -statistics.lossMinor,
                containerColor = colors.lossContainer,
                contentColor = colors.onLossContainer,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.result_draw),
                count = statistics.drawCount,
                amountMinor = 0L,
                containerColor = colors.drawContainer,
                contentColor = colors.onDrawContainer,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        ShareBar(statistics = statistics)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Metric(
                label = stringResource(R.string.home_stat_net),
                value = UiText.signedYuan(statistics.netMinor),
                valueColor = when {
                    statistics.netMinor > 0L -> colors.win
                    statistics.netMinor < 0L -> colors.loss
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Metric(
                label = stringResource(R.string.home_stat_win_rate),
                value = "${UiText.percent(statistics.winCount, statistics.sessionCount)}%",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Metric(
                label = stringResource(R.string.home_stat_net_wins),
                value = "${statistics.winCount - statistics.lossCount}",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TotalSessionsBadge(count: Int) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_overview_total_format, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_overview_total_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    count: Int,
    amountMinor: Long,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = UiText.signedYuan(amountMinor),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 胜 / 负 / 平 占比条。全部为 0 时只画一条空轨道。 */
@Composable
private fun ShareBar(statistics: PlayerStatistics) {
    val colors = MaterialTheme.manote
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (statistics.winCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(statistics.winCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.win),
                )
            }
            if (statistics.lossCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(statistics.lossCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.loss),
                )
            }
            if (statistics.drawCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(statistics.drawCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.draw),
                )
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
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
                selfNickname = "菠萝",
                statistics = PlayerStatistics(
                    sessionCount = 10,
                    winCount = 1,
                    lossCount = 9,
                    drawCount = 0,
                    winMinor = 2_600L,
                    lossMinor = 123_900L,
                ),
                recentSessions = listOf(
                    SessionRowUi(
                        id = "1",
                        title = "麻将对局",
                        note = "",
                        playedAt = 1_791_000_000_000L,
                        selfNetMinor = -200L,
                        opponentNames = listOf("次小", "次建米", "麻米建"),
                        searchHaystack = "麻将对局\n次小\n次建米\n麻米建",
                    ),
                    SessionRowUi(
                        id = "2",
                        title = "麻将对局",
                        note = "",
                        playedAt = 1_790_000_000_000L,
                        selfNetMinor = 2_600L,
                        opponentNames = listOf("阿强", "小美"),
                        searchHaystack = "麻将对局\n阿强\n小美",
                    ),
                ),
            ),
            onSelectTab = {},
            onRecordSession = {},
            onOpenSessionDetail = {},
        )
    }
}

@Preview(name = "首页 无记录", showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    ManoteTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(loading = false, selfNickname = "我"),
            onSelectTab = {},
            onRecordSession = {},
            onOpenSessionDetail = {},
        )
    }
}
