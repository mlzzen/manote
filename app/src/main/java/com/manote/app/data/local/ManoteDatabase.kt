package com.manote.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 本地数据库（规格 5.1）。
 *
 * schema 从版本 1 起导出（`app/schemas`），升级必须写显式 migration，
 * 禁止 destructive migration。
 */
@Database(
    entities = [
        PlayerEntity::class,
        SessionEntity::class,
        SessionResultEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ManoteConverters::class)
abstract class ManoteDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao

    abstract fun sessionDao(): SessionDao

    abstract fun sessionResultDao(): SessionResultDao

    companion object {
        const val DATABASE_NAME = "manote.db"
    }
}
