package com.manote.app.domain

/** 一场里「我」与某位同场牌友的对照。 */
data class OpponentMatch(
    val playerId: String,
    /** 本场我的净额，分。 */
    val myNetMinor: Long,
    /** 本场这位牌友的净额，分。 */
    val theirNetMinor: Long,
)

/**
 * 某位牌友与「我」同场的账。
 *
 * 两侧的净额都留着：有的标签看「我打成什么样」，有的看「他打成什么样」，
 * 而两者的分母都是同一组同场场次——不这么定的话，两个标签会把
 * 「和谁打」与「打了多少场」混在一起，结论就没法互相印证了。
 */
data class RivalryRecord(
    val playerId: String,
    /** 两人同场的场数，也是下面两个场均的分母。 */
    val commonSessionCount: Int,
    /** 这些同场场次里我的净额合计，分。 */
    val myNetMinor: Long,
    /** 这些同场场次里他的净额合计，分。 */
    val theirNetMinor: Long,
) {
    /**
     * 同场时我的场均，分（四舍五入到分）。
     *
     * 分母是**同场场数**，不是我的总场数：这个数字回答的是「只要他在桌上，我平均打成什么样」，
     * 拿我全部的场次当分母会把与别人打的场混进来，得到的既不是他的账、也不是我的账。
     */
    val myAverageMinor: Long get() = averageOf(myNetMinor)

    /** 同场时他的场均，分。分母同为同场场数。 */
    val theirAverageMinor: Long get() = averageOf(theirNetMinor)

    private fun averageOf(netMinor: Long): Long =
        if (commonSessionCount == 0) 0L else divideRounded(netMinor, commonSessionCount.toLong())
}

/**
 * 同场账，以及挂在牌友列表上的三个标签的判定依据。
 *
 * 三者都要求**同场 ≥ [MIN_COMMON_SESSIONS] 场**，且都由已保存场次实时算出，不另存累计值：
 * - 最苦主 [worstRival]：我在他面前的场均最低；
 * - 福星 [bestRival]：我在他面前的场均最高；
 * - 牌王 [strongestOpponent]：他自己的场均最高。
 *
 * 前两个看「我」，第三个看「他」。比的都是**场均**而不是总账：打得多的那位总盈亏
 * 往往更大，但场均未必更高，而「他在场我就打不好」是场均才回答得了的问题。
 */
object Rivalry {

    /**
     * 入榜门槛：至少同场这么多场才参与评选。
     *
     * 没有门槛的话，偶尔一两次同桌输得多的人就会被标上标签，
     * 那更像抽样噪声而不是稳定的事实。
     */
    const val MIN_COMMON_SESSIONS = 3

    /** 按 playerId 聚合成每位牌友的同场账，结果按 ID 升序，便于比较与测试。 */
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

    /** 某位牌友与我的同场账；从没同场过时返回 null。 */
    fun recordFor(matches: List<OpponentMatch>, playerId: String): RivalryRecord? =
        of(matches).firstOrNull { it.playerId == playerId }

    /** 最苦主：我在他面前的场均最低的那位。没有达标的人时返回 null。 */
    fun worstRival(matches: List<OpponentMatch>): RivalryRecord? =
        qualifying(matches).sortedWith(WORST_FIRST).firstOrNull()

    /** 福星：我在他面前的场均最高的那位。 */
    fun bestRival(matches: List<OpponentMatch>): RivalryRecord? =
        qualifying(matches).sortedWith(BEST_FIRST).firstOrNull()

    /** 牌王：他自己的场均最高的那位。 */
    fun strongestOpponent(matches: List<OpponentMatch>): RivalryRecord? =
        qualifying(matches).sortedWith(STRONGEST_FIRST).firstOrNull()

    /** 同场场数达标、可以参与评选的人。 */
    private fun qualifying(matches: List<OpponentMatch>): List<RivalryRecord> =
        of(matches).filter { it.commonSessionCount >= MIN_COMMON_SESSIONS }

    /** 我在他面前的场均从低到高；并列时取 ID 较小的。 */
    private val WORST_FIRST: Comparator<RivalryRecord> = Comparator { a, b ->
        compareAverage({ it.myNetMinor }, a, b) ?: a.playerId.compareTo(b.playerId)
    }

    /** 我在他面前的场均从高到低；并列时同样取 ID 较小的，标签不会在两次刷新之间跳来跳去。 */
    private val BEST_FIRST: Comparator<RivalryRecord> = Comparator { a, b ->
        compareAverage({ it.myNetMinor }, b, a) ?: a.playerId.compareTo(b.playerId)
    }

    /** 他自己的场均从高到低；并列同样取 ID 较小的。 */
    private val STRONGEST_FIRST: Comparator<RivalryRecord> = Comparator { a, b ->
        compareAverage({ it.theirNetMinor }, b, a) ?: a.playerId.compareTo(b.playerId)
    }

    /**
     * 按场均比较 a 与 b；相等时返回 null，交给调用方决定怎么并列。
     *
     * 先交叉相乘再比大小，而不是各自四舍五入成场均再比：场均常常是无限小数，
     * 先取整会把 -33.33 与 -33.4 并列，甚至排出相反的次序。
     * 场数恒为正，因此两边同乘正数不改变不等号方向；净额上限 × 场数上限远不足以溢出 Long。
     */
    private fun compareAverage(
        select: (RivalryRecord) -> Long,
        a: RivalryRecord,
        b: RivalryRecord,
    ): Int? {
        val left = select(a) * b.commonSessionCount
        val right = select(b) * a.commonSessionCount
        val compared = left.compareTo(right)
        return if (compared == 0) null else compared
    }
}
