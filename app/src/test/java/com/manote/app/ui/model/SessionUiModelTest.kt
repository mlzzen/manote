package com.manote.app.ui.model

import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.SessionRules
import com.manote.app.domain.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 场次 → 界面模型（规格 1.3、2.5）。
 *
 * 纯 JVM 测试：排名、合计与「是否给结清建议」都在这里一次算好，
 * Composable 只负责渲染，所以这些规则必须能被直接钉死。
 */
class SessionUiModelTest {

    @Test
    fun partialSeatsAreReportedWithTheirOwnTotal() {
        // 只记录了本人 +30 与上家 -0.10 元，合计 +29.90 元
        val detail = sessionWith(3000L, -1000L).toDetailUi()

        assertEquals(2, detail.seats.size)
        assertEquals(listOf(0, 1), detail.seats.map { it.seat })
        assertEquals(2000L, detail.totalMinor)
        assertFalse("合计不为 0 时应标记为不平账", detail.balanced)
    }

    @Test
    fun unbalancedSessionGetsNoSettlementSuggestion() {
        // 结清算法假定合计为零；不平账时给出建议会剩下一笔算不清的余额
        val detail = sessionWith(3000L, -1000L).toDetailUi()

        assertTrue(
            "不平账时不能给结清建议，否则会输出算错的数字",
            detail.transfers.isEmpty(),
        )
    }

    @Test
    fun balancedPartialSessionStillSuggestsSettlement() {
        val detail = sessionWith(3000L, -3000L).toDetailUi()

        assertTrue(detail.balanced)
        assertEquals(1, detail.transfers.size)
        assertEquals(3000L, detail.transfers.single().amountMinor)
        assertEquals(1, detail.transfers.single().fromSeat)
        assertEquals(0, detail.transfers.single().toSeat)
    }

    @Test
    fun ranksAreComputedFromTheRecordedSeatsOnly() {
        // 只记录三个位置：1、2、3 名，不会因为「缺一个」而报错或错位
        val detail = sessionWith(3000L, -1000L, -2000L).toDetailUi()

        assertEquals(listOf(1, 2, 3), detail.seats.map { it.rank })
        assertEquals(0L, detail.totalMinor)
        assertTrue(detail.balanced)
    }

    @Test
    fun defaultTitleIsNotTreatedAsACustomTitle() {
        // 没起过标题的场次在列表里不显示标题行，否则每行都重复一句「麻将对局」
        val placeholder = sessionWith(3000L, title = SessionRules.DEFAULT_TITLE).toRowUi()

        assertFalse(placeholder.hasCustomTitle)

        val named = sessionWith(3000L, title = "周五晚局").toRowUi()

        assertTrue(named.hasCustomTitle)
        assertEquals("周五晚局", named.title)
    }

    @Test
    fun placeholderTitleIsStillSearchable() {
        // 标题只是不显示，搜索串里仍保留，用「麻将」照旧搜得到这些场次
        val row = sessionWith(3000L, title = SessionRules.DEFAULT_TITLE).toRowUi()

        assertFalse(row.hasCustomTitle)
        assertTrue(row.searchHaystack.contains("麻将对局"))
    }

    @Test
    fun rowCarriesTheTimeSlotJudgedInTheGivenZone() {
        // 2026-10-05 21:30（上海）→ 晚场；同一时刻按 UTC 算落在当天 13:30 → 下午场
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
        )

        assertEquals(TimeSlot.EVENING, session.toRowUi(SHANGHAI).timeSlot)
        assertEquals(TimeSlot.AFTERNOON, session.toRowUi(ZoneId.of("UTC")).timeSlot)
    }

    @Test
    fun detailCarriesTheSameTimeSlotAsTheRow() {
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
        )

        assertEquals(session.toRowUi(SHANGHAI).timeSlot, session.toDetailUi(SHANGHAI).timeSlot)
        assertEquals(TimeSlot.EVENING, session.toDetailUi(SHANGHAI).timeSlot)
    }

    private fun sessionWith(
        vararg netsBySeat: Long,
        title: String = "周一晚局",
        playedAt: Long = 1_700_000_000_000L,
    ): SessionWithResults {
        return SessionWithResults(
            session = SessionEntity(
                id = SESSION_ID,
                title = title,
                playedAt = playedAt,
                note = "",
                createdAt = playedAt,
                updatedAt = playedAt,
                revision = 1L,
            ),
            results = netsBySeat.mapIndexed { seat, net ->
                SessionResultEntity(
                    sessionId = SESSION_ID,
                    seat = seat,
                    playerId = playerIdFor(seat),
                    nameSnapshot = "牌友$seat",
                    netMinor = net,
                )
            },
        )
    }

    private fun playerIdFor(seat: Int): String =
        "00000000-0000-4000-8000-" + (seat + 1).toString().padStart(12, '0')

    private companion object {
        const val SESSION_ID = "00000000-0000-4000-8000-000000000009"
        val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
