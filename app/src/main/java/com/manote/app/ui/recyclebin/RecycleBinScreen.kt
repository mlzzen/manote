package com.manote.app.ui.recyclebin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.components.TimeSlotTag
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.UiText
import kotlinx.coroutines.launch

/**
 * 回收站（规格 3.3）。
 *
 * 在详情页删除的场次先落到这里：它们已经退出列表、统计与牌友战绩，
 * 但结果行还在，可以还原。这里也是唯一能彻底删除它们的地方。
 *
 * 页面用居中标题 + 返回键的子页面骨架，与其它子页面一致。
 */
@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        RecycleBinViewModel(container.sessionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecycleBinEvent.Notify -> scope.launch {
                    val text = event.formatArg
                        ?.let { context.getString(event.messageRes, it) }
                        ?: context.getString(event.messageRes)
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }

    ManoteScaffold(
        title = stringResource(R.string.bin_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            // 没有可清空的东西时不摆这个动作，免得点了没反应
            if (state.rows.isNotEmpty()) {
                ManoteTextAction(
                    label = stringResource(R.string.bin_empty_action),
                    onClick = viewModel::requestEmpty,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = stringResource(R.string.bin_retention_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // 首帧还没读到时留白，避免闪一下空态
            if (state.loading) return@Column

            if (state.rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.bin_empty_title),
                        description = stringResource(R.string.bin_empty_desc),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = state.rows, key = { it.id }) { row ->
                        BinRow(
                            row = row,
                            working = state.working,
                            onRestore = { viewModel.restore(row.id) },
                            onPurge = { viewModel.requestPurge(row.id) },
                        )
                    }
                }
            }
        }
    }

    state.pendingPurgeId?.let {
        PurgeConfirmDialog(
            working = state.working,
            onDismiss = viewModel::dismissPurge,
            onConfirm = viewModel::confirmPurge,
        )
    }

    if (state.confirmEmptyVisible) {
        EmptyTrashConfirmDialog(
            count = state.rows.size,
            working = state.working,
            onDismiss = viewModel::dismissEmpty,
            onConfirm = viewModel::confirmEmpty,
        )
    }
}

/**
 * 回收站里的一行：和场次列表读同样的日期、时段与本人净额，
 * 但把「点进去看详情」换成两个明确的动作——还原、彻底删除。
 *
 * 这里刻意不给整行加点击：回收站里的记录本来就不该被打开（它已经不在应用的
 * 正常视图里），只留下两个出口，避免误点。
 */
@Composable
private fun BinRow(
    row: SessionRowUi,
    working: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    ManoteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UiText.date(row.playedAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeSlotTag(slot = row.timeSlot)
                    if (row.hasCustomTitle) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SignedAmount(netMinor = row.selfNetMinor)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManoteTextAction(
                label = stringResource(R.string.bin_restore_action),
                onClick = { if (!working) onRestore() },
            )
            ManoteTextAction(
                label = stringResource(R.string.bin_purge_action),
                onClick = { if (!working) onPurge() },
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 彻底删除单场的确认：不可逆，因此文案要走红。 */
@Composable
private fun PurgeConfirmDialog(
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(text = stringResource(R.string.bin_purge_confirm_title)) },
        text = { Text(text = stringResource(R.string.bin_purge_confirm_message)) },
        confirmButton = {
            TextButton(enabled = !working, onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.bin_purge_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 清空回收站的确认：先摆清楚要删掉几场。 */
@Composable
private fun EmptyTrashConfirmDialog(
    count: Int,
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(text = stringResource(R.string.bin_empty_confirm_title)) },
        text = {
            Text(text = stringResource(R.string.bin_empty_confirm_message, count))
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.bin_empty_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
