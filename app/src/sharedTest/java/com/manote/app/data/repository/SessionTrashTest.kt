package com.manote.app.data.repository

import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回收站（软删除）的行为（规格 3.3）。
 *
 * 核心不变量只有一条：**移入回收站等于从一切读数里消失，但数据还在**。
 * 列表、统计、牌友战绩、备份导出四处都要立刻看不到它，同时结果行必须原地保留，
 * 否则「还原」就没有东西可还原。
 */
class SessionTrashTest : RoomTestCase() {

    @Test
    fun trashedSessionLeavesListStatisticsAndBackup() = runBlocking {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()

        sessionRepository.moveSessionToTrash("session-1").value()

        // 列表：一条都不剩
        assertEquals(emptyList<String>(), sessionRepository.observeSessions().first().map { it.session.id })
        // 统计：本人与麻友都清零
        assertEquals(0, playerRepository.statistics(players[0]).sessionCount)
        assertEquals(0L, playerRepository.statistics(players[0]).netMinor)
        assertEquals(0, playerRepository.statistics(players[1]).sessionCount)
        assertEquals(0L, playerRepository.statistics(players[1]).netMinor)
        // 备份：回收站里的场次与其结果都不导出
        val exported = backupRepository.export()
        assertTrue("回收站里的场次不该进备份", exported.sessions.isEmpty())
        assertTrue("回收站场次的结果也不该进备份", exported.sessionResults.isEmpty())
        // 导出的文件仍然能通过自己的校验（不会出现指向不存在场次的结果行）
        val json = backupRepository.encode(exported)
        assertTrue(backupRepository.inspect(json.toByteArray(Charsets.UTF_8)) is BackupCheck.Passed)

        // 但结果行还在库里：还原才有东西可还原
        assertEquals(4, resultsOf("session-1").size)
        assertEquals(1, trashCount())
    }

    @Test
    fun restoreBringsTheSessionAndItsStatisticsBack() = runBlocking {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()

        sessionRepository.moveSessionToTrash("session-1").value()
        sessionRepository.restoreSession("session-1").value()

        assertEquals(listOf("session-1"), sessionRepository.observeSessions().first().map { it.session.id })
        assertEquals(0, trashCount())
        assertEquals(3000L, playerRepository.statistics(players[0]).netMinor)
        assertEquals(1, playerRepository.statistics(players[0]).sessionCount)
    }

    @Test
    fun purgeRemovesTheSessionAndItsResultsForever() = runBlocking {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()
        sessionRepository.moveSessionToTrash("session-1").value()

        sessionRepository.purgeSession("session-1").value()

        assertEquals(0, sessionCount())
        assertEquals(0, trashCount())
        assertEquals(0, resultsOf("session-1").size)
        // 档案不受影响，历史清零后仍可继续使用
        assertEquals(0, playerRepository.statistics(players[0]).sessionCount)
    }

    @Test
    fun emptyTrashPurgesEveryTrashedSessionAndReportsTheCount() = runBlocking {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()
        sessionRepository.createSession(
            draftOf(players, listOf(-2000L, 2000L, 0L, 0L), id = "s-2"),
        ).value()
        sessionRepository.moveSessionToTrash("s-1").value()
        sessionRepository.moveSessionToTrash("s-2").value()

        assertEquals(2, sessionRepository.emptyTrash().value())

        assertEquals(0, trashCount())
        assertEquals(0, resultsOf("s-1").size)
        assertEquals(0, resultsOf("s-2").size)
    }

    @Test
    fun expiredTrashIsPurgedButRecentlyDeletedOnesSurvive() = runBlocking {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L), id = "old"),
        ).value()
        sessionRepository.createSession(
            draftOf(players, listOf(-2000L, 2000L, 0L, 0L), id = "fresh"),
        ).value()

        // 「old」很早被删；随后时间前进到超过保留期，才删掉「fresh」
        sessionRepository.moveSessionToTrash("old").value()
        nowMillis = BASE_NOW + SessionRepository.TRASH_RETENTION_MILLIS + 1L
        sessionRepository.moveSessionToTrash("fresh").value()

        assertEquals(1, sessionRepository.purgeExpiredTrash())

        assertEquals("只清掉超过保留期的那一场", listOf("fresh"), database.sessionDao().findTrashedIds())
        assertEquals(0, resultsOf("old").size)
        assertEquals(4, resultsOf("fresh").size)
    }
}
