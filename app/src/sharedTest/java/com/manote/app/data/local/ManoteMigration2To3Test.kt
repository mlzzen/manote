package com.manote.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * v2 → v3 的升级（规格 5.1、3.3）。
 *
 * 这条 migration 只加一列 `deletedAt`。风险点不在「加列」本身，而在两个方向：
 * 升级后旧场次必须**仍然在列表里**（`deletedAt` 为 NULL，而不是被当成已删除），
 * 以及这一列必须真的可空、能容纳回收站里的时间戳。
 *
 * 与 1→2 那条一样，这里造一个真的 v2 库、塞一行旧场次，再用 Room 打开它。
 * 建表语句逐个抄自 `app/schemas/.../2.json`。
 */
@RunWith(AndroidJUnit4::class)
class ManoteMigration2To3Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // 自带线程池：不用 ArchTaskExecutor 的全局 IO 线程，避免 close 之后仍有失效通知
    // 在后台访问已关闭的连接（详情见 RoomTestCase 里的同一段说明）。
    private val queryExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-migration-v3-query") }
    private val transactionExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-migration-v3-tx") }

    @After
    fun deleteDatabase() {
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun upgradingFromV2KeepsRowsAndLeavesThemOutOfTheTrash() = runBlocking {
        createV2DatabaseWithOneSession()

        val database = openDatabaseWithMigrations()
        try {
            val session = database.sessionDao().findById(SESSION_ID)
            assertNotNull("升级不能丢掉 v2 里已有的场次", session)
            assertNull("旧场次升级后必须是「未删除」，而不是被打上删除时间", session!!.deletedAt)
            assertEquals("老场次的其余字段原样保留", 240, session.durationMinutes)
            assertEquals("周五晚局", session.title)

            // 旧数据仍算在场：计数与列表都要看得到它
            assertEquals(1, database.sessionDao().count())
            assertEquals(0, database.sessionDao().trashCount())
            assertEquals(
                listOf(SESSION_ID),
                database.sessionDao().observeAllWithResults().first().map { it.session.id },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun newRowsAfterMigrationCanEnterAndLeaveTheTrash() = runBlocking {
        createV2DatabaseWithOneSession()

        val database = openDatabaseWithMigrations()
        try {
            // 升级之后写入的场次照常能软删除、能还原
            assertEquals(
                1,
                database.sessionDao().moveToTrash(SESSION_ID, deletedAt = 5_000L, updatedAt = 5_000L),
            )
            assertEquals(0, database.sessionDao().count())
            assertEquals(1, database.sessionDao().trashCount())
            assertEquals(
                listOf(SESSION_ID),
                database.sessionDao().observeTrashWithResults().first().map { it.session.id },
            )

            assertEquals(1, database.sessionDao().restoreFromTrash(SESSION_ID, updatedAt = 6_000L))
            assertEquals(1, database.sessionDao().count())
            assertEquals(0, database.sessionDao().trashCount())
        } finally {
            database.close()
        }
    }

    /** 造一个 v2 的库文件：三张表 + 一行场次 + 两条结果，user_version 留在 2。 */
    private fun createV2DatabaseWithOneSession() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V2_SCHEMA.forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // 这个 Callback 只负责造出 v2 的库；升级由 Room 的 migration 负责
                    }
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            // 先插档案、再插场次、最后插结果：外键的引用顺序不能倒过来
            db.execSQL(
                "INSERT INTO player (id, kind, nickname, note, archivedAt, createdAt, updatedAt) " +
                    "VALUES ('$SELF_ID', 'SELF', '我', '', NULL, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO player (id, kind, nickname, note, archivedAt, createdAt, updatedAt) " +
                    "VALUES ('$FRIEND_ID', 'FRIEND', '老王', '', NULL, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO session (id, title, playedAt, durationMinutes, note, createdAt, updatedAt, revision) " +
                    "VALUES ('$SESSION_ID', '周五晚局', 3000, 240, '', 1, 2, 1)",
            )
            db.execSQL(
                "INSERT INTO session_result (sessionId, seat, playerId, nameSnapshot, netMinor) " +
                    "VALUES ('$SESSION_ID', 0, '$SELF_ID', '我', 3000)",
            )
            db.execSQL(
                "INSERT INTO session_result (sessionId, seat, playerId, nameSnapshot, netMinor) " +
                    "VALUES ('$SESSION_ID', 1, '$FRIEND_ID', '老王', -3000)",
            )
        }
        helper.close()
    }

    private fun openDatabaseWithMigrations(): ManoteDatabase = Room
        .databaseBuilder(context, ManoteDatabase::class.java, DB_NAME)
        .addMigrations(*ManoteMigrations.ALL)
        .allowMainThreadQueries()
        .setQueryExecutor(queryExecutor)
        .setTransactionExecutor(transactionExecutor)
        .build()

    private companion object {
        const val DB_NAME = "manote-migration-v3-test.db"
        const val SESSION_ID = "00000000-0000-4000-8000-000000000009"
        const val SELF_ID = "00000000-0000-4000-8000-000000000001"
        const val FRIEND_ID = "00000000-0000-4000-8000-000000000002"

        /** 逐字抄自 `app/schemas/.../2.json`（v2 的真实建表语句）。 */
        val V2_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `player` (" +
                "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `nickname` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_player_kind` ON `player` (`kind`)",
            "CREATE TABLE IF NOT EXISTS `session` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, " +
                "`durationMinutes` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`revision` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_session_playedAt` ON `session` (`playedAt`)",
            "CREATE TABLE IF NOT EXISTS `session_result` (" +
                "`sessionId` TEXT NOT NULL, `seat` INTEGER NOT NULL, `playerId` TEXT NOT NULL, " +
                "`nameSnapshot` TEXT NOT NULL, `netMinor` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sessionId`, `seat`), " +
                "FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`playerId`) REFERENCES `player`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_session_result_sessionId_playerId` " +
                "ON `session_result` (`sessionId`, `playerId`)",
            "CREATE INDEX IF NOT EXISTS `index_session_result_playerId` " +
                "ON `session_result` (`playerId`)",
        )
    }
}
