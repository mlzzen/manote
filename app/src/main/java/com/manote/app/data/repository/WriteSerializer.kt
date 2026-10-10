package com.manote.app.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 所有写操作共用的串行闸门（规格 2.3：新建、修改、删除、档案操作统一经 Repository 串行写入）。
 *
 * 单实例贯穿全应用，因此跨仓库的写操作也不会相互交错。
 * 它只保证写入串行，读操作不受影响。
 */
class WriteSerializer {

    private val mutex = Mutex()

    suspend fun <T> withWriteLock(block: suspend () -> T): T = mutex.withLock { block() }
}
