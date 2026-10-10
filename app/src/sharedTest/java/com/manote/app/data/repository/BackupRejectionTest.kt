package com.manote.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.data.ARRIVED_SUFFIX
import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.error
import com.manote.app.data.uuidFor
import com.manote.app.data.value
import com.manote.app.data.withFreshIds
import com.manote.app.data.local.PlayerKind
import com.manote.app.domain.BackupRejectReason
import com.manote.app.domain.ManoteError
import com.manote.app.domain.SessionRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 坏备份必须被拒绝、且原库一个字节都不变（规格 6.1）。对应验收用例 T21。
 *
 * 每个用例都在同一份真实数据上跑：先记录当前的导出文本，再尝试导入坏备份，
 * 最后再导一次做逐字节比较——只有「完全没动过」才算通过。
 */
@RunWith(AndroidJUnit4::class)
class BackupRejectionTest : RoomTestCase() {

    @Test
    fun everyRejectionReasonLeavesTheLibraryUntouched() = runBlocking {
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(ids, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()
        sessionRepository.createSession(
            draftOf(
                ids,
                listOf(-2000L, 500L, 500L, 1000L),
                id = uuidFor(102),
                playedAt = BASE_NOW - 120_000L,
            ),
        ).value()

        // 旧规格版本
        assertRejectedAndUntouched(BackupRejectReason.UNSUPPORTED_VERSION) {
            it.copy(schemaVersion = 2)
        }

        // 缺 SELF
        assertRejectedAndUntouched(BackupRejectReason.INVALID_SELF) {
            it.copy(players = it.players.filterNot { player -> player.kind == PlayerKind.SELF.name })
        }

        // 两 SELF（不并存两个本人）
        assertRejectedAndUntouched(BackupRejectReason.INVALID_SELF) {
            val self = it.players.first { player -> player.kind == PlayerKind.SELF.name }
            it.copy(players = it.players + self.copy(id = uuidFor(999)))
        }

        // 重复座位（把 seat 3 改成 seat 2）
        assertRejectedAndUntouched(BackupRejectReason.INCOMPLETE_SEATS) {
            it.copy(
                sessionResults = it.sessionResults.map { result ->
                    if (result.seat == 3) result.copy(seat = 2) else result
                },
            )
        }

        // 孤立引用（结果指向不存在的场次）
        assertRejectedAndUntouched(BackupRejectReason.ORPHAN_REFERENCE) {
            it.copy(
                sessionResults = it.sessionResults.map { result ->
                    if (result.seat == 1) result.copy(sessionId = uuidFor(998)) else result
                },
            )
        }

        // 缺少本人位置（把 seat 0 那条结果删掉）
        assertRejectedAndUntouched(BackupRejectReason.INCOMPLETE_SEATS) {
            it.copy(sessionResults = it.sessionResults.filterNot { result -> result.seat == 0 })
        }

        // 超限（1,001 场）
        assertRejectedAndUntouched(BackupRejectReason.LIMIT_EXCEEDED) {
            it.copy(
                sessions = (0..SessionRules.MAX_SESSIONS).map { index ->
                    it.sessions.first().copy(id = uuidFor(10_000 + index))
                },
                sessionResults = emptyList(),
            )
        }
    }

    @Test
    fun importReplacesEverythingRatherThanMerging() = runBlocking {
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(ids, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()

        val incoming = backupRepository.export().withFreshIds()
        val imported = backupRepository.importBackup(incoming)
        assertTrue("导入应当成功：$imported", imported is BackupCheck.Passed)

        // 只保留文件里的内容，本机原有的场次与档案都不再存在
        assertEquals(1, sessionCount())
        assertEquals(4, database.playerDao().findAllOrdered().size)

        val newSelf = playerRepository.self()
        assertEquals("本人昵称换成文件里的那个", "我$ARRIVED_SUFFIX", newSelf?.nickname)
        assertEquals(uuidFor(900), newSelf?.id)

        // 昵称快照按文件原样写入，不跟着当前昵称走（规格 2.2）
        val snapshot = sessionRepository.getSessionDetail(uuidFor(950)).value()
            .results.first { it.seat == 1 }.nameSnapshot
        assertEquals("上家", snapshot)
    }

    /**
     * T24 的实质：导入之后，导入前的 ID 再也写不进去，
     * 因此停留在旧编辑页上的草稿不会把旧身份的数据写进新库（规格 2.3）。
     */
    @Test
    fun oldIdsCannotBeWrittenAfterImport() = runBlocking {
        val oldIds = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(oldIds, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()

        val imported = backupRepository.importBackup(backupRepository.export().withFreshIds())
        assertTrue("导入应当成功：$imported", imported is BackupCheck.Passed)

        // 旧的档案 ID：新建场次会被事务拒绝
        assertEquals(
            ManoteError.PLAYER_NOT_FOUND,
            sessionRepository.createSession(
                draftOf(oldIds, listOf(1000L, -1000L, 0L, 0L), id = uuidFor(103)),
            ).error(),
        )

        // 旧的场次 ID：修改会被拒绝，而不是凭空重建一场
        assertEquals(
            ManoteError.SESSION_NOT_FOUND,
            sessionRepository.updateSession(
                SessionEdit(
                    id = uuidFor(101),
                    expectedRevision = 1L,
                    playedAt = BASE_NOW - 1_000L,
                    title = "改",
                    note = "",
                    netMinorBySeat = mapOf(0 to 3000L, 1 to -1000L, 2 to -1000L, 3 to -1000L),
                ),
            ).error(),
        )

        // 新库里的 ID 才是可写的
        val newSelfId = playerRepository.self()!!.id
        val newFriendIds = database.playerDao().findAllByKind(PlayerKind.FRIEND).map { it.id }
        assertNotEquals(oldIds.first(), newSelfId)
        sessionRepository.createSession(
            draftOf(
                listOf(newSelfId) + newFriendIds,
                listOf(1000L, -1000L, 0L, 0L),
                id = uuidFor(104),
            ),
        ).value()
        assertEquals("旧场次已被替换，只加上新记的这场", 2, sessionCount())
    }

    @Test
    fun onlySuccessfulImportNotifiesDataReset() = runBlocking {
        givenSelfAndThreeFriends()
        val received = mutableListOf<Unit>()
        val job = launch(Dispatchers.Unconfined) {
            dataResetSignal.events.collect { received += it }
        }

        // 被拒绝的导入不能发信号：界面不该因为一次失败而清草稿
        backupRepository.importBackup(backupRepository.export().copy(schemaVersion = 2))
        assertEquals(0, received.size)

        backupRepository.importBackup(backupRepository.export().withFreshIds())
        assertEquals("导入成功必须发出数据重置信号（T24）", 1, received.size)

        job.cancel()
    }

    // ————————————————————— 辅助 —————————————————————

    /** 断言被拒绝，且库与导入前逐字节一致。 */
    private suspend fun assertRejectedAndUntouched(
        expected: BackupRejectReason,
        mutate: (BackupFile) -> BackupFile,
    ) {
        val before = backupRepository.encode(backupRepository.export())

        val check = backupRepository.importBackup(mutate(backupRepository.export()))

        assertEquals(
            "拒绝原因应当具体",
            expected,
            (check as? BackupCheck.Rejected)?.reason,
        )
        assertEquals(
            "坏备份不能让原库发生任何变化",
            before,
            backupRepository.encode(backupRepository.export()),
        )
    }
}
