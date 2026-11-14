package com.manote.app.domain.mahjong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 向听、进张与打牌建议。
 *
 * 这些用例都写成了牌谱，一边是「这手牌长什么样」，一边是「它该报几步」——
 * 向听算错不会崩，只会让人照着错的思路打一晚上，所以这里覆盖的是
 * 几种典型的牌型，而不是逐条分支。
 */
class ChengduShantenTest {

    @Test
    fun emptyHand_reportsMaxShanten() {
        assertEquals(ChengduShanten.MAX_SHANTEN, ChengduShanten.of(ChengduHand.empty))
        assertTrue(ChengduShanten.effectiveTiles(ChengduHand.empty).isEmpty())
    }

    @Test
    fun thirteenTilesInTenpai_isZero() {
        // 三门面子 + 一对将 + 两面搭子：听 5 条 / 8 条
        val hand = handOf("123456789m55t67t")
        assertEquals(13, hand.size)
        assertEquals(0, ChengduShanten.of(hand))

        val waits = ChengduShanten.effectiveTiles(hand).map { it.tile }
        assertEquals(
            listOf(ChengduTile(MahjongSuit.TIAO, 5), ChengduTile(MahjongSuit.TIAO, 8)),
            waits,
        )
    }

    @Test
    fun effectiveTiles_reportHowManyAreLeft() {
        // 手里已经两张 5 条，所以 5 条只剩 2 张可摸；8 条一张没露，剩 4 张
        val hand = handOf("123456789m55t67t")
        val remaining = ChengduShanten.effectiveTiles(hand).associate { it.tile to it.remaining }

        assertEquals(2, remaining[ChengduTile(MahjongSuit.TIAO, 5)])
        assertEquals(4, remaining[ChengduTile(MahjongSuit.TIAO, 8)])
    }

    @Test
    fun winningHand_isMinusOne() {
        assertEquals(-1, ChengduShanten.of(handOf("123456789m55t678t")))
        // 和了之后没有「进张」这回事
        assertTrue(ChengduShanten.effectiveTiles(handOf("123456789m55t678t")).isEmpty())
    }

    @Test
    fun openWait_usesOnlyThePairedTile() {
        // 四副面子 + 一张单牌：单吊。听的只有那张
        val hand = handOf("123456789m123t5p")
        assertEquals(0, ChengduShanten.of(hand))
        assertEquals(
            listOf(ChengduTile(MahjongSuit.TONG, 5)),
            ChengduShanten.effectiveTiles(hand).map { it.tile },
        )
    }

    @Test
    fun sevenPairs_tenpaiWaitsTheLonelyTile() {
        // 六对 + 一张单牌，听第七对
        val hand = handOf("112233m445566t7p")
        assertEquals(0, ChengduShanten.of(hand))
        assertEquals(
            listOf(ChengduTile(MahjongSuit.TONG, 7)),
            ChengduShanten.effectiveTiles(hand).map { it.tile },
        )
    }

    @Test
    fun sevenPairs_winningHand() {
        assertEquals(-1, ChengduShanten.of(handOf("112233m445566t77p")))
    }

    @Test
    fun sevenPairs_countsFourOfAKindAsTwoPairs() {
        // 龙七对：四张同牌算两对，六种牌也能凑出七对
        assertEquals(-1, ChengduShanten.of(handOf("1111m22334455t66p")))
    }

    @Test
    fun bannedSuit_tilesDoNotTakePartInTheShape() {
        // 筒子那一副 123 其实是个顺子，但不限缺时它不算数
        val hand = handOf("123456789m5t123p")
        assertEquals(13, hand.size)
        assertEquals("不限缺门时，123 筒可以当一副面子", 0, ChengduShanten.of(hand))
        assertEquals(
            "定缺筒之后，只剩三副万子面子加一张孤张",
            2,
            ChengduShanten.of(hand, banned = MahjongSuit.TONG),
        )
    }

    @Test
    fun bannedSuit_neverAppearsInEffectiveTiles() {
        val hand = handOf("123456789m5t123p")
        val waits = ChengduShanten.effectiveTiles(hand, banned = MahjongSuit.TONG)

        assertTrue(waits.isNotEmpty())
        assertTrue(
            "摸到缺门牌只能打出去，不能算进张：${waits.map { it.tile }}",
            waits.none { it.tile.suit == MahjongSuit.TONG },
        )
    }

    @Test
    fun discardOptions_putTheLeastUsefulTileFirst() {
        // 十三张听牌上多摸了一张 1 筒：打掉它，手牌回到原来的听牌形
        val hand = handOf("123456789m55t67t1p")
        assertEquals(14, hand.size)

        val best = ChengduShanten.discardOptions(hand).first()
        assertEquals(ChengduTile(MahjongSuit.TONG, 1), best.tile)
        assertEquals(0, best.shanten)
        // 5 条剩 2 张、8 条剩 4 张
        assertEquals(6, best.ukeire)
    }

    @Test
    fun discardOptions_includeEveryDistinctTileInHand() {
        val hand = handOf("123456789m55t67t1p")
        val discarded = ChengduShanten.discardOptions(hand).map { it.tile }

        assertEquals("同一张牌只需要一种打法", discarded.size, discarded.toSet().size)
        assertEquals("手里每种牌都要给出一条", hand.tiles().toSet(), discarded.toSet())
    }

