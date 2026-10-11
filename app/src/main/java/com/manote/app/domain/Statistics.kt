package com.manote.app.domain

/**
 * 个人跨场统计（规格 2.4）。
 *
 * 全部由已保存场次的净金额实时计算，不另存手工累计，也不维护独立账本。
 */
data class PlayerStatistics(
    /** 已记录场数 = 该玩家参与的 Session 数。 */
    val sessionCount: Int,
    /** 赢的场数（净金额 > 0）。 */
    val winCount: Int,
    /** 输的场数（净金额 < 0）。 */
    val lossCount: Int,
    /** 持平的场数（净金额 = 0）。 */
    val drawCount: Int,
    /** 累计赢钱 = 各场正净金额之和。 */
    val winMinor: Long,
    /** 累计输钱 = 各场负净金额绝对值之和，非负。 */
    val lossMinor: Long,
) {
    /** 净输赢 = 累计赢钱 - 累计输钱。 */
    val netMinor: Long get() = winMinor - lossMinor

    /**
     * 场均净输赢 = 净输赢 ÷ 场数，四舍五入到分。
     *
     * 没有场次时为 0，避免除零；全程整数运算，不经过浮点（规格 2.1）。
     */
    val averageNetMinor: Long
        get() = if (sessionCount == 0) 0L else divideRounded(netMinor, sessionCount.toLong())

    companion object {

        val EMPTY = PlayerStatistics(0, 0, 0, 0, 0L, 0L)

        /**
         * 由某位玩家的各场净金额（分）计算统计。
         * 赢场数 + 输场数 + 持平场数恒等于已记录场数。
         */
        fun of(netMinors: List<Long>): PlayerStatistics {
            var winCount = 0
            var lossCount = 0
            var drawCount = 0
            var winMinor = 0L
            var lossMinor = 0L
            for (net in netMinors) {
                when {
                    net > 0L -> {
                        winCount++
                        winMinor += net
                    }

                    net < 0L -> {
                        lossCount++
                        lossMinor += -net
                    }

                    else -> drawCount++
                }
            }
            return PlayerStatistics(
                sessionCount = netMinors.size,
                winCount = winCount,
                lossCount = lossCount,
                drawCount = drawCount,
                winMinor = winMinor,
                lossMinor = lossMinor,
            )
        }
    }
}

/**
 * 某个时段（早场 / 下午场 / 晚场）的个人统计。
 *
 * 与 [PlayerStatistics] 同一口径：完全由已保存场次实时算出，不另存任何累计账本。
 */
data class TimeSlotStatistics(
    val slot: TimeSlot,
    val statistics: PlayerStatistics,
) {
    /** 该时段已记录场数。 */
    val sessionCount: Int get() = statistics.sessionCount

    /** 该时段净额，分。 */
    val netMinor: Long get() = statistics.netMinor

    /**
     * 该时段胜率，百分数取整（四舍五入）。
     *
     * 分母是「该时段的全部场次」，持平场也算在分母里——「十场只赢三场」与
     * 「三场全赢」不是一回事，把持平剔除会把胜率算虚高。
     * 没有场次时返回 0，由界面自行显示占位符。
     */
    val winRatePercent: Int
        get() = if (sessionCount == 0) {
            0
        } else {
            (statistics.winCount * 100 + sessionCount / 2) / sessionCount
        }

    /** 该时段场均净额，分。没有场次时为 0。 */
    val averageNetMinor: Long get() = statistics.averageNetMinor

    /** 该时段是否还没有任何记录。 */
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {

        /**
         * 由「时段 → 该时段各场净额」生成三个时段的统计。
         *
         * 返回值恒为三条，顺序固定为早 / 下午 / 晚；缺失的时段补成 0 场，
         * 界面因此不必再判断「有没有这一段」。
         */
        fun of(netMinorsBySlot: Map<TimeSlot, List<Long>>): List<TimeSlotStatistics> =
            TimeSlot.displayOrder.map { slot ->
                TimeSlotStatistics(
                    slot = slot,
                    statistics = PlayerStatistics.of(netMinorsBySlot[slot].orEmpty()),
                )
            }

        /** 三个时段都是空的统计，用作还没读到数据时的初值。 */
        fun blank(): List<TimeSlotStatistics> = of(emptyMap())
    }
}

/**
 * 整数除法，四舍五入（.5 向远离零的方向进位），不经过浮点。
 *
 * 负数必须单独走一遍，否则 `(value + divisor / 2) / divisor` 会把 -1.5 读成 -1。
 *
 * 放在这里给整个 domain 用：[PlayerStatistics.averageNetMinor] 与
 * [RivalryRecord.myAverageMinor] 都是「合计 ÷ 场数」，两处必须用同一套取整规则，
 * 否则同一种「场均」在两个页面上会有分毫之差。
 */
internal fun divideRounded(value: Long, divisor: Long): Long {
    val half = divisor / 2
    return if (value >= 0L) {
        (value + half) / divisor
    } else {
        -((-value + half) / divisor)
    }
}
