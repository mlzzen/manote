package com.manote.app.ui.session

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.repository.BackupFileStore
import com.manote.app.data.repository.SessionCsv
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** 已经生成好、等待用户选定保存位置的 CSV。 */
data class PendingCsvExport(
    val fileName: String,
    val text: String,
)

/** 四人详情状态。场次被删除后 [detail] 变为 null，界面据此给出提示而不是崩溃。 */
data class SessionDetailUiState(
    val loading: Boolean = true,
    val detail: SessionDetailUi? = null,
    /** 删除二次确认弹窗是否可见（规格 3.3：删除需要确认）。 */
    val confirmDeleteVisible: Boolean = false,
    val deleting: Boolean = false,
    /** CSV 已生成，界面应立刻拉起系统「创建文件」入口。 */
    val pendingCsv: PendingCsvExport? = null,
    val exporting: Boolean = false,
)

/** 详情页的一次性事件。 */
sealed interface SessionDetailEvent {
    /** 删除成功。界面据此回到首页（规格 3.3）。 */
    data object Deleted : SessionDetailEvent

    /** 一次性提示（导出成功、导出失败、删除失败都用它）。 */
    data class Notify(@StringRes val messageRes: Int) : SessionDetailEvent
}

/**
 * 四人详情 ViewModel。
 *
 * 排名与结清建议都在订阅里实时重算（规格 2.5：根据当前四人结果实时计算），
 * 因此金额被修改后名次与结清会立刻跟着变。
 *
 * 删除经 Repository 的事务执行：先删四条结果再删场次，保留麻友档案（规格 2.3、5.1）。
 * CSV 导出只读：取当前场次的原始结果生成文本，再交给系统文件选择器写盘（规格 6.2）。
 */
class SessionDetailViewModel(
    private val sessionRepository: SessionRepository,
    private val fileStore: BackupFileStore,
    private val sessionId: String,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private data class Flags(
        val deleteRequested: Boolean = false,
        val deleting: Boolean = false,
        val exporting: Boolean = false,
        val pendingCsv: PendingCsvExport? = null,
    )

    private val _events = MutableSharedFlow<SessionDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionDetailEvent> = _events.asSharedFlow()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        flags,
    ) { session, current ->
        SessionDetailUiState(
            loading = false,
            detail = session?.toDetailUi(),
            confirmDeleteVisible = current.deleteRequested,
            deleting = current.deleting,
            pendingCsv = current.pendingCsv,
            exporting = current.exporting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SessionDetailUiState(),
    )

    fun requestDelete() {
        if (flags.value.deleting) return
        flags.update { it.copy(deleteRequested = true) }
    }

    fun dismissDelete() = flags.update { it.copy(deleteRequested = false) }

    /** 确认删除。失败时保留现场并提示，不假装成功。 */
    fun deleteConfirmed() {
        if (flags.value.deleting) return
        viewModelScope.launch {
            flags.update { it.copy(deleting = true) }
            when (val result = sessionRepository.deleteSession(sessionId)) {
                is ManoteResult.Success -> {
                    flags.update { it.copy(deleteRequested = false) }
                    emit(SessionDetailEvent.Deleted)
                }

                is ManoteResult.Failure -> {
                    flags.update { it.copy(deleteRequested = false) }
                    emit(SessionDetailEvent.Notify(result.error.messageRes()))
                }
            }
            flags.update { it.copy(deleting = false) }
        }
    }

    /**
     * 生成 CSV 文本。这里不写文件：先把内容准备好，界面拿到文件名再去拉系统「创建文件」入口，
     * 避免用户在系统界面里停留期间数据发生变化。
     */
    fun requestCsvExport() {
        if (flags.value.exporting) return
        viewModelScope.launch {
            flags.update { it.copy(exporting = true) }
            when (val result = sessionRepository.getSessionDetail(sessionId)) {
                is ManoteResult.Success -> {
                    val detail = result.value
                    val text = SessionCsv.build(detail, zoneId)
                    if (text == null) {
                        // 座位不齐说明库已损坏：宁可明确失败，也不导出一份缺列的文件
                        flags.update { it.copy(exporting = false) }
                        emit(SessionDetailEvent.Notify(R.string.csv_export_failed))
                    } else {
                        flags.update {
                            it.copy(
                                exporting = false,
                                pendingCsv = PendingCsvExport(
                                    fileName = SessionCsv.fileName(detail, zoneId),
                                    text = text,
                                ),
                            )
                        }
                    }
                }

                is ManoteResult.Failure -> {
                    flags.update { it.copy(exporting = false) }
                    emit(SessionDetailEvent.Notify(R.string.csv_export_failed))
                }
            }
        }
    }

    /**
     * 用户选定了保存位置（[uri] 为 null 表示取消）。
     * 取消不做任何事、也不报错——用户只是反悔了（规格 3.5）。
     */
    fun onCsvTargetChosen(uri: String?) {
        val pending = flags.value.pendingCsv
        flags.update { it.copy(pendingCsv = null) }
        if (uri == null || pending == null) return

        viewModelScope.launch {
            val succeeded = runCatching { fileStore.writeText(uri, pending.text) }.isSuccess
            emit(
                SessionDetailEvent.Notify(
                    if (succeeded) R.string.csv_export_success else R.string.csv_export_failed,
                ),
            )
        }
    }

    private fun emit(event: SessionDetailEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
