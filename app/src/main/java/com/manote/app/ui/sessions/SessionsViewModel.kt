package com.manote.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.SessionRepository
import com.manote.app.ui.model.SessionFilter
import com.manote.app.ui.model.SessionFiltering
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 场次列表状态。 */
data class SessionsUiState(
    val loading: Boolean = true,
    val filter: SessionFilter = SessionFilter.ALL,
    val query: String = "",
    /** 当前筛选与搜索下要展示的场次。 */
    val visible: List<SessionRowUi> = emptyList(),
    /** 库里是否存在任何场次，用于区分「还没有记录」与「筛选/搜索后为空」。 */
    val hasAny: Boolean = false,
)

/**
 * 场次列表 ViewModel。
 *
 * 订阅全部场次，月份筛选与关键词搜索都只在内存里做，不额外发起查询；
 * 时间以注入的 [clock] 为准，便于测试「本月 / 上月」。
 */
class SessionsViewModel(
    sessionRepository: SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val filter = MutableStateFlow(SessionFilter.ALL)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<SessionsUiState> = combine(
        sessionRepository.observeSessions(),
        filter,
        query,
    ) { sessions, currentFilter, keyword ->
        val rows = sessions.map { it.toRowUi() }
        SessionsUiState(
            loading = false,
            filter = currentFilter,
            query = keyword,
            visible = SessionFiltering.apply(
                rows = rows,
                filter = currentFilter,
                nowMillis = clock(),
                query = keyword,
            ),
            hasAny = rows.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SessionsUiState(),
    )

    fun setFilter(value: SessionFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
