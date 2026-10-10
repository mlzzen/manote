package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 本场排名（规格 2.5、验收用例 T15）。 */
class RankingTest {

    @Test
    fun allZeroIsFourWayTieAtFirstPlace() {
        // T15 前半：全零排序为 1、1、1、1
        val ranks = Ranking.rank(
            listOf(
                SeatNet(0, 0L),
                SeatNet(1, 0L),
                SeatNet(2, 0L),
                SeatNet(3, 0L),
            ),
        )

        assertEquals(listOf(1, 1, 1, 1), ranks.map { it.rank })
        assertEquals(listOf(0, 1, 2, 3), ranks.map { it.seat })
    }

    @Test
    fun competitionRankingSkipsRanksAfterTies() {
        // T15 后半：+10、+10、0、-20 排序为 1、1、3、4
        val ranks = Ranking.rank(
            listOf(
                SeatNet(0, 1000L),
                SeatNet(1, 1000L),
                SeatNet(2, 0L),
                SeatNet(3, -2000L),
            ),
        )

        assertEquals(listOf(1, 1, 3, 4), ranks.map { it.rank })
    }

    @Test
    fun ordersByAmountThenSeat() {
        val ranks = Ranking.rank(
            listOf(
                SeatNet(3, -2000L),
                SeatNet(2, 0L),
                SeatNet(1, 1000L),
                SeatNet(0, 1000L),
            ),
        )

        // 同额按 seat 升序展示
        assertEquals(listOf(0, 1, 2, 3), ranks.map { it.seat })
        assertEquals(listOf(1, 1, 3, 4), ranks.map { it.rank })
    }

    @Test
    fun assignsDistinctRanksWhenNoTies() {
        val ranks = Ranking.rank(
            listOf(
                SeatNet(0, 5000L),
                SeatNet(1, 1000L),
                SeatNet(2, -1000L),
                SeatNet(3, -5000L),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), ranks.map { it.rank })
        assertEquals(listOf(0, 1, 2, 3), ranks.map { it.seat })
    }
}
