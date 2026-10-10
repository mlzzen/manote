package com.manote.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.PlayerStatistics
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页状态。统计与最近场次都来自同一次订阅，两处数字不会互相对不上。 */
data class HomeUiState(
    val loading: Boolean = true,
    /** 本人昵称；档案尚未就绪时为 null。 */
    val selfNickname: String? = null,
    val statistics: PlayerStatistics = PlayerStatistics.EMPTY,
    val recentSessions: List<SessionRowUi> = emptyList(),
)

/**
 * 首页 ViewModel。
 *
 * 规格 2.4：统计一律从 session_result 实时计算，禁止另存手工累计；
 * 因此这里订阅全部场次及其结果，再在内存里汇总，任何写入都会自动刷新首页数字。
 */
class HomeViewModel(
    private val playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            playerRepository.observeSelf(),
            sessionRepository.observeSessions(),
        ) { self, sessions ->
            val rows = sessions.map { it.toRowUi() }
            HomeUiState(
                loading = false,
                selfNickname = self?.nickname,
                // seat=0 恒为 SELF，因此 seat 0 的净额序列就是本人的跨场净额序列
                statistics = PlayerStatistics.of(rows.map { it.selfNetMinor }),
                recentSessions = rows.take(RECENT_LIMIT),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(),
        )

    init {
        // 幂等兜底：Application 启动时已尝试创建，这里再确认一次，
        // 避免首页在启动协程尚未完成时长时间没有本人档案。
        // 两次调用都会被 WriteSerializer 串行化，不会创建出第二个 SELF。
        viewModelScope.launch { playerRepository.ensureSelfPlayer() }
    }

    private companion object {
        /** 首页最多展示 5 场，完整列表在「场次」页。 */
        const val RECENT_LIMIT = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
