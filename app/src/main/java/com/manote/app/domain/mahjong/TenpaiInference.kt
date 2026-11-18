package com.manote.app.domain.mahjong

/**
 * 一种「对手手里可能是这样」的摆法。
 *
 * @param hand 对手的整手牌，按花色点数列好。
 * @param waits 这一手听什么（去重后的牌种）。摸到其中任何一张就能和。
 * @param leftovers 剩下的那几张还在牌山里。听的那张若不在这里面，
 *   对手就只能指望别家打出来——那是「点炮」这件事在牌面上的样子。
 */
data class InferredHand(
    val hand: List<ChengduTile>,
    val waits: List<ChengduTile>,
    val leftovers: List<ChengduTile>,
)

/**
 * 由记牌器的「未出现」倒推对手可能听什么。
 *
 * 记牌器一门只记九种、每种四张，所以「还没露面的牌」本身就是一池已知的牌。
 * 对手手上张数要是几乎占满这一池（只差三张以内），那这池牌的分配方式只剩很少几种：
 * 绝大多数在他手里、剩下几张在牌山。把每一种分配都摆出来，凡是能听牌的，
 * 就连同「听什么」「牌山剩什么」一起报——这正是打牌时想知道的：
 * 他可能在做哪一手、我这张打出去会不会点炮。
 *
 * 三个前提，界面上要说清楚：
 * - **只按一门算**：牌池就是记牌器当前那一门的未出现牌；
 * - **对手的张数是人填的**（碰过几组就少三张），不是推出来的；
 * - **不看副露**：手上那几张能不能听，只看牌型本身。
 */
object TenpaiInference {

    /**
     * 牌山最多剩几张才推。
     *
     * 剩余每多一张，摆法就多一个数量级；三张以内还是「能一眼看完」的规模，
     * 再多就成了一面墙，反而看不出结论。
     */
    const val MAX_LEFTOVER = 3

    /**
     * 这一池牌能不能推：张数要装得下对手的手牌，牌山也不能剩太多。
     *
     * 对手手牌必须是 `3n+1` 张（13、10、7、4、1）——只有那个张数才谈得上「听牌」，
     * 十四张是已经摸完该打了，而碰过牌的人手上就正好少三张。
     */
    fun canInfer(unseen: List<ChengduTile>, opponentHand: Int): Boolean =
        opponentHand > 0 &&
            opponentHand % 3 == 1 &&
            unseen.size >= opponentHand &&
            unseen.size - opponentHand <= MAX_LEFTOVER

    /**
     * 把每一种「牌山剩这几张」的分配都试一遍，留下能听牌的那些。
     *
     * 结果按整手牌从小到大排（2 2 3 4 5 6 6 在 3 4 5 6 6 7 9 之前）：
     * 同一个牌池的摆法本来就该稳定地排一个顺序，否则每次重算都换个样子，
     * 上次看到的那一条就找不着了。
     *
     * 不在这里截断：这个函数只回答「有哪些」。要不要只列前几条，是界面的事。
     */
    fun infer(unseen: List<ChengduTile>, opponentHand: Int): List<InferredHand> {
        if (!canInfer(unseen, opponentHand)) return emptyList()

        val pool = unseen.sortedBy { it.index }
        val leftoverCount = pool.size - opponentHand
        val poolCounts = IntArray(ChengduTile.KINDS)
        pool.forEach { poolCounts[it.index]++ }

        val result = ArrayList<InferredHand>()
        for (leftover in combinations(pool, leftoverCount)) {
            val counts = poolCounts.copyOf()
            leftover.forEach { counts[it.index]-- }
            val hand = ChengduHand(counts.toList())
            if (ChengduShanten.of(hand) != 0) continue
            result.add(
                InferredHand(
                    hand = expand(counts),
                    waits = ChengduShanten.effectiveTiles(hand).map { it.tile },
                    leftovers = leftover,
                ),
            )
        }
        return result.sortedWith(HAND_ORDER)
    }

    /**
     * 整手牌的先后：逐张比点数，前一张就分出胜负。
     *
     * 所有摆法张数相同，因此不必再补一条「长度」的比较。
     */
    private val HAND_ORDER = Comparator<InferredHand> { a, b ->
        for (index in 0 until minOf(a.hand.size, b.hand.size)) {
            val diff = a.hand[index].index - b.hand[index].index
            if (diff != 0) return@Comparator diff
        }
        0
    }

    /**
     * 从 [source]（已按点数排好）里取 [size] 张，列出所有**不同的**摆法。
     *
     * 同一张牌取这一枚还是那一枚没有区别，所以同一层递归里相同的点数只走一次——
     * 否则「2 万两张里取一张」会被数出两种，同一个结论在界面上出现两遍。
     */
    private fun combinations(source: List<ChengduTile>, size: Int): List<List<ChengduTile>> {
        val result = ArrayList<List<ChengduTile>>()
        val pick = ArrayList<ChengduTile>(size)

        fun walk(start: Int) {
            if (pick.size == size) {
                result.add(ArrayList(pick))
                return
            }
            var index = start
            while (index < source.size) {
                pick.add(source[index])
                walk(index + 1)
                pick.removeAt(pick.lastIndex)
                val value = source[index]
                index++
                while (index < source.size && source[index] == value) index++
            }
        }

        walk(0)
        return result
    }

    /** 把计数摊回一串牌，顺序同 [ChengduTile.index]。 */
    private fun expand(counts: IntArray): List<ChengduTile> =
        ChengduTile.all.flatMap { tile -> List(counts[tile.index]) { tile } }
}
