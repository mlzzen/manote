package com.manote.app.ui.session

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.ui.model.SessionDetailUi
import com.manote.app.ui.model.messageRes
import com.manote.app.ui.model.toDetailUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 四人详情状态。场次被删除后 [detail] 变为 null，界面据此给出提示而不是崩溃。 */
data class SessionDetailUiState(
    val loading: Boolean = true,
    val detail: SessionDetailUi? = null,
    /** 删除二次确认弹窗是否可见（规格 3.3：删除需要确认）。 */
    val confirmDeleteVisible: Boolean = false,
    val deleting: Boolean = false,
)

/** 详情页的一次性事件。 */
sealed interface SessionDetailEvent {
    /** 删除成功。界面据此回到首页（规格 3.3）。 */
    data object Deleted : SessionDetailEvent

    data class Failed(@StringRes val messageRes: Int) : SessionDetailEvent
}

/**
 * 四人详情 ViewModel。
 *
 * 排名与结清建议都在订阅里实时重算（规格 2.5：根据当前四人结果实时计算），
 * 因此金额被修改后名次与结清会立刻跟着变。
 *
 * 删除经 Repository 的事务执行：先删四条结果再删场次，保留麻友档案（规格 2.3、5.1）。
 */
class SessionDetailViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionId: String,
) : ViewModel() {

    private val _events = MutableSharedFlow<SessionDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionDetailEvent> = _events.asSharedFlow()

    private val deleteRequested = MutableStateFlow(false)
    private val deletingNow = MutableStateFlow(false)

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        deleteRequested,
        deletingNow,
    ) { session, confirm, deleting ->
        SessionDetailUiState(
            loading = false,
            detail = session?.toDetailUi(),
            confirmDeleteVisible = confirm,
            deleting = deleting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SessionDetailUiState(),
    )

    fun requestDelete() {
        if (deletingNow.value) return
        deleteRequested.value = true
    }

    fun dismissDelete() {
        deleteRequested.value = false
    }

    /** 确认删除。失败时保留现场并提示，不假装成功。 */
    fun deleteConfirmed() {
        if (deletingNow.value) return
        viewModelScope.launch {
            deletingNow.value = true
            when (val result = sessionRepository.deleteSession(sessionId)) {
                is ManoteResult.Success -> {
                    deleteRequested.value = false
                    _events.tryEmit(SessionDetailEvent.Deleted)
                }

                is ManoteResult.Failure -> {
                    deleteRequested.value = false
                    _events.tryEmit(SessionDetailEvent.Failed(result.error.messageRes()))
                }
            }
            deletingNow.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
