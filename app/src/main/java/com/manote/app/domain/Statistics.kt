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
 * 整数除法，四舍五入（.5 向远离零的方向进位），不经过浮点。
 *
 * 负数必须单独走一遍，否则 `(value + divisor / 2) / divisor` 会把 -1.5 读成 -1。
 */
private fun divideRounded(value: Long, divisor: Long): Long {
    val half = divisor / 2
    return if (value >= 0L) {
        (value + half) / divisor
    } else {
        -((-value + half) / divisor)
    }
}
