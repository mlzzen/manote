package com.manote.app.domain

/**
 * 走势图与连输/连赢记录的最小输入：一场的（结束时间，某人的净额）。
 *
 * 只带这两个字段，看图与看连输连赢的地方就不必各自去翻 Session 结果表。
 */
data class DatedNet(
    val playedAt: Long,
    /** 该场净额，分。 */
    val netMinor: Long,
)

/** 走势上的一个点：累计到这一场为止的净额。 */
data class TrendPoint(
    val playedAt: Long,
    /** 本场净额，分。 */
    val netMinor: Long,
    /** 含本场在内的累计净额，分。 */
    val cumulativeMinor: Long,
)

/**
 * 累计净额走势（规格 2.4 的「实时算出、不另存累计值」口径）。
 *
 * 输入是各场的净额，输出是逐场累加后的折线点。任何一场被修改或删除，
 * 重算一遍即可得到新的走势，不存在第二份需要同步的累计数据。
 */
object Trend {

    /**
     * 按结束时间升序把各场净额累加成走势点。
     *
     * 结束时间相同的场次（同一分钟里连着记两场）保持传入顺序，
     * 与 `sortedBy` 的稳定性一致，因此同一份输入永远得到同一条曲线。
     * 空输入返回空列表——调用方据此决定显示空态而不是画一条零线。
     */
    fun cumulative(items: List<DatedNet>): List<TrendPoint> {
        var running = 0L
        return items.sortedBy { it.playedAt }.map { item ->
            running += item.netMinor
            TrendPoint(
                playedAt = item.playedAt,
                netMinor = item.netMinor,
                cumulativeMinor = running,
            )
        }
    }
}
