package com.manote.app.data.repository

import androidx.room.withTransaction
import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.error
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.local.SessionEntity
import com.manote.app.data.value
import com.manote.app.domain.ManoteError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 新建整场的事务与校验（规格 2.1、2.2、2.3、5.1；验收用例 T01–T03、T08、T16、T18）。 */
class SessionCreateTest : RoomTestCase() {

    @Test
    fun createsOneSessionWithFourBalancedResults() = runBlocking {
        // T01：+30、-10、-10、-10 → 一场四结果，总和零
        val players = givenSelfAndThreeFriends()

        val sessionId = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()

        assertEquals("session-1", sessionId)
        assertEquals(1, sessionCount())

        val results = resultsOf(sessionId)
        assertEquals(4, results.size)
        assertEquals(listOf(0, 1, 2, 3), results.map { it.seat })
        assertEquals(listOf(3000L, -1000L, -1000L, -1000L), results.map { it.netMinor })
        assertEquals(0L, results.sumOf { it.netMinor })

        val detail = sessionRepository.getSessionDetail(sessionId).value()
        assertEquals(1L, detail.session.revision)
        assertEquals("周五晚局", detail.session.title)
        assertEquals(BASE_NOW - 60_000L, detail.session.playedAt)
        assertEquals(players, detail.results.map { it.playerId })
        assertEquals(listOf("我", "上家", "对家", "下家"), detail.results.map { it.nameSnapshot })
    }

