package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 累计净额走势（首页「累计净额曲线」与麻友详情的「净额走势」）。
 *
 * 这条曲线是纯粹的累加：任何一场被改动，重算一遍即可，不存在第二份累计值。
 */
class TrendTest {

    @Test
    fun emptyHistoryProducesNoPoints() {
        assertEquals(emptyList<TrendPoint>(), Trend.cumulative(emptyList()))
    }

    @Test
    fun accumulatesInChronologicalOrder() {
        val points = Trend.cumulative(
            listOf(
                DatedNet(playedAt = 1_000L, netMinor = 3_000L),
                DatedNet(playedAt = 2_000L, netMinor = -1_000L),
                DatedNet(playedAt = 3_000L, netMinor = -5_000L),
            ),
        )

        assertEquals(3, points.size)
        assertEquals(3_000L, points[0].cumulativeMinor)
        assertEquals(2_000L, points[1].cumulativeMinor)
        assertEquals(-3_000L, points[2].cumulativeMinor)
    }

    @Test
    fun sortsByTimeSoUnsortedInputStillReadsLeftToRight() {
        // 输入顺序与时间顺序不一致时，必须按时间排好再累加，
        // 否则曲线的形状会依赖调用方传参的顺序。
        val points = Trend.cumulative(
            listOf(
                DatedNet(playedAt = 3_000L, netMinor = -1_000L),
                DatedNet(playedAt = 1_000L, netMinor = 1_000L),
                DatedNet(playedAt = 2_000L, netMinor = 1_000L),
            ),
        )

        assertEquals(listOf(1_000L, 2_000L, 3_000L), points.map { it.playedAt })
        assertEquals(listOf(1_000L, 2_000L, 1_000L), points.map { it.cumulativeMinor })
    }

    @Test
    fun keepsPerSessionAmountAlongsideCumulative() {
        val points = Trend.cumulative(
            listOf(
                DatedNet(playedAt = 1_000L, netMinor = -500L),
                DatedNet(playedAt = 2_000L, netMinor = 800L),
            ),
        )

        assertEquals(-500L, points[0].netMinor)
        assertEquals(800L, points[1].netMinor)
        assertEquals("累计值必须含本场", 300L, points[1].cumulativeMinor)
    }

    @Test
    fun drawsAtMostOnePointPerSession() {
        // 单场也要有一个点：界面据此判断「不足一场画不出走势」，
        // 这里不能因为「一个点连不成线」就把数据吞掉。
        val points = Trend.cumulative(listOf(DatedNet(playedAt = 1_000L, netMinor = 100L)))

        assertEquals(1, points.size)
        assertEquals(100L, points.single().cumulativeMinor)
    }
}
