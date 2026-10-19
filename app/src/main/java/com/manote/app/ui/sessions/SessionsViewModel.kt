package com.manote.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.ManoteError
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.model.SessionFiltering
import com.manote.app.ui.model.SessionFilters
import com.manote.app.ui.model.SessionFriendOption
import com.manote.app.ui.model.SessionGrouping
import com.manote.app.ui.model.SessionMonthGroup
import com.manote.app.ui.model.SessionOutcome
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 场次列表状态。 */
data class SessionsUiState(
    val loading: Boolean = true,
    /** 当前生效的筛选条件；弹层里的胶囊全部从这里读，改也改到这里。 */
    val filters: SessionFilters = SessionFilters(),
    /**
     * 当前筛选下要展示的场次，已按月分组（规格 3.3）。
     *
     * 只保留分组这一种形态：`monthGroups` 拼接起来就是完整列表，
     * 再加一份平铺列表会让两处有机会不一致。
     */
    val monthGroups: List<SessionMonthGroup> = emptyList(),
    /** 年筛选的选项：库里出现过的年份，由近到远。 */
    val yearOptions: List<Int> = emptyList(),
    /** 牌友筛选的选项：同桌过的牌友，同桌次数多的在前。 */
    val friendOptions: List<SessionFriendOption> = emptyList(),
    /** 库里是否存在任何场次，用于区分「还没有记录」与「筛选后为空」。 */
    val hasAny: Boolean = false,
    /** 是否处于多选模式：行不再打开详情，而是切换选中。 */
    val selecting: Boolean = false,
    /** 已选中的场次 ID，恒为「当前看得见的那批」的子集。 */
    val selectedIds: Set<String> = emptySet(),
    /** 正在删除：确认按钮置灰，避免连点删两遍。 */
    val deleting: Boolean = false,
) {
    /** 筛选后还剩多少场——弹层底部那颗按钮上的数字，就是它。 */
    val visibleCount: Int get() = monthGroups.sumOf { it.sessionCount }

    /** 筛选后这些场次的本人净额合计，分。 */
    val visibleNetMinor: Long get() = monthGroups.sumOf { it.selfNetMinor }

    /** 当前筛选结果里的全部 ID，全选只作用于这一批。 */
    val visibleIds: List<String> get() = monthGroups.flatMap { group -> group.rows.map { it.id } }

    /** 当前看得见的这些是否已经全选中。列表为空时算「没全选」。 */
    val allVisibleSelected: Boolean
        get() = selectedIds.isNotEmpty() && visibleIds.all { it in selectedIds }
}

/** 一次性提示：删完之后要告诉用户「删了几场」，失败则给出原因。 */
sealed interface SessionsMessage {
    data class Deleted(val count: Int) : SessionsMessage
    data class DeleteFailed(val error: ManoteError) : SessionsMessage
}

/**
 * 场次列表 ViewModel。
 *
 * 订阅全部场次与全部牌友档案，筛选只在内存里做，不额外发起查询；
 * 档案那份只用于把「牌友 id」翻成当前昵称——牌友改名后筛选项跟着改，
 * 但筛选本身认的是 ID，历史场次的昵称快照不会影响命中结果。
 *
 * 筛选条件是这一页的主要状态，界面上的改动都汇聚到 [filters] 一个流里：
 * 弹层不必自己维护草稿，改动即时生效，也就不会出现「弹层里选了、列表却没变」。
 *
 * **多选删除只作用于「看得见的场次」**：全选选的是当前筛选结果，
 * 筛选条件一改，已选集合立刻被裁到新的可见范围内——
 * 否则点一次删除会连带删掉屏幕上根本没显示的记录，而用户无法从界面上察觉。
 * 删除是软删除（进回收站，30 天内可还原），确认框里会写明这一点。
 */
