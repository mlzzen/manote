package com.manote.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.SessionRules
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.SeatUi
import com.manote.app.ui.model.SessionDetailUi
import com.manote.app.ui.model.TransferUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.theme.manote

/**
 * 四人详情（规格 3.3）。
 *
 * 展示四个位置、快照昵称、净输赢、排名与结清建议，本人结果突出显示。
 * 排名与结清都从当前四人结果实时计算（规格 2.5），不另存结果。
 * 修改、CSV 导出与删除属 P3/P4，本页不放置对应入口。
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel(key = sessionId) { container, _ ->
        SessionDetailViewModel(container.sessionRepository, sessionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SessionDetailContent(
        title = state.detail?.title ?: stringResource(R.string.detail_title),
        loading = state.loading,
        detail = state.detail,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun SessionDetailContent(
    title: String,
    loading: Boolean,
    detail: SessionDetailUi?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = title,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { innerPadding ->
        if (detail == null) {
            // 首帧还没读到时不要误报「已不存在」，只有确认读完了才提示
            if (!loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.detail_missing_title),
                        description = stringResource(R.string.detail_missing_desc),
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
            ManoteCard {
                Text(
                    text = UiText.dateTime(detail.playedAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (detail.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.detail_note_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detail.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detail.seats.forEach { seat ->
                    SeatRow(seat = seat, isSelf = seat.seat == SessionRules.SEAT_SELF)
                }
            }

            SettlementCard(detail = detail)
        }
    }
}

@Composable
private fun SeatRow(seat: SeatUi, isSelf: Boolean) {
    val colors = MaterialTheme.manote
    val rankColor = if (seat.rank == 1) colors.gold else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelf) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(
                name = seat.nickname,
                size = 42.dp,
                containerColor = if (isSelf) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSelf) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seat.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = seatLabel(seat.seat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(netMinor = seat.netMinor)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.detail_rank_format, seat.rank),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (seat.rank == 1) FontWeight.Bold else FontWeight.Normal,
                    color = rankColor,
                )
            }
        }
    }
}

@Composable
private fun SettlementCard(detail: SessionDetailUi) {
    val nameBySeat = detail.seats.associate { it.seat to it.nickname }

    ManoteCard {
        Text(
            text = stringResource(R.string.detail_settlement_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.detail_settlement_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (detail.transfers.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_no_settlement),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            return@ManoteCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            detail.transfers.forEach { transfer ->
                TransferRow(
                    transfer = transfer,
                    fromName = nameBySeat[transfer.fromSeat].orEmpty(),
                    toName = nameBySeat[transfer.toSeat].orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: TransferUi, fromName: String, toName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.detail_transfer_format,
                seatLabel(transfer.fromSeat) + " " + fromName,
                seatLabel(transfer.toSeat) + " " + toName,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        SignedAmount(netMinor = transfer.amountMinor, style = MaterialTheme.typography.titleSmall)
    }
}
