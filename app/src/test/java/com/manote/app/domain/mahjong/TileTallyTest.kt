package com.manote.app.domain.mahjong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记牌器：上下两块之间的来回搬动。
 *
 * 这里守的是两条边界——同一种牌不能被点出第五张、也不能在撤回到零之后再撤；
 * 其余（剩余张数、还剩几种、逐张展开）都是从「已出现几张」这一份数据算出来的。
 */
class TileTallyTest {

    private val wan3 = ChengduTile(MahjongSuit.WAN, 3)

    @Test
    fun emptyTally_hasThirtySixPerSuitRemaining() {
        val tally = TileTally.empty

        assertEquals(0, tally.seenCount(MahjongSuit.WAN))
        assertEquals(36, tally.remainingCount(MahjongSuit.WAN))
        assertEquals(4, tally.remainingOf(wan3))
        assertTrue(tally.visibleTiles(MahjongSuit.WAN).isEmpty())
    }

    @Test
    fun mark_movesOneCopyFromRemainingToSeen() {
        val tally = TileTally.empty.mark(wan3).mark(wan3)

        assertEquals(2, tally.countOf(wan3))
        assertEquals(2, tally.remainingOf(wan3))
        assertEquals(2, tally.seenCount(MahjongSuit.WAN))
        assertEquals(34, tally.remainingCount(MahjongSuit.WAN))
        assertEquals(listOf(wan3, wan3), tally.visibleTiles(MahjongSuit.WAN))
    }

    @Test
    fun mark_stopsAtFourCopies() {
        var tally = TileTally.empty
        repeat(6) { tally = tally.mark(wan3) }

        assertEquals("一副牌里只有四张", 4, tally.countOf(wan3))
        assertEquals(0, tally.remainingOf(wan3))
    }

    @Test
    fun unmark_stopsAtZero() {
        val tally = TileTally.empty.mark(wan3).unmark(wan3)

        assertEquals(0, tally.countOf(wan3))
        // 减到底之后必须原样返回，不能造出一个负数
        assertSame(tally, tally.unmark(wan3))
    }

    @Test
    fun suitsAreCountedSeparately() {
        val tally = TileTally.empty
            .mark(ChengduTile(MahjongSuit.WAN, 1))
            .mark(ChengduTile(MahjongSuit.TIAO, 1))
            .mark(ChengduTile(MahjongSuit.TIAO, 1))

        assertEquals(1, tally.seenCount(MahjongSuit.WAN))
        assertEquals(2, tally.seenCount(MahjongSuit.TIAO))
        assertEquals(0, tally.seenCount(MahjongSuit.TONG))
        assertEquals(35, tally.remainingCount(MahjongSuit.WAN))
        assertEquals(34, tally.remainingCount(MahjongSuit.TIAO))
    }

    @Test
    fun visibleTiles_areOrderedByRank() {
        val tally = TileTally.empty
            .mark(ChengduTile(MahjongSuit.TONG, 9))
            .mark(ChengduTile(MahjongSuit.TONG, 2))
            .mark(ChengduTile(MahjongSuit.TONG, 2))

        assertEquals(
            listOf(
                ChengduTile(MahjongSuit.TONG, 2),
                ChengduTile(MahjongSuit.TONG, 2),
                ChengduTile(MahjongSuit.TONG, 9),
            ),
            tally.visibleTiles(MahjongSuit.TONG),
        )
    }

    @Test
    fun remainingTiles_complementTheSeenOnes() {
        // 一万出了两张、九万出了一张，其余原封不动
        val tally = TileTally.empty
            .mark(ChengduTile(MahjongSuit.WAN, 1))
            .mark(ChengduTile(MahjongSuit.WAN, 1))
            .mark(ChengduTile(MahjongSuit.WAN, 9))

        val pool = tally.remainingTiles(MahjongSuit.WAN)

        assertEquals(33, pool.size)
        assertEquals("还剩两张就摆两块", 2, pool.count { it.rank == 1 })
        assertEquals(3, pool.count { it.rank == 9 })
        assertEquals(4, pool.count { it.rank == 5 })
        // 念过去就是 11 3333 4444 …… 999，所以必须按点数排好、同点相连
        assertEquals(pool.sortedBy { it.rank }, pool)
        assertEquals(
            "未出现 + 已出现，每种牌都得是四张",
            (1..9).associateWith { 4 },
            (1..9).associateWith { rank ->
                pool.count { it.rank == rank } +
                    tally.visibleTiles(MahjongSuit.WAN).count { it.rank == rank }
            },
        )
    }

    @Test
    fun clear_oneSuitLeavesTheOthers() {
        val tally = TileTally.empty
            .mark(ChengduTile(MahjongSuit.WAN, 5))
            .mark(ChengduTile(MahjongSuit.TIAO, 5))
            .cleared(MahjongSuit.WAN)

        assertEquals(0, tally.seenCount(MahjongSuit.WAN))
        assertEquals(1, tally.seenCount(MahjongSuit.TIAO))
        assertTrue(tally.cleared().seen.all { it == 0 })
    }
}
