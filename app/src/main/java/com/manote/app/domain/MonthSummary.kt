package com.manote.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 某个月的逐日战况与月度合计。
 *
 * 和 [PlayerStatistics]、[Streaks] 同一口径：全部由已保存场次实时算出，
 * 不另存任何月度账本（规格 2.4）。任何一场被改、被删，重算一遍就是新的月历。
 *
 * [days] 里只有**真的打过的那几天**。没有记录的日子不在表里，
 * 界面据此把格子画成空白，而不是画一个「0 元」——
 * 「这天没打」和「这天打了但持平」是两件事。
 */
data class MonthSummary(
    val yearMonth: YearMonth,
    /** 键是自然日，值是该日合计；按日期升序，遍历顺序即时间顺序。 */
    val days: Map<LocalDate, DayNet>,
    /** 本月全部场次的个人统计。 */
    val statistics: PlayerStatistics,
    /** 本月净额最高的一天（不要求为正）；一天都没记时为 null。 */
    val bestDay: DayNet?,
    /** 本月净额最低的一天（不要求为负）；一天都没记时为 null。 */
    val worstDay: DayNet?,
) {
    /** 本月场数。 */
    val sessionCount: Int get() = statistics.sessionCount

    /** 本月净额，分。 */
    val netMinor: Long get() = statistics.netMinor

    /** 有记录的天数（「出勤」天数），不等于自然日数。 */
    val playedDayCount: Int get() = days.size

    /** 本月是否有任何记录。 */
    val hasSessions: Boolean get() = statistics.sessionCount > 0

    /**
     * 本月胜率，百分数取整（四舍五入）。
     *
     * 分母含持平场，与 [TimeSlotStatistics.winRatePercent] 同一算法：
     * 把持平剔除会把胜率算虚高。
     */
    val winRatePercent: Int
        get() = if (sessionCount == 0) {
            0
        } else {
            (statistics.winCount * 100 + sessionCount / 2) / sessionCount
        }

    /** 某一天的合计；那天没打过时为 null。 */
    fun dayOf(date: LocalDate): DayNet? = days[date]

    companion object {
        /** 空月份：一格记录都没有。 */
        fun empty(yearMonth: YearMonth): MonthSummary = MonthSummary(
            yearMonth = yearMonth,
            days = emptyMap(),
            statistics = PlayerStatistics.EMPTY,
            bestDay = null,
            worstDay = null,
        )
    }
}

/**
 * 把各场净额按月归拢成逐日战况（规格 2.4 的「实时算出」口径）。
 *
 * [zoneId] 决定「哪一天」与「哪一月」怎么划分，必须与界面显示用同一个时区：
 * 存储是 UTC 毫秒，若一处按 UTC 分月、一处按设备时区显示，
 * 就会出现「列表里 10 月 1 日的场次，月历里找不到」这种只能换台机器才复现的错。
 */
object MonthCalendar {

    fun of(
        items: List<DatedNet>,
        yearMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MonthSummary {
        val grouped = HashMap<LocalDate, MutableList<Long>>()
        val monthNets = ArrayList<Long>()
        for (item in items) {
            val day = toLocalDate(item.playedAt, zoneId)
            if (YearMonth.from(day) != yearMonth) continue
            grouped.getOrPut(day) { mutableListOf() }.add(item.netMinor)
            // 月度统计按**场**算，不按天算：一天里赢一场输一场，那是「一胜一负」，
            // 不是「一个持平日」。两者只在按天汇总时才会合并。
            monthNets.add(item.netMinor)
        }
        if (grouped.isEmpty()) return MonthSummary.empty(yearMonth)

        // 按日期升序遍历：同额时「最佳 / 最差单日」命中的是最早的一天，
        // 与 Streaks 里那一对极值的取舍一致，同一份输入永远给出同一个日期。
        val days = grouped
            .mapValues { (day, nets) -> DayNet(day = day, netMinor = nets.sum(), sessionCount = nets.size) }
            .toSortedMap()

        return MonthSummary(
            yearMonth = yearMonth,
            days = days,
            statistics = PlayerStatistics.of(monthNets),
            bestDay = days.values.maxByOrNull { it.netMinor },
            worstDay = days.values.minByOrNull { it.netMinor },
        )
    }

    private fun toLocalDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}
