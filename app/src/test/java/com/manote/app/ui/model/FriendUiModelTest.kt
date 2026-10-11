package com.manote.app.ui.model

import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.PlayerStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 牌友列表行与麻友详情的映射（规格 3.4）。
 *
 * 重点是两类边界：
 * 1. 刚建好、还没打过牌的麻友必须照样出现在列表里（聚合缺失按 0 处理，而不是整行丢掉）；
 * 2. 某场里查不到这位玩家的结果时要跳过该场，而不是用 0 顶替——那会凭空造出一条他没参加过的记录。
 */
class FriendUiModelTest {

    private fun player(
        id: String,
        nickname: String,
        note: String = "",
        archivedAt: Long? = null,
        updatedAt: Long = 1_000L,
    ) = PlayerEntity(
        id = id,
        kind = PlayerKind.FRIEND,
        nickname = nickname,
        note = note,
        archivedAt = archivedAt,
        createdAt = 500L,
        updatedAt = updatedAt,
    )

    private fun session(
        id: String,
        title: String,
        playedAt: Long,
        results: List<SessionResultEntity>,
    ) = SessionWithResults(
        session = SessionEntity(
            id = id,
            title = title,
            playedAt = playedAt,
            note = "",
            createdAt = playedAt,
            updatedAt = playedAt,
            revision = 1L,
        ),
        results = results,
    )

    private fun result(
        sessionId: String,
        seat: Int,
        playerId: String,
        nameSnapshot: String,
        netMinor: Long,
    ) = SessionResultEntity(
        sessionId = sessionId,
        seat = seat,
        playerId = playerId,
        nameSnapshot = nameSnapshot,
        netMinor = netMinor,
    )

    // ————————————————————— 列表行 —————————————————————

    @Test
    fun rowUiFallsBackToZeroWhenAggregateIsMissing() {
        val row = player("f1", "阿明").toRowUi(aggregate = null)

        assertEquals("f1", row.id)
        assertEquals("阿明", row.nickname)
        assertEquals(0, row.sessionCount)
        assertEquals(0L, row.netMinor)
        assertEquals(false, row.archived)
    }

    @Test
    fun rowUiUsesAggregateWhenPresent() {
        val row = player("f1", "阿明").toRowUi(
            PlayerAggregate(playerId = "f1", sessionCount = 7, netMinor = -12_300L),
        )

        assertEquals(7, row.sessionCount)
        assertEquals(-12_300L, row.netMinor)
    }

    @Test
    fun rowUiCarriesNoteArchivedFlagAndUpdatedAt() {
        val row = player("f9", "老陈", note = "不常来", archivedAt = 900L, updatedAt = 950L)
            .toRowUi(aggregate = null)

        assertEquals("不常来", row.note)
        assertEquals(true, row.archived)
        assertEquals("排序键必须原样带出，列表才能稳定排序", 950L, row.updatedAt)
    }

    @Test
    fun rowAverageIsTheNetDividedBySessionCount() {
        val won = player("f1", "阿明").toRowUi(
            PlayerAggregate(playerId = "f1", sessionCount = 3, netMinor = 4_500L),
        )
        val lost = player("f2", "阿华").toRowUi(
            PlayerAggregate(playerId = "f2", sessionCount = 3, netMinor = -5_000L),
        )

        assertEquals(1_500L, won.averageMinor)
        // -5000 ÷ 3 = -1666.67 → -1667，与其它「场均」用同一套四舍五入
        assertEquals(-1667L, lost.averageMinor)
    }

    @Test
    fun rowAverageIsZeroWithoutSessions() {
        // 一场都没打过：场均无从谈起，这里返回 0，
        // 由 FriendFiltering 在按数值排序时把他排到最后，而不是当成「场均 0 元」
        assertEquals(0L, player("f1", "阿明").toRowUi(aggregate = null).averageMinor)
    }

    // ————————————————————— 麻友详情 —————————————————————

