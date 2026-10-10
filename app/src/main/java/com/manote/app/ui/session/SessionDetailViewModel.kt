package com.manote.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.SessionRepository
import com.manote.app.ui.model.SessionDetailUi
import com.manote.app.ui.model.toDetailUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 四人详情状态。场次被删除后 [detail] 变为 null，界面据此给出提示而不是崩溃。 */
data class SessionDetailUiState(
    val loading: Boolean = true,
    val detail: SessionDetailUi? = null,
)

/**
 * 四人详情 ViewModel。
 *
 * 排名与结清建议都在订阅里实时重算（规格 2.5：根据当前四人结果实时计算），
 * 因此金额被修改后名次与结清会立刻跟着变。
 */
class SessionDetailViewModel(
    sessionRepository: SessionRepository,
    sessionId: String,
) : ViewModel() {

    val uiState: StateFlow<SessionDetailUiState> =
        sessionRepository.observeSessionDetail(sessionId)
            .map { SessionDetailUiState(loading = false, detail = it?.toDetailUi()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SessionDetailUiState(),
            )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
