package com.manote.app.ui.session

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.repository.DataResetSignal
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SeatResultInput
import com.manote.app.data.repository.SessionDraft
import com.manote.app.data.repository.SessionEdit
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.AmountParseResult
import com.manote.app.domain.AmountParser
import com.manote.app.domain.ManoteError
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.Money
import com.manote.app.domain.SeatEntry
import com.manote.app.domain.SessionRules
import com.manote.app.domain.valueOrNull
import com.manote.app.ui.model.filterAmountInput
import com.manote.app.ui.model.messageRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 表单用途：新建一整场，或修改已有场次。 */
sealed interface SessionEditMode {
    data object Create : SessionEditMode
    data class Edit(val sessionId: String) : SessionEditMode
}

/** 单个座位的编辑状态。 */
data class SeatDraft(
    val seat: Int,
    /** seat=0 恒为本人；1～3 由用户从牌友中选择。为空表示本场不记录这个位置。 */
    val playerId: String? = null,
    /** 显示用昵称；选择牌友或恢复草稿时填入。 */
    val nickname: String = "",
    /** 赢 / 输 切换。金额为 0 时不影响符号（规格 2.1）。 */
    val isWin: Boolean = true,
    /** 非负金额文本；留空按 0 计（规格 1.3）。 */
    val amountText: String = "",
) {
    val isSelfSeat: Boolean get() = seat == SessionRules.SEAT_SELF
}

/** 牌友选择器里的一项。 */
data class FriendOptionUi(
    val id: String,
    val nickname: String,
    val note: String,
    /** 已被哪个座位选走；非空时置灰，保证一场不会重复选到同一人。 */
    val takenBySeat: Int? = null,
)

/** 记一场 / 修改本场表单状态。 */
data class SessionEditUiState(
    val mode: SessionEditMode = SessionEditMode.Create,
    val selfNickname: String? = null,
    val seats: List<SeatDraft> = defaultSeatDrafts(),
    val playedAt: Long = 0L,
    val title: String = "",
    val note: String = "",
    /** 各**已记录**座位（已选牌友）的符号化净额；金额留空按 0 计（规格 1.3）。 */
    val signedBySeat: Map<Int, Long> = emptyMap(),
    /** 各座位金额的解析失败原因（字符串资源 id）。留空不算失败。 */
    val amountErrors: Map<Int, Int> = emptyMap(),
    /** 已记录座位的合计（分）；有金额解析失败时为 null。 */
    val totalMinor: Long? = null,
    /** 所有已记录座位的金额都填写正确（留空视为 0）。 */
    val amountsValid: Boolean = true,
    val saving: Boolean = false,
    val previewVisible: Boolean = false,
    /** 表单是否有改动，用于返回时确认放弃。 */
    val dirty: Boolean = false,
    /** 修改模式下原始场次读不出来（已被删除等情况），界面据此给出提示而不是当成空表单。 */
    val loadFailed: Boolean = false,
) {
    /** 本场实际记录的位置数（选了牌友的座位）。 */
    val recordedSeatCount: Int get() = seats.count { it.playerId != null }

    /** 是否为全零。全零也允许保存，但需要额外确认（规格 2.1）。 */
    val allZero: Boolean
        get() = amountsValid && signedBySeat.isNotEmpty() && signedBySeat.values.all { it == 0L }

    /**
     * 合计与 0.00 元不符。
     *
     * 这不是错误：只记一部分人的输赢时合计本来就不会是 0（规格 1.3），
     * 界面据此给出提示色，但保存按钮保持可用。
     */
    val unbalanced: Boolean get() = totalMinor != null && totalMinor != 0L

    /** 修改模式下人员与位置只读（规格 3.3）；新建时可自由选人。 */
    val rosterLocked: Boolean get() = mode is SessionEditMode.Edit

    val isEditing: Boolean get() = mode is SessionEditMode.Edit
}

/** 表单一次性事件。 */
sealed interface SessionEditEvent {
    /**
     * 保存成功，携带场次 ID。
     * 新建时界面据此跳到四人详情；修改时该场次已在返回栈里，界面直接返回即可。
     */
    data class Saved(val sessionId: String) : SessionEditEvent

