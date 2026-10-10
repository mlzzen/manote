package com.manote.app

import android.app.Application
import android.util.Log
import com.manote.app.domain.ManoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：构造依赖容器，并在启动时确保唯一的本人档案存在（规格 2.2）。
 *
 * 该协程作用域只承载启动期的必要写入，不用于 UI 工作。
 */
class ManoteApplication : Application() {

    val container: ManoteContainer by lazy { ManoteContainer(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val result = container.playerRepository.ensureSelfPlayer()
            if (result is ManoteResult.Failure) {
                // 只记录技术错误码，不输出昵称、金额等业务数据（规格 5.2）。
                Log.e(TAG, "首次启动创建本人档案失败，错误码=${result.error}")
            }
        }
    }

    private companion object {
        const val TAG = "ManoteApplication"
    }
}
