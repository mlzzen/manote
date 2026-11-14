package com.manote.app.domain.mahjong

import kotlin.math.min

/**
 * 一张「摸到什么有用」的牌，以及它还剩几张没露面。
 *
 * @param remaining 场上还能摸到的张数（四张减去自己手上的），也就是进张枚数。
 */
data class EffectiveTile(val tile: ChengduTile, val remaining: Int)

/**
 * 一种打法的评估结果。
 *
 * @param shanten 打出 [tile] 之后这一手牌的向听数（-1 表示和了）。
 * @param ukeire 打出之后有多少张牌能进张。和牌之后再谈进张没有意义，记为 0。
 * @param bannedSuit 这张是不是定缺那一门——它无论如何都要打出去，
 *   因此在向听与进张都相同的并列里排在最前。
 */
data class DiscardOption(
    val tile: ChengduTile,
    val shanten: Int,
    val ukeire: Int,
    val bannedSuit: Boolean,
)

/**
 * 成都麻将的牌理：向听数、进张（有效牌）与打牌建议。
 *
 * **它只算牌型，不判番。** 与 tenhou 的牌理工具同一个立场：告诉你「还差几步听牌」、
 * 「摸哪些牌有用」，不告诉你这一手能和多少番——番种涉及定缺、幺九、将对等一堆
 * 地方规则，各家还不一样，算出来的数字只会比牌型更容易吵起来。
 *
 * 向听的定义沿用通用口径：`-1` 是和了，`0` 是听牌，`n` 是还差 n 次有效摸牌。
 * 成都麻将没有字牌，牌种只有 27 种，因此比标准日麻少了两件事——
 * 字牌只能成刻子的特例，以及国士无双那一类役满形。
 *
 * **定缺的处理**（[banned] 不为 null 时）：那一门的牌直接**不参与**牌型拆解，
 * 也就是说算出来的是「这一门打完之后，剩下这些牌是什么向听」。
 * 手上的缺门牌另有张数，界面会并排给出——只报一个「N 向听」而把
 * 「还有两张筒要打」藏起来，等于报了一个还没到手的进度。
 *
 * 有效牌反过来：定缺那一门的牌**一律不进进张列表**。摸到它只能打出去，
 * 它不会让牌型变好，把它算成「有效」会让人以为摸到就有用。
 */
object ChengduShanten {

    /** 一手空牌的向听数。十三张互不相干的牌也是这个数。 */
    const val MAX_SHANTEN = 8

    /** 定型要凑四副面子；碰 / 杠出去的每一副都从这里面扣。 */
    const val MAX_MELDS = 4

    /**
     * 这一手牌的向听数。
     *
     * [hand] 只填**手上还没摊出去的牌**——碰、杠出去的那几组不填进手牌，
     * 而由 [hand] 的张数反推出已经被副露占掉几副面子（见 [meldsOf]）。
     * 因此十张、七张、四张、一张都能算，与十三张同一套口径。
     *
     * [banned] 是已定缺的那一门；传 null 表示不限缺门（三门都算）。
     */
    fun of(hand: ChengduHand, banned: MahjongSuit? = null): Int {
        if (hand.isEmpty) return MAX_SHANTEN
        return shapeShanten(hand.effectiveCounts(banned), meldLimitOf(hand.size))
    }

    /**
     * 摸到哪些牌能让向听数下降，以及各自还剩几张。
     *
     * 只有张数满足 `3n+1`（十三张、十张……）的手牌才谈得上「摸一张」；
     * 十四张时该问的是打哪张，见 [discardOptions]。
     */
    fun effectiveTiles(hand: ChengduHand, banned: MahjongSuit? = null): List<EffectiveTile> {
        if (hand.isEmpty) return emptyList()
        val base = of(hand, banned)
        if (base < 0) return emptyList()

        val meldLimit = meldLimitOf(hand.size)
        val counts = hand.effectiveCounts(banned)
        val result = ArrayList<EffectiveTile>()
        for (tile in ChengduTile.all) {
            if (banned != null && tile.suit == banned) continue
            val owned = hand.countOf(tile)
            if (owned >= ChengduTile.COPIES) continue
            counts[tile.index] += 1
            val better = shapeShanten(counts, meldLimit) < base
            counts[tile.index] -= 1
            if (better) result.add(EffectiveTile(tile, ChengduTile.COPIES - owned))
        }
        return result
    }