    /** 失败提示。 */
    data class Failed(@StringRes val messageRes: Int) : SessionEditEvent

    /**
     * 表单已作废：库被整体替换（导入备份）时发出（规格 2.3、T24）。
     * 界面据此直接离开本页——草稿里的 playerId 属于导入前的数据，继续填下去也写不进去。
     */
    data object Invalidated : SessionEditEvent
}

/**
 * 记一场 / 修改本场 ViewModel（规格 1.3、3.2、3.3）。
 *
 * 新建：
 * - 草稿保存在 [SavedStateHandle] 中，旋转或进程重建后可恢复；
 * - 恢复时会重新校验档案是否仍存在、是否仍可选（已归档的会被清掉）
 * - 提交前先预览确认；保存期间禁用按钮；
 * - 同一次提交复用同一个 Session UUID，重试不会记出第二场（规格 2.3）；
 * - **不要求选齐四人，也不要求每人都填金额**：只记录选了牌友的位置，金额留空按 0 计；
 *   合计不为 0 只在界面提示，不阻拦保存（规格 1.3）。
 *
 * 修改（规格 3.3）：
 * - 人员与位置只读，可编辑时间、标题、备注与已记录位置的金额，进入时全部预填；
 * - 保存走 `updateSession`，携带进入时的 revision，过期则拒绝覆盖并保留输入（T19）；
 * - 不做「恢复草稿后清空已归档牌友」这一步：已归档麻友的历史场次本来就允许修正（T13）。
 */
