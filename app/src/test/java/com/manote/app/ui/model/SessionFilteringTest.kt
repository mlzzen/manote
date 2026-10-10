package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 场次筛选（全部 / 本月 / 上月）。
 *
 * 时区是这里最容易出错的地方：列表显示用的是设备时区，
 * 因此「本月」必须按同一时区判断，否则月末月初会出现「显示 10 月却被筛掉」。
 */
class SessionFilteringTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun row(
        id: String,
        playedAt: Long,
        title: String = "麻将对局",
        names: List<String> = emptyList(),
    ) = SessionRowUi(
        id = id,
        title = title,
        note = "",
        playedAt = playedAt,
        selfNetMinor = 0L,
        opponentNames = names,
        // 与 SessionWithResults.toRowUi() 的拼法保持一致
        searchHaystack = (listOf(title) + names).joinToString("\n").lowercase(),
    )

    private val now = at(2026, 10, 10)

    private val october = row("oct", at(2026, 10, 5))
    private val september = row("sep", at(2026, 9, 28))
    private val august = row("aug", at(2026, 8, 15))
    private val all = listOf(october, september, august)

    @Test
    fun all_keepsEverythingAndOrder() {
        val result = SessionFiltering.apply(all, SessionFilter.ALL, now, zone)
        assertEquals(listOf("oct", "sep", "aug"), result.map { it.id })
    }

    @Test
    fun thisMonth_keepsOnlyCurrentMonth() {
        val result = SessionFiltering.apply(all, SessionFilter.THIS_MONTH, now, zone)
        assertEquals(listOf("oct"), result.map { it.id })
    }

    @Test
    fun lastMonth_keepsOnlyPreviousMonth() {
        val result = SessionFiltering.apply(all, SessionFilter.LAST_MONTH, now, zone)
        assertEquals(listOf("sep"), result.map { it.id })
    }

    @Test
    fun lastMonth_acrossYearBoundary() {
        val january = at(2026, 1, 10)
        val december = row("dec", at(2025, 12, 31, hour = 22))
        val november = row("nov", at(2025, 11, 30))

        val result = SessionFiltering.apply(
            listOf(december, november),
            SessionFilter.LAST_MONTH,
            january,
            zone,
        )

        assertEquals(listOf("dec"), result.map { it.id })
    }

    @Test
    fun monthBoundary_usesDeviceTimeZoneNotUtc() {
        // 2026-09-30 23:00（东八区）在 UTC 下已是 09-30 15:00，仍是九月；
        // 2026-10-01 00:30（东八区）在 UTC 下才是 09-30，若按 UTC 判月会被误判成九月。
        val lateSeptember = row("late-sep", at(2026, 9, 30, hour = 23, minute = 0))
        val earlyOctober = row("early-oct", at(2026, 10, 1, hour = 0, minute = 30))
        val rows = listOf(earlyOctober, lateSeptember)

        val thisMonth = SessionFiltering.apply(rows, SessionFilter.THIS_MONTH, now, zone)
        val lastMonth = SessionFiltering.apply(rows, SessionFilter.LAST_MONTH, now, zone)

        assertEquals("东八区 10-01 00:30 属于本月", listOf("early-oct"), thisMonth.map { it.id })
        assertEquals("东八区 09-30 23:00 属于上月", listOf("late-sep"), lastMonth.map { it.id })
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        SessionFilter.entries.forEach { filter ->
            assertEquals(emptyList<SessionRowUi>(), SessionFiltering.apply(emptyList(), filter, now, zone))
        }
    }

    // ————————————————————— 关键词搜索（规格 3.3） —————————————————————

    private val friday = row("fri", at(2026, 10, 5), title = "周五夜战", names = listOf("老王", "小李", "小陈"))
    private val unit = row("unit", at(2026, 10, 3), title = "单位牌局", names = listOf("老王", "小张", "小陈"))
    private val searchable = listOf(friday, unit)

    @Test
    fun search_matchesTitle() {
        assertEquals(listOf("fri"), SessionFiltering.search(searchable, "周五").map { it.id })
    }

    @Test
    fun search_matchesHistoricalNicknameSnapshot() {
        // 「老王」两场都在，两场都应命中
        assertEquals(listOf("fri", "unit"), SessionFiltering.search(searchable, "老王").map { it.id })
        assertEquals(listOf("fri"), SessionFiltering.search(searchable, "小李").map { it.id })
    }

    @Test
    fun search_isCaseInsensitiveAndTrimsQuery() {
        val abc = row("abc", at(2026, 10, 5), title = "Night Game", names = listOf("Alice"))
        assertEquals(listOf("abc"), SessionFiltering.search(listOf(abc), "  alice  ").map { it.id })
        assertEquals(listOf("abc"), SessionFiltering.search(listOf(abc), "NIGHT").map { it.id })
    }

    @Test
    fun blankQuery_keepsEverything() {
        assertEquals(searchable, SessionFiltering.search(searchable, ""))
        assertEquals(searchable, SessionFiltering.search(searchable, "   "))
    }

    @Test
    fun search_doesNotMatchNote() {
        // 规格 3.3 只按标题与历史昵称搜索，备注不参与，避免标题搜索被备注里的词意外命中
        val withNote = searchable.first().copy(note = "喝了很多茶")
        assertEquals(emptyList<SessionRowUi>(), SessionFiltering.search(listOf(withNote), "喝茶"))
    }

    @Test
    fun search_doesNotJoinTitleAndNicknameIntoPhantomWord() {
        // 标题「周五夜战」+ 昵称「老王」，直接拼接会出现「战老」这个并不存在的词
        assertEquals(emptyList<SessionRowUi>(), SessionFiltering.search(searchable, "战老"))
    }

    @Test
    fun filterAndSearchCombine() {
        val september = row("sep", at(2026, 9, 28), title = "周五夜战", names = listOf("老王"))
        val rows = listOf(friday, unit, september)

        val result = SessionFiltering.apply(rows, SessionFilter.THIS_MONTH, now, zone, query = "周五")

        assertEquals("本月 + 关键词同时生效", listOf("fri"), result.map { it.id })
    }
}
