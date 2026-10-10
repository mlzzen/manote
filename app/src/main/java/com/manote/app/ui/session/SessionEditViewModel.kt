package com.manote.app.ui.session

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SeatResultInput
import com.manote.app.data.repository.SessionDraft
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.AmountParseResult
import com.manote.app.domain.AmountParser
import com.manote.app.domain.ManoteResult
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

/** 单个座位的编辑状态。 */
data class SeatDraft(
    val seat: Int,
    /** seat=0 恒为本人；1～3 由用户从牌友中选择。 */
    val playerId: String? = null,
    /** 显示用昵称；选择牌友或恢复草稿时填入。 */
    val nickname: String = "",
    /** 赢 / 输 切换。金额为 0 时不影响符号（规格 2.1）。 */
    val isWin: Boolean = true,
    /** 非负金额文本，初始为空；不能把未填当成零。 */
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

/** 记一场表单状态。 */
data class SessionEditUiState(
    val selfNickname: String? = null,
    val seats: List<SeatDraft> = defaultSeatDrafts(),
    val playedAt: Long = 0L,
    val title: String = "",
    val note: String = "",
    /** 各座位金额的符号化净额，仅包含解析成功的座位。 */
    val signedBySeat: Map<Int, Long> = emptyMap(),
    /** 各座位金额的解析失败原因（字符串资源 id）。 */
    val amountErrors: Map<Int, Int> = emptyMap(),
    /** 四人合计（分）；只要有一个座位没填好就为 null。 */
    val totalMinor: Long? = null,
    /** 四个座位都选了人且金额都填好了。 */
    val complete: Boolean = false,
    val saving: Boolean = false,
    val previewVisible: Boolean = false,
    /** 表单是否有改动，用于返回时确认放弃。 */
    val dirty: Boolean = false,
) {
    /** 是否为四人全零。全零也允许保存，但需要额外确认（规格 2.1）。 */
    val allZero: Boolean get() = complete && totalMinor == 0L && signedBySeat.values.all { it == 0L }
}

/** 表单一次性事件。 */
sealed interface SessionEditEvent {
    /** 保存成功，携带新场次 ID；界面据此跳到四人详情并清除草稿。 */
    data class Saved(val sessionId: String) : SessionEditEvent

    /** 失败提示。 */
    data class Failed(@StringRes val messageRes: Int) : SessionEditEvent
}

/**
 * 记一场 ViewModel（规格 3.2）。
 *
 * - 草稿保存在 [SavedStateHandle] 中，旋转或进程重建后可恢复；
 * - 恢复时会重新校验档案是否仍存在、是否仍可选（已归档的会被清掉）；
 * - 提交前先预览确认；保存期间禁用按钮；
 * - 同一次提交复用同一个 Session UUID，重试不会记出第二场（规格 2.3）。
 */
class SessionEditViewModel(
    private val playerRepository: PlayerRepository,
    private val sessionRepository: SessionRepository,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _events = MutableSharedFlow<SessionEditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEditEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(SessionEditUiState(playedAt = clock()))
    val uiState: StateFlow<SessionEditUiState> = _uiState.asStateFlow()

    /** 可选牌友（未归档）及各座位的占用情况。 */
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
        restoreDraft()
        loadSelf()
    }

    // ————————————————————— 表单编辑 —————————————————————

    fun setSeatWin(seat: Int, isWin: Boolean) = updateSeat(seat) { it.copy(isWin = isWin) }

    fun setSeatAmount(seat: Int, text: String) =
        updateSeat(seat) { it.copy(amountText = filterAmountInput(text)) }

    fun selectFriend(seat: Int, option: FriendOptionUi) =
        updateSeat(seat) { it.copy(playerId = option.id, nickname = option.nickname) }

    /** 清空某个非本人座位。 */
    fun clearSeat(seat: Int) {
        if (seat == SessionRules.SEAT_SELF) return
        updateSeat(seat) { it.copy(playerId = null, nickname = "") }
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
    fun createFriend(rawNickname: String, rawNote: String, seat: Int) {        viewModelScope.launch {
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
        if (state.saving) return
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
        if (state.saving) return

        val error = preflight(state)
        if (error != null) {
            _uiState.update { it.copy(previewVisible = false) }
            emit(SessionEditEvent.Failed(error))
            return
        }

        val inputs = state.seats.mapNotNull { draft ->
            val playerId = draft.playerId ?: return@mapNotNull null
            val netMinor = state.signedBySeat[draft.seat] ?: return@mapNotNull null
            SeatResultInput(seat = draft.seat, playerId = playerId, netMinor = netMinor)
        }
        if (inputs.size != SessionRules.SEAT_COUNT) {
            _uiState.update { it.copy(previewVisible = false) }
            emit(SessionEditEvent.Failed(R.string.error_invalid_seat_set))
            return
        }

        val draft = SessionDraft(
            // 同一次提交复用同一个 UUID，重试不会插入第二场
            id = pendingSessionId(),
            playedAt = state.playedAt,
            title = state.title,
            note = state.note,
            seats = inputs,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, previewVisible = false) }
            when (val result = sessionRepository.createSession(draft)) {
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

    // ————————————————————— 内部 —————————————————————

    private fun preflight(state: SessionEditUiState): Int? = when {
        state.seats.any { it.playerId == null } || state.seats.any { it.amountText.isBlank() } ->
            R.string.session_total_incomplete

        !state.complete -> R.string.session_total_incomplete
        state.totalMinor != 0L -> R.string.error_sum_not_zero
        state.playedAt > clock() -> R.string.error_played_at_in_future
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
            var complete = true

            for (draft in state.seats) {
                if (draft.playerId == null || draft.amountText.isBlank()) {
                    complete = false
                    continue
                }
                when (val parsed = AmountParser.parse(draft.amountText)) {
                    is AmountParseResult.Success ->
                        signed[draft.seat] = SeatEntry.signedMinor(parsed.minor, draft.isWin)

                    is AmountParseResult.Failure -> {
                        errors[draft.seat] = parsed.reason.messageRes()
                        complete = false
                    }
                }
            }

            state.copy(
                signedBySeat = signed,
                amountErrors = errors,
                complete = complete,
                totalMinor = if (complete) signed.values.sum() else null,
                dirty = state.isDirty(),
            )
        }
    }

    private fun SessionEditUiState.isDirty(): Boolean =
        title.isNotBlank() ||
            note.isNotBlank() ||
            seats.any { it.seat != SessionRules.SEAT_SELF && it.playerId != null } ||
            seats.any { it.amountText.isNotBlank() }

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

    private fun restoreDraft() {
        val seats = (0 until SessionRules.SEAT_COUNT).map { seat ->
            SeatDraft(
                seat = seat,
                playerId = savedState.get<String>(seatPlayerIdKey(seat)),
                nickname = savedState.get<String>(seatNicknameKey(seat)).orEmpty(),
                isWin = savedState.get<Boolean>(seatWinKey(seat)) ?: true,
                amountText = savedState.get<String>(seatAmountKey(seat)).orEmpty(),
            )
        }
        _uiState.update { state ->
            state.copy(
                seats = seats,
                playedAt = savedState.get<Long>(KEY_PLAYED_AT) ?: state.playedAt,
                title = savedState.get<String>(KEY_TITLE).orEmpty(),
                note = savedState.get<String>(KEY_NOTE).orEmpty(),
            )
        }
        recompute()
        viewModelScope.launch { validateRestoredFriends() }
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
        savedState[KEY_PLAYED_AT] = state.playedAt
        savedState[KEY_TITLE] = state.title
        savedState[KEY_NOTE] = state.note
        state.seats.forEach { draft ->
            savedState[seatPlayerIdKey(draft.seat)] = draft.playerId
            savedState[seatNicknameKey(draft.seat)] = draft.nickname
            savedState[seatWinKey(draft.seat)] = draft.isWin
            savedState[seatAmountKey(draft.seat)] = draft.amountText
        }
    }

    private fun clearDraft() {
        savedState.remove<String>(KEY_PENDING_ID)
        savedState.remove<Long>(KEY_PLAYED_AT)
        savedState.remove<String>(KEY_TITLE)
        savedState.remove<String>(KEY_NOTE)
        (0 until SessionRules.SEAT_COUNT).forEach { seat ->
            savedState.remove<String>(seatPlayerIdKey(seat))
            savedState.remove<String>(seatNicknameKey(seat))
            savedState.remove<Boolean>(seatWinKey(seat))
            savedState.remove<String>(seatAmountKey(seat))
        }
        _uiState.update { state ->
            SessionEditUiState(selfNickname = state.selfNickname, playedAt = clock())
        }
        recompute()
    }

    private fun pendingSessionId(): String =
        savedState.get<String>(KEY_PENDING_ID)
            ?: idFactory().also { savedState[KEY_PENDING_ID] = it }

    private fun emit(event: SessionEditEvent) {
        _events.tryEmit(event)
    }

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
