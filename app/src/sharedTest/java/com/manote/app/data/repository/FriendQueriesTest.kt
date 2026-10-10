package com.manote.app.data.repository

import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.value
import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 只读查询（规格 3.3、3.4）。
 *
 * 覆盖牌友列表的聚合（场数 / 净输赢）、麻友逐场记录、档案订阅，
 * 并验证这些派生值在增删改场次后会跟着刷新——它们是同一份 `session_result` 的投影，
 * 不允许出现「列表写着 +30，详情里算出来是 +60」这类对不上的情况。
 */
class FriendQueriesTest : RoomTestCase() {

    /** 建本人 + 四位麻友，返回 [自己, 麻友1, 麻友2, 麻友3, 麻友4] 的 ID。 */
    private suspend fun givenSelfAndFourFriends(): List<String> {
        val friends = (1..4).map { index ->
            nowMillis += 1_000L
            playerRepository.createFriend("麻友$index", "").value()
        }
        return listOf(playerRepository.ensureSelfPlayer().value().id) + friends
    }

    @Test
    fun aggregatesReportSessionCountAndNetPerPlayer() = runBlocking {
        val players = givenSelfAndFourFriends()
        val (self, friend1, friend2, friend3, friend4) = players

        // 第一场：自己 +30、麻友1 -10、麻友2 -10、麻友3 -10
        sessionRepository.createSession(
            draftOf(listOf(self, friend1, friend2, friend3), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()
        // 第二场：麻友1 换到下家赢 50，麻友4 顶上对家持平
        sessionRepository.createSession(
            draftOf(
                listOf(self, friend2, friend4, friend1),
                listOf(-5000L, 0L, 0L, 5000L),
                id = "s-2",
                playedAt = BASE_NOW - 120_000L,
            ),
        ).value()

        val aggregates = playerRepository.observeAggregates().first().associateBy { it.playerId }

        assertEquals(2, aggregates.getValue(friend1).sessionCount)
        assertEquals(4000L, aggregates.getValue(friend1).netMinor) // -1000 + 5000
        assertEquals(2, aggregates.getValue(friend2).sessionCount)
        assertEquals(-1000L, aggregates.getValue(friend2).netMinor)
        assertEquals(2, aggregates.getValue(self).sessionCount)
        assertEquals(-2000L, aggregates.getValue(self).netMinor) // +3000 - 5000

        // 只参加一场的麻友也在聚合里；净额为 0 的场次照实记 0，不因此被丢掉
        assertEquals(1, aggregates.getValue(friend3).sessionCount)
        assertEquals(-1000L, aggregates.getValue(friend3).netMinor)
        assertEquals(1, aggregates.getValue(friend4).sessionCount)
        assertEquals(0L, aggregates.getValue(friend4).netMinor)

        // 聚合口径与按 ID 单独统计必须完全一致，否则列表和详情会自相矛盾
        assertEquals(playerRepository.statistics(friend1).netMinor, aggregates.getValue(friend1).netMinor)
    }

    @Test
    fun aggregatesOmitPlayersWhoNeverPlayed() = runBlocking {
        val players = givenSelfAndFourFriends()
        val (self, friend1) = players

        sessionRepository.createSession(
            draftOf(players.take(4), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()

        val aggregates = playerRepository.observeAggregates().first().associateBy { it.playerId }

        // 没打过牌的麻友没有聚合行——列表侧负责补 0，而不是在这里造假数据
        assertNull(aggregates[players[4]])
        assertTrue(aggregates.containsKey(self))
        assertTrue(aggregates.containsKey(friend1))
    }

    @Test
    fun observeAllFriendsExcludesSelfAndIncludesArchivedOrderedByUpdatedAt() = runBlocking {
        val players = givenSelfAndFourFriends()
        val self = players[0]

        val all = playerRepository.observeAllFriends().first()
        assertEquals(4, all.size)
        assertTrue("本人不应出现在牌友列表", all.none { it.id == self })
        assertTrue(all.all { it.kind == PlayerKind.FRIEND })
        assertTrue("都还没归档", all.all { it.archivedAt == null })

        // 归档一位麻友，它必须仍在列表里（由界面按开关过滤），且 updatedAt 变为最新、排到最前
        nowMillis += 10_000L
        playerRepository.archiveFriend(players[2]).value()

        val afterArchive = playerRepository.observeAllFriends().first()
        assertEquals(4, afterArchive.size)
        assertEquals(players[2], afterArchive.first().id)
        assertTrue(afterArchive.first().archivedAt != null)

        // 直接断言「updatedAt 倒序」这条不变量，避免依赖创建顺序的巧合
        val order = afterArchive.map { it.updatedAt }
        assertEquals("updatedAt 必须倒序", order.sortedDescending(), order)
    }

    @Test
    fun observeByIdEmitsProfileAndNullForUnknown() = runBlocking {
        val players = givenSelfAndFourFriends()
        val friend = players[1]

        val loaded = playerRepository.observeById(friend).first()
        assertEquals(friend, loaded?.id)
        assertEquals("麻友1", loaded?.nickname)

        assertNull(playerRepository.observeById("no-such-id").first())
    }

    @Test
    fun observeByIdReflectsRenameImmediately() = runBlocking {
        val players = givenSelfAndFourFriends()
        val friend = players[1]

        playerRepository.updateFriend(friend, "新昵称", "常来").value()

        val loaded = playerRepository.observeById(friend).first()
        assertEquals("新昵称", loaded?.nickname)
        assertEquals("常来", loaded?.note)
    }

    @Test
    fun observeByPlayerWithResultsReturnsOnlyTheirSessionsWithAllFourResults() = runBlocking {
        val players = givenSelfAndFourFriends()
        val (self, friend1, friend2, friend3, friend4) = players

        sessionRepository.createSession(
            draftOf(listOf(self, friend1, friend2, friend3), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()
        sessionRepository.createSession(
            draftOf(
                listOf(self, friend2, friend3, friend4),
                listOf(6000L, -2000L, -2000L, -2000L),
                id = "s-2",
                playedAt = BASE_NOW - 120_000L,
            ),
        ).value()

        // 麻友1 只参加了 s-1，不应看到 s-2
        val onlyFirst = sessionRepository.observeSessionsByPlayer(friend1).first()
        assertEquals(listOf("s-1"), onlyFirst.map { it.session.id })

        // 每场都要带齐四条结果，界面才能同时显示「麻友本场」与「本人本场」
        val first = onlyFirst.single()
        assertEquals(4, first.results.size)
        assertEquals(
            "四个座位各一条，且本人恒为 seat 0",
            listOf(0, 1, 2, 3),
            first.results.map { it.seat }.sorted(),
        )
        // 与按 ID 取详情得到的是同一份结果（只比内容，不比顺序：@Relation 不保证结果行顺序）
        assertEquals(
            sessionRepository.getSessionDetail("s-1").value().results.sortedBy { it.seat },
            first.results.sortedBy { it.seat },
        )

        // 麻友2 两场都在，按 playedAt 倒序：s-1 比 s-2 晚
        val both = sessionRepository.observeSessionsByPlayer(friend2).first()
        assertEquals(listOf("s-1", "s-2"), both.map { it.session.id })
    }

    @Test
    fun observeByPlayerWithResultsIsEmptyForPlayersWithoutSessions() = runBlocking {
        val players = givenSelfAndFourFriends()

        sessionRepository.createSession(
            draftOf(players.take(4), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()

        assertEquals(emptyList<String>(), sessionRepository.observeSessionsByPlayer(players[4]).first().map { it.session.id })
        assertEquals(emptyList<String>(), sessionRepository.observeSessionsByPlayer("no-such-id").first().map { it.session.id })
    }

    @Test
    fun deletingSessionRemovesItFromHistoryAndAggregates() = runBlocking {
        val players = givenSelfAndFourFriends()
        val (self, friend1, friend2, friend3) = players

        sessionRepository.createSession(
            draftOf(listOf(self, friend1, friend2, friend3), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()
        assertEquals("本人本场 +30", 3000L, aggregateOf(self)!!.netMinor)
        assertEquals("麻友1 本场 -10", -1000L, aggregateOf(friend1)!!.netMinor)

        sessionRepository.deleteSession("s-1").value()

        assertNull("删完后该麻友不应再有聚合行", aggregateOf(friend1))
        assertEquals(emptyList<String>(), sessionRepository.observeSessionsByPlayer(friend1).first().map { it.session.id })
        assertEquals(0, playerRepository.statistics(friend1).sessionCount)
        // 唯一一场被删后，聚合整体为空（不残留任何玩家行）
        assertEquals(emptyList<PlayerAggregate>(), playerRepository.observeAggregates().first())
    }

    @Test
    fun editingSessionReplacesAggregateInsteadOfDoubleCounting() = runBlocking {
        val players = givenSelfAndFourFriends()
        val (self, friend1, friend2, friend3) = players

        sessionRepository.createSession(
            draftOf(listOf(self, friend1, friend2, friend3), listOf(3000L, -1000L, -1000L, -1000L), id = "s-1"),
        ).value()

        sessionRepository.updateSession(
            SessionEdit(
                id = "s-1",
                expectedRevision = 1L,
                playedAt = BASE_NOW - 60_000L,
                title = "改过的标题",
                note = "",
                netMinorBySeat = mapOf(0 to 6000L, 1 to -2000L, 2 to -2000L, 3 to -2000L),
            ),
        ).value()

        val aggregate = aggregateOf(friend1)
        assertEquals(1, aggregate!!.sessionCount)
        assertEquals(-2000L, aggregate.netMinor)
        assertEquals(6000L, aggregateOf(self)!!.netMinor)
    }

    /** 取某位玩家的聚合行；从未参与过任何场次时返回 null。 */
    private suspend fun aggregateOf(playerId: String) =
        playerRepository.observeAggregates().first().firstOrNull { it.playerId == playerId }
}
