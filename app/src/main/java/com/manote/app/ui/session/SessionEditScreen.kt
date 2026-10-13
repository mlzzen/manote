package com.manote.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.manote.app.R
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.sessionClockRange
import com.manote.app.ui.model.timeSlotLabel
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
 *
 * 本文件只保留**页面骨架与状态接线**：时间块、座位卡片、底部合计栏与两个对话框
 * 各自拆在 `SessionEditTimeSection.kt` / `SessionSeatEditor.kt` /
 * `SessionEditSaveBar.kt` / `SessionEditDialogs.kt` 里。
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
