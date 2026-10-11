package com.manote.app.ui.friends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.Rivalry
import com.manote.app.ui.model.FriendBadge
import com.manote.app.ui.model.FriendFiltering
import com.manote.app.ui.model.FriendRowUi
import com.manote.app.ui.model.messageRes
import com.manote.app.ui.model.opponentMatches
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 牌友页状态（规格 3.4）。 */
data class FriendsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val includeArchived: Boolean = false,
    /** 当前条件下要展示的麻友。 */
    val visible: List<FriendRowUi> = emptyList(),
    val totalActive: Int = 0,
    val totalArchived: Int = 0,
) {
    val hasAny: Boolean get() = totalActive + totalArchived > 0
}

/** 牌友页的一次性事件。 */
sealed interface FriendsEvent {
    /** 新建成功，关闭对话框即可；列表会通过订阅自动刷新。 */
    data object Created : FriendsEvent

    data class Failed(@StringRes val messageRes: Int) : FriendsEvent
}

/**
 * 牌友页 ViewModel（规格 3.4）。
 *
 * 档案、聚合与全部场次各订阅一份，在内存里合并：场数与净输赢直接来自 `session_result` 的聚合，
 * 不另存累计值，因此修改或删除场次后这里的数字会立刻跟着变。
 *
 * 三个标签（最苦主、福星、牌王）也在这里一次算完，见 [Rivalry]；
 * 除了聚合还要读场次结果本身才能判定，上限 1000 场，在内存里聚合的开销可以忽略。
 *
 * 搜索、「包含已归档」开关与排序都是纯函数（[FriendFiltering]），便于单测。
 */
class FriendsViewModel(
    private val playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    private val query = MutableStateFlow("")
    private val includeArchived = MutableStateFlow(false)

    val uiState: StateFlow<FriendsUiState> = combine(
        playerRepository.observeAllFriends(),
        playerRepository.observeAggregates(),
        sessionRepository.observeSessions(),
        query,
        includeArchived,
    ) { friends, aggregates, sessions, keyword, archived ->
        val aggregateByPlayer = aggregates.associateBy { it.playerId }
        // 三个标签都要横比所有同场账才能判定，因此在内存里一次算完，
        // 而不是交给每一行自己去比——那样既重复计算，也容易出现口径分歧。
        val matches = sessions.flatMap { it.opponentMatches() }
        val worstRivalId = Rivalry.worstRival(matches)?.playerId
        val bestRivalId = Rivalry.bestRival(matches)?.playerId
        val tableKingId = Rivalry.strongestOpponent(matches)?.playerId
        val rows = friends.map { friend ->
            friend.toRowUi(
                aggregate = aggregateByPlayer[friend.id],
                badges = buildSet {
                    if (friend.id == worstRivalId) add(FriendBadge.WORST_RIVAL)
                    if (friend.id == bestRivalId) add(FriendBadge.LUCKY_STAR)
                    if (friend.id == tableKingId) add(FriendBadge.TABLE_KING)
                },
            )
        }
        FriendsUiState(
            loading = false,
            query = keyword,
            includeArchived = archived,
            visible = FriendFiltering.apply(rows, keyword, archived),
            totalActive = rows.count { !it.archived },
            totalArchived = rows.count { it.archived },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FriendsUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setIncludeArchived(value: Boolean) {
        includeArchived.value = value
    }

    /** 同名档案数量，用于新建时的提示（规格 2.2）。仅提示，不阻止创建。 */
    suspend fun countSameNickname(nickname: String): Int =
        playerRepository.findFriendsByNickname(nickname.trim()).size

    fun createFriend(rawNickname: String, rawNote: String) {
        viewModelScope.launch {
            when (val result = playerRepository.createFriend(rawNickname, rawNote)) {
                is ManoteResult.Success -> _events.tryEmit(FriendsEvent.Created)
                is ManoteResult.Failure ->
                    _events.tryEmit(FriendsEvent.Failed(result.error.messageRes()))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
