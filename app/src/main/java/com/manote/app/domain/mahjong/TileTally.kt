package com.manote.app.domain.mahjong

/**
 * 记牌器：一种花色里，每种点数已经出现了几张。
 *
 * 成都麻将的记牌只需要盯**自己缺的那门以外**的牌，而一门只有九种、
 * 每种四张，所以在脑子里数很容易数岔。这里把它摊开成「已出现 / 未出现」
 * 两块：点一下把一张从上面挪到下面，点下面的牌再挪回来。
 *
 * 只存「已出现的张数」这一份数据：剩余张数、还剩几种、合计几张全部由它算出来。
 * 再存一份「剩余」就等于存了两份互相能推导的账，迟早会对不上。
 */
data class TileTally(val seen: List<Int>) {

    init {
        require(seen.size == ChengduTile.KINDS) {
            "记牌器需要 ${ChengduTile.KINDS} 个计数，实际 ${seen.size}"
        }
        require(seen.all { it in 0..ChengduTile.COPIES }) {
            "每种牌已出现的张数必须在 0..${ChengduTile.COPIES} 之间"
        }
    }

    fun countOf(tile: ChengduTile): Int = seen[tile.index]

    /** 这种牌还有几张没出现。 */
    fun remainingOf(tile: ChengduTile): Int = ChengduTile.COPIES - countOf(tile)

    /** 记一条「这张出现了」；已经出现满四张时原样返回。 */
    fun mark(tile: ChengduTile): TileTally {
        if (countOf(tile) >= ChengduTile.COPIES) return this
        val next = seen.toMutableList()
        next[tile.index] = next[tile.index] + 1
        return TileTally(next)
    }

    /** 撤销一条；本来就没出现过时原样返回。 */
    fun unmark(tile: ChengduTile): TileTally {
        if (countOf(tile) == 0) return this
        val next = seen.toMutableList()
        next[tile.index] = next[tile.index] - 1
        return TileTally(next)
    }

    fun seenCount(suit: MahjongSuit): Int {
        val start = suit.ordinal * ChengduTile.RANKS
        return seen.subList(start, start + ChengduTile.RANKS).sum()
    }

    /** 这一门还剩几张没出现。 */
    fun remainingCount(suit: MahjongSuit): Int =
        ChengduTile.RANKS * ChengduTile.COPIES - seenCount(suit)

    /**
     * 这一门已出现的牌，逐张展开（出现两张就给出两个元素）。
     *
     * 界面下方那一块是「一张牌一个位置」，不做「同种牌合成一个 ×2」——
     * 真牌局里同一张牌出现几轮是不同的信号，摊开摆才看得出先后。
     * 按花色、点数排好，因此新点的牌总是落在自己那一组里，不会跳来跳去。
     */
    fun visibleTiles(suit: MahjongSuit): List<ChengduTile> {
        val start = suit.ordinal * ChengduTile.RANKS
        return (0 until ChengduTile.RANKS).flatMap { offset ->
            val tile = ChengduTile.ofIndex(start + offset)
            List(seen[start + offset]) { tile }
        }
    }

    /**
     * 这一门还没出现的牌，逐张展开（还剩两张就给出两个元素）。
     *
     * 与 [visibleTiles] 正好互补：两个列表加起来，每种牌都是四张。
     * 摊开而不是「每种给一个张数」，是因为界面上要回答的问题是
     * 「池子里还剩哪些牌」——玩家念的是 `118899` 这样一串牌，
     * 不是「1 剩 2、8 剩 2、9 剩 2」三句结论。串本身就能拿去对搭子。
     */
    fun remainingTiles(suit: MahjongSuit): List<ChengduTile> {
        val start = suit.ordinal * ChengduTile.RANKS
        return (0 until ChengduTile.RANKS).flatMap { offset ->
            val tile = ChengduTile.ofIndex(start + offset)
            List(ChengduTile.COPIES - seen[start + offset]) { tile }
        }
    }

    /** 清空某一门；[suit] 为 null 时全清。 */
    fun cleared(suit: MahjongSuit? = null): TileTally {
        if (suit == null) return TileTally.empty
        val start = suit.ordinal * ChengduTile.RANKS
        val next = seen.toMutableList()
        for (index in start until start + ChengduTile.RANKS) next[index] = 0
        return TileTally(next)
    }

    companion object {
        val empty: TileTally = TileTally(List(ChengduTile.KINDS) { 0 })
    }
}
