package com.manote.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.DatedNet
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.SessionExtremes
import com.manote.app.domain.StreakStats
import com.manote.app.domain.Streaks
import com.manote.app.domain.TimeSlotStatistics
import com.manote.app.domain.Trend
import com.manote.app.domain.TrendPoint
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页状态。统计、走势与最近场次都来自同一次订阅，几处数字不会互相对不上。 */
data class HomeUiState(
    val loading: Boolean = true,
    val statistics: PlayerStatistics = PlayerStatistics.EMPTY,
    /** 早 / 下午 / 晚三个时段的战绩，顺序固定，恒为三条（规格 2.4 的口径）。 */
    val slotStatistics: List<TimeSlotStatistics> = TimeSlotStatistics.blank(),
    /** 本人累计净额走势，按结束时间升序；一场都没有时为空。 */
    val trend: List<TrendPoint> = emptyList(),
    /** 连输 / 连赢与单日极值。 */
    val streak: StreakStats = StreakStats.EMPTY,
    /** 单场赢最多 / 输最多；从没赢过或从没输过时对应的一边为 null。 */
    val extremes: SessionExtremes = SessionExtremes.EMPTY,
    val recentSessions: List<SessionRowUi> = emptyList(),
)

/**
 * 首页 ViewModel。
 *
 * 规格 2.4：统计一律从 session_result 实时计算，禁止另存手工累计；
 * 因此这里订阅全部场次及其结果，再在内存里汇总，任何写入都会自动刷新首页数字。
 *
 * 累计净额走势与连输/连赢也走这一份数据：三者同源，不会出现「概览的净胜」与
 * 「曲线终点」对不上的情况。
 */
class HomeViewModel(
    private val playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        sessionRepository.observeSessions()
            .map { sessions ->
                val rows = sessions.map { it.toRowUi() }
                // 列表是倒序（最近在前），走势要正序：反过来再交给 Trend，
                // 这样同一时刻的场次也会按 ID 升序落在后面，与列表口径一致。
                val dated = rows.asReversed().map {
                    DatedNet(playedAt = it.playedAt, netMinor = it.selfNetMinor)
                }
                HomeUiState(
                    loading = false,
                    // seat=0 恒为 SELF，因此 seat 0 的净额序列就是本人的跨场净额序列
                    statistics = PlayerStatistics.of(rows.map { it.selfNetMinor }),
                    // 分时段统计与列表上的时段标签共用 SessionRowUi.timeSlot，不会各判各的
                    slotStatistics = TimeSlotStatistics.of(
                        rows.groupBy({ it.timeSlot }, { it.selfNetMinor }),
                    ),
                    trend = Trend.cumulative(dated),
                    streak = Streaks.of(dated),
                    // 单场极值与走势、连输连赢同源：都是这同一份 dated，
                    // 不会出现「曲线上的顶点」与「单场赢最多」对不上
                    extremes = SessionExtremes.of(dated),
                    recentSessions = rows.take(RECENT_LIMIT),
                )
            }
            // 统计、走势、连输连赢都在这里全量遍历，量级随场次增长；
            // 放到 Default 线程算，别让它和主线程的渲染抢同一帧。
            .flowOn(Dispatchers.Default)
            .stateIn(
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
