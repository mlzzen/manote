package com.manote.app.ui.friends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.ui.model.FriendFiltering
import com.manote.app.ui.model.FriendRowUi
import com.manote.app.ui.model.messageRes
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
 * 档案与聚合各订阅一份，在内存里合并：场数与净输赢直接来自 `session_result` 的聚合，
 * 不另存累计值，因此修改或删除场次后这里的数字会立刻跟着变。
 *
 * 搜索、「包含已归档」开关与排序都是纯函数（[FriendFiltering]），便于单测。
 */
class FriendsViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    private val query = MutableStateFlow("")
    private val includeArchived = MutableStateFlow(false)

    val uiState: StateFlow<FriendsUiState> = combine(
        playerRepository.observeAllFriends(),
        playerRepository.observeAggregates(),
        query,
        includeArchived,
    ) { friends, aggregates, keyword, archived ->
        val aggregateByPlayer = aggregates.associateBy { it.playerId }
        val rows = friends.map { it.toRowUi(aggregateByPlayer[it.id]) }
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
