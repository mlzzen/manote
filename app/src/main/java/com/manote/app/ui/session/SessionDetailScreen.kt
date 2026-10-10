package com.manote.app.ui.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

/**
 * 场次详情（规格 3.3）。
 *
 * 展示已记录的位置、快照昵称、净输赢、排名与结清建议，本人结果突出显示。
 * 排名与结清都从当前结果实时计算（规格 2.5），不另存结果。
 *
 * 一场可能只记录了 1～4 个位置，且合计不一定为 0（规格 1.3）：
 * 前者用一行文字说明，后者不给结清建议、只显示合计数。
 *
 * 菜单提供「修改」「导出 CSV」「删除」（规格 3.3）。
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel(key = sessionId) { container, _ ->
        SessionDetailViewModel(
            sessionRepository = container.sessionRepository,
            fileStore = container.backupFileStore,
            sessionId = sessionId,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 系统「创建文件」入口。取消（uri 为 null）什么都不做。
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri ->
        viewModel.onCsvTargetChosen(uri?.toString())
    }

    // CSV 文本准备好之后才拉选择器，文件名用打完时间与场次 ID 短串（规格 6.2）
    LaunchedEffect(state.pendingCsv) {
        state.pendingCsv?.let { csvLauncher.launch(it.fileName) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionDetailEvent.Deleted -> onDeleted()
                is SessionDetailEvent.Notify -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    SessionDetailContent(
        title = state.detail?.title ?: stringResource(R.string.detail_title),
        loading = state.loading,
        detail = state.detail,
        deleting = state.deleting,
        exporting = state.exporting,
        onNavigateBack = onNavigateBack,
        onEdit = { onEdit(sessionId) },
        onExportCsv = viewModel::requestCsvExport,
        onRequestDelete = viewModel::requestDelete,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )

    if (state.confirmDeleteVisible) {
        DeleteSessionDialog(
            onDismiss = viewModel::dismissDelete,
            onConfirm = viewModel::deleteConfirmed,
        )
    }
}

@Composable
private fun SessionDetailContent(
    title: String,
    loading: Boolean,
    detail: SessionDetailUi?,
    deleting: Boolean,
    exporting: Boolean,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onExportCsv: () -> Unit,
    onRequestDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = title,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        actions = {
            // 场次还在时才给菜单；已不存在的场次没有可操作的对象
            if (detail != null) {
                SessionDetailMenu(
                    busy = deleting || exporting,
                    onEdit = onEdit,
                    onExportCsv = onExportCsv,
                    onRequestDelete = onRequestDelete,
                )
            }
        },
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

        // 合计不为 0 时不给建议：撮合算不完，给出「剩下一点余额」的假结果比不给更糟（规格 1.3）
        if (!detail.balanced) {
            Text(
                text = stringResource(
                    R.string.detail_unbalanced_format,
                    UiText.signedYuan(detail.totalMinor),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.manote.draw,
            )
            PartialSeatsHint(seatCount = detail.seats.size)
            return@ManoteCard
        }

        if (detail.transfers.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_no_settlement),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PartialSeatsHint(seatCount = detail.seats.size)
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
        PartialSeatsHint(seatCount = detail.seats.size)
    }
}

/**
 * 本场只记录了部分位置时明确说明（规格 1.3）。
 * 不写这一句，用户会以为另外几位本场净额为 0，而不是「没有记录」。
 */
@Composable
private fun PartialSeatsHint(seatCount: Int) {
    if (seatCount >= SessionRules.SEAT_COUNT) return
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.detail_partial_seats_format, seatCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

/**
 * 详情顶栏的「更多」菜单（规格 3.3：修改、CSV 导出、删除）。
 * 删除或导出进行中一律禁用，避免重复触发。
 */
@Composable
private fun SessionDetailMenu(
    busy: Boolean,
    onEdit: () -> Unit,
    onExportCsv: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = !busy,
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
                text = { Text(text = stringResource(R.string.detail_action_edit)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.detail_action_export_csv)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onExportCsv()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.detail_action_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onRequestDelete()
                },
            )
        }
    }
}

/** 删除二次确认：明确说明结果会一并移除、档案保留、不可撤销（规格 3.3、T11）。 */
@Composable
private fun DeleteSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.detail_delete_title)) },
        text = { Text(text = stringResource(R.string.detail_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.detail_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
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

/** CSV 的 MIME 类型（规格 6.2）。 */
private const val CSV_MIME_TYPE = "text/csv"
