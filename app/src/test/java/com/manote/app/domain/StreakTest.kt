package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 连输 / 连赢与单日极值（首页「连输 / 连赢」）。
 *
 * 时区一律显式传入：本机在东八区、CI 在 UTC，
 * 「哪一天」只要隐式依赖跑测机器的时区，就会出现本地全绿、CI 必挂。
 */
class StreakTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun emptyHistoryYieldsEmptyStats() {
        assertEquals(StreakStats.EMPTY, Streaks.of(emptyList(), zone))
    }

    @Test
    fun currentStreakCountsBackwardsFromTheLastSession() {
        // 先赢两场、再连输三场：当前连输 3，当前连胜 0
        val stats = Streaks.of(
            listOf(
                DatedNet(at(1, 20), 100L),
                DatedNet(at(2, 20), 200L),
                DatedNet(at(3, 20), -100L),
                DatedNet(at(4, 20), -200L),
                DatedNet(at(5, 20), -300L),
            ),
            zone,
        )

        assertEquals(0, stats.currentWinStreak)
        assertEquals(3, stats.currentLossStreak)
    }

    @Test
    fun drawBreaksBothCurrentStreaks() {
        // 最后一场持平：既不是连胜也不是连输，两条都归零
        val stats = Streaks.of(
            listOf(
                DatedNet(at(1, 20), 100L),
                DatedNet(at(2, 20), -100L),
                DatedNet(at(3, 20), 0L),
            ),
            zone,
        )

        assertEquals(0, stats.currentWinStreak)
        assertEquals(0, stats.currentLossStreak)
    }

    @Test
    fun longestStreaksKeepHistoricalRecords() {
        val stats = Streaks.of(
            listOf(
                DatedNet(at(1, 20), 100L),
                DatedNet(at(2, 20), 100L),
                DatedNet(at(3, 20), 100L),
                DatedNet(at(4, 20), -100L),
                DatedNet(at(5, 20), -100L),
                DatedNet(at(6, 20), 100L),
            ),
            zone,
        )

        assertEquals("最长连胜是前三场", 3, stats.longestWinStreak)
        assertEquals("最长连输是中间两场", 2, stats.longestLossStreak)
        assertEquals("当前只有最后一场是赢的", 1, stats.currentWinStreak)
        assertEquals(0, stats.currentLossStreak)
    }

    @Test
    fun bestAndWorstDaysSumAllSessionsOfThatDay() {
        // 10.01 两场共 +500；10.02 一场 -800；10.03 两场共 +1_200
        val stats = Streaks.of(
            listOf(
                DatedNet(at(1, 14), 300L),
                DatedNet(at(1, 21), 200L),
                DatedNet(at(2, 21), -800L),
                DatedNet(at(3, 14), 700L),
                DatedNet(at(3, 21), 500L),
            ),
            zone,
        )

        val best = requireNotNull(stats.bestDay)
        assertEquals(1_200L, best.netMinor)
        assertEquals(2, best.sessionCount)
        assertEquals(3, best.day.dayOfMonth)

        val worst = requireNotNull(stats.worstDay)
        assertEquals(-800L, worst.netMinor)
        assertEquals(1, worst.sessionCount)
        assertEquals(2, worst.day.dayOfMonth)
    }

    @Test
    fun daysAreSplitByTheGivenZoneNotByUtc() {
        // 上海时间 10.02 00:30 在 UTC 还是 10.01 16:30：
        // 按传入时区分组应当是 10.02，若按 UTC 分组会落到 10.01。
        val stats = Streaks.of(listOf(DatedNet(at(2, 0, 30), 100L)), zone)

        assertEquals(2, requireNotNull(stats.bestDay).day.dayOfMonth)
    }

    @Test
    fun singleSessionIsBothBestAndWorstDay() {
        val stats = Streaks.of(listOf(DatedNet(at(1, 20), -300L)), zone)

        // 只有一天时最佳与最差是同一天，这是事实，不该硬凑出一个「无」来
        assertEquals(-300L, requireNotNull(stats.bestDay).netMinor)
        assertEquals(-300L, requireNotNull(stats.worstDay).netMinor)
        assertEquals(true, stats.hasSessions)
    }
}
