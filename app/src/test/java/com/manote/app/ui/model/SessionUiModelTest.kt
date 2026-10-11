package com.manote.app.ui.model

import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.SessionDuration
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
        // 结束于 2026-10-05 21:30（上海）、打了 2 小时 → 区间 19:30–21:30、中点 20:30 → 晚场；
        // 同一时刻按 UTC 算，中点落在当天 12:30 → 下午场。
        // 时段判定必须与显示用同一个时区，否则会出现「显示 19:30、却算成早场」。
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
            durationMinutes = 120,
        )

        assertEquals(TimeSlot.EVENING, session.toRowUi(SHANGHAI).timeSlot)
        assertEquals(TimeSlot.AFTERNOON, session.toRowUi(ZoneId.of("UTC")).timeSlot)
    }

    @Test
    fun timeSlotIsJudgedByTheMidpointNotTheEndTime() {
        // 这是本次要修的那个错：14:00 打到 18:30 本来是下午场，
        // 只看结束时间会被算成晚场，而中点 16:15 明确落在下午。
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 18, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
            durationMinutes = 270,
        )

        assertEquals(TimeSlot.EVENING, TimeSlot.of(session.session.playedAt, SHANGHAI))
        assertEquals(TimeSlot.AFTERNOON, session.toRowUi(SHANGHAI).timeSlot)
    }

    @Test
    fun timeSlotIsJudgedByTheMidpointNotTheStartTime() {
        // 17:30 打到 21:30：开始时间落下午场、结束时间落晚场，中点 19:30 说明主要发生在晚上。
        // 这是「按开始时间判定」会判错的场景。
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
            durationMinutes = 240,
        )

        val startedAt = SessionDuration.startAt(session.session.playedAt, 240)
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(startedAt, SHANGHAI))
        assertEquals(TimeSlot.EVENING, session.toRowUi(SHANGHAI).timeSlot)
    }

    @Test
    fun defaultDurationIsUsedWhenNothingWasRecorded() {
        // 21:30 收场、没填时长 → 按默认 4 小时，区间 17:30–21:30、中点 19:30 → 晚场。
        // 界面上的区间提示与这里同源，用户看着不对可以直接改时长。
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
        )

        assertEquals(SessionDuration.DEFAULT_MINUTES, session.session.durationMinutes)
        assertEquals(TimeSlot.EVENING, session.toRowUi(SHANGHAI).timeSlot)
    }

    @Test
    fun ordinaryEveningSessionStaysInTheEveningSlot() {
        // 20:00 打到次日 0:00（中点 22:00）：改动没有把最普通的一场弄歪
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 6, 0, 0).atZone(SHANGHAI).toInstant().toEpochMilli(),
        )

        assertEquals(TimeSlot.EVENING, session.toRowUi(SHANGHAI).timeSlot)
    }

    @Test
    fun morningSessionEndingAtNoonStaysInTheMorningSlot() {
        // 09:00 打到 13:00（中点 11:00）：按结束时间会落到下午场，中点把它留在了早场
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 13, 0).atZone(SHANGHAI).toInstant().toEpochMilli(),
        )

        assertEquals(TimeSlot.MORNING, session.toRowUi(SHANGHAI).timeSlot)
    }

    @Test
    fun detailCarriesTheSameTimeSlotAsTheRow() {
        val session = sessionWith(
            3000L,
            playedAt = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(SHANGHAI).toInstant().toEpochMilli(),
            durationMinutes = 120,
        )

        assertEquals(session.toRowUi(SHANGHAI).timeSlot, session.toDetailUi(SHANGHAI).timeSlot)
        assertEquals(TimeSlot.EVENING, session.toDetailUi(SHANGHAI).timeSlot)
    }

    @Test
    fun detailCarriesStartTimeAndDurationForDisplay() {
        val end = LocalDateTime.of(2026, 10, 5, 18, 30).atZone(SHANGHAI).toInstant().toEpochMilli()

        val detail = sessionWith(3000L, playedAt = end, durationMinutes = 270).toDetailUi()

        assertEquals(end, detail.playedAt)
        assertEquals(end - 270 * 60_000L, detail.startedAt)
        assertEquals(270, detail.durationMinutes)
    }

    private fun sessionWith(
        vararg netsBySeat: Long,
        title: String = "周一晚局",
        playedAt: Long = 1_700_000_000_000L,
        durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
    ): SessionWithResults {
        return SessionWithResults(
            session = SessionEntity(
                id = SESSION_ID,
                title = title,
                playedAt = playedAt,
                durationMinutes = durationMinutes,
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
