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

    /** 这一局里的四个人：[自己, 上家, 对家, 下家]。 */
    private var players: List<String> = emptyList()

    /** 建一场基础数据：自己 +30、上家 -10、对家 -10、下家 -10。 */
    private suspend fun givenSavedSession(): List<String> {
        players = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()
        return players
    }

    /** 四个位置都用 [players] 里的同序号那位。 */
    private fun fullSeats(nets: List<Long>): List<SeatResultInput> =
        players.indices.map { seat -> SeatResultInput(seat, players[seat], nets[seat]) }

    private fun editOfSeats(
        seats: List<SeatResultInput>,
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
        seats = seats,
        durationMinutes = durationMinutes,
    )

    private fun editOf(
        nets: List<Long>,
        expectedRevision: Long = 1L,
        playedAt: Long = BASE_NOW - 60_000L,
        title: String = "周五晚局",
        note: String = "",
        durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
    ) = editOfSeats(
        seats = fullSeats(nets),
        expectedRevision = expectedRevision,
        playedAt = playedAt,
        title = title,
        note = note,
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
    fun editKeepsSnapshotsWhenTheRosterIsUnchanged() = runBlocking {
        // 人员与位置都没动时，昵称快照不该被重写：改名不改写历史
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
    fun editCanSwapTwoFriendsBetweenSeats() = runBlocking {
        // 记错座位（上和下反了）：两个位置对调，人换了、金额跟着人走
        givenSavedSession()

        sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 3000L),
                    SeatResultInput(1, players[3], -1000L),
                    SeatResultInput(2, players[2], -1000L),
                    SeatResultInput(3, players[1], -1000L),
                ),
            ),
        ).value()

        val after = sessionRepository.getSessionDetail("session-1").value()
        assertEquals(players[3], after.results.first { it.seat == 1 }.playerId)
        assertEquals(players[1], after.results.first { it.seat == 3 }.playerId)
        // 场数不变，统计跟着新位置走
        assertEquals(1, sessionCount())
        assertEquals(-1000L, playerRepository.statistics(players[1]).netMinor)
    }

    @Test
    fun editCanGrowAndShrinkTheRecordedSeatSet() = runBlocking {
        // 只记录了本人与上家的场次（规格 1.3）：修改时可以补一位，也可以把人去掉
        players = givenSelfAndThreeFriends()
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

        // 补上对家
        sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 5000L),
                    SeatResultInput(1, players[1], -3000L),
                    SeatResultInput(2, players[2], -2000L),
                ),
            ),
        ).value()
        assertEquals(setOf(0, 1, 2), resultsOf("session-1").map { it.seat }.toSet())
        assertEquals(-2000L, playerRepository.statistics(players[2]).netMinor)

        // 再把上家整个去掉：位置集合收缩，那个人不再计入这一场
        sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 2000L),
                    SeatResultInput(2, players[2], -2000L),
                ),
                expectedRevision = 2L,
            ),
        ).value()

        assertEquals(setOf(0, 2), resultsOf("session-1").map { it.seat }.toSet())
        assertEquals(0, playerRepository.statistics(players[1]).sessionCount)
        assertEquals(2000L, playerRepository.statistics(players[0]).netMinor)
    }

    @Test
    fun editUsesCurrentNicknameOnlyWhereThePlayerChanged() = runBlocking {
        // 换人的位置取档案里的当前昵称；没动的位置继续沿用当时的快照
        givenSavedSession()
        playerRepository.updateFriend(players[1], "新名字", "").value()
        val newcomer = playerRepository.createFriend("新来的", "").value()
        sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 3000L),
                    SeatResultInput(1, players[1], -1000L),
                    SeatResultInput(2, newcomer, -1000L),
                    SeatResultInput(3, players[3], -1000L),
                ),
            ),
        ).value()

        val after = sessionRepository.getSessionDetail("session-1").value()
        assertEquals("上家", after.results.first { it.seat == 1 }.nameSnapshot)
        assertEquals("新来的", after.results.first { it.seat == 2 }.nameSnapshot)
    }

    @Test
    fun editRejectsTheSamePlayerInTwoSeats() = runBlocking {
        // 一场不能同时记录同一个人两次：唯一约束之外，事务先给出明确错误
        givenSavedSession()

        val error = sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 3000L),
                    SeatResultInput(1, players[1], -1000L),
                    SeatResultInput(2, players[1], -1000L),
                    SeatResultInput(3, players[3], -1000L),
                ),
            ),
        ).error()

        assertEquals(ManoteError.DUPLICATE_PLAYER, error)
        assertEquals(listOf(3000L, -1000L, -1000L, -1000L), resultsOf("session-1").map { it.netMinor })
    }

    @Test
    fun editStillRequiresSelfAtSeatZero() = runBlocking {
        // 换人可以，但座位 0 恒为本人：别人坐不到自己那一格
        givenSavedSession()

        val error = sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[1], 3000L),
                    SeatResultInput(1, players[0], -1000L),
                    SeatResultInput(2, players[2], -1000L),
                    SeatResultInput(3, players[3], -1000L),
                ),
            ),
        ).error()

        assertEquals(ManoteError.SEAT_ZERO_NOT_SELF, error)
        assertEquals(1L, sessionRepository.getSessionDetail("session-1").value().session.revision)
    }

    @Test
    fun editRejectsAddingAnArchivedFriendToANewSeat() = runBlocking {
        givenSavedSession()
        val archived = players[1]
        playerRepository.archiveFriend(archived).value()

        // 原位保留仍可保存（归档不阻止历史修正，T13）
        sessionRepository.updateSession(editOf(listOf(6000L, -2000L, -2000L, -2000L))).value()

        // 但把已归档的人塞到另一个位置要拦住：那等于绕过归档状态记新账
        val error = sessionRepository.updateSession(
            editOfSeats(
                listOf(
                    SeatResultInput(0, players[0], 6000L),
                    SeatResultInput(1, players[2], -2000L),
                    SeatResultInput(2, archived, -2000L),
                    SeatResultInput(3, players[3], -2000L),
                ),
                expectedRevision = 2L,
            ),
        ).error()

        assertEquals(ManoteError.FRIEND_ARCHIVED, error)
        assertEquals(2L, sessionRepository.getSessionDetail("session-1").value().session.revision)
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
        sessionRepository.moveSessionToTrash("session-1").value()

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
        players = givenSelfAndThreeFriends()

        val error = sessionRepository.updateSession(editOf(listOf(6000L, -2000L, -2000L, -2000L))).error()

        assertEquals(ManoteError.SESSION_NOT_FOUND, error)
    }

    @Test
    fun deleteMovesSessionToTrashAndKeepsPlayers() = runBlocking {
        // T11：确认后统计移除；结果行与 Player 都保留，随时可以还原（规格 3.3）
        val players = givenSavedSession()

        sessionRepository.moveSessionToTrash("session-1").value()

        // 在场场次清零、统计清零，但结果行还在回收站里（软删除的关键差别）
        assertEquals(0, sessionCount())
        assertEquals(1, trashCount())
        assertEquals(4, resultsOf("session-1").size)
        assertEquals(0, playerRepository.statistics(players[0]).sessionCount)
        assertEquals(0L, playerRepository.statistics(players[0]).netMinor)
        assertNotNull(playerRepository.findById(players[1]))
        // 档案并未被归档，仍可用于新建场次
        assertNull(playerRepository.findById(players[1])!!.archivedAt)

        // 还原后一切回到删除前的样子
        sessionRepository.restoreSession("session-1").value()
        assertEquals(1, sessionCount())
        assertEquals(0, trashCount())
        assertEquals(3000L, playerRepository.statistics(players[0]).netMinor)
    }

    @Test
    fun deleteUnknownSessionFails() = runBlocking {
        givenSelfAndThreeFriends()

        assertEquals(
            ManoteError.SESSION_NOT_FOUND,
            sessionRepository.moveSessionToTrash("session-1").error(),
        )
        assertEquals(0, sessionCount())
        assertEquals(0, trashCount())
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
