package com.manote.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * migration 注册表的自检（规格 5.1）。
 *
 * 真正会出事的是「把 version 改成 3 却忘了加 3 的 migration」：这种错在空库上完全看不出来，
 * 只有装了旧版本的用户升级时才会崩。所以这里不比对某个手抄的版本号，
 * 而是**打开一次真的库**，读出它实际的版本，再要求 migration 链条正好接到那里。
 *
 * 放在 sharedTest 下：它同时参与 JVM（Robolectric）与真机仪器测试两个源集。
 */
@RunWith(AndroidJUnit4::class)
class ManoteMigrationChainTest {

    @Test
    fun migrationsChainReachesTheActualDatabaseVersion() {
        // 与 RoomTestCase 同一个理由：不能让 Room 用默认的 ArchTaskExecutor 全局 IO 线程。
        // 它会在 database.close() 之后再跑一次失效通知，撞上已关闭的连接就抛
        // IllegalStateException: Illegal connection pointer。这条异常本身被丢掉，
        // 但同一个 JVM 里后跑的 Compose 测试会把它读成 UncaughtExceptionsBeforeTest，
        // 表现为随机某个 UI 测试挂掉。
        val queryExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-chain-db-query") }
        val transactionExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-chain-db-tx") }
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ManoteDatabase::class.java,
        )
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()
        val actualVersion = database.openHelper.writableDatabase.version
        database.close()
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()

        val migrations = ManoteMigrations.ALL.sortedBy { it.startVersion }

        assertEquals("第一条 migration 必须从版本 1 开始", 1, migrations.first().startVersion)
        migrations.forEachIndexed { index, migration ->
            assertEquals(
                "第 ${index + 1} 条 migration 的起点应与上一条相接",
                index + 1,
                migration.startVersion,
            )
            assertEquals(
                "每条 migration 只能升一个版本",
                migration.startVersion + 1,
                migration.endVersion,
            )
        }
        assertEquals(
            "库的实际版本是 $actualVersion，migration 链条必须正好接到它，中间不能有空洞",
            actualVersion,
            migrations.last().endVersion,
        )
    }
}
