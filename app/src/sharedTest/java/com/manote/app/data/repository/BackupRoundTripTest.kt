package com.manote.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.uuidFor
import com.manote.app.data.value
import com.manote.app.domain.BackupRejectReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * JSON 备份的往返一致（规格 6.1）。对应验收用例 T20。
 *
 * 用真实的 Room + SQLite 栈（本机为 Robolectric）跑：导出 → 改乱 → 导入 → 再导出，
 * 要求两次导出的文本完全一致，并逐项核对人员、金额、时间、场数与统计。
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest : RoomTestCase() {

    @Test
    fun exportThenImportRestoresIdenticalLibrary() = runBlocking {
        val ids = givenSelfAndThreeFriends()
        val self = ids[0]
        val friend1 = ids[1]
        val friend2 = ids[2]
        val friend3 = ids[3]

        // 第一场：自己 +30，三位麻友各 -10（此时 friend1 还叫「上家」）
        sessionRepository.createSession(
            draftOf(ids, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()

        // 改名与归档都必须能被备份带走（T20）
        playerRepository.updateFriend(friend1, "老王", "常来").value()
        playerRepository.renameSelf("老板").value()

        // 第二场：换座位，friend1 坐到自己下家并赢 50
        sessionRepository.createSession(
            draftOf(
                listOf(self, friend3, friend2, friend1),
                listOf(-5000L, 0L, 0L, 5000L),
                id = uuidFor(102),
                playedAt = BASE_NOW - 120_000L,
            ),
        ).value()
        playerRepository.archiveFriend(friend2).value()

        val exported = backupRepository.export()
        val json = backupRepository.encode(exported)
        val inspected = backupRepository.inspect(json.toByteArray(Charsets.UTF_8))
        assertTrue("导出的文件必须能通过自己的校验：$inspected", inspected is BackupCheck.Passed)

        // 把库改乱，确保后面的导入确实起了作用，而不是「什么都没做也算通过」
        sessionRepository.deleteSession(uuidFor(101)).value()
        playerRepository.renameSelf("乱改").value()
        playerRepository.unarchiveFriend(friend2).value()

        val imported = backupRepository.importBackup(exported)
        assertTrue("导入应当成功：$imported", imported is BackupCheck.Passed)

        // 逐字节一致的二次导出：三张表全部回到原样
        assertEquals(json, backupRepository.encode(backupRepository.export()))

        // 场数与统计（统计一律由 session_result 实时算出，规格 2.4）
        assertEquals(2, sessionCount())
        assertEquals(-2000L, playerRepository.statistics(self).netMinor)
        assertEquals(4000L, playerRepository.statistics(friend1).netMinor)
        assertEquals(-1000L, playerRepository.statistics(friend2).netMinor)
        assertEquals(2, playerRepository.statistics(friend2).sessionCount)

        // 昵称快照按当时的名字保存，本人改名与麻友改名都不影响旧场
        val firstSession = sessionRepository.getSessionDetail(uuidFor(101)).value()
        assertEquals("我", firstSession.results.first { it.seat == 0 }.nameSnapshot)
        assertEquals("上家", firstSession.results.first { it.seat == 1 }.nameSnapshot)
        val secondSession = sessionRepository.getSessionDetail(uuidFor(102)).value()
        assertEquals("老板", secondSession.results.first { it.seat == 0 }.nameSnapshot)
        assertEquals("老王", secondSession.results.first { it.seat == 3 }.nameSnapshot)

        // 归档状态一并恢复
        assertEquals("老板", playerRepository.self()?.nickname)
        assertNotNull("已归档的麻友导入后仍是归档状态", playerRepository.findById(friend2)?.archivedAt)
    }

    @Test
    fun inspectRejectsOversizedNonUtf8AndNonJsonFiles() = runBlocking {
        givenSelfAndThreeFriends()
        val json = backupRepository.encode(backupRepository.export())

        assertTrue(backupRepository.inspect(json.toByteArray(Charsets.UTF_8)) is BackupCheck.Passed)

        // 有些编辑器另存会加 BOM，不应因此拒绝
        val withBom = ("\uFEFF$json").toByteArray(Charsets.UTF_8)
        assertTrue("带 BOM 的备份应当照常读取", backupRepository.inspect(withBom) is BackupCheck.Passed)

        val oversized = ByteArray((BackupCodec.MAX_FILE_BYTES + 1L).toInt())
        assertEquals(
            "超过 20 MiB 的文件必须按实际字节拒绝",
            BackupRejectReason.TOO_LARGE,
            (backupRepository.inspect(oversized) as BackupCheck.Rejected).reason,
        )

        val notUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        assertEquals(
            BackupRejectReason.MALFORMED,
            (backupRepository.inspect(notUtf8) as BackupCheck.Rejected).reason,
        )

        val notJson = "这不是备份".toByteArray(Charsets.UTF_8)
        assertEquals(
            BackupRejectReason.MALFORMED,
            (backupRepository.inspect(notJson) as BackupCheck.Rejected).reason,
        )
    }

    @Test
    fun partialAndUnbalancedSessionsSurviveTheRoundTrip() = runBlocking {
        val ids = givenSelfAndThreeFriends()

        // 只记录本人与上家，且合计不为 0（规格 1.3）
        sessionRepository.createSession(
            SessionDraft(
                id = uuidFor(101),
                playedAt = BASE_NOW - 60_000L,
                title = "只记了两个位置",
                note = "",
                seats = listOf(
                    SeatResultInput(0, ids[0], 3000L),
                    SeatResultInput(1, ids[1], -1000L),
                ),
            ),
        ).value()

        // 只记录本人
        sessionRepository.createSession(
            SessionDraft(
                id = uuidFor(102),
                playedAt = BASE_NOW - 120_000L,
                title = "只记了自己",
                note = "",
                seats = listOf(SeatResultInput(0, ids[0], -2000L)),
            ),
        ).value()

        val exported = backupRepository.export()
        val json = backupRepository.encode(exported)
        assertTrue(
            "自己的备份必须能通过自己的校验，否则用户根本恢复不了数据",
            backupRepository.inspect(json.toByteArray(Charsets.UTF_8)) is BackupCheck.Passed,
        )

        sessionRepository.deleteSession(uuidFor(101)).value()
        sessionRepository.deleteSession(uuidFor(102)).value()
        assertEquals(0, sessionCount())

        assertTrue(backupRepository.importBackup(exported) is BackupCheck.Passed)
        assertEquals(2, sessionCount())
        assertEquals(listOf(0, 1), resultsOf(uuidFor(101)).map { it.seat })
        assertEquals(listOf(0), resultsOf(uuidFor(102)).map { it.seat })
        assertEquals("部分座位与不平的合计都要逐字节恢复", json, backupRepository.encode(backupRepository.export()))

        // 没记录的位置不算「参加了 0 元的一场」，而是根本没参与
        assertEquals(1000L, playerRepository.statistics(ids[0]).netMinor)
        assertEquals(-1000L, playerRepository.statistics(ids[1]).netMinor)
        assertEquals("没记录的人不该被算成参加过", 0, playerRepository.statistics(ids[2]).sessionCount)
    }

    @Test
    fun currentCountsMatchTheLibrary() = runBlocking {
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(ids, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()

        val counts = backupRepository.currentCounts()

        // 三位麻友（不含本人），一场
        assertEquals(1, counts.sessionCount)
        assertEquals(3, counts.friendCount)
    }
}
