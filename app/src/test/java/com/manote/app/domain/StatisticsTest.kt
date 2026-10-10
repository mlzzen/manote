package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 跨场统计（规格 2.4、验收用例 T09）。 */
class StatisticsTest {

    @Test
    fun countsWinsAndLossesSeparately() {
        // T09：某麻友两场分别赢 30 元、输 10 元 → 赢钱 30、输钱 10、净赢 20、共 2 场
        val statistics = PlayerStatistics.of(listOf(3000L, -1000L))

        assertEquals(2, statistics.sessionCount)
        assertEquals(1, statistics.winCount)
        assertEquals(1, statistics.lossCount)
        assertEquals(0, statistics.drawCount)
        assertEquals(3000L, statistics.winMinor)
        assertEquals(1000L, statistics.lossMinor)
        assertEquals(2000L, statistics.netMinor)
    }

    @Test
    fun treatZeroAsDrawAndKeepItOutOfBothTotals() {
        val statistics = PlayerStatistics.of(listOf(0L, 0L))

        assertEquals(2, statistics.sessionCount)
        assertEquals(0, statistics.winCount)
        assertEquals(0, statistics.lossCount)
        assertEquals(2, statistics.drawCount)
        assertEquals(0L, statistics.winMinor)
        assertEquals(0L, statistics.lossMinor)
        assertEquals(0L, statistics.netMinor)
    }

    @Test
    fun lossMinorIsAlwaysNonNegative() {
        val statistics = PlayerStatistics.of(listOf(-1000L, -2500L))

        assertEquals(0, statistics.winCount)
        assertEquals(2, statistics.lossCount)
        assertEquals(0L, statistics.winMinor)
        assertEquals(3500L, statistics.lossMinor)
        assertEquals(-3500L, statistics.netMinor)
    }

    @Test
    fun countsAlwaysSumToSessionCount() {
        val cases = listOf(
            emptyList(),
            listOf(0L),
            listOf(100L, -100L, 0L),
            listOf(1L, 2L, 3L, -6L, 0L, 0L),
        )
        for (nets in cases) {
            val statistics = PlayerStatistics.of(nets)
            assertEquals(
                "场数必须等于赢+输+持平：$nets",
                statistics.sessionCount,
                statistics.winCount + statistics.lossCount + statistics.drawCount,
            )
        }
    }

    @Test
    fun emptyHistoryYieldsZeros() {
        assertEquals(PlayerStatistics.EMPTY, PlayerStatistics.of(emptyList()))
    }
}
