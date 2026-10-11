package com.manote.app.domain

/** 一场里「我」与某位同桌牌友的净额对照。 */
data class OpponentMatch(
    val playerId: String,
    /** 本场我的净额，分。 */
    val myNetMinor: Long,
    /** 本场这位牌友的净额，分。 */
    val theirNetMinor: Long,
)

/** 某位牌友与「我」同桌的账。 */
data class RivalryRecord(
    val playerId: String,
    /** 两人同桌的场数。 */
    val commonSessionCount: Int,
    /** 这些同桌场次里我的净额合计，分。 */
    val myNetMinor: Long,
    /** 这些同桌场次里他的净额合计，分。 */
    val theirNetMinor: Long,
) {
    /**
     * 同桌场次里他比我多赢的部分（分）；为正表示我相对吃亏。
     *
     * 四人对局没有两两转账记录，「他赢我多少」无法从数据里直接读出，
     * 因此用同桌各场的净额之差作为近似：这不等于他从我手里直接拿走了这么多，
     * 而是说在两人都在的场次里，他整体比我赢得更多。
     * 界面上的文案据此写成「同桌净输给他最多」，而不是「他赢走了我多少钱」。
     */
    val marginMinor: Long get() = theirNetMinor - myNetMinor
}

/**
 * 对手账（「最苦主」的判定依据）。
 *
 * 与其余统计同一口径：由已保存场次实时算出，不另存累计值。
 */
object Rivalry {

    /**
     * 入榜门槛：至少同桌这么多场才参与「最苦主」评选。
     *
     * 没有门槛的话，偶尔一两次同桌输得多的人就会被标成「最苦主」，
     * 那更像抽样噪声而不是稳定的事实。
     */
    const val MIN_COMMON_SESSIONS = 5

    /** 按 playerId 聚合成每位牌友的同桌账，结果按 ID 升序，便于比较与测试。 */
    fun of(matches: List<OpponentMatch>): List<RivalryRecord> =
        matches.groupBy { it.playerId }
            .map { (playerId, group) ->
                RivalryRecord(
                    playerId = playerId,
                    commonSessionCount = group.size,
                    myNetMinor = group.sumOf { it.myNetMinor },
                    theirNetMinor = group.sumOf { it.theirNetMinor },
                )
            }
            .sortedBy { it.playerId }

    /**
     * 最苦主：同桌场次达标、且他整体比我多赢的那一位；并列时取 ID 较小的一位，保证结果稳定。
     *
     * 没有人满足条件时返回 null——「打够五场还净输给他」这件事本身可能就不存在，
     * 这时不该硬挑一个人出来打标。
     */
    fun worstRival(matches: List<OpponentMatch>): RivalryRecord? =
        of(matches)
            .filter { it.commonSessionCount >= MIN_COMMON_SESSIONS && it.marginMinor > 0L }
            .sortedWith(compareByDescending<RivalryRecord> { it.marginMinor }.thenBy { it.playerId })
            .firstOrNull()
}
