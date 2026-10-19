package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 月历的逐日汇总与月度合计。
 *
 * 时区一律显式传入：本机在东八区、CI 在 UTC，
 * 「哪一天 / 哪一月」只要隐式依赖跑测机器的时区，就会出现本地全绿、CI 必挂。
 */
class MonthSummaryTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(month: Int, day: Int, hour: Int = 20): Long =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun emptyInputYieldsEmptyMonth() {
        val summary = MonthCalendar.of(emptyList(), YearMonth.of(2026, 10), zone)

        assertTrue(summary.days.isEmpty())
        assertEquals(0, summary.sessionCount)
        assertEquals(0L, summary.netMinor)
        assertEquals(0, summary.playedDayCount)
        assertNull(summary.bestDay)
        assertNull(summary.worstDay)
        assertTrue(!summary.hasSessions)
    }

    @Test
    fun onlyTheRequestedMonthIsCounted() {
        val summary = MonthCalendar.of(
            listOf(
                DatedNet(at(9, 30), 500L),
                DatedNet(at(10, 1), 100L),
                DatedNet(at(10, 31), -300L),
                DatedNet(at(11, 1), 900L),
            ),
            YearMonth.of(2026, 10),
            zone,
        )

        // 9 月 30 日与 11 月 1 日都在边界之外：月份按设备时区切，不按 UTC
        assertEquals(2, summary.sessionCount)
        assertEquals(-200L, summary.netMinor)
        assertEquals(2, summary.playedDayCount)
        assertEquals(setOf(1, 31), summary.days.keys.map { it.dayOfMonth }.toSet())
    }

    @Test
    fun sameDaySessionsAreSummedIntoOneCell() {
        val summary = MonthCalendar.of(
            listOf(
                DatedNet(at(10, 5, 10), 300L),
                DatedNet(at(10, 5, 22), -100L),
                DatedNet(at(10, 6, 20), 400L),
            ),
            YearMonth.of(2026, 10),
            zone,
        )

        val fifth = summary.dayOf(YearMonth.of(2026, 10).atDay(5))
        assertEquals(200L, fifth?.netMinor)
        // 一天的场数也要算对：月历格子上写的是这一天的合计，不是最后一场
        assertEquals(2, fifth?.sessionCount)
        assertEquals(2, summary.playedDayCount)
        assertEquals(3, summary.sessionCount)
        assertEquals(600L, summary.netMinor)
    }

    @Test
    fun daysAreInChronologicalOrderAndExtremesPickEarliestOnTie() {
        val summary = MonthCalendar.of(
            listOf(
                DatedNet(at(10, 20), 500L),
                DatedNet(at(10, 3), 500L),
                DatedNet(at(10, 9), -800L),
            ),
            YearMonth.of(2026, 10),
            zone,
        )

        assertEquals(
            listOf(3, 9, 20),
            summary.days.keys.map { it.dayOfMonth },
        )
        // 同额时命中最早的一天，与 Streaks / SessionExtremes 的取舍一致
        assertEquals(3, summary.bestDay?.day?.dayOfMonth)
        assertEquals(9, summary.worstDay?.day?.dayOfMonth)
    }

    @Test
    fun allDrawMonthStillReportsZeroNetAndFiftyPercentDraws() {
        val summary = MonthCalendar.of(
            listOf(DatedNet(at(10, 7), 0L), DatedNet(at(10, 8), 0L)),
            YearMonth.of(2026, 10),
            zone,
        )

        assertEquals(0L, summary.netMinor)
        // 持平算在分母里，不算赢：胜率 0%，不是 100%
        assertEquals(0, summary.winRatePercent)
        assertEquals(2, summary.statistics.drawCount)
        // 打平的两天仍是「有记录」：格子要铺持平色，而不是留空
        assertTrue(summary.hasSessions)
        assertEquals(2, summary.playedDayCount)
    }

    @Test
    fun winRateRoundsToNearestPercent() {
        // 三场赢两场 → 66.67%，四舍五入到 67
        val summary = MonthCalendar.of(
            listOf(
                DatedNet(at(10, 1), 100L),
                DatedNet(at(10, 2), 100L),
                DatedNet(at(10, 3), -100L),
            ),
            YearMonth.of(2026, 10),
            zone,
        )

        assertEquals(67, summary.winRatePercent)
        // 场均同样四舍五入：(100 + 100 - 100) / 3 = 33.33 → 33
        assertEquals(33L, summary.statistics.averageNetMinor)
    }
}
