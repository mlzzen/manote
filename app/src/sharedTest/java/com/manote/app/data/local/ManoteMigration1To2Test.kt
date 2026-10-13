package com.manote.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * v1 → v2 的升级（规格 5.1）。
 *
 * 这是本工程的第一条 migration，也是唯一一类「空库上测不出来」的改动：
 * 新装的用户一切正常，只有**装着 v1 数据的老用户**升级时才会走到这段代码。
 * 因此这里不满足于「链条接得上」，而是真的造一个 v1 的库、塞一行旧场次、
 * 再用 Room 打开它，确认升级后旧数据还在、时长拿到了默认的 4 小时。
 *
 * 建表语句逐个抄自 `app/schemas/.../1.json`：只有与线上 v1 完全一致，
 * 升级后 Room 的 schema 校验才会真的走一遍，测出来的结论才算数。
 */
@RunWith(AndroidJUnit4::class)
class ManoteMigration1To2Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // 自带线程池：不用 ArchTaskExecutor 的全局 IO 线程，避免 close 之后仍有失效通知
    // 在后台访问已关闭的连接（详情见 RoomTestCase 里的同一段说明）。
    private val queryExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-migration-query") }
    private val transactionExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-migration-tx") }

    @After
    fun deleteDatabase() {
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun upgradingFromV1KeepsOldRowsAndBackfillsTheDefaultDuration() = runBlocking {
        createV1DatabaseWithOneSession()

        val database = openDatabaseWithMigrations()
        try {
            val session = database.sessionDao().findById(SESSION_ID)
            assertNotNull("升级不能丢掉 v1 里已有的场次", session)
            assertEquals(240, session!!.durationMinutes)
            assertEquals("老场次的其余字段原样保留", "周五晚局", session.title)
            assertEquals(3_000L, session.playedAt)

            val results = database.sessionResultDao().findBySession(SESSION_ID)
            assertEquals("外键关系不能被打断", 2, results.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun newRowsAfterMigrationCanChooseTheirOwnDuration() = runBlocking {
        createV1DatabaseWithOneSession()

        val database = openDatabaseWithMigrations()
        try {
            // 默认值只负责把旧行补上；升级之后写入的时长照用户填的来
            database.sessionDao().updateIfRevisionMatches(
                id = SESSION_ID,
                expectedRevision = 1L,
                title = "周五晚局",
                playedAt = 3_000L,
                durationMinutes = 270,
                note = "",
                updatedAt = 4_000L,
            )
            assertEquals(270, database.sessionDao().findById(SESSION_ID)!!.durationMinutes)
        } finally {
            database.close()
        }
    }

    /** 造一个 v1 的库文件：三张表 + 一行场次 + 两条结果，user_version 留在 1。 */
    private fun createV1DatabaseWithOneSession() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V1_SCHEMA.forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // 这个 Callback 只负责造出 v1 的库；升级由 Room 的 migration 负责
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
                "INSERT INTO session (id, title, playedAt, note, createdAt, updatedAt, revision) " +
                    "VALUES ('$SESSION_ID', '周五晚局', 3000, '', 1, 2, 1)",
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
        const val DB_NAME = "manote-migration-test.db"
        const val SESSION_ID = "00000000-0000-4000-8000-000000000009"
        const val SELF_ID = "00000000-0000-4000-8000-000000000001"
        const val FRIEND_ID = "00000000-0000-4000-8000-000000000002"

        /** 逐字抄自 `app/schemas/.../1.json`（v1 的真实建表语句）。 */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `player` (" +
                "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `nickname` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_player_kind` ON `player` (`kind`)",
            "CREATE TABLE IF NOT EXISTS `session` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
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
