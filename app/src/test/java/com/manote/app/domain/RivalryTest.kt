package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 同场账与挂在牌友列表上的三个标签。
 *
 * 口径都要求同场满 [Rivalry.MIN_COMMON_SESSIONS] 场：
 * - 最苦主：我在他面前的场均**最低**；
 * - 福星：我在他面前的场均**最高**；
 * - 牌王：**他自己**的场均最高（别看错成我那侧）。
 *
 * 前两个看「我」，第三个看「他」——这是本次最容易被实现错的地方，因此单独钉住。
 * 比较的还必须是**场均**而不是总账：打得多的那位总盈亏更大，场均未必更高。
 */
class RivalryTest {

    /**
     * 只关心我的净额时用（最苦主 / 福星）；他那一侧填 0，不影响这两个标签的判定。
     */
    private fun games(playerId: String, vararg myNets: Long): List<OpponentMatch> =
        myNets.map { OpponentMatch(playerId = playerId, myNetMinor = it, theirNetMinor = 0L) }

    /** 两侧净额都要指定时用（牌王看的是他那侧）。 */
    private fun bothGames(playerId: String, vararg nets: Pair<Long, Long>): List<OpponentMatch> =
        nets.map { (mine, theirs) ->
            OpponentMatch(playerId = playerId, myNetMinor = mine, theirNetMinor = theirs)
        }

    // ————————————————————— 聚合 —————————————————————

    @Test
    fun aggregatesByPlayerId() {
        val records = Rivalry.of(bothGames("f1", -100L to 300L, 200L to -100L, -300L to 0L))

        assertEquals(1, records.size)
        val record = records.single()
        assertEquals("f1", record.playerId)
        assertEquals(3, record.commonSessionCount)
        assertEquals(-200L, record.myNetMinor)
        assertEquals(200L, record.theirNetMinor)
    }

    @Test
    fun bothAveragesAreDividedByCommonSessions() {
        val record = Rivalry.of(bothGames("f1", -100L to 300L, -100L to 300L, -100L to 0L)).single()

        assertEquals(-100L, record.myAverageMinor)
        // 他的合计 +600 ÷ 3 场 = +200
        assertEquals(200L, record.theirAverageMinor)
    }

    @Test
    fun averagesRoundHalfAwayFromZero() {
        // -200 ÷ 3 = -66.67 → -67；+200 ÷ 3 = 66.67 → 67
        val record = Rivalry.of(bothGames("f1", -100L to 100L, -100L to 100L, 0L to 0L)).single()

        assertEquals(-67L, record.myAverageMinor)
        assertEquals(67L, record.theirAverageMinor)
    }

    @Test
    fun recordsAreSortedByPlayerId() {
        val records = Rivalry.of(games("z", 0L) + games("a", 0L) + games("m", 0L))

        assertEquals(listOf("a", "m", "z"), records.map { it.playerId })
    }

    @Test
    fun recordForReturnsTheSameNumbersTheDetailPageShows() {
        // 牌友详情页的「同场时我的场均」走的就是这个函数，
        // 与列表上打标签用的是同一份账，两处不可能对不上。
        val matches = games("f1", -300L, 0L, 300L) + games("f2", -100L, -100L, -100L)

        val record = Rivalry.recordFor(matches, "f1")
        assertEquals(3, record?.commonSessionCount)
        assertEquals(0L, record?.myAverageMinor)

        assertNull("没同场过的人没有账", Rivalry.recordFor(matches, "f3"))
    }

    // ————————————————————— 最苦主：我的场均最低 —————————————————————

    @Test
    fun worstRivalPicksTheLowestAverageOfMine() {
        val matches = games("a", -100L, -100L, -100L) + games("b", 50L, 50L, 50L)

        assertEquals("a", Rivalry.worstRival(matches)?.playerId)
    }

    @Test
    fun worstRivalRequiresAtLeastThreeCommonSessions() {
        assertNull("只同场 2 场不入榜", Rivalry.worstRival(games("f1", -5000L, -5000L)))
        assertEquals("f1", Rivalry.worstRival(games("f1", -5000L, -5000L, -5000L))?.playerId)
    }