class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    playerRepository: PlayerRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(SessionFilters())

    /** 多选模式的开关与选中集合。与筛选分开两个流，改筛选时不必重建选中对象。 */
    private val selection = MutableStateFlow(Selection())

    private val _messages = MutableSharedFlow<SessionsMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<SessionsMessage> = _messages

    val uiState: StateFlow<SessionsUiState> = combine(
        sessionRepository.observeSessions(),
        playerRepository.observeAllFriends(),
        filters,
        selection,
    ) { sessions, friends, currentFilters, currentSelection ->
        val rows = sessions.map { it.toRowUi() }
        val groups = SessionGrouping.byMonth(SessionFiltering.apply(rows, currentFilters))
        // 只认看得见的：筛选把某些场次藏起来之后，它们就不该再算「已选」
        val visibleIdSet = groups.flatMap { group -> group.rows.map { it.id } }.toSet()
        SessionsUiState(
            loading = false,
            filters = currentFilters,
            monthGroups = groups,
            yearOptions = SessionFiltering.yearOptions(rows),
            friendOptions = SessionFiltering.friendOptions(rows, friends),
            hasAny = rows.isNotEmpty(),
            selecting = currentSelection.active,
            selectedIds = currentSelection.ids intersect visibleIdSet,
            deleting = currentSelection.deleting,
        )
    }
        // 筛选、分组与选项构建都是全量遍历，每次数据或条件变更都会跑一遍；
        // 放到 Default 线程，弹层里连点胶囊时才不会掉帧。
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SessionsUiState(),
        )

    /** 弹层里每点一下都整体换一个条件对象：筛选的唯一入口。 */
    fun setFilters(value: SessionFilters) {
        filters.value = value
        clearSelection()
    }

    fun toggleTimeSlot(value: TimeSlot) = updateFilters { it.toggleTimeSlot(value) }

    fun selectYear(value: Int?) = updateFilters { it.withYear(value) }

    fun selectMonth(value: Int?) = updateFilters { it.withMonth(value) }

    fun selectDate(value: LocalDate?) = updateFilters { it.withDate(value) }

    fun toggleFriend(playerId: String) = updateFilters { it.toggleFriend(playerId) }

    fun toggleOutcome(value: SessionOutcome) = updateFilters { it.toggleOutcome(value) }

    fun resetFilters() {
        filters.value = SessionFilters()
        clearSelection()
    }

    /** 进入多选模式：顶栏换成「已选 N 场」，行变成可勾选的。 */
    fun startSelecting() {
        selection.update { it.copy(active = true) }
    }

    /** 退出多选并清空选中。 */
    fun cancelSelecting() {
        clearSelection()
    }

    /** 点一行：多选模式下切换它的选中状态。 */
    fun toggleSelected(sessionId: String) {
        selection.update { current ->
            if (!current.active) current
            else current.copy(
                ids = if (sessionId in current.ids) current.ids - sessionId else current.ids + sessionId,
            )
        }
    }

    /**
     * 全选 / 取消全选，只作用于当前筛选结果。
     *
     * 已经是全选状态时改为清空，这样一颗按钮就够了；否则「全选」按下去毫无反馈。
     */
    fun toggleSelectAll() {
        val state = uiState.value
        val visible = state.visibleIds.toSet()
        selection.update { current ->
            val allSelected = visible.isNotEmpty() && visible.all { it in current.ids }
            current.copy(ids = if (allSelected) current.ids - visible else current.ids + visible)
        }
    }

    /**
     * 删除已选中的场次 → 移入回收站（不是彻底删除）。
     *
     * 失败时保留选中，用户可以再点一次；成功则退出多选——
     * 一批记录已经不在了，继续停在「已选 0 场」的空壳模式里没有意义。
     */
    fun deleteSelected() {
        val state = uiState.value
        val ids = state.selectedIds
        if (ids.isEmpty() || state.deleting) return

        selection.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val result = sessionRepository.moveSessionsToTrash(ids)) {
                is ManoteResult.Success -> {
                    selection.value = Selection()
                    if (result.value > 0) _messages.tryEmit(SessionsMessage.Deleted(result.value))
                }

                is ManoteResult.Failure -> {
                    selection.update { it.copy(deleting = false) }
                    _messages.tryEmit(SessionsMessage.DeleteFailed(result.error))
                }
            }
        }
    }

    private fun updateFilters(block: (SessionFilters) -> SessionFilters) {
        filters.update(block)
        // 筛选一变，选中集合就作废：留着它只会让人误删看不见的记录
        clearSelection()
    }

    private fun clearSelection() {
        if (selection.value != Selection()) selection.value = Selection()
    }

    /** 多选的内部状态。确认框的开关留在界面里，这里只管「选了谁、能不能点删除」。 */
    private data class Selection(
        val active: Boolean = false,
        val ids: Set<String> = emptySet(),
        val deleting: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