class SessionEditViewModel(
    private val playerRepository: PlayerRepository,
    private val sessionRepository: SessionRepository,
    private val savedState: SavedStateHandle,
    private val dataReset: DataResetSignal,
    private val mode: SessionEditMode = SessionEditMode.Create,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _events = MutableSharedFlow<SessionEditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEditEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(SessionEditUiState(mode = mode, playedAt = clock()))
    val uiState: StateFlow<SessionEditUiState> = _uiState.asStateFlow()

    /** 修改模式下进入时读到的 revision；写回时用于防过期覆盖。 */
    private var expectedRevision: Long? = null

    /**
     * 修改模式的基线：预填完成时的表单内容。
     * 「有没有改动」在这里必须与基线比较——修改模式一进表单就已经填满，不能算改动。
     */
    private var baseline: FormBaseline? = null

    /** 可选牌友（未归档）及各座位的占用情况。修改模式人员只读，不消费这份数据。 */
    val friendOptions: StateFlow<List<FriendOptionUi>> =
        combine(playerRepository.observeActiveFriends(), _uiState) { friends, state ->
            friends.map { friend ->
                FriendOptionUi(
                    id = friend.id,
                    nickname = friend.nickname,
                    note = friend.note,
                    takenBySeat = state.seats.firstOrNull { it.playerId == friend.id }?.seat,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    init {
        val hadDraft = restoreDraft()
        when (val current = mode) {
            SessionEditMode.Create -> loadSelf()

            is SessionEditMode.Edit -> viewModelScope.launch {
                // 有草稿时只补出 revision，不用数据库覆盖用户已经改了一半的输入
                loadSessionForEdit(current.sessionId, overwriteFields = !hadDraft)
            }
        }
        // 备份导入会把整库换掉：这份表单连同草稿全部作废（规格 2.3、T24）
        viewModelScope.launch {
            dataReset.events.collect { onDataReplaced() }
        }
    }

    // ————————————————————— 表单编辑 —————————————————————

    fun setSeatWin(seat: Int, isWin: Boolean) = updateSeat(seat) { it.copy(isWin = isWin) }

    fun setSeatAmount(seat: Int, text: String) =
        updateSeat(seat) { it.copy(amountText = filterAmountInput(text)) }

    fun selectFriend(seat: Int, option: FriendOptionUi) {
        // 修改模式人员只读（规格 3.3）
        if (_uiState.value.rosterLocked) return
        updateSeat(seat) { it.copy(playerId = option.id, nickname = option.nickname) }
    }

    /** 清空某个非本人座位：连同金额一起清掉，本场不再记录这个位置。 */
    fun clearSeat(seat: Int) {
        if (seat == SessionRules.SEAT_SELF) return
        if (_uiState.value.rosterLocked) return
        // 金额必须一并清掉：不记录的位置若留着金额，会被静默忽略，看起来像已经记上了
        updateSeat(seat) { it.copy(playerId = null, nickname = "", amountText = "") }
    }

    fun setPlayedAt(epochMillis: Long) {
        _uiState.update { it.copy(playedAt = epochMillis) }
        persist()
        recompute()
    }

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value) }
        persist()
        recompute()
    }

    fun setNote(value: String) {
        _uiState.update { it.copy(note = value) }
        persist()
        recompute()
    }

    // ————————————————————— 牌友 —————————————————————

    /** 同名档案数量，用于新建牌友时的提示（规格 2.2）。仅提示，不阻止创建。 */
    suspend fun countSameNickname(nickname: String): Int =
        playerRepository.findFriendsByNickname(nickname.trim()).size

    /** 即时创建牌友并选中到 [seat]；创建本身单独持久化，取消记账也会保留（规格 3.2）。 */
    fun createFriend(rawNickname: String, rawNote: String, seat: Int) {
        viewModelScope.launch {
            when (val result = playerRepository.createFriend(rawNickname, rawNote)) {
                is ManoteResult.Success -> {
                    val created = playerRepository.findById(result.value)
                    if (created != null) {
                        selectFriend(
                            seat,
                            FriendOptionUi(
                                id = created.id,
                                nickname = created.nickname,
                                note = created.note,
                            ),
                        )
                    }
                }

                is ManoteResult.Failure -> emit(SessionEditEvent.Failed(result.error.messageRes()))
            }
        }
    }

    // ————————————————————— 保存 —————————————————————

    /** 校验并打开预览；不通过时给出提示而不清空输入。 */
    fun requestSave() {
        val state = _uiState.value
        if (state.saving || state.loadFailed) return
        val error = preflight(state)
        if (error != null) {
            emit(SessionEditEvent.Failed(error))
            return
        }
        _uiState.update { it.copy(previewVisible = true) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewVisible = false) }
    }

    /** 预览确认后真正写入。保存期间按钮禁用，失败保留输入。 */
    fun confirmSave() {
        val state = _uiState.value
        if (state.saving || state.loadFailed) return

        val error = preflight(state)
        if (error != null) {
            _uiState.update { it.copy(previewVisible = false) }
            emit(SessionEditEvent.Failed(error))
            return
        }

        val inputs = state.seats.mapNotNull { draft ->
            val playerId = draft.playerId ?: return@mapNotNull null
            // 留空按 0 计（规格 1.3）：只记一部分人的输赢是正常用法
            SeatResultInput(
                seat = draft.seat,
                playerId = playerId,
                netMinor = state.signedBySeat[draft.seat] ?: 0L,
            )
        }
        if (inputs.isEmpty()) {
            _uiState.update { it.copy(previewVisible = false) }
            emit(SessionEditEvent.Failed(R.string.error_invalid_seat_set))
            return
        }

        val currentMode = state.mode
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, previewVisible = false) }
            val result: ManoteResult<String> = when (currentMode) {
                SessionEditMode.Create -> {
                    val draft = SessionDraft(
                        // 同一次提交复用同一个 UUID，重试不会插入第二场
                        id = pendingSessionId(),
                        playedAt = state.playedAt,
                        title = state.title,
                        note = state.note,
                        seats = inputs,
                    )
                    sessionRepository.createSession(draft)
                }

                is SessionEditMode.Edit -> {
                    val revision = expectedRevision
                    if (revision == null) {
                        // 装载时就已失败，理论上到不了这里；真到了就按「场次不存在」处理
                        ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
                    } else {
                        when (
                            val updated = sessionRepository.updateSession(
                                SessionEdit(
                                    id = currentMode.sessionId,
                                    expectedRevision = revision,
                                    playedAt = state.playedAt,
                                    title = state.title,
                                    note = state.note,
                                    netMinorBySeat = inputs.associate { it.seat to it.netMinor },
                                ),
                            )
                        ) {
                            is ManoteResult.Success -> ManoteResult.Success(currentMode.sessionId)
                            is ManoteResult.Failure -> updated
                        }
                    }
                }
            }

            when (result) {
                is ManoteResult.Success -> {
                    clearDraft()
                    emit(SessionEditEvent.Saved(result.value))
                }

                is ManoteResult.Failure -> {
                    _uiState.update { it.copy(saving = false) }
                    emit(SessionEditEvent.Failed(result.error.messageRes()))
                }
            }
        }
    }

    /** 用户确认放弃草稿：清掉已保存的草稿，避免下次进入又恢复出来。 */
    fun discardDraft() {
        clearDraft()
    }

    /**
     * 整库被替换：草稿里保存的 playerId、场次 ID 都属于导入前的库，
     * 留着只会让用户填完才发现写不进去，因此立即清空并让界面离开本页（规格 2.3、T24）。
     */
    private fun onDataReplaced() {
        clearDraft()
        emit(SessionEditEvent.Invalidated)
    }

    // ————————————————————— 内部 —————————————————————

    private fun preflight(state: SessionEditUiState): Int? = when {
        state.loadFailed -> R.string.error_session_not_found

        // 填错（而不是留空）必须拦住：这是笔误，不是「这一位先不记」
        state.amountErrors.isNotEmpty() ->
            state.amountErrors.entries.minByOrNull { it.key }?.value
                ?: R.string.session_total_incomplete

        state.signedBySeat.isEmpty() -> R.string.session_total_incomplete
        SessionRules.SEAT_SELF !in state.signedBySeat -> R.string.error_invalid_seat_set
        state.playedAt > clock() -> R.string.error_played_at_in_future

        // 合计不为 0 只提示，不阻拦保存（规格 1.3）
        else -> null
    }

    private fun updateSeat(seat: Int, block: (SeatDraft) -> SeatDraft) {
        _uiState.update { state ->
            state.copy(seats = state.seats.map { if (it.seat == seat) block(it) else it })
        }
        persist()
        recompute()
    }

    private fun recompute() {
        _uiState.update { state ->
            val signed = mutableMapOf<Int, Long>()
            val errors = mutableMapOf<Int, Int>()

            for (draft in state.seats) {
                // 没选牌友的位置本场不记录，金额一并忽略（清空座位时也已经清掉了）
                if (draft.playerId == null) continue

                // 留空按 0 计（规格 1.3）：不必为每个人都填数字
                val text = draft.amountText.trim()
                if (text.isEmpty()) {
                    signed[draft.seat] = 0L
                    continue
                }

                when (val parsed = AmountParser.parse(text)) {
                    is AmountParseResult.Success ->
                        signed[draft.seat] = SeatEntry.signedMinor(parsed.minor, draft.isWin)

                    is AmountParseResult.Failure -> errors[draft.seat] = parsed.reason.messageRes()
                }
            }

            state.copy(
                signedBySeat = signed,
                amountErrors = errors,
                amountsValid = errors.isEmpty(),
                totalMinor = if (errors.isEmpty()) signed.values.sum() else null,
                dirty = state.isDirty(),
            )
        }
    }

    /**
     * 「有没有改动」。
     *
     * 新建时任何输入都算改动；修改时与进入时的基线比较——
     * 否则一进修改页就因为「字段都填着」而被判成脏，返回时白白拦一次。
     */
    private fun SessionEditUiState.isDirty(): Boolean {
        val base = baseline
        return if (base != null) {
            playedAt != base.playedAt ||
                title != base.title ||
                note != base.note ||
                seats.map { SeatFormValue(it.isWin, it.amountText) } != base.seatValues
        } else {
            title.isNotBlank() ||
                note.isNotBlank() ||
                seats.any { it.seat != SessionRules.SEAT_SELF && it.playerId != null } ||
                seats.any { it.amountText.isNotBlank() }
        }
    }

    private fun loadSelf() {
        viewModelScope.launch {
            val self = playerRepository.self()
                ?: playerRepository.ensureSelfPlayer().valueOrNull()
            if (self != null) {
                _uiState.update { state ->
                    state.copy(
                        selfNickname = self.nickname,
                        seats = state.seats.map { draft ->
                            if (draft.isSelfSeat) {
                                draft.copy(playerId = self.id, nickname = self.nickname)
                            } else {
                                draft
                            }
                        },
                    )
                }
                persist()
                recompute()
            }
        }
    }

    /**
     * 修改模式装载：读出该场，记录 revision。
     *
     * [overwriteFields] 为 true 时按数据库内容预填；为 false 时说明用户已有草稿，
     * 只用数据库补出 revision，不覆盖草稿里的输入。
     */
    private suspend fun loadSessionForEdit(sessionId: String, overwriteFields: Boolean) {
        when (val result = sessionRepository.getSessionDetail(sessionId)) {
            is ManoteResult.Success -> {
                val detail = result.value
                expectedRevision = detail.session.revision

                if (overwriteFields) {
                    val bySeat = detail.results.associateBy { it.seat }
                    _uiState.update { state ->
                        state.copy(
                            mode = SessionEditMode.Edit(sessionId),
                            playedAt = detail.session.playedAt,
                            // 从没起过标题的场次留空，让输入框显示「留空则为「麻将对局」」的提示，
                            // 而不是把占位名当成用户填过的内容摆在框里
                            title = detail.session.title
                                .takeUnless { SessionRules.isPlaceholderTitle(it) }
                                .orEmpty(),
                            note = detail.session.note,
                            seats = (0 until SessionRules.SEAT_COUNT).map { seat ->
                                val row = bySeat[seat]
                                if (row == null) {
                                    // 本场没有记录这个位置（规格 1.3）：留空即可，不是错误
                                    SeatDraft(seat = seat)
                                } else {
                                    SeatDraft(
                                        seat = seat,
                                        playerId = row.playerId,
                                        nickname = row.nameSnapshot,
                                        isWin = row.netMinor >= 0L,
                                        amountText = Money.formatMinor(
                                            if (row.netMinor < 0L) -row.netMinor else row.netMinor,
                                        ),
                                    )
                                }
                            },
                            loadFailed = false,
                        )
                    }
                    captureBaseline()
                }
                persist()
                recompute()
            }

            is ManoteResult.Failure -> {
                _uiState.update { it.copy(loadFailed = true, saving = false) }
                emit(SessionEditEvent.Failed(result.error.messageRes()))
            }
        }
    }

    private fun captureBaseline() {
        val state = _uiState.value
        baseline = FormBaseline(
            playedAt = state.playedAt,
            title = state.title,
            note = state.note,
            seatValues = state.seats.map { SeatFormValue(it.isWin, it.amountText) },
        )
    }

    /**
     * 恢复草稿。返回是否确实读到过草稿。
     *
     * 新建模式下恢复后还要重新校验档案；修改模式不校验——
     * 已归档麻友的历史场次本来就允许修正（T13）。
     */
    private fun restoreDraft(): Boolean {
        val hasDraft = savedState.contains(key(KEY_PLAYED_AT))
        val seats = (0 until SessionRules.SEAT_COUNT).map { seat ->
            SeatDraft(
                seat = seat,
                playerId = savedState.get<String>(key(seatPlayerIdKey(seat))),
                nickname = savedState.get<String>(key(seatNicknameKey(seat))).orEmpty(),
                isWin = savedState.get<Boolean>(key(seatWinKey(seat))) ?: true,
                amountText = savedState.get<String>(key(seatAmountKey(seat))).orEmpty(),
            )
        }
        _uiState.update { state ->
            state.copy(
                seats = seats,
                playedAt = savedState.get<Long>(key(KEY_PLAYED_AT)) ?: state.playedAt,
                title = savedState.get<String>(key(KEY_TITLE)).orEmpty(),
                note = savedState.get<String>(key(KEY_NOTE)).orEmpty(),
            )
        }
        recompute()
        if (hasDraft && mode is SessionEditMode.Create) {
            viewModelScope.launch { validateRestoredFriends() }
        }
        return hasDraft
    }

    /**
     * 恢复草稿后重新校验档案：可能已被删除或已归档，这样的座位必须清空，
     * 否则会出现「选了不可用的牌友还允许保存」的假象（规格 3.2）。
     */
    private suspend fun validateRestoredFriends() {
        val current = _uiState.value
        var changed = false
        val seats = current.seats.map { draft ->
            val playerId = draft.playerId
            if (draft.isSelfSeat || playerId == null) return@map draft
            val player = playerRepository.findById(playerId)
            if (player != null &&
                player.kind == PlayerKind.FRIEND &&
                player.archivedAt == null
            ) {
                // 仍可选：顺带把快照昵称刷新为最新昵称
                if (draft.nickname != player.nickname) {
                    draft.copy(nickname = player.nickname)
                } else {
                    draft
                }
            } else {
                // 已删除或已归档：清空座位，避免「选了不可用牌友还能保存」
                changed = true
                draft.copy(playerId = null, nickname = "")
            }
        }
        if (seats != current.seats) {
            _uiState.update { it.copy(seats = seats) }
            persist()
            recompute()
        }
        if (changed) {
            emit(SessionEditEvent.Failed(R.string.error_player_not_found))
        }
    }

    private fun persist() {
        val state = _uiState.value
        savedState[key(KEY_PLAYED_AT)] = state.playedAt
        savedState[key(KEY_TITLE)] = state.title
        savedState[key(KEY_NOTE)] = state.note
        state.seats.forEach { draft ->
            savedState[key(seatPlayerIdKey(draft.seat))] = draft.playerId
            savedState[key(seatNicknameKey(draft.seat))] = draft.nickname
            savedState[key(seatWinKey(draft.seat))] = draft.isWin
            savedState[key(seatAmountKey(draft.seat))] = draft.amountText
        }
    }

    private fun clearDraft() {
        if (mode is SessionEditMode.Create) {
            savedState.remove<String>(key(KEY_PENDING_ID))
        }
        savedState.remove<Long>(key(KEY_PLAYED_AT))
        savedState.remove<String>(key(KEY_TITLE))
        savedState.remove<String>(key(KEY_NOTE))
        (0 until SessionRules.SEAT_COUNT).forEach { seat ->
            savedState.remove<String>(key(seatPlayerIdKey(seat)))
            savedState.remove<String>(key(seatNicknameKey(seat)))
            savedState.remove<Boolean>(key(seatWinKey(seat)))
            savedState.remove<String>(key(seatAmountKey(seat)))
        }
        baseline = null
        expectedRevision = null
        _uiState.update { state ->
            SessionEditUiState(
                mode = state.mode,
                selfNickname = state.selfNickname,
                playedAt = clock(),
            )
        }
        recompute()
    }

    private fun pendingSessionId(): String =
        savedState.get<String>(key(KEY_PENDING_ID))
            ?: idFactory().also { savedState[key(KEY_PENDING_ID)] = it }

    /**
     * 修改模式的草稿键独立于新建模式，否则「改到一半退出、再去记一场」
     * 会把上一场的修改内容带进新表单。
     */
    private fun key(base: String): String = when (val current = mode) {
        SessionEditMode.Create -> base
        is SessionEditMode.Edit -> "edit_${current.sessionId}_$base"
    }

    private fun emit(event: SessionEditEvent) {
        _events.tryEmit(event)
    }

    private data class SeatFormValue(val isWin: Boolean, val amountText: String)

    private data class FormBaseline(
        val playedAt: Long,
        val title: String,
        val note: String,
        val seatValues: List<SeatFormValue>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_PENDING_ID = "pending_session_id"
        const val KEY_PLAYED_AT = "played_at"
        const val KEY_TITLE = "title"
        const val KEY_NOTE = "note"

        fun seatPlayerIdKey(seat: Int) = "seat_${seat}_player_id"
        fun seatNicknameKey(seat: Int) = "seat_${seat}_nickname"
        fun seatWinKey(seat: Int) = "seat_${seat}_win"
        fun seatAmountKey(seat: Int) = "seat_${seat}_amount"
    }
}

/** 四个空座位：0=自己、1=上家、2=对家、3=下家。 */
private fun defaultSeatDrafts(): List<SeatDraft> =
    (0 until SessionRules.SEAT_COUNT).map { SeatDraft(seat = it) }
