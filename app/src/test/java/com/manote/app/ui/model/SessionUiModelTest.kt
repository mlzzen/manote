package com.manote.app.ui.model

import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.data.local.SessionWithResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun sessionWith(vararg netsBySeat: Long): SessionWithResults {
        val playedAt = 1_700_000_000_000L
        return SessionWithResults(
            session = SessionEntity(
                id = SESSION_ID,
                title = "周一晚局",
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
    }
}
