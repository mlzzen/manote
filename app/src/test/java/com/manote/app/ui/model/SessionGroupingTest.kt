package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 场次列表的按月分组（规格 3.3）。
 *
 * 纯 JVM 测试：分组只做「同月归一组」，不重排、不改数字，
 * 并且必须按与显示时间相同的时区判定，跨月边界要准。
 */
class SessionGroupingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun emptyInputProducesNoGroups() {
        assertTrue(SessionGrouping.byMonth(emptyList(), zone).isEmpty())
    }

    @Test
    fun rowsAreGroupedByMonthKeepingDescendingOrder() {
        val rows = listOf(
            row("a", at(2026, 10, 8, 21, 30), selfNet = 3000L),
            row("b", at(2026, 10, 5, 20, 0), selfNet = -1000L),
            row("c", at(2026, 9, 28, 22, 0), selfNet = 500L),
            row("d", at(2026, 8, 1, 19, 0), selfNet = 0L),
        )

        val groups = SessionGrouping.byMonth(rows, zone)

        assertEquals(3, groups.size)
        assertEquals(listOf("2026年10月", "2026年9月", "2026年8月"), groups.map { it.label })
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d")), groups.map { g -> g.rows.map { it.id } })
    }

    @Test
    fun monthBoundaryUsesTheDisplayTimeZone() {
        // 2026-10-01 00:30（上海）= 2026-09-30 16:30 UTC，必须算作 10 月
        val rows = listOf(
            row("oct", at(2026, 10, 1, 0, 30), selfNet = 1000L),
            row("sep", at(2026, 9, 30, 23, 59), selfNet = 2000L),
        )

        val groups = SessionGrouping.byMonth(rows, zone)

        assertEquals(listOf("2026年10月", "2026年9月"), groups.map { it.label })
    }

    @Test
    fun groupCarriesCountAndSelfNetTotal() {
        val rows = listOf(
            row("a", at(2026, 10, 8, 21, 30), selfNet = 3000L),
            row("b", at(2026, 10, 5, 20, 0), selfNet = -1000L),
            row("c", at(2026, 9, 28, 22, 0), selfNet = -500L),
        )

        val groups = SessionGrouping.byMonth(rows, zone)

        assertEquals(2, groups[0].sessionCount)
        assertEquals(2000L, groups[0].selfNetMinor)
        assertEquals(1, groups[1].sessionCount)
        assertEquals(-500L, groups[1].selfNetMinor)
    }

    @Test
    fun sameMonthRowsThatAreNotAdjacentStillLandInOneGroup() {
        // 同一月份的行即使被别的月份隔开，也只应产生一个分组
        val rows = listOf(
            row("a", at(2026, 10, 8, 21, 30), selfNet = 100L),
            row("b", at(2026, 9, 10, 21, 30), selfNet = 200L),
            row("c", at(2026, 10, 1, 21, 30), selfNet = 300L),
        )

        val groups = SessionGrouping.byMonth(rows, zone)

        assertEquals(2, groups.size)
        assertEquals("2026年10月", groups[0].label)
        assertEquals(listOf("a", "c"), groups[0].rows.map { it.id })
        assertEquals(400L, groups[0].selfNetMinor)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun row(id: String, playedAt: Long, selfNet: Long): SessionRowUi = SessionRowUi(
        id = id,
        title = "周一晚局",
        note = "",
        playedAt = playedAt,
        selfNetMinor = selfNet,
        opponentNames = emptyList(),
        searchHaystack = "",
    )
}
