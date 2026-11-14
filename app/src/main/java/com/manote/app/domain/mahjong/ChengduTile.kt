package com.manote.app.domain.mahjong

/**
 * 成都麻将的三门花色。
 *
 * 成都麻将（血战到底）**没有字牌**：整副只有万、条、筒三门 1-9，共 108 张。
 * 因此这里没有「风」「箭」的位置，向听计算也不必处理字牌只能成刻子这条特例。
 *
 * 枚举顺序就是界面上从上到下的顺序，也是 [ChengduTile.index] 把一手牌压成
 * 0..26 的进位顺序——顺序变了，索引与界面就对不上了。
 */
enum class MahjongSuit {
    WAN,
    TIAO,
    TONG,
}

/**
 * 一张成都麻将牌：花色 + 点数（1-9）。
 *
 * 用「花色 + 点数」而不是「一个整数」来抬头，是为了让调用点读得出
 * `ChengduTile(MahjongSuit.TONG, 3)` 是三筒；真正的计算一律换成
 * [index] 之后的整数数组，那一步只在 [ChengduHand] 与向听算法里发生一次。
 */
data class ChengduTile(val suit: MahjongSuit, val rank: Int) {

    init {
        require(rank in 1..RANKS) { "点数必须在 1..$RANKS 之间：$rank" }
    }

    /** 0..26 的线性下标：[suit] 落在哪一段，[rank] 落在段内第几位。 */
    val index: Int get() = suit.ordinal * RANKS + (rank - 1)

    companion object {
        /** 每门九张点数。 */
        const val RANKS = 9

        /** 整副的牌种数：三门 × 九张 = 27。 */
        val KINDS: Int = MahjongSuit.entries.size * RANKS

        /** 同一种牌最多四张——整副 108 张 = 27 种 × 4。 */
        const val COPIES = 4

        /** 全部 27 种牌，顺序按 [index]。 */
        val all: List<ChengduTile> = MahjongSuit.entries.flatMap { suit ->
            (1..RANKS).map { rank -> ChengduTile(suit, rank) }
        }

        /** 由 0..26 的下标取牌。 */
        fun ofIndex(index: Int): ChengduTile {
            require(index in 0 until KINDS) { "下标越界：$index" }
            return all[index]
        }
    }
}

/**
 * 一手牌：27 个计数，每种牌 0..4 张。
 *
 * 存「每种几张」而不是「一串牌」，是因为牌理计算里几乎每一步都在问
 * 「这种牌还剩几张」——拆面子、找搭子、判断进张都要反复查这个数。
 * 存成一串牌则每次都得先数一遍，而且顺序还会影响相等判断。
 *
 * 所有操作都返回新实例，不就地修改：界面上一张张点牌时，
 * 交给 Compose 的永远是一个「另一个手牌」，不必担心同一个对象被改掉而漏了重组。
 */
data class ChengduHand(val counts: List<Int>) {

    init {
        require(counts.size == ChengduTile.KINDS) {
            "手牌需要 ${ChengduTile.KINDS} 个计数，实际 ${counts.size}"
        }
        require(counts.all { it in 0..ChengduTile.COPIES }) {
            "每种牌的张数必须在 0..${ChengduTile.COPIES} 之间"
        }
    }

    /** 手上的张数。 */
    val size: Int get() = counts.sum()

    val isEmpty: Boolean get() = size == 0

    fun countOf(tile: ChengduTile): Int = counts[tile.index]

    /** 摸进一张；该种牌已经有四张时原样返回（物理上不存在第五张）。 */
    fun plus(tile: ChengduTile): ChengduHand {
        if (countOf(tile) >= ChengduTile.COPIES) return this
        val next = counts.toMutableList()
        next[tile.index] = next[tile.index] + 1
        return ChengduHand(next)
    }

    /** 打出一张；手上没有这张时原样返回。 */
    fun minus(tile: ChengduTile): ChengduHand {
        if (countOf(tile) == 0) return this
        val next = counts.toMutableList()
        next[tile.index] = next[tile.index] - 1
        return ChengduHand(next)
    }

    /** 清空某一门；[suit] 为 null 时清空全部（「清空」按钮用）。 */
    fun cleared(suit: MahjongSuit? = null): ChengduHand {
        if (suit == null) return ChengduHand.empty
        val start = suit.ordinal * ChengduTile.RANKS
        val next = counts.toMutableList()
        for (index in start until start + ChengduTile.RANKS) next[index] = 0
        return ChengduHand(next)
    }

    /** 按花色、点数排好的一手牌，用来铺界面上的牌面。 */
    fun tiles(): List<ChengduTile> = ChengduTile.all.filter { countOf(it) > 0 }

    /** 某一门已经选了几张。 */
    fun suitCount(suit: MahjongSuit): Int {
        val start = suit.ordinal * ChengduTile.RANKS
        return counts.subList(start, start + ChengduTile.RANKS).sum()
    }

    companion object {
        val empty: ChengduHand = ChengduHand(List(ChengduTile.KINDS) { 0 })
    }
}
