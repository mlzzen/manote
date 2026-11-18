package com.manote.app.domain.mahjong

/**
 * 测试用的手牌简写：`123m456m789m55t67t`，`m` 万、`t` 条、`p` 筒。
 *
 * 用简写而不是 `plus()` 串一长串，是因为这些用例本身就是「一手牌长什么样」，
 * 写成牌谱形式之后，断言左边那一串可以直接对着牌面读，
 * 不必先在脑子里把 `ChengduTile(TONG, 3)` 翻译一遍。
 */
internal fun handOf(spec: String): ChengduHand {
    var hand = ChengduHand.empty
    val digits = StringBuilder()
    fun flush(suit: MahjongSuit) {
        for (digit in digits) {
            hand = hand.plus(ChengduTile(suit, digit - '0'))
        }
        digits.clear()
    }

    for (symbol in spec) {
        when (symbol) {
            in '1'..'9' -> digits.append(symbol)
            'm' -> flush(MahjongSuit.WAN)
            't' -> flush(MahjongSuit.TIAO)
            'p' -> flush(MahjongSuit.TONG)
            ' ' -> Unit
            else -> throw IllegalArgumentException("不认识的牌谱符号：$symbol")
        }
    }
    require(digits.isEmpty()) { "牌谱必须以花色字母结尾：$spec" }
    return hand
}

/**
 * 一手牌摊成列表：同一种牌有几张就给几个元素。
 *
 * 牌面、进张、推算出来的整手牌都是「逐张」的，用例里也跟着用列表，
 * 免得到断言时再临时展开一遍。
 */
internal fun tilesOf(spec: String): List<ChengduTile> {
    val hand = handOf(spec)
    return ChengduTile.all.flatMap { tile -> List(hand.countOf(tile)) { tile } }
}

/**
 * 把一串牌压回牌谱写法（只用于同一门）：`2234566`。
 *
 * 断言里写牌谱，左边那一串能直接对着牌面读；写成 27 个计数就没人看得懂了。
 * 这里顺带排一次序，调用方不必先自己理好。
 */
internal fun specOf(tiles: List<ChengduTile>): String =
    tiles.sortedBy { it.index }.joinToString("") { it.rank.toString() }
