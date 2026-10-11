package com.manote.app.data.repository

import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.error
import com.manote.app.data.value
import com.manote.app.domain.ManoteError
import com.manote.app.domain.Money
import com.manote.app.domain.SessionDuration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 修改与删除整场（规格 1.3、2.3、5.1；验收用例 T10–T13、T18、T19）。 */
class SessionEditTest : RoomTestCase() {

    /** 建一场基础数据：自己 +30、上家 -10、对家 -10、下家 -10。 */
    private suspend fun givenSavedSession(): List<String> {
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()
        return players
    }

    private fun editOf(
        nets: List<Long>,
        expectedRevision: Long = 1L,
        playedAt: Long = BASE_NOW - 60_000L,
        title: String = "周五晚局",
        note: String = "",
        durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
    ) = SessionEdit(
        id = "session-1",
        expectedRevision = expectedRevision,
        playedAt = playedAt,
        title = title,
        note = note,
        netMinorBySeat = mapOf(0 to nets[0], 1 to nets[1], 2 to nets[2], 3 to nets[3]),
        durationMinutes = durationMinutes,
    )

    @Test
    fun editUpdatesDurationAlongsideTheAmounts() = runBlocking {
        givenSavedSession()

        sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), durationMinutes = 270),
        ).value()

        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(270, detail.session.durationMinutes)
    }

    @Test
    fun editRejectsInvalidDurationAndKeepsTheStoredValue() = runBlocking {
        givenSavedSession()

        val error = sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), durationMinutes = 0),
        ).error()

        assertEquals(ManoteError.INVALID_DURATION, error)
        // 非法输入不落库，库里仍是原来那场 4 小时
        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(SessionDuration.DEFAULT_MINUTES, detail.session.durationMinutes)
        assertEquals(1L, detail.session.revision)
    }

    @Test
    fun editReplacesResultsWithoutAddingSessions() = runBlocking {
        // T10：原场改为 +60、-20、-20、-20 → 原场替换，场数不变，无叠加
        val players = givenSavedSession()

        val newRevision = sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), title = "改过的标题"),
        ).value()

        assertEquals(2L, newRevision)
        assertEquals(1, sessionCount())

        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(listOf(6000L, -2000L, -2000L, -2000L), detail.results.map { it.netMinor })
        assertEquals(2L, detail.session.revision)
        assertEquals("改过的标题", detail.session.title)

        // 统计刷新：本人净额由 +30 变为 +60，而不是叠加成 +90
        assertEquals(6000L, playerRepository.statistics(players[0]).netMinor)
        assertEquals(-2000L, playerRepository.statistics(players[1]).netMinor)
    }

    @Test
    fun editKeepsPlayersPositionsAndNameSnapshots() = runBlocking {
        // 规格 2.3：首版修改不支持换人员、位置及昵称快照
        givenSavedSession()
        val before = sessionRepository.getSessionDetail("session-1").value()

        sessionRepository.updateSession(editOf(listOf(6000L, -2000L, -2000L, -2000L))).value()

        val after = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(before.results.map { it.seat }, after.results.map { it.seat })
        assertEquals(before.results.map { it.playerId }, after.results.map { it.playerId })
        assertEquals(before.results.map { it.nameSnapshot }, after.results.map { it.nameSnapshot })
    }

    @Test
    fun editAcceptsUnbalancedAmountsAndReplacesResults() = runBlocking {
        // 规格 1.3：合计不为 0 也能保存；原场原子替换、不叠加
        val players = givenSavedSession()

        sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -1000L)),
        ).value()

        assertEquals(1, sessionCount())
        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(listOf(6000L, -2000L, -2000L, -1000L), detail.results.map { it.netMinor })
        assertEquals(2L, detail.session.revision)
        // 统计按新值重算，而不是把差额累加上去
        assertEquals(6000L, playerRepository.statistics(players[0]).netMinor)
        assertEquals(-1000L, playerRepository.statistics(players[3]).netMinor)
    }

    @Test
    fun editRejectsOutOfRangeAmountsAndLeavesOriginalIntact() = runBlocking {
        // 事务原子性：真校验失败时原场与结果都不应被改动
        givenSavedSession()
        val before = sessionRepository.getSessionDetail("session-1").value()

        val error = sessionRepository.updateSession(
            editOf(listOf(Money.MAX_NET_MINOR + 1L, -1000L, -1000L, 0L)),
        ).error()

        assertEquals(ManoteError.AMOUNT_OUT_OF_RANGE, error)

        val after = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(before.session, after.session)
        assertEquals(before.results, after.results)
        assertEquals(1, sessionCount())
    }

    @Test
    fun editRequiresExactlyTheRecordedSeatSet() = runBlocking {
        // 只记录了本人与上家的场次（规格 1.3）
        val players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            SessionDraft(
                id = "session-1",
                playedAt = BASE_NOW - 60_000L,
                title = "周五晚局",
                note = "",
                seats = listOf(
                    SeatResultInput(0, players[0], 3000L),
                    SeatResultInput(1, players[1], -3000L),
                ),
            ),
        ).value()

        sessionRepository.updateSession(
            editOf(listOf(5000L, -5000L, 0L, 0L)).copy(
                netMinorBySeat = mapOf(0 to 5000L, 1 to -5000L),
            ),
        ).value()
        assertEquals(listOf(5000L, -5000L), resultsOf("session-1").map { it.netMinor })

        // 人员与位置只读：提交的座位集合必须与保存时逐一对应
        val tooMany = sessionRepository.updateSession(
            editOf(listOf(5000L, -3000L, -2000L, 0L), expectedRevision = 2L),
        ).error()
        assertEquals(ManoteError.INVALID_SEAT_SET, tooMany)

        val tooFew = sessionRepository.updateSession(
            editOf(listOf(5000L, 0L, 0L, 0L), expectedRevision = 2L).copy(
                netMinorBySeat = mapOf(0 to 5000L),
            ),
        ).error()
        assertEquals(ManoteError.INVALID_SEAT_SET, tooFew)

        // 两次非法提交都没有改动库
        assertEquals(2L, sessionRepository.getSessionDetail("session-1").value().session.revision)
        assertEquals(listOf(5000L, -5000L), resultsOf("session-1").map { it.netMinor })
    }

    @Test
    fun editRejectsStaleRevision() = runBlocking {
        // T19：用过期 revision 保存必须被拒绝，且不改动库中数据
        givenSavedSession()
        sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), expectedRevision = 1L),
        ).value()

        val staleError = sessionRepository.updateSession(
            editOf(listOf(9000L, -3000L, -3000L, -3000L), expectedRevision = 1L),
        ).error()

        assertEquals(ManoteError.SESSION_REVISION_CONFLICT, staleError)

        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(2L, detail.session.revision)
        assertEquals(listOf(6000L, -2000L, -2000L, -2000L), detail.results.map { it.netMinor })
    }

    @Test
    fun editAfterDeleteIsRejectedInsteadOfRecreating() = runBlocking {
        // T19：场次已删除后保存 → 拒绝并提示重新加载，不重建场次
        givenSavedSession()
        sessionRepository.deleteSession("session-1").value()

        val error = sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), expectedRevision = 1L),
        ).error()

        assertEquals(ManoteError.SESSION_NOT_FOUND, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun editAcceptsBackdatedButRejectsFuturePlayedAt() = runBlocking {
        // T18：修改为另一过去时间可以，未来时间拒绝
        givenSavedSession()

        sessionRepository.updateSession(
            editOf(listOf(6000L, -2000L, -2000L, -2000L), playedAt = BASE_NOW - 172_800_000L),
        ).value()
        assertEquals(BASE_NOW - 172_800_000L, sessionRepository.getSessionDetail("session-1").value().session.playedAt)

        val error = sessionRepository.updateSession(
            editOf(listOf(9000L, -3000L, -3000L, -3000L), expectedRevision = 2L, playedAt = BASE_NOW + 1L),
        ).error()

        assertEquals(ManoteError.PLAYED_AT_IN_FUTURE, error)
        assertEquals(2L, sessionRepository.getSessionDetail("session-1").value().session.revision)
    }

    @Test
    fun editWorksEvenWhenAFriendIsArchived() = runBlocking {
        // 规格 2.3：归档状态不阻止历史修正
        val players = givenSavedSession()
        playerRepository.archiveFriend(players[1]).value()

        sessionRepository.updateSession(editOf(listOf(6000L, -2000L, -2000L, -2000L))).value()

        val detail = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(listOf(6000L, -2000L, -2000L, -2000L), detail.results.map { it.netMinor })
    }

    @Test
    fun editRejectsUnknownSession() = runBlocking {
        givenSelfAndThreeFriends()

        val error = sessionRepository.updateSession(editOf(listOf(6000L, -2000L, -2000L, -2000L))).error()

        assertEquals(ManoteError.SESSION_NOT_FOUND, error)
    }

    @Test
    fun deleteRemovesResultsButKeepsPlayers() = runBlocking {
        // T11：确认后统计移除且无孤立结果，Player 保留
        val players = givenSavedSession()

        sessionRepository.deleteSession("session-1").value()

        assertEquals(0, sessionCount())
        assertEquals(0, resultsOf("session-1").size)
        assertEquals(0, playerRepository.statistics(players[0]).sessionCount)
        assertNotNull(playerRepository.findById(players[1]))
        // 档案并未被归档，仍可用于新建场次
        assertNull(playerRepository.findById(players[1])!!.archivedAt)
    }

    @Test
    fun deleteUnknownSessionFails() = runBlocking {
        givenSelfAndThreeFriends()

        assertEquals(ManoteError.SESSION_NOT_FOUND, sessionRepository.deleteSession("session-1").error())
        assertEquals(0, sessionCount())
    }

    @Test
    fun renameFriendKeepsOldSessionSnapshotAndUsesNewNameForNewSessions() = runBlocking {
        // T12：旧快照不变，新场用新昵称，统计归同一个 ID
        val players = givenSavedSession()

        playerRepository.updateFriend(players[1], "新名字", "").value()
        sessionRepository.createSession(
            draftOf(players, listOf(-3000L, 3000L, 0L, 0L), id = "session-2"),
        ).value()

        val oldSnapshot = sessionRepository.getSessionDetail("session-1").value()
            .results.first { it.seat == 1 }.nameSnapshot
        val newSnapshot = sessionRepository.getSessionDetail("session-2").value()
            .results.first { it.seat == 1 }.nameSnapshot

        assertEquals("上家", oldSnapshot)
        assertEquals("新名字", newSnapshot)
        assertEquals(2, playerRepository.statistics(players[1]).sessionCount)
    }
}
