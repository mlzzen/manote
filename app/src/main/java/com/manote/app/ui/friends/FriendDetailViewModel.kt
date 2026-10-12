package com.manote.app.ui.friends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.ui.model.FriendDetailUi
import com.manote.app.ui.model.friendBadges
import com.manote.app.ui.model.messageRes
import com.manote.app.ui.model.opponentMatches
import com.manote.app.ui.model.toDetailUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 麻友详情状态（规格 3.4）。 */
data class FriendDetailUiState(
    val loading: Boolean = true,
    val detail: FriendDetailUi? = null,
    val editVisible: Boolean = false,
    val archiveConfirmVisible: Boolean = false,
    /** 归档/取消归档进行中，期间禁用菜单避免重复触发。 */
    val working: Boolean = false,
)

/** 麻友详情的一次性事件。归档不跳页，界面靠订阅自动刷新。 */
sealed interface FriendDetailEvent {
    data class Failed(@StringRes val messageRes: Int) : FriendDetailEvent
}

/**
 * 麻友详情 ViewModel（规格 3.4）。
 *
 * 四份数据各订阅一份再在内存里合并：档案、跨场统计、参与的场次，以及**全部场次**。
 * 统计与逐场结果都实时派生自 `session_result`，所以任何一场被修改或删除后
 * 这里的数字会立刻跟着变，不存在对不上的第二份累计值。
 *
 * 全部场次只为一件事而订阅：本页要显示与牌友列表相同的三个标签，
 * 而它们要横比所有牌友，光看这位麻友自己的场次判不出来（见 `friendBadges`）。
 */
class FriendDetailViewModel(
    private val playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
    private val playerId: String,
) : ViewModel() {

    private data class Flags(
        val editVisible: Boolean = false,
        val archiveConfirmVisible: Boolean = false,
        val working: Boolean = false,
    )

    private val _events = MutableSharedFlow<FriendDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FriendDetailEvent> = _events.asSharedFlow()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<FriendDetailUiState> = combine(
        playerRepository.observeById(playerId),
        playerRepository.observeStatistics(playerId),
        sessionRepository.observeSessionsByPlayer(playerId),
        sessionRepository.observeSessions(),
        flags,
    ) { player, statistics, sessions, allSessions, current ->
        FriendDetailUiState(
            loading = false,
            detail = player?.toDetailUi(
                statistics = statistics,
                sessionsWithResults = sessions,
                // 与牌友列表同一处判定，因此两页的标签不可能分叉
                badges = friendBadges(allSessions.flatMap { it.opponentMatches() })[playerId]
                    .orEmpty(),
            ),
            editVisible = current.editVisible,
            archiveConfirmVisible = current.archiveConfirmVisible,
            working = current.working,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FriendDetailUiState(),
    )

    // ————————————————————— 编辑 —————————————————————

    fun requestEdit() = flags.update { it.copy(editVisible = true) }

    fun dismissEdit() = flags.update { it.copy(editVisible = false) }

    fun saveEdit(rawNickname: String, rawNote: String) {
        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            when (val result = playerRepository.updateFriend(playerId, rawNickname, rawNote)) {
                is ManoteResult.Success -> flags.update { it.copy(editVisible = false, working = false) }
                is ManoteResult.Failure -> {
                    flags.update { it.copy(working = false) }
                    _events.tryEmit(FriendDetailEvent.Failed(result.error.messageRes()))
                }
            }
        }
    }

    // ————————————————————— 归档 —————————————————————

    fun requestArchiveToggle() = flags.update { it.copy(archiveConfirmVisible = true) }

    fun dismissArchiveToggle() = flags.update { it.copy(archiveConfirmVisible = false) }

    /**
     * 按当前状态归档或取消归档。归档幂等且可逆，历史场次始终保留（规格 3.4、T13）。
     *
     * 归档还是取消，以**点击这一刻库里的真实状态**为准，而不是界面上缓存的那一份：
     * 界面在弹确认框期间可能已被别处改动（例如从归档列表切回来），缓存值会做反动作。
     * 档案已不存在时明确失败，不静默当作成功。
     */
    fun confirmArchiveToggle() {
        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            val isArchived = playerRepository.findById(playerId)?.archivedAt != null
            val result = if (isArchived) {
                playerRepository.unarchiveFriend(playerId)
            } else {
                playerRepository.archiveFriend(playerId)
            }
            flags.update { it.copy(archiveConfirmVisible = false, working = false) }
            if (result is ManoteResult.Failure) {
                _events.tryEmit(FriendDetailEvent.Failed(result.error.messageRes()))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