    @Test
    fun worstRivalPicksTheLowestAverageNotTheLargestTotal() {
        // a：3 场共 -300 → 场均 -100；b：5 场共 -400 → 场均 -80。
        // b 的总亏损更大，但「在他面前我场均更低」的是 a。
        val matches = games("a", -100L, -100L, -100L) + games("b", -80L, -80L, -80L, -80L, -80L)

        assertEquals("a", Rivalry.worstRival(matches)?.playerId)
    }

    @Test
    fun worstRivalComparesAveragesExactlyInsteadOfRoundingFirst() {
        // 场均都挤在 -33.3 分附近：-100/3 = -33.33…、-199/6 = -33.17…，
        // 各自四舍五入到分都会变成 -33（并列），真实关系却是前者的场均更低。
        // 把场均更低的那个放在 ID 更大的位置：先取整再比的实现会因并列而选错人。
        val matches = games("z-worse", -50L, -25L, -25L) +
            games("a-better", -33L, -33L, -33L, -33L, -34L, -33L)

        assertEquals("z-worse", Rivalry.worstRival(matches)?.playerId)
    }

    // ————————————————————— 福星：我的场均最高 —————————————————————

    @Test
    fun luckyStarPicksTheHighestAverageOfMine() {
        val matches = games("a", -100L, -100L, -100L) + games("b", 50L, 50L, 50L)

        assertEquals("b", Rivalry.bestRival(matches)?.playerId)
    }

    @Test
    fun luckyStarIsTheExactMirrorOfWorstRival() {
        val matches = games("a", -30L, -30L, -30L) + games("b", 10L, 10L, 10L) + games("c", 90L, 90L, 90L)

        assertEquals("a", Rivalry.worstRival(matches)?.playerId)
        assertEquals("c", Rivalry.bestRival(matches)?.playerId)
    }

    @Test
    fun luckyStarRequiresAtLeastThreeCommonSessions() {
        assertNull("只同场 2 场不入榜", Rivalry.bestRival(games("f1", 5000L, 5000L)))
        assertEquals("f1", Rivalry.bestRival(games("f1", 5000L, 5000L, 5000L))?.playerId)
    }

    // ————————————————————— 牌王：他的场均最高 —————————————————————

    @Test
    fun tableKingLooksAtTheirAverageNotMine() {
        // a 让我输得最多（我场均 -300），b 自己赢得最多（他场均 +500）。
        // 牌王看的是他那侧，因此是 b；这证明它与最苦主是两个独立的维度。
        val matches = bothGames("a", -300L to 100L, -300L to 100L, -300L to 100L) +
            bothGames("b", -50L to 500L, -50L to 500L, -50L to 500L)

        assertEquals("a", Rivalry.worstRival(matches)?.playerId)
        assertEquals("b", Rivalry.strongestOpponent(matches)?.playerId)
    }

    @Test
    fun tableKingIgnoresMyNumbers() {
        // 两位牌友各同场 3 次。把我这一侧的数字整体取反（从「我输给他」变成「我赢他」），
        // 牌王看的是他那侧，结果必须纹丝不动。
        fun build(sign: Long): List<OpponentMatch> = listOf(
            OpponentMatch("strong", myNetMinor = sign * -500L, theirNetMinor = 300L),
            OpponentMatch("strong", myNetMinor = sign * -500L, theirNetMinor = 300L),
            OpponentMatch("strong", myNetMinor = sign * -500L, theirNetMinor = 300L),
            OpponentMatch("weak", myNetMinor = sign * 900L, theirNetMinor = -200L),
            OpponentMatch("weak", myNetMinor = sign * 900L, theirNetMinor = -200L),
            OpponentMatch("weak", myNetMinor = sign * 900L, theirNetMinor = -200L),
        )

        assertEquals("strong", Rivalry.strongestOpponent(build(1L))?.playerId)
        assertEquals("strong", Rivalry.strongestOpponent(build(-1L))?.playerId)
    }

