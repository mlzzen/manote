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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.manote.app.R
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.SessionRules
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.model.sessionClockRange
import com.manote.app.ui.model.sessionTimeRange
import com.manote.app.ui.model.timeSlotLabel
import com.manote.app.ui.theme.manote
import kotlinx.coroutines.launch

/** 两种用途各用一个 ViewModel key，避免共用同一份草稿。 */
private fun SessionEditMode.viewModelKey(): String = when (this) {
    SessionEditMode.Create -> "session_form_create"
    is SessionEditMode.Edit -> "session_form_edit_$sessionId"
}

/**
 * 记一场 / 修改本场（规格 1.3、3.2、3.3）。
 *
 * 一屏完成时间、时长、位置选择与金额填写：自己自动关联本人，上家/对家/下家从牌友中选择或即时创建；
 * 每个已记录的位置用「赢 / 输」切换加非负金额输入；底部实时显示合计。
 *
 * 时间只填结束时间，时长可留空（默认 4 小时）；两者定出这一场的时间区间，
 * 早场 / 下午场 / 晚场按区间中点判定，算出来的区间与时段直接显示在输入框下方。
 *
 * 选完牌友、或切换「赢 / 输」之后，焦点会自动落到该座位的金额输入框：
 * 这两步紧接着都是填数字，不该再让用户点一次。
 *
 * 不要求选齐四位、也不要求每人都填金额：没选牌友的位置不记录（不显示输入框），
 * 金额留空按 0 计；合计不为 0 时只给出提示色，保存按钮照常可用。
 * 提交前预览确认，保存期间禁用按钮，返回有改动时确认放弃。
 *
 * 修改模式复用同一套表单，但人员与位置只读、进入即预填（规格 3.3）。
 */