    @Test
    fun rejectsUnbalancedTotalsWithoutAutoFixing() = runBlocking {
        // T02：+30、-10、-10、0 合计 +10 元，拒绝且不入库
        val players = givenSelfAndThreeFriends()

        val error = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, 0L)),
        ).error()

        assertEquals(ManoteError.SUM_NOT_ZERO, error)
        assertEquals(0, sessionCount())
        assertEquals(0, database.sessionResultDao().countBySession("session-1"))
    }

    @Test
    fun keepsCentPrecisionForSmallAmounts() = runBlocking {
        // T03：+0.30、-0.10、-0.10、-0.10 → 精确平衡，存 +30/-10/-10/-10 分
        val players = givenSelfAndThreeFriends()

        val sessionId = sessionRepository.createSession(
            draftOf(players, listOf(30L, -10L, -10L, -10L)),
        ).value()

        assertEquals(listOf(30L, -10L, -10L, -10L), resultsOf(sessionId).map { it.netMinor })
    }

    @Test
    fun acceptsAllZeroSession() = runBlocking {
        // T05：确认后计为一场持平
        val players = givenSelfAndThreeFriends()

        sessionRepository.createSession(draftOf(players, listOf(0L, 0L, 0L, 0L))).value()

        val statistics = playerRepository.statistics(players[0])
        assertEquals(1, statistics.sessionCount)
        assertEquals(1, statistics.drawCount)
        assertEquals(0L, statistics.netMinor)
    }

    @Test
    fun rejectsDuplicatePlayerInSameSession() = runBlocking {
        // T07：同一 playerId 不能占两个座位
        val players = givenSelfAndThreeFriends()
        val duplicated = listOf(players[0], players[1], players[1], players[3])

        val error = sessionRepository.createSession(
            draftOf(duplicated, listOf(3000L, -1000L, -1000L, -1000L)),
        ).error()

        assertEquals(ManoteError.DUPLICATE_PLAYER, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun rejectsIncompleteOrUnknownSeatSet() = runBlocking {
        val players = givenSelfAndThreeFriends()

        val missingSeat = SessionDraft(
            id = "session-1",
            playedAt = BASE_NOW - 1_000L,
            title = "",
            note = "",
            seats = listOf(
                SeatResultInput(0, players[0], 3000L),
                SeatResultInput(1, players[1], -1000L),
                SeatResultInput(2, players[2], -1000L),
            ),
        )
        assertEquals(ManoteError.INVALID_SEAT_SET, sessionRepository.createSession(missingSeat).error())

        val unknownSeat = SessionDraft(
            id = "session-1",
            playedAt = BASE_NOW - 1_000L,
            title = "",
            note = "",
            seats = listOf(
                SeatResultInput(0, players[0], 3000L),
                SeatResultInput(1, players[1], -1000L),
                SeatResultInput(2, players[2], -1000L),
                SeatResultInput(9, players[3], -1000L),
            ),
        )
        assertEquals(ManoteError.INVALID_SEAT_SET, sessionRepository.createSession(unknownSeat).error())
        assertEquals(0, sessionCount())
    }

    @Test
    fun seatZeroMustBeSelf() = runBlocking {
        val players = givenSelfAndThreeFriends()
        val wrongSelfAtZero = listOf(players[1], players[0], players[2], players[3])

        val error = sessionRepository.createSession(
            draftOf(wrongSelfAtZero, listOf(3000L, -1000L, -1000L, -1000L)),
        ).error()

        assertEquals(ManoteError.SEAT_ZERO_NOT_SELF, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun otherSeatsMustBeFriendsNotSelf() = runBlocking {
        val players = givenSelfAndThreeFriends()

        // 健康库里这条分支不可达（seat=0 已是唯一的 SELF，再放 SELF 会先被判重复）。
        // 这里人为写入第二个 SELF 来模拟库损坏，验证事务内的身份校验确实在守卫。
        database.playerDao().insert(
            PlayerEntity(
                id = "second-self",
                kind = PlayerKind.SELF,
                nickname = "另一个我",
                note = "",
                archivedAt = null,
                createdAt = BASE_NOW,
                updatedAt = BASE_NOW,
            ),
        )

        val error = sessionRepository.createSession(
            draftOf(
                listOf(players[0], "second-self", players[2], players[3]),
                listOf(-1000L, 1000L, 0L, 0L),
            ),
        ).error()

        assertEquals(ManoteError.NON_SELF_SEAT_NOT_FRIEND, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun rejectsUnknownPlayer() = runBlocking {
        val players = givenSelfAndThreeFriends()

        val error = sessionRepository.createSession(
            draftOf(listOf(players[0], players[1], players[2], "ghost"), listOf(3000L, -1000L, -1000L, -1000L)),
        ).error()

        assertEquals(ManoteError.PLAYER_NOT_FOUND, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun archivedFriendCannotBePickedForNewSession() = runBlocking {
        // T13：归档后新场暂不可选
        val players = givenSelfAndThreeFriends()
        playerRepository.archiveFriend(players[1]).value()

        val error = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).error()

        assertEquals(ManoteError.FRIEND_ARCHIVED, error)
        assertEquals(0, sessionCount())

        // 取消归档后可以重新选择
        playerRepository.unarchiveFriend(players[1]).value()
        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).value()
        assertEquals(1, sessionCount())
    }

    @Test
    fun allowsBackdatedButRejectsFuturePlayedAt() = runBlocking {
        // T18：过去可记，未来拒绝
        val players = givenSelfAndThreeFriends()

        sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L), playedAt = BASE_NOW - 86_400_000L),
        ).value()
        assertEquals(1, sessionCount())

        val error = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L), id = "session-2", playedAt = BASE_NOW + 1L),
        ).error()

        assertEquals(ManoteError.PLAYED_AT_IN_FUTURE, error)
        assertEquals(1, sessionCount())
    }

    @Test
    fun sameSessionIdIsNeverInsertedTwice() = runBlocking {
        // T16：连点提交只产生一场
        val players = givenSelfAndThreeFriends()
        val draft = draftOf(players, listOf(3000L, -1000L, -1000L, -1000L))

        sessionRepository.createSession(draft).value()
        val error = sessionRepository.createSession(draft).error()

        assertEquals(ManoteError.SESSION_ID_ALREADY_EXISTS, error)
        assertEquals(1, sessionCount())
        assertEquals(4, resultsOf("session-1").size)
    }

    @Test
    fun blankTitleFallsBackToDefaultAndNoteIsTrimmed() = runBlocking {
        val players = givenSelfAndThreeFriends()

        val sessionId = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L), title = "   ", note = "  三缺一  "),
        ).value()

        val detail = sessionRepository.getSessionDetail(sessionId).value()
        assertEquals("麻将对局", detail.session.title)
        assertEquals("三缺一", detail.session.note)
    }

    @Test
    fun rejectsOutOfRangeAmount() = runBlocking {
        val players = givenSelfAndThreeFriends()

        val error = sessionRepository.createSession(
            draftOf(players, listOf(1_000_001L, -1_000_001L, 0L, 0L)),
        ).error()

        assertEquals(ManoteError.AMOUNT_OUT_OF_RANGE, error)
        assertEquals(0, sessionCount())
    }

    @Test
    fun enforcesSessionLimitOfOneThousand() = runBlocking {
        val players = givenSelfAndThreeFriends()

        // 直接批量写入占满名额，避免调用 1000 次完整创建流程。
        database.withTransaction {
            repeat(1_000) { index ->
                database.sessionDao().insert(
                    SessionEntity(
                        id = "filled-$index",
                        title = "占位",
                        playedAt = BASE_NOW - 1_000L,
                        note = "",
                        createdAt = BASE_NOW,
                        updatedAt = BASE_NOW,
                        revision = 1L,
                    ),
                )
            }
        }

        val error = sessionRepository.createSession(
            draftOf(players, listOf(3000L, -1000L, -1000L, -1000L)),
        ).error()

        assertEquals(ManoteError.SESSION_LIMIT_REACHED, error)
        assertEquals(1_000, sessionCount())
    }

    @Test
    fun enforcesFriendLimitOfFiveThousandIncludingArchived() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        database.withTransaction {
            repeat(5_000) { index ->
                database.playerDao().insert(
                    PlayerEntity(
                        id = "filled-$index",
                        kind = PlayerKind.FRIEND,
                        nickname = "麻友$index",
                        note = "",
                        archivedAt = if (index % 2 == 0) BASE_NOW - 1_000L else null,
                        createdAt = BASE_NOW,
                        updatedAt = BASE_NOW,
                    ),
                )
            }
        }

        val error = playerRepository.createFriend("再来一个", "").error()

        assertEquals(ManoteError.FRIEND_LIMIT_REACHED, error)
        assertEquals(5_000, database.playerDao().countByKind(PlayerKind.FRIEND))
        assertNotNull(playerRepository.self())
    }
}
