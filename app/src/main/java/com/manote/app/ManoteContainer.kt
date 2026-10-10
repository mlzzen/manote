package com.manote.app

import android.content.Context
import androidx.room.Room
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.data.repository.WriteSerializer

/**
 * 手动构造依赖（规格 4：单 Activity、手动构造依赖，不引入 Hilt 或通用框架）。
 *
 * 数据库与写入闸门在应用内各只有一份；两个 Repository 共享同一个 [WriteSerializer]，
 * 因此跨仓库的写操作同样串行。
 */
class ManoteContainer(context: Context) {

    private val database: ManoteDatabase = Room
        .databaseBuilder(
            context.applicationContext,
            ManoteDatabase::class.java,
            ManoteDatabase.DATABASE_NAME,
        )
        .build()

    private val writeSerializer = WriteSerializer()

    val playerRepository: PlayerRepository = PlayerRepository(database, writeSerializer)

    val sessionRepository: SessionRepository = SessionRepository(database, writeSerializer)
}
