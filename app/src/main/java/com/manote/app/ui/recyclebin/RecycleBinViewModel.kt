package com.manote.app.ui.recyclebin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.repository.SessionRepository
import com.manote.app.domain.ManoteResult
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.messageRes
import com.manote.app.ui.model.toRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 回收站状态（规格 3.3）。 */
data class RecycleBinUiState(
    val loading: Boolean = true,
    /** 回收站里的场次，最近删的在前。 */
    val rows: List<SessionRowUi> = emptyList(),
    /** 正在还原或删除，期间禁用其它操作避免重复触发。 */
    val working: Boolean = false,
    /** 非空表示正在确认彻底删除这一场。 */
    val pendingPurgeId: String? = null,
    /** 是否正在确认「清空回收站」。 */
    val confirmEmptyVisible: Boolean = false,
)

/** 回收站的一次性事件。 */
sealed interface RecycleBinEvent {
    /**
     * 一次性提示（还原、彻底删除、清空的结果都用它）。
     * [formatArg] 非空时按 `%1$d` 格式化，用于「已彻底删除 N 场」这类带数量的文案。
     */
    data class Notify(
        @StringRes val messageRes: Int,
        val formatArg: Int? = null,
    ) : RecycleBinEvent
}

/**
 * 回收站 ViewModel（规格 3.3）。
 *
 * 只订阅回收站那一份数据（`deletedAt IS NOT NULL`），与场次列表订阅的那份互补，
 * 两者加起来才是库里全部的行。还原与彻底删除都经 Repository 的事务执行，
 * 列表靠订阅自动刷新。
 */
class RecycleBinViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private data class Flags(
        val working: Boolean = false,
        val pendingPurgeId: String? = null,
        val confirmEmptyVisible: Boolean = false,
    )

    private val _events = MutableSharedFlow<RecycleBinEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RecycleBinEvent> = _events.asSharedFlow()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<RecycleBinUiState> =
        combine(sessionRepository.observeTrash(), flags) { trash, current ->
            RecycleBinUiState(
                loading = false,
                rows = trash.map { it.toRowUi() },
                working = current.working,
                pendingPurgeId = current.pendingPurgeId,
                confirmEmptyVisible = current.confirmEmptyVisible,
            )
        }
            // 与其它页面一致：映射放到 Default 线程，别占着主线程。
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = RecycleBinUiState(),
            )

    // ————————————————————— 还原 —————————————————————

    /** 还原是安全操作（可逆），因此不弹确认，直接执行。 */
    fun restore(sessionId: String) {
        if (flags.value.working) return
        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            val result = sessionRepository.restoreSession(sessionId)
            flags.update { it.copy(working = false) }
            when (result) {
                is ManoteResult.Success -> emit(RecycleBinEvent.Notify(R.string.bin_restore_success))
                // 满 1,000 场时还原会被拒绝，这里直接把上限那条错误提示出来
                is ManoteResult.Failure -> emit(RecycleBinEvent.Notify(result.error.messageRes()))
            }
        }
    }

    // ————————————————————— 彻底删除 —————————————————————

    fun requestPurge(sessionId: String) = flags.update { it.copy(pendingPurgeId = sessionId) }

    fun dismissPurge() = flags.update { it.copy(pendingPurgeId = null) }

    fun confirmPurge() {
        val sessionId = flags.value.pendingPurgeId ?: return
        if (flags.value.working) return
        viewModelScope.launch {
            flags.update { it.copy(working = true, pendingPurgeId = null) }
            val result = sessionRepository.purgeSession(sessionId)
            flags.update { it.copy(working = false) }
            when (result) {
                is ManoteResult.Success -> emit(RecycleBinEvent.Notify(R.string.bin_purge_success))
                is ManoteResult.Failure -> emit(RecycleBinEvent.Notify(R.string.bin_purge_failed))
            }
        }
    }

    // ————————————————————— 清空 —————————————————————

    fun requestEmpty() = flags.update { it.copy(confirmEmptyVisible = true) }

    fun dismissEmpty() = flags.update { it.copy(confirmEmptyVisible = false) }

    fun confirmEmpty() {
        if (flags.value.working) return
        viewModelScope.launch {
            flags.update { it.copy(working = true, confirmEmptyVisible = false) }
            val result = sessionRepository.emptyTrash()
            flags.update { it.copy(working = false) }
            when (result) {
                is ManoteResult.Success -> emit(
                    RecycleBinEvent.Notify(R.string.bin_empty_success_format, result.value),
                )
                is ManoteResult.Failure -> emit(RecycleBinEvent.Notify(R.string.bin_purge_failed))
            }
        }
    }

    private suspend fun emit(event: RecycleBinEvent) {
        _events.emit(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
