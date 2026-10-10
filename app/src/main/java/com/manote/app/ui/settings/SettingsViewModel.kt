package com.manote.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manote.app.data.repository.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 「我的」页状态。 */
data class SettingsUiState(
    val loading: Boolean = true,
    val selfNickname: String? = null,
)

/**
 * 「我的」页 ViewModel。
 *
 * 本人昵称从唯一 SELF 订阅得到；改昵称属 P3，这里只读展示，
 * 因此不放置点了没有反应的编辑入口。
 */
class SettingsViewModel(
    playerRepository: PlayerRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        playerRepository.observeSelf()
            .map { SettingsUiState(loading = false, selfNickname = it?.nickname) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