    @Test
    fun discardOptions_preferTheBannedSuitWhenEverythingElseTies() {
        // 定缺筒：三张筒都要打，打哪张对牌型的影响都一样，它们应当排在最前
        val hand = handOf("123456789m55t6p78p")
        val options = ChengduShanten.discardOptions(hand, banned = MahjongSuit.TONG)

        assertTrue("缺门牌必须优先打出去：${options.map { it.tile }}", options.first().bannedSuit)
        assertFalse("缺门之外还有得打", options.all { it.bannedSuit })
    }

    @Test
    fun meldsOf_isDerivedFromTheTileCount() {
        // 手牌只填手上剩下的：每碰一组、杠一组都少三张
        assertEquals(0, ChengduShanten.meldsOf(handOf("123456789m55t67t")))
        assertEquals(0, ChengduShanten.meldsOf(handOf("123456789m55t67t8t")))
        assertEquals(1, ChengduShanten.meldsOf(handOf("123456789m5t")))
        assertEquals(1, ChengduShanten.meldsOf(handOf("123456789m55t")))
        assertEquals(2, ChengduShanten.meldsOf(handOf("1234567m")))
        assertEquals(3, ChengduShanten.meldsOf(handOf("1234m")))
        assertEquals("四副全碰出去，手上只剩一张", 4, ChengduShanten.meldsOf(handOf("1m")))
    }

    @Test
    fun onePong_tenTilesKeepTheirShanten() {
        // 碰了一副、手上三副面子加一张单牌：单钓
        val hand = handOf("123456789m5t")
        assertEquals(10, hand.size)
        assertEquals(0, ChengduShanten.of(hand))

        val waits = ChengduShanten.effectiveTiles(hand)
        assertEquals(listOf(ChengduTile(MahjongSuit.TIAO, 5)), waits.map { it.tile })
        // 手上已经有一张 5 条，还能摸三张
        assertEquals(3, waits.single().remaining)
    }

    @Test
    fun threeMelds_ceilingDropsWithTheSetsAlreadyOnTheTable() {
        // 碰出去三副、手上四张互不相干的牌：目标只剩一副面子加一对将，
        // 顶格是 2 * 1 步，而不是十三张时的 8 步
        val hand = handOf("147m2p")
        assertEquals(4, hand.size)
        assertEquals(3, ChengduShanten.meldsOf(hand))
        assertEquals(2, ChengduShanten.of(hand))
    }

    @Test
    fun fourMelds_waitOnThePairOnly() {
        // 四副全碰出去，手上只剩一张：单钓将
        val hand = handOf("5t")
        assertEquals(1, hand.size)
        assertEquals(4, ChengduShanten.meldsOf(hand))
        assertEquals(0, ChengduShanten.of(hand))
        assertEquals(
            listOf(ChengduTile(MahjongSuit.TIAO, 5)),
            ChengduShanten.effectiveTiles(hand).map { it.tile },
        )

        // 将补上了就是和了
        assertEquals(-1, ChengduShanten.of(handOf("55t")))
    }

    @Test
    fun elevenTilesWithAfullShape_isAWin() {
        // 杠上开花那一步：副露一副 + 手上三副面子一对将
        val hand = handOf("123456789m55t")
        assertEquals(11, hand.size)
        assertEquals(-1, ChengduShanten.of(hand))
    }

    @Test
    fun discardOptions_workWithElevenTiles() {
        // 碰过一副、刚摸起来一张：该问「打哪张」，不该再劝人去凑十四张
        val hand = handOf("123m456m55t78t9p")
        assertEquals(11, hand.size)

        val best = ChengduShanten.discardOptions(hand).first()
        assertEquals(ChengduTile(MahjongSuit.TONG, 9), best.tile)
        assertEquals(0, best.shanten)
        // 打完 9 筒听 6 条 / 9 条，各四张
        assertEquals(8, best.ukeire)
    }

    @Test
    fun sevenPairs_ignoredOnceASetIsExposed() {
        // 十张五个对子。没副露时七对只要再好两对，但碰过牌的人根本走不了七对，
        // 只能按一般形报：还差两副面子和将
        val hand = handOf("1122m3344t55p")
        assertEquals(10, hand.size)
        assertEquals(1, ChengduShanten.meldsOf(hand))
        assertEquals(
            "碰过牌之后七对不成立，不能被那一项拉低",
            2,
            ChengduShanten.of(hand),
        )
    }

    @Test
    fun plusAndMinus_respectTheFourCopyLimit() {
        var hand = ChengduHand.empty
        repeat(5) { hand = hand.plus(ChengduTile(MahjongSuit.WAN, 1)) }

        assertEquals("同一种牌最多四张", 4, hand.countOf(ChengduTile(MahjongSuit.WAN, 1)))
        assertEquals(4, hand.size)

        repeat(5) { hand = hand.minus(ChengduTile(MahjongSuit.WAN, 1)) }
        assertTrue("减到零就不再减", hand.isEmpty)
    }

    @Test
    fun clearingOneSuit_keepsTheOtherTwo() {
        val hand = handOf("123456789m55t67t")
        val cleared = hand.cleared(MahjongSuit.TIAO)

        assertEquals(9, cleared.size)
        assertEquals(0, cleared.suitCount(MahjongSuit.TIAO))
        assertEquals(9, cleared.suitCount(MahjongSuit.WAN))
        assertTrue(hand.cleared().isEmpty)
    }
}