    /**
     * 十四张时「打哪张」的候选，好的在前面。
     *
     * 排序看三件事：打完之后的向听、能进多少张、以及这张是不是定缺那一门。
     * 先比向听再比进张，是因为「少一步」永远强过「多七张」——
     * 进张再多，方向错了也还是在原地绕。
     */
    fun discardOptions(hand: ChengduHand, banned: MahjongSuit? = null): List<DiscardOption> {
        val options = ArrayList<DiscardOption>()
        for (tile in ChengduTile.all) {
            if (hand.countOf(tile) == 0) continue
            val after = hand.minus(tile)
            val shanten = of(after, banned)
            val ukeire = if (shanten < 0) 0 else effectiveTiles(after, banned).sumOf { it.remaining }
            options.add(
                DiscardOption(
                    tile = tile,
                    shanten = shanten,
                    ukeire = ukeire,
                    bannedSuit = banned != null && tile.suit == banned,
                ),
            )
        }
        options.sortWith(
            compareBy(
                { it.shanten },
                { -it.ukeire },
                { if (it.bannedSuit) 0 else 1 },
            ),
        )
        return options
    }

    /**
     * 已经碰 / 杠出去的面子数，由手牌张数倒推。
     *
     * 界面上的「手牌」只填**还没摊在桌上的牌**，而每副副露都让手里少三张：
     * 十三张是没副露、十张一副、七张两副、四张三副、一张四副。
     * 摸完牌是十四 / 十一 / 八 / 五 / 二张，同样对得上。
     *
     * **杠与碰在这里没有区别。** 杠虽然占四张牌，但它顶掉的同样只是三张手牌——
     * 第四张是从牌墙补进来的，本来就不在手里。所以「杠了一张、手上剩十张」
     * 与「碰了一张、手上剩十张」要算的是同一个目标，不必分开记。
     *
     * 只有张数的余数是 1 或 2 时才成立：3n 张（十二张、九张……）是还没打完一轮
     * 或者输错了，那时这个数没有意义，调用方要先看 [ChengduHand.size]。
     */
    fun meldsOf(hand: ChengduHand): Int = MAX_MELDS - meldLimitOf(hand.size)

    /**
     * 目标面子数。
     *
     * 成都麻将定型是「四副面子 + 一对将」，副露已经占掉几副，手上就只要凑剩下的：
     * 十三、十四张都得四个面子，十张、十一张得三个——十张时若是四个面子，
     * 那这一手就永远差一张牌，向听数会一直挂着一个根本达不到的目标。
     */
    private fun meldLimitOf(size: Int): Int = (size / 3).coerceIn(0, MAX_MELDS)

    /** 把定缺那一门的计数清零；张数仍然按原手牌算，缺门牌只是「不参与拆解」。 */
    private fun ChengduHand.effectiveCounts(banned: MahjongSuit?): IntArray {
        val array = counts.toIntArray()
        if (banned != null) {
            val start = banned.ordinal * ChengduTile.RANKS
            for (index in start until start + ChengduTile.RANKS) array[index] = 0
        }
        return array
    }

    private fun shapeShanten(counts: IntArray, meldLimit: Int): Int {
        val standard = standardShanten(counts, meldLimit)
        // 七对要十四张、且一副都不能摊在桌上：碰过、杠过的人只剩一般形这一条路。
        // 少了这一道限制，十张里攥着五个对子会被算成一向听，而那一手其实永远和不了七对。
        if (meldLimit < MAX_MELDS) return standard
        return min(standard, sevenPairsShanten(counts))
    }

