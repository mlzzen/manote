package com.manote.app.ui.friends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.ui.model.messageRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 牌友列表的一行。 */
data class FriendRowUi(val id: String, val nickname: String, val note: String)

data class FriendsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val visible: List<FriendRowUi> = emptyList(),
    /** 未归档牌友总数，与 [visible] 区分，便于空态文案判断。 */
    val total: Int = 0,
)

sealed interface FriendsEvent {
    data object Created : FriendsEvent
    data class Failed(@StringRes val messageRes: Int) : FriendsEvent
}

/**
 * 牌友页 ViewModel。
 *
 * 只展示未归档牌友（规格 3.4 默认视图）。搜索在内存里做，
 * 匹配昵称或备注；新增走 Repository 事务并在事务内重新校验昵称与数量上限。
 */
class FriendsViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<FriendsUiState> =
        combine(playerRepository.observeActiveFriends(), query) { friends, keyword ->
            val rows = friends.map { FriendRowUi(it.id, it.nickname, it.note) }
            FriendsUiState(
                loading = false,
                query = keyword,
                visible = if (keyword.isBlank()) {
                    rows
                } else {
                    rows.filter {
                        it.nickname.contains(keyword, ignoreCase = true) ||
                            it.note.contains(keyword, ignoreCase = true)
                    }
                },
                total = rows.size,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FriendsUiState(),
        )

    fun setQuery(value: String) {
        query.value = value
    }

    /** 同名档案数量，用于创建前提示（规格 2.2）。仅提示，不阻止创建。 */
    suspend fun countSameNickname(nickname: String): Int =
        playerRepository.findFriendsByNickname(nickname.trim()).size

    fun createFriend(rawNickname: String, rawNote: String) {
        viewModelScope.launch {
            when (val result = playerRepository.createFriend(rawNickname, rawNote)) {
                is ManoteResult.Success -> _events.emit(FriendsEvent.Created)
                is ManoteResult.Failure -> _events.emit(FriendsEvent.Failed(result.error.messageRes()))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
