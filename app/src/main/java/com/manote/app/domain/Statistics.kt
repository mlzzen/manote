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
