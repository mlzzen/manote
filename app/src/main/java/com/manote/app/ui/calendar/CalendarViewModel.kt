package com.manote.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.DatedNet
import com.manote.app.domain.MonthCalendar
import com.manote.app.domain.MonthSummary
import com.manote.app.ui.model.SessionGrouping
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 月历页状态：当前正在看的月份、那个月的逐日战况与当月统计。 */
data class CalendarUiState(
    val loading: Boolean = true,
    /** 正在看的月份（已夹在「有数据的最早一月」到「本月」之间）。 */
    val month: YearMonth = YearMonth.now(),
    /** 该月的逐日战况与合计。 */
    val summary: MonthSummary = MonthSummary.empty(YearMonth.now()),
    /** 该月的场次，倒序（最近在前），与场次页同一套排序。 */
    val sessions: List<SessionRowUi> = emptyList(),
    /** 高亮用的今天，按同一个时区算好，界面不再自己取 `LocalDate.now()`。 */
    val today: LocalDate = LocalDate.now(),
    /** 还能往前翻一个月（再往前就没有记录了）。 */
    val canGoEarlier: Boolean = false,
    /** 还能往后翻一个月（最多到本月）。 */
    val canGoLater: Boolean = false,
    /** 正在看的就是本月，「回到本月」因此不必出现。 */
    val isCurrentMonth: Boolean = true,
)

/**
 * 月历 ViewModel。
 *
 * 与其它页面同一个口径：不查新表，只订阅全部场次，在内存里按月切。
 * 翻月份因此是零成本的——数据早就在手里，切一个月只是重新分组一次。
 *
 * 翻页有边界：最早不超过「第一条记录所在的月」，最晚不超过「本月」。
 * 允许翻到 1970 年或明年，只会让用户在一片空白格子里找不到北；
 * 而「本月」永远可达，哪怕最后一场是上个月记的。
 *
 * [zoneId] 决定「哪一天 / 哪一月」怎么划分，与界面显示同源，
 * 默认设备时区；测试可以显式钉死。
 */
class CalendarViewModel(
    sessionRepository: SessionRepository,
    initialMonth: YearMonth,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId),
) : ViewModel() {

    private val currentMonth: YearMonth = YearMonth.from(today)

    /** 用户点出来的月份。它可能越过边界，真正展示的月份在 [uiState] 里夹一次。 */
    private val requested = MutableStateFlow(initialMonth)

    val uiState: StateFlow<CalendarUiState> =
        combine(requested, sessionRepository.observeSessions()) { month, sessions -> month to sessions }
            .map { (requestedMonth, sessions) ->
                val rows = sessions.map { it.toRowUi(zoneId) }
                val dated = rows.asReversed().map {
                    DatedNet(playedAt = it.playedAt, netMinor = it.selfNetMinor)
                }
                // 分组顺带给出边界：分组顺序沿用 playedAt DESC，第一个是最近的一月，最后一个是更早的一月。
                // 上下界各自与「本月」取一次极值，保证 lower ≤ 本月 ≤ upper——
                // 否则一旦库里的时间比系统时钟还新（导入了一份未来日期的备份），
                // coerceIn 会因下界大于上界直接抛异常。
                val groups = SessionGrouping.byMonth(rows, zoneId)
                val lowerBound = minOf(groups.lastOrNull()?.yearMonth ?: currentMonth, currentMonth)
                val upperBound = maxOf(groups.firstOrNull()?.yearMonth ?: currentMonth, currentMonth)
                val month = requestedMonth.coerceIn(lowerBound, upperBound)

                CalendarUiState(
                    loading = false,
                    month = month,
                    summary = MonthCalendar.of(dated, month, zoneId),
                    sessions = groups.firstOrNull { it.yearMonth == month }?.rows.orEmpty(),
                    today = today,
                    canGoEarlier = month > lowerBound,
                    canGoLater = month < upperBound,
                    isCurrentMonth = month == currentMonth,
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CalendarUiState(),
            )

    /** 跳到某个月；越界时由 [uiState] 夹回边界，界面不会停在空白月份上。 */
    fun select(month: YearMonth) {
        requested.value = month
    }

    /** 回到本月（顶栏那颗「本月」）。 */
    fun goToCurrentMonth() {
        requested.value = currentMonth
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