    /**
     * 一般形（面子 + 一对将）的向听数，面子数由 [meldLimit] 给定。
     *
     * `2 * meldLimit - 2 * 面子 - 搭子 - 将`：每差一副面子要摸两张（搭子补一张、散张补两张），
     * 有一对将就少摸一张。这一串在搜索里随时可算，因此不必等到叶子节点才记账——
     * 中途的形态本身就是一个合法答案（剩下的牌全当散张）。
     *
     * [meldLimit] 可以为 0：四副都碰出去了、手上只剩一两张等将的时候，
     * 这一手照样有向听（单钓 0、已组将 -1），不能当成「算不出来」。
     *
     * 搜索按 0..26 的顺序推进，每个位置只做一件事：把这种牌当成刻子、顺子、
     * 将、搭子，或者干脆弃掉。全部试完之后直接跳到下一种牌——孤张对向听数没有贡献，
     * 逐张弃只会在同一棵子树上重复走很多遍。
     */
    private fun standardShanten(counts: IntArray, meldLimit: Int): Int {
        var best = 2 * meldLimit

        fun visit(index: Int, melds: Int, partials: Int, hasPair: Boolean) {
            if (best <= -1) return
            val value = 2 * meldLimit - 2 * melds - partials - if (hasPair) 1 else 0
            if (value < best) best = value
            if (index >= ChengduTile.KINDS) return
            if (counts[index] == 0) {
                visit(index + 1, melds, partials, hasPair)
                return
            }

            val rank = index % ChengduTile.RANKS

            // 面子与搭子合起来最多四块。只卡 `面子 <= 4` 是不够的：
            // 先拆出四个两面搭子、再拆出四副面子，一张牌会被数两遍，
            // 向听数就会掉到 -1——「能和」与「还差得远」被算成同一个答案。
            if (melds + partials < meldLimit) {
                // 刻子：四张同牌时这里只取三张，剩下那张交给后面的分支。
                if (counts[index] >= 3) {
                    counts[index] -= 3
                    visit(index, melds + 1, partials, hasPair)
                    counts[index] += 3
                }
                // 顺子
                if (rank <= ChengduTile.RANKS - 3 &&
                    counts[index + 1] > 0 && counts[index + 2] > 0
                ) {
                    counts[index]--
                    counts[index + 1]--
                    counts[index + 2]--
                    visit(index, melds + 1, partials, hasPair)
                    counts[index]++
                    counts[index + 1]++
                    counts[index + 2]++
                }
                // 对子作搭子（碰出来就是刻子）
                if (counts[index] >= 2) {
                    counts[index] -= 2
                    visit(index, melds, partials + 1, hasPair)
                    counts[index] += 2
                }
                // 两面 / 边张
                if (rank <= ChengduTile.RANKS - 2 && counts[index + 1] > 0) {
                    counts[index]--
                    counts[index + 1]--
                    visit(index, melds, partials + 1, hasPair)
                    counts[index]++
                    counts[index + 1]++
                }
                // 嵌张
                if (rank <= ChengduTile.RANKS - 3 && counts[index + 2] > 0) {
                    counts[index]--
                    counts[index + 2]--
                    visit(index, melds, partials + 1, hasPair)
                    counts[index]++
                    counts[index + 2]++
                }
            }

            // 对子作将：将不占面子/搭子的名额，所以它不受 meldLimit 限制。
            if (!hasPair && counts[index] >= 2) {
                counts[index] -= 2
                visit(index, melds, partials, true)
                counts[index] += 2
            }

            // 这一张什么也不配：连同剩余的同类牌一起弃掉，看下一种。
            visit(index + 1, melds, partials, hasPair)
        }

        visit(0, 0, 0, false)
        return best
    }

    /**
     * 七对的向听数。
     *
     * 日麻的七对要求七个**不同种类**的对子，所以公式里要补一项「种类数不够」；
     * 成都麻将的龙七对允许四张同牌算两对，因此这里直接数「能拼出几对」
     * （`张数 / 2` 的和），只要够七对就和——少几对就是几步。
     */
    private fun sevenPairsShanten(counts: IntArray): Int {
        var pairs = 0
        for (count in counts) pairs += count / 2
        return 6 - pairs
    }
}