@Composable
fun SessionEditScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    mode: SessionEditMode = SessionEditMode.Create,
    onUpdated: () -> Unit = {},
    onInvalidated: () -> Unit = {},
) {
    val viewModel = manoteViewModel(key = mode.viewModelKey()) { container, extras ->
        SessionEditViewModel(
            playerRepository = container.playerRepository,
            sessionRepository = container.sessionRepository,
            savedState = extras.createSavedStateHandle(),
            dataReset = container.dataReset,
            mode = mode,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val friendOptions by viewModel.friendOptions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pickingSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var creatingFriendForSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    // 刚选好牌友的座位：它的金额输入框该自动获得焦点。
    // 用 remember 而不是 rememberSaveable——这是一次性的手势意图，
    // 旋转屏幕后不该凭空又弹一次键盘。
    var pendingAmountFocusSeat by remember { mutableStateOf<Int?>(null) }
    var showPlayedAtPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // 新建：跳到场次详情；修改：该场次已在返回栈里，直接回上一页即可
                is SessionEditEvent.Saved ->
                    if (mode is SessionEditMode.Edit) onUpdated() else onSaved(event.sessionId)

                // 备份导入把整库换掉了，草稿已作废：直接离开本页（规格 2.3、T24）
                SessionEditEvent.Invalidated -> onInvalidated()

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
        title = stringResource(
            if (state.isEditing) R.string.session_edit_title else R.string.action_record_session,
        ),
        onNavigateBack = requestLeave,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            // 原场次都读不出来时不给保存按钮，避免点了才报错
            if (!state.loadFailed) {
                SaveBar(
                    state = state,
                    onSave = viewModel::requestSave,
                )
            }
        },
    ) { innerPadding ->
        if (state.loadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                ManoteEmptyState(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.detail_missing_title),
                    description = stringResource(R.string.session_edit_stale),
                )
            }
            return@ManoteScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.rosterLocked) {
                RosterLockedNotice()
            }

            PlayedAtRow(
                playedAt = state.playedAt,
                durationText = state.durationText,
                durationError = state.durationError,
                // 时长合法时把这一场的时间区间与算出的时段显示出来：
                // 时段按区间中点判定，不写出来用户就只能猜为什么它被算成下午场
                durationHint = state.durationMinutes?.let { minutes ->
                    stringResource(
                        R.string.session_duration_slot_hint,
                        sessionClockRange(
                            startedAt = SessionDuration.startAt(state.playedAt, minutes),
                            playedAt = state.playedAt,
                        ),
                        timeSlotLabel(
                            TimeSlot.of(SessionDuration.midpointAt(state.playedAt, minutes)),
                        ),
                    )
                },
                onPickTime = { showPlayedAtPicker = true },
                onDurationChange = viewModel::setDuration,
            )

            state.seats.forEach { draft ->
                SeatEditor(
                    draft = draft,
                    errorRes = state.amountErrors[draft.seat],
                    rosterLocked = state.rosterLocked,
                    requestAmountFocus = pendingAmountFocusSeat == draft.seat,
                    onAmountFocusRequested = { pendingAmountFocusSeat = null },
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
                // 选完就该填数字了，把焦点直接送过去
                pendingAmountFocusSeat = seat
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
                // 新建档案是异步的，这一刻座位还没被填上；标记先留下，
                // SeatEditor 会等到座位真的记上了再抢焦点
                pendingAmountFocusSeat = seat
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

/** 修改模式的说明条：明确告诉用户人员与位置改不了（规格 3.3）。 */
@Composable
private fun RosterLockedNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.session_edit_roster_locked),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 结束时间与时长（规格 1.3、3.2）。
 *
 * 只填结束时间是刻意的——打完这一刻是确定的，开始时间靠时长倒推，
 * 用户不必为了记一场去回忆几点开局。推算结果直接写在下面，
 * 避免「为什么这一场被算成下午场」变成一个看不见的黑箱。
 */
@Composable
private fun PlayedAtRow(
    playedAt: Long,
    durationText: String,
    durationError: Int?,
    durationHint: String?,
    onPickTime: () -> Unit,
    onDurationChange: (String) -> Unit,
) {
    ManoteCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onPickTime)
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

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = durationText,
            onValueChange = onDurationChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = durationError != null,
            label = { Text(text = stringResource(R.string.session_duration_label)) },
            placeholder = { Text(text = stringResource(R.string.session_duration_hint)) },
            suffix = { Text(text = stringResource(R.string.session_duration_unit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
        )

        when {
            durationError != null -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(durationError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            durationHint != null -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = durationHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SeatEditor(
    draft: SeatDraft,
    errorRes: Int?,
    rosterLocked: Boolean,
    /** 刚刚选好这位牌友：金额框该自动获得焦点。 */
    requestAmountFocus: Boolean,
    /** 焦点已送过去，父级可以把标记清掉，免得下次再选同一个人时不再触发。 */
    onAmountFocusRequested: () -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onWinChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val selected = draft.playerId != null
    // 没选牌友的位置本场不记录：不显示金额与赢输开关，
    // 否则用户填完才发现被忽略（规格 1.3）
    val recorded = selected || draft.isSelfSeat

    // 点「赢 / 输」之后几乎一定紧接着要填金额，这里直接把焦点送过去，
    // 省掉「点开关 → 再点输入框」这一步。
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    ManoteCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                PlayerAvatar(
                    name = draft.nickname,
                    size = 42.dp,
                    containerColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            } else {
                // 空位用品牌色的浅底 + 主色加号，而不是一块中性灰：
                // 四个位置里还没填的那几个应当是「等你来点」，不是「不可用」
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 修改模式人员只读，因此这里不再挂点击事件
            val clickable = !draft.isSelfSeat && !rosterLocked
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (clickable) Modifier.clickable(onClick = onPick) else Modifier),
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
                Spacer(modifier = Modifier.height(4.dp))
                ManotePill(
                    text = if (recorded) {
                        seatLabel(draft.seat)
                    } else {
                        stringResource(R.string.session_seat_not_recorded, seatLabel(draft.seat))
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    horizontalPadding = 6.dp,
                    verticalPadding = 1.dp,
                )
            }

            if (!draft.isSelfSeat && selected && !rosterLocked) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recorded) {
                WinLoseToggle(
                    isWin = draft.isWin,
                    onWinChange = { win ->
                        onWinChange(win)
                        // 输入框在同一个卡片里且恒为已组合状态，这里聚焦不会落空；
                        // 键盘再显式唤一次，避免个别机型聚焦后不弹键盘。
                        amountFocus.requestFocus()
                        keyboard?.show()
                    },
                )
            }
        }

        if (!recorded) return@ManoteCard

        // 选好牌友之后把焦点直接送到金额框：这一步紧接着就是填数字，不该再让用户点一次。
        //
        // 焦点请求必须放在这里、而不是选人那一刻：座位从「未记录」变成「已记录」，
        // 金额框是这一步才被组合出来的，选人的瞬间它还不存在，`requestFocus()` 会落空。
        // 依赖 recorded 也顺带覆盖了「新建牌友」——那位牌友是异步建好再选上的，
        // 标记先留着，等座位真的记上了再抢焦点。
        LaunchedEffect(requestAmountFocus, recorded) {
            if (requestAmountFocus && recorded) {
                amountFocus.requestFocus()
                keyboard?.show()
                onAmountFocusRequested()
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.amountText,
            onValueChange = onAmountChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(amountFocus),
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

/**
 * 底部合计与保存（规格 1.3）。
 *
 * 合计不为 0 时只用琥珀色提示，**不再禁用保存**：允许只记一部分人的输赢。
 * 真正会挡住保存的只有「金额填错」这类笔误，以及没有可记录的位置。
 */
@Composable
private fun SaveBar(state: SessionEditUiState, onSave: () -> Unit) {
    val total = state.totalMinor
    val canSave = state.amountsValid && !state.saving
    val message: String
    val messageColor: Color

    when {
        !state.amountsValid -> {
            message = stringResource(R.string.session_total_incomplete)
            messageColor = MaterialTheme.colorScheme.error
        }

        total == 0L -> {
            message = stringResource(R.string.session_total_ok)
            messageColor = MaterialTheme.colorScheme.onSurfaceVariant
        }

        else -> {
            message = stringResource(R.string.session_total_warning, UiText.signedYuan(total ?: 0L))
            messageColor = MaterialTheme.manote.draw
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
                    when {
                        state.saving -> R.string.session_saving
                        state.isEditing -> R.string.session_edit_save
                        else -> R.string.session_save
                    },
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
                // 时间与时长也在这里复述一遍：确认框是最后一次看清单的机会，
                // 时段是按开始时间判定的，只写结束时间说不清它会被归到哪一段
                Text(
                    text = sessionTimeRange(
                        startedAt = SessionDuration.startAt(
                            state.playedAt,
                            state.effectiveDurationMinutes,
                        ),
                        playedAt = state.playedAt,
                        durationMinutes = state.effectiveDurationMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 只列出本场实际记录的位置；没选牌友的座位不入库，也就没必要在这里出现
                state.seats.filter { it.playerId != null }.forEach { draft ->
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

                // 合计不为 0 是可保存的（规格 1.3），但保存前再提醒一次，避免看漏
                if (state.unbalanced) {
                    Text(
                        text = stringResource(
                            R.string.session_preview_unbalanced,
                            UiText.signedYuan(state.totalMinor ?: 0L),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.manote.draw,
                    )
                }

                // 只记录了部分位置时说明清楚，免得以为另外几位本场是 0
                if (state.recordedSeatCount < SessionRules.SEAT_COUNT) {
                    Text(
                        text = stringResource(
                            R.string.session_preview_partial_format,
                            state.recordedSeatCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
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
