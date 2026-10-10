package com.manote.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.manote.app.R
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.theme.manote
import kotlinx.coroutines.launch

/**
 * 记一场（规格 3.2）。
 *
 * 一屏完成四人选择与金额填写：自己自动关联本人，上家/对家/下家从牌友中选择或即时创建；
 * 每个座位用「赢 / 输」切换加非负金额输入；底部实时显示四人合计。
 * 提交前预览确认，保存期间禁用按钮，返回有改动时确认放弃。
 */
@Composable
fun SessionEditScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, extras ->
        SessionEditViewModel(
            playerRepository = container.playerRepository,
            sessionRepository = container.sessionRepository,
            savedState = extras.createSavedStateHandle(),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val friendOptions by viewModel.friendOptions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pickingSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var creatingFriendForSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var showPlayedAtPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEditEvent.Saved -> onSaved(event.sessionId)
                is SessionEditEvent.Failed -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    val requestLeave = {
        if (state.dirty) showDiscardDialog = true else onNavigateBack()
    }
    // 系统返回键在有改动时也要先确认（规格 3.2）
    BackHandler(enabled = state.dirty) { showDiscardDialog = true }

    ManoteScaffold(
        title = stringResource(R.string.action_record_session),
        onNavigateBack = requestLeave,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            SaveBar(
                state = state,
                onSave = viewModel::requestSave,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayedAtRow(
                playedAt = state.playedAt,
                onClick = { showPlayedAtPicker = true },
            )

            state.seats.forEach { draft ->
                SeatEditor(
                    draft = draft,
                    errorRes = state.amountErrors[draft.seat],
                    onPick = { pickingSeat = draft.seat },
                    onClear = { viewModel.clearSeat(draft.seat) },
                    onWinChange = { viewModel.setSeatWin(draft.seat, it) },
                    onAmountChange = { viewModel.setSeatAmount(draft.seat, it) },
                )
            }

            ManoteCard {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.session_title_label)) },
                    placeholder = { Text(text = stringResource(R.string.session_title_hint)) },
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.session_note_label)) },
                    placeholder = { Text(text = stringResource(R.string.session_note_hint)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    pickingSeat?.let { seat ->
        FriendPickerSheet(
            forSeat = seat,
            options = friendOptions,
            onDismiss = { pickingSeat = null },
            onPick = { option ->
                viewModel.selectFriend(seat, option)
                pickingSeat = null
            },
            onCreateNew = {
                creatingFriendForSeat = seat
                pickingSeat = null
            },
        )
    }

    creatingFriendForSeat?.let { seat ->
        CreateFriendDialog(
            countSameNickname = viewModel::countSameNickname,
            onDismiss = { creatingFriendForSeat = null },
            onConfirm = { nickname, note ->
                viewModel.createFriend(nickname, note, seat)
                creatingFriendForSeat = null
            },
        )
    }

    if (showPlayedAtPicker) {
        PlayedAtPickerDialog(
            initialMillis = state.playedAt,
            onDismiss = { showPlayedAtPicker = false },
            onConfirm = {
                viewModel.setPlayedAt(it)
                showPlayedAtPicker = false
            },
        )
    }

    if (state.previewVisible) {
        SessionPreviewDialog(
            state = state,
            onDismiss = viewModel::dismissPreview,
            onConfirm = viewModel::confirmSave,
        )
    }

    if (showDiscardDialog) {
        DiscardDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                viewModel.discardDraft()
                onNavigateBack()
            },
        )
    }
}

@Composable
private fun PlayedAtRow(playedAt: Long, onClick: () -> Unit) {
    ManoteCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.session_played_at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = UiText.dateTime(playedAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeatEditor(
    draft: SeatDraft,
    errorRes: Int?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onWinChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val selected = draft.playerId != null

    ManoteCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                PlayerAvatar(
                    name = draft.nickname,
                    size = 42.dp,
                    containerColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (draft.isSelfSeat) Modifier else Modifier.clickable(onClick = onPick)),
            ) {
                Text(
                    text = if (selected) draft.nickname else stringResource(R.string.session_pick_friend),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = seatLabel(draft.seat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!draft.isSelfSeat && selected) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            WinLoseToggle(isWin = draft.isWin, onWinChange = onWinChange)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.amountText,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorRes != null,
            placeholder = { Text(text = stringResource(R.string.session_amount_hint)) },
            suffix = { Text(text = stringResource(R.string.common_amount_unit)) },
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
        )

        if (errorRes != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 赢 / 输 切换。金额为 0 时不影响结果（规格 2.1）。 */
@Composable
private fun WinLoseToggle(isWin: Boolean, onWinChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.manote
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp),
    ) {
        ToggleHalf(
            label = stringResource(R.string.result_win),
            selected = isWin,
            selectedColor = colors.win,
            onClick = { onWinChange(true) },
        )
        ToggleHalf(
            label = stringResource(R.string.result_loss),
            selected = !isWin,
            selectedColor = colors.loss,
            onClick = { onWinChange(false) },
        )
    }
}

@Composable
private fun ToggleHalf(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = if (selected) selectedColor else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(34.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 底部合计与保存。合计不为 0 时按钮保持禁用并给出原因，不做任何「凑平」。 */
@Composable
private fun SaveBar(state: SessionEditUiState, onSave: () -> Unit) {
    val total = state.totalMinor
    val canSave = state.complete && total == 0L && !state.saving
    val message: String
    val messageColor: Color

    when {
        !state.complete -> {
            message = stringResource(R.string.session_total_incomplete)
            messageColor = MaterialTheme.colorScheme.error
        }

        else -> if (total == 0L) {
            message = stringResource(R.string.session_total_ok)
            messageColor = MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            message = stringResource(R.string.session_total_error, UiText.signedYuan(total ?: 0L))
            messageColor = MaterialTheme.colorScheme.error
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.session_total_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (total != null) {
                    SignedAmount(netMinor = total, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(
                        text = stringResource(R.string.common_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = messageColor,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ManotePrimaryButton(
                text = stringResource(
                    if (state.saving) R.string.session_saving else R.string.session_save,
                ),
                onClick = onSave,
                enabled = canSave,
            )
        }
    }
}

/** 提交前预览（规格 3.2）；全零时在同一处追加确认语，不弹第二个对话框。 */
@Composable
private fun SessionPreviewDialog(
    state: SessionEditUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.seats.forEach { draft ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = draft.nickname,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = seatLabel(draft.seat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SignedAmount(
                            netMinor = state.signedBySeat[draft.seat] ?: 0L,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                if (state.allZero) {
                    Text(
                        text = stringResource(R.string.session_all_zero_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.session_preview_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DiscardDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_discard_title)) },
        text = { Text(text = stringResource(R.string.session_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.session_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.session_discard_stay))
            }
        },
    )
}