    @Test
    fun tableKingComparesTheirAveragesExactlyToo() {
        // 他那一侧同样不能先四舍五入：+100/3 = 33.33…、+199/6 = 33.17…
        val matches = bothGames(
            "z-stronger", 0L to 50L, 0L to 25L, 0L to 25L,
        ) + bothGames(
            "a-weaker", 0L to 33L, 0L to 33L, 0L to 33L, 0L to 33L, 0L to 34L, 0L to 33L,
        )

        assertEquals("z-stronger", Rivalry.strongestOpponent(matches)?.playerId)
    }

    @Test
    fun tableKingRequiresAtLeastThreeCommonSessions() {
        assertNull(
            "只同场 2 场不入榜，哪怕他赢得再多",
            Rivalry.strongestOpponent(bothGames("f1", 0L to 9000L, 0L to 9000L)),
        )
        assertEquals(
            "f1",
            Rivalry.strongestOpponent(bothGames("f1", 0L to 9000L, 0L to 9000L, 0L to 9000L))?.playerId,
        )
    }

    // ————————————————————— 并列与退化 —————————————————————

    @Test
    fun tiesAreBrokenByPlayerIdSoBadgesDoNotFlicker() {
        val matches = games("bbb", -100L, -100L, -100L) + games("aaa", -100L, -100L, -100L)

        assertEquals("并列时取 ID 较小的一个，标签不会在两次刷新之间跳来跳去", "aaa", Rivalry.worstRival(matches)?.playerId)
        assertEquals("aaa", Rivalry.bestRival(matches)?.playerId)
    }

    @Test
    fun noOneQualifiesWithoutEnoughMatches() {
        assertNull(Rivalry.worstRival(emptyList()))
        assertNull(Rivalry.bestRival(emptyList()))
        assertNull(Rivalry.strongestOpponent(emptyList()))

        val tooShort = games("f1", 100L) + games("f2", -100L, -100L)
        assertNull(Rivalry.worstRival(tooShort))
        assertNull(Rivalry.bestRival(tooShort))
        assertNull(Rivalry.strongestOpponent(tooShort))
    }

    @Test
    fun ignoresOpponentsBelowTheThresholdEvenIfTheirNumbersLookExtreme() {
        val matches = games("rare", -5000L, -5000L) +
            bothGames("often", -100L to 0L, -100L to 0L, -100L to 0L)

        assertEquals("often", Rivalry.worstRival(matches)?.playerId)
        assertEquals("often", Rivalry.bestRival(matches)?.playerId)
        assertEquals("rare 只同场 2 场，不入牌王评选", "often", Rivalry.strongestOpponent(matches)?.playerId)
    }

    @Test
    fun labelsTheLowestAverageEvenWhenEveryOpponentIsProfitable() {
        // 明确记录下来的取舍：不要求场均必须为负——赢遍所有人时，赢最少的那位仍会被标成最苦主。
        // 想让「苦主」只在净输时才出现，改这一条断言与 [Rivalry.worstRival] 的过滤即可。
        val matches = games("a", 100L, 100L, 100L) + games("b", 30L, 30L, 30L)

        assertEquals("b", Rivalry.worstRival(matches)?.playerId)
        assertEquals("a", Rivalry.bestRival(matches)?.playerId)
    }

    @Test
    fun theOnlyQualifyingOpponentCanCarryBothLabels() {
        // 退化情形：只有一位同场满 3 场的牌友时，他既是我场均最低的、也是最高的。
        // 这是「最低 / 最高」这对口径的直接结果，不是 bug——
        // 因此界面必须允许多个标签同时挂在一行上，而不能假设它们互斥。
        val matches = games("only", -100L, -100L, -100L)

        assertEquals("only", Rivalry.worstRival(matches)?.playerId)
        assertEquals("only", Rivalry.bestRival(matches)?.playerId)
    }
}
