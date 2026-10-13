package com.manote.app.ui.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** 场次列表按月分组后的一组。 */
data class SessionMonthGroup(
    val yearMonth: YearMonth,
    /** 形如「2026年10月」。 */
    val label: String,
    /** 本组内的场次，顺序沿用传入顺序（playedAt 倒序）。 */
    val rows: List<SessionRowUi>,
) {
    val sessionCount: Int get() = rows.size

    /** 本人这些场次的净额合计，分。 */
    val selfNetMinor: Long get() = rows.sumOf { it.selfNetMinor }
}

/**
 * 场次列表的按月分组（规格 3.3）。
 *
 * 纯函数，便于单元测试。只做「同月归一组」，**不重新排序**：
 * 顺序依旧由 SQL 的 playedAt DESC（同时间按 ID）决定，
 * 分组不偷偷改掉用户熟悉的排序，也不破坏「同时间按 ID 稳定排序」这条不变量。
 *
 * 月份按设备时区判定，与显示时间用同一个时区，
 * 不会出现列表显示 10 月却被分到 9 月的情况。
 *
 * [locale] 只影响分组标题的写法（详见 [UiText.monthLabel]），
 * 默认取设备语言；测试要钉死标题文案时显式传入。
 */
object SessionGrouping {

    fun byMonth(
        rows: List<SessionRowUi>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<SessionMonthGroup> {
        // LinkedHashMap 保留「首次出现的月份」的顺序，因此组本身也是 playedAt 由近到远。
        val grouped = LinkedHashMap<YearMonth, MutableList<SessionRowUi>>()
        for (row in rows) {
            grouped.getOrPut(yearMonthOf(row.playedAt, zoneId)) { mutableListOf() }.add(row)
        }
        return grouped.map { (yearMonth, monthRows) ->
            SessionMonthGroup(
                yearMonth = yearMonth,
                label = UiText.monthLabel(yearMonth, locale),
                rows = monthRows.toList(),
            )
        }
    }

    private fun yearMonthOf(epochMillis: Long, zoneId: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
}
