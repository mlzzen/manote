package com.manote.app.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
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

/** 「我的」页状态。 */
data class SettingsUiState(
    val loading: Boolean = true,
    val selfNickname: String? = null,
    val renameVisible: Boolean = false,
    val saving: Boolean = false,
)

/** 「我的」页的一次性事件。 */
sealed interface SettingsEvent {
    data class Failed(@StringRes val messageRes: Int) : SettingsEvent
}

/**
 * 「我的」页 ViewModel。
 *
 * 本人昵称从唯一 SELF 订阅得到，改昵称走 `renameSelf`：
 * 只改昵称、不改 ID，也不影响历史场次里保存的昵称快照（规格 2.2、T06/T12）。
 */
class SettingsViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private data class Flags(
        val renameVisible: Boolean = false,
        val saving: Boolean = false,
    )

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<SettingsUiState> = combine(
        playerRepository.observeSelf(),
        flags,
    ) { self, current ->
        SettingsUiState(
            loading = false,
            selfNickname = self?.nickname,
            renameVisible = current.renameVisible,
            saving = current.saving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun requestRename() = flags.update { it.copy(renameVisible = true) }

    fun dismissRename() = flags.update { it.copy(renameVisible = false) }

    fun rename(rawNickname: String) {
        viewModelScope.launch {
            flags.update { it.copy(saving = true) }
            when (val result = playerRepository.renameSelf(rawNickname)) {
                is ManoteResult.Success -> flags.update { it.copy(renameVisible = false, saving = false) }
                is ManoteResult.Failure -> {
                    flags.update { it.copy(saving = false) }
                    _events.tryEmit(SettingsEvent.Failed(result.error.messageRes()))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
