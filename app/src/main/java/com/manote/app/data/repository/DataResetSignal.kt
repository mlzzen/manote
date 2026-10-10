package com.manote.app.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「整库被替换」的跨页信号（规格 2.3：「导入成功后退出旧编辑并清除旧草稿，
 * 不能写入导入前的 playerId 或场次数据」）。
 *
 * 导入成功后由 [BackupRepository] 发出；当时还活着的编辑表单收到后立即作废自己的草稿并退出。
 * 使用 `replay = 0`：只需要通知「此刻还开着的页面」，不需要让之后才出现的页面重放这条历史。
 */
class DataResetSignal {

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyReset() {
        _events.tryEmit(Unit)
    }
}
