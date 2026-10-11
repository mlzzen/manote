package com.manote.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 某一自然日的净额合计（分）。 */
data class DayNet(
    val day: LocalDate,
    /** 该日各场净额之和，分。 */
    val netMinor: Long,
    /** 该日记录的场数。 */
    val sessionCount: Int,
)

/**
 * 连输 / 连赢与单日极值。
 *
 * 全部由已保存场次实时算出，不另存任何累计值。
 *
 * 持平（净额 = 0）既不算赢也不算输，会把当前连胜与连败**同时切断**——
 * 把持平塞进连胜里，会把「连赢三场」说成「连赢四场」，那是另一个事实。
 */
data class StreakStats(
    /** 目前处于连胜的第几场；最后一场不是赢时为 0。 */
    val currentWinStreak: Int,
    /** 目前处于连败的第几场；最后一场不是输时为 0。 */
    val currentLossStreak: Int,
    /** 历史上最长的连胜场数。 */
    val longestWinStreak: Int,
    /** 历史上最长的连败场数。 */
    val longestLossStreak: Int,
    /** 净额最高的一天；一天都还没记时为 null。 */
    val bestDay: DayNet?,
    /** 净额最低的一天；一天都还没记时为 null。 */
    val worstDay: DayNet?,
) {
    /** 是否有任何可展示的记录。 */
    val hasSessions: Boolean get() = bestDay != null

    companion object {
        val EMPTY = StreakStats(
            currentWinStreak = 0,
            currentLossStreak = 0,
            longestWinStreak = 0,
            longestLossStreak = 0,
            bestDay = null,
            worstDay = null,
        )
    }
}

object Streaks {

    /**
     * 计算连赢/连输与单日极值。
     *
     * [zoneId] 决定「哪一天」怎么划分，必须显式传或统一取设备时区：
     * 存储是 UTC 毫秒，若一处按 UTC 分组、一处按设备时区显示，
     * 就会出现「单日最佳」指向一个用户界面上并不存在的日期。
     */
    fun of(items: List<DatedNet>, zoneId: ZoneId = ZoneId.systemDefault()): StreakStats {
        if (items.isEmpty()) return StreakStats.EMPTY

        val ordered = items.sortedBy { it.playedAt }

        var longestWin = 0
        var longestLoss = 0
        var runWin = 0
        var runLoss = 0
        for (item in ordered) {
            when {
                item.netMinor > 0L -> {
                    runWin++
                    runLoss = 0
                }

                item.netMinor < 0L -> {
                    runLoss++
                    runWin = 0
                }

                // 持平把两条连续记录都打断
                else -> {
                    runWin = 0
                    runLoss = 0
                }
            }
            if (runWin > longestWin) longestWin = runWin
            if (runLoss > longestLoss) longestLoss = runLoss
        }

        // 按日期升序分组求和；同额时 first/last 命中的是最早的一天，结果稳定可复现
        val days = ordered
            .groupBy { toLocalDate(it.playedAt, zoneId) }
            .map { (day, list) ->
                DayNet(day = day, netMinor = list.sumOf { it.netMinor }, sessionCount = list.size)
            }
            .sortedBy { it.day }

        return StreakStats(
            currentWinStreak = runWin,
            currentLossStreak = runLoss,
            longestWinStreak = longestWin,
            longestLossStreak = longestLoss,
            bestDay = days.maxByOrNull { it.netMinor },
            worstDay = days.minByOrNull { it.netMinor },
        )
    }

    private fun toLocalDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}
