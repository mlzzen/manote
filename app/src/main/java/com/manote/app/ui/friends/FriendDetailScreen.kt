package com.manote.app.ui.friends

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteReadout
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.NetTrendChart
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.FriendDetailUi
import com.manote.app.ui.model.FriendSessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.theme.manote
import com.manote.app.domain.TrendPoint
import kotlinx.coroutines.launch

/**
 * 麻友详情（规格 3.4）。
 *
 * 展示当前昵称、备注、累计赢钱、累计输钱、净输赢、场均与场数；
 * 逐场列出日期、这位麻友所在位置，以及他与本人各自的本场结果，点按进入完整四人详情。
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
            FriendStatisticsCard(detail = detail)
            FriendTrendCard(trend = detail.trend)

            ManoteSectionHeader(title = stringResource(R.string.friend_detail_sessions_title))

            if (detail.sessions.isEmpty()) {
                ManoteCard {
                    Text(
                        text = stringResource(R.string.friend_detail_no_sessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.sessions.forEach { row ->
                        FriendSessionCard(
                            row = row,
                            onClick = { onOpenSessionDetail(row.sessionId) },
                        )
                    }
                }
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

/** 昵称 + 备注；已归档时给出明确说明，避免用户以为历史被清掉了。 */
@Composable
private fun FriendProfileCard(detail: FriendDetailUi) {
    ManoteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = detail.nickname,
                size = 52.dp,
                containerColor = if (detail.archived) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (detail.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.nickname,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail.note.ifBlank { stringResource(R.string.common_unavailable) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (detail.archived) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.friend_detail_archived_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 累计赢钱 / 累计输钱 / 净输赢 / 场均 / 场数。全部实时来自 `session_result`。 */
@Composable
private fun FriendStatisticsCard(detail: FriendDetailUi) {
    val colors = MaterialTheme.manote
    val statistics = detail.statistics

    ManoteCard {
        Text(
            text = stringResource(R.string.friend_detail_stats_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Statistic(
                label = stringResource(R.string.friend_stat_win_amount),
                value = UiText.yuan(statistics.winMinor),
                valueColor = colors.win,
                modifier = Modifier.weight(1f),
            )
            Statistic(
                label = stringResource(R.string.friend_stat_loss_amount),
                value = UiText.yuan(statistics.lossMinor),
                valueColor = colors.loss,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Statistic(
                label = stringResource(R.string.friend_stat_net),
                value = UiText.signedYuan(statistics.netMinor),
                valueColor = signedColor(statistics.netMinor),
                modifier = Modifier.weight(1f),
            )
            Statistic(
                label = stringResource(R.string.stat_average),
                value = UiText.signedYuan(statistics.averageNetMinor),
                valueColor = signedColor(statistics.averageNetMinor),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Statistic(
                label = stringResource(R.string.friend_stat_sessions),
                value = statistics.sessionCount.toString(),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
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
 * 两人之间的相对输赢另有口径（见首页与牌友列表的「最苦主」），不混在一条线里。
 */
@Composable
private fun FriendTrendCard(trend: List<TrendPoint>) {
    ManoteCard {
        Text(
            text = stringResource(R.string.friend_detail_trend_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (trend.size < 2) {
            Text(
                text = stringResource(R.string.friend_detail_trend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManoteCard
        }

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

@Composable
private fun Statistic(
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

/** 逐场一行：日期、标题（没起过名时不显示）、这位麻友的位置、他与本人各自的本场结果（规格 3.4）。 */
@Composable
private fun FriendSessionCard(row: FriendSessionRowUi, onClick: () -> Unit) {
    ManoteCard(
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UiText.date(row.playedAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // 「麻将对局」只是占位默认名，没起过标题就不显示，避免每一行都重复
                if (row.hasCustomTitle) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = seatLabel(row.seat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(
                    netMinor = row.netMinor,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(2.dp))
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
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.friend_note_label)) },
                    placeholder = { Text(text = stringResource(R.string.friend_note_hint)) },
                    shape = MaterialTheme.shapes.small,
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
