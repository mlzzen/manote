package com.manote.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.manote.app.domain.SessionDuration

/**
 * 显式 migration（规格 5.1：升级必须写 migration，禁止 destructive migration）。
 *
 * 每条 migration 都要能在**已有数据的库**上跑通，而不是只在空库上验证过。
 */
object ManoteMigrations {

    /**
     * v1 → v2：`session` 增加 `durationMinutes`（本场时长，分钟）。
     *
     * 旧数据没有时长可言，统一补成默认的 4 小时——这正是「不填按 4 小时算」
     * 这条规则的取值，因此旧场次的时段归类与升级后新建的场次口径一致。
     * `NOT NULL DEFAULT` 让已有行一次性拿到这个值，不必再逐行回填。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE session ADD COLUMN durationMinutes INTEGER NOT NULL " +
                    "DEFAULT ${SessionDuration.DEFAULT_MINUTES}",
            )
        }
    }

    /** 全部 migration，按版本顺序排列；构建数据库时一次性注册。 */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
