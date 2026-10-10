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
 * 场次筛选与搜索。
 *
 * 纯函数，便于单元测试；「本月 / 上月」按设备时区判断，
 * 与显示时间用同一个时区，不会出现列表显示 10 月却被判成 9 月的情况。
 *
 * 搜索按规格 3.3 匹配「标题或历史昵称」——历史昵称取该场保存时的快照，
 * 因此麻友后来改名，用旧昵称仍然搜得到那一场（规格 2.2、T12）。
 */
object SessionFiltering {

    fun apply(
        rows: List<SessionRowUi>,
        filter: SessionFilter,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        query: String = "",
    ): List<SessionRowUi> =
        search(byMonth(rows, filter, nowMillis, zoneId), query)

    /** 关键词搜索；空串或纯空白表示不过滤。 */
    fun search(rows: List<SessionRowUi>, query: String): List<SessionRowUi> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return rows
        return rows.filter { it.searchHaystack.contains(keyword) }
    }

    private fun byMonth(
        rows: List<SessionRowUi>,
        filter: SessionFilter,
        nowMillis: Long,
        zoneId: ZoneId,
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
