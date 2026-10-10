package com.manote.app.ui.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 场次列表的筛选条件。 */
enum class SessionFilter {
    ALL,
    THIS_MONTH,
    LAST_MONTH,
}

/**
 * 场次筛选。
 *
 * 纯函数，便于单元测试；「本月 / 上月」按设备时区判断，
 * 与显示时间用同一个时区，不会出现列表显示 10 月却被判成 9 月的情况。
 */
object SessionFiltering {

    fun apply(
        rows: List<SessionRowUi>,
        filter: SessionFilter,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<SessionRowUi> = when (filter) {
        SessionFilter.ALL -> rows
        SessionFilter.THIS_MONTH -> {
            val target = yearMonthOf(nowMillis, zoneId)
            rows.filter { yearMonthOf(it.playedAt, zoneId) == target }
        }

        SessionFilter.LAST_MONTH -> {
            val target = yearMonthOf(nowMillis, zoneId).minusMonths(1)
            rows.filter { yearMonthOf(it.playedAt, zoneId) == target }
        }
    }

    private fun yearMonthOf(epochMillis: Long, zoneId: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
}
