package com.manote.app.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.R
import com.manote.app.data.repository.BackupCheck
import com.manote.app.data.repository.BackupCodec
import com.manote.app.data.repository.BackupFile
import com.manote.app.data.repository.BackupFileStore
import com.manote.app.data.repository.BackupRepository
import com.manote.app.data.repository.BackupSummary
import com.manote.app.data.repository.DataCounts
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** 「我的」页状态。 */
data class SettingsUiState(
    val loading: Boolean = true,
    val selfNickname: String? = null,
    val renameVisible: Boolean = false,
    val saving: Boolean = false,
    /** 本机现有的场数与牌友数，用于说明导入会覆盖多少数据。 */
    val counts: DataCounts = DataCounts(sessionCount = 0, friendCount = 0),
    /** 正在读文件或写库（导出、导入期间禁用入口，避免并发操作）。 */
    val working: Boolean = false,
    /** 非空表示有一份已通过校验、等待用户确认覆盖的备份。 */
    val pendingImport: BackupSummary? = null,
    /** 回收站里的场次数；为 0 时「我的」页照常显示回收站入口，只是说一句它是空的。 */
    val trashCount: Int = 0,
)

/** 「我的」页的一次性事件。 */
sealed interface SettingsEvent {

    /** 一次性提示；成功与失败都用它，只是文案不同。 */
    data class Notify(@StringRes val messageRes: Int) : SettingsEvent

    /**
     * 导入成功（规格 6.1：成功清除旧页面草稿和旧本人 ID，回首页）。
     * 界面据此回到首页，离开任何停留在旧数据上的页面。
     */
    data object DataReplaced : SettingsEvent
}

/**
 * 「我的」页 ViewModel。
 *
 * 两件事：
 * - 本人昵称从唯一 SELF 订阅得到，改昵称走 `renameSelf`，只改昵称、不改 ID，
 *   也不影响历史场次里保存的昵称快照（规格 2.2、T06/T12）。
 * - 数据管理：JSON 导出与导入（规格 3.5、6.1）。导入先完整校验，把将覆盖的场数、
 *   牌友数与本人昵称交给界面确认，确认后才在一个事务里整体替换。
 *
 * 文件读写通过 [BackupFileStore] 进行，因此这里不直接依赖 Android 的 `ContentResolver`，
 * 业务分支可以在 JVM 上测。
 */
class SettingsViewModel(
    private val playerRepository: PlayerRepository,
    private val backupRepository: BackupRepository,
    private val fileStore: BackupFileStore,
    /** 只为回收站入口上那个场次数而订阅；本页不读其它场次数据。 */
    private val sessionRepository: SessionRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private data class Flags(
        val renameVisible: Boolean = false,
        val saving: Boolean = false,
        val counts: DataCounts = DataCounts(sessionCount = 0, friendCount = 0),
        val working: Boolean = false,
        val pendingImport: BackupSummary? = null,
    )

    /**
     * 已通过校验、等待确认的备份文件。
     * 只放在内存里：确认弹窗与它同生共死，转屏时 ViewModel 存活，两者不会失配。
     */
    private var pendingFile: BackupFile? = null

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<SettingsUiState> = combine(
        playerRepository.observeSelf(),
        sessionRepository.observeTrashCount(),
        flags,
    ) { self, trashCount, current ->
        SettingsUiState(
            loading = false,
            selfNickname = self?.nickname,
            renameVisible = current.renameVisible,
            saving = current.saving,
            counts = current.counts,
            working = current.working,
            pendingImport = current.pendingImport,
            trashCount = trashCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    init {
        refreshCounts()
    }

    // ————————————————————— 本人昵称 —————————————————————

    fun requestRename() = flags.update { it.copy(renameVisible = true) }

    fun dismissRename() = flags.update { it.copy(renameVisible = false) }

    fun rename(rawNickname: String) {
        viewModelScope.launch {
            flags.update { it.copy(saving = true) }
            when (val result = playerRepository.renameSelf(rawNickname)) {
                is ManoteResult.Success -> flags.update { it.copy(renameVisible = false, saving = false) }
                is ManoteResult.Failure -> {
                    flags.update { it.copy(saving = false) }
                    emit(SettingsEvent.Notify(result.error.messageRes()))
                }
            }
        }
    }

    // ————————————————————— 数据管理 —————————————————————

    /** 传给系统「创建文件」界面的建议文件名，不含昵称等用户文本（规格 6.2）。 */
    fun exportFileName(): String = BackupCodec.suggestedFileName(System.currentTimeMillis(), zoneId)

    /**
     * 导出到用户选定的位置。写入失败时数据库不受任何影响，提示重试即可（规格 3.5）。
     */
    fun exportTo(uri: String) {
        if (flags.value.working) return
        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            val succeeded = runCatching {
                val text = backupRepository.encode(backupRepository.export())
                fileStore.writeText(uri, text)
            }.isSuccess
            flags.update { it.copy(working = false) }
            emit(
                SettingsEvent.Notify(
                    if (succeeded) R.string.settings_export_success else R.string.settings_export_failed,
                ),
            )
        }
    }

    /**
     * 读取并校验所选文件。**只读不写**：校验不过就直接给出原因，数据库一动不动（T21）。
     * 通过了也只是把摘要摆出来等用户确认，此时还没有替换任何数据。
     */
    fun requestImport(uri: String) {
        if (flags.value.working) return
        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            val outcome = runCatching {
                backupRepository.inspect(fileStore.readBytes(uri))
            }.getOrNull()
            flags.update { it.copy(working = false) }

            when (outcome) {
                null -> emit(SettingsEvent.Notify(R.string.settings_import_read_failed))
                is BackupCheck.Rejected -> emit(SettingsEvent.Notify(outcome.reason.messageRes()))
                is BackupCheck.Passed -> {
                    pendingFile = outcome.file
                    flags.update { it.copy(pendingImport = outcome.summary) }
                }
            }
        }
    }

    /** 放弃导入：清掉待确认的文件，数据库未被触碰。 */
    fun dismissImport() {
        pendingFile = null
        flags.update { it.copy(pendingImport = null) }
    }

    /** 确认覆盖导入。整个过程在单个事务里，失败回滚，原库不变（规格 6.1）。 */
    fun confirmImport() {
        val file = pendingFile
        if (file == null) {
            dismissImport()
            return
        }
        if (flags.value.working) return

        viewModelScope.launch {
            flags.update { it.copy(working = true) }
            val result = runCatching { backupRepository.importBackup(file) }.getOrNull()
            pendingFile = null
            flags.update { it.copy(working = false, pendingImport = null) }

            when (result) {
                null -> emit(SettingsEvent.Notify(R.string.settings_import_failed))
                is BackupCheck.Rejected -> emit(SettingsEvent.Notify(result.reason.messageRes()))
                is BackupCheck.Passed -> {
                    refreshCounts()
                    emit(SettingsEvent.Notify(R.string.settings_import_success))
                    emit(SettingsEvent.DataReplaced)
                }
            }
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            val counts = backupRepository.currentCounts()
            flags.update { it.copy(counts = counts) }
        }
    }

    private suspend fun emit(event: SettingsEvent) {
        // 挂起式发送：buffer 满时排队而不是静默丢弃提示。
        _events.emit(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