    @Test
    fun detailUiPicksFriendSeatAndSelfSeatFromEachSession() {
        val friend = player("f2", "阿华")
        val sessions = listOf(
            session(
                id = "s-2",
                title = "周日夜战",
                playedAt = 2_000L,
                results = listOf(
                    result("s-2", 0, "self", "我", -800L),
                    result("s-2", 1, "f1", "阿明", 300L),
                    result("s-2", 2, "f2", "阿华", 500L),
                    result("s-2", 3, "f3", "老陈", 0L),
                ),
            ),
            session(
                id = "s-1",
                title = "周五晚局",
                playedAt = 1_000L,
                results = listOf(
                    result("s-1", 0, "self", "我", 2_000L),
                    result("s-1", 1, "f2", "阿华", -2_000L),
                    result("s-1", 2, "f1", "阿明", 0L),
                    result("s-1", 3, "f3", "老陈", 0L),
                ),
            ),
        )

        val detail = friend.toDetailUi(
            statistics = PlayerStatistics.of(listOf(500L, -2_000L)),
            sessionsWithResults = sessions,
        )

        assertEquals("f2", detail.id)
        assertEquals(false, detail.archived)
        assertEquals(2, detail.statistics.sessionCount)
        assertEquals(-1_500L, detail.statistics.netMinor)

        // 顺序与传入一致（数据库已按 playedAt 倒序）；每行取到自己在场上的位置与本人结果
        assertEquals(listOf("s-2", "s-1"), detail.sessions.map { it.sessionId })
        assertEquals(listOf(2, 1), detail.sessions.map { it.seat })
        assertEquals(listOf(500L, -2_000L), detail.sessions.map { it.netMinor })
        assertEquals(listOf(-800L, 2_000L), detail.sessions.map { it.selfNetMinor })
        assertEquals("周日夜战", detail.sessions.first().title)

        // 同场时我的场均：两场我的净额分别是 -800 与 +2_000，合计 +1_200 → 场均 +600
        assertEquals(2, detail.commonSessionCount)
        assertEquals(600L, detail.myAverageTogetherMinor)
    }

    @Test
    fun detailUiSkipsSessionsWhereThePlayerHasNoResult() {
        // 异常/脏数据下若某场没有这位玩家的结果，宁可少显示一场，也不能用 0 造出一条假记录
        val friend = player("f2", "阿华")
        val sessions = listOf(
            session(
                id = "s-1",
                title = "他缺席的一场",
                playedAt = 1_000L,
                results = listOf(
                    result("s-1", 0, "self", "我", 1_000L),
                    result("s-1", 1, "f1", "阿明", -1_000L),
                ),
            ),
            session(
                id = "s-2",
                title = "他参加的一场",
                playedAt = 2_000L,
                results = listOf(
                    result("s-2", 0, "self", "我", -500L),
                    result("s-2", 1, "f2", "阿华", 500L),
                ),
            ),
        )

        val detail = friend.toDetailUi(PlayerStatistics.EMPTY, sessions)

        assertEquals(listOf("s-2"), detail.sessions.map { it.sessionId })
        // 同场账只算他真的在场的那些场：他缺席的那一场我的 +1_000 不计入
        assertEquals(1, detail.commonSessionCount)
        assertEquals(-500L, detail.myAverageTogetherMinor)
    }

    @Test
    fun detailUiHandlesEmptyHistory() {
        val detail = player("f2", "阿华").toDetailUi(PlayerStatistics.EMPTY, emptyList())

        assertEquals(emptyList<FriendSessionRowUi>(), detail.sessions)
        assertEquals(0, detail.statistics.sessionCount)
        assertEquals(0L, detail.statistics.netMinor)
        // 一场都没打过：没有同场账可言，界面据此显示占位符而不是 ¥0.00
        assertNull(detail.myAverageTogetherMinor)
        assertEquals(0, detail.commonSessionCount)
    }

    @Test
    fun detailUiCarriesArchivedFlag() {
        val detail = player("f2", "阿华", archivedAt = 5_000L)
            .toDetailUi(PlayerStatistics.EMPTY, emptyList())

        assertEquals(true, detail.archived)
    }
}
