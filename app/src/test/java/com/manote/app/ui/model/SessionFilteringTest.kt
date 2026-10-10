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

    private fun row(id: String, playedAt: Long) = SessionRowUi(
        id = id,
        title = "麻将对局",
        note = "",
        playedAt = playedAt,
        selfNetMinor = 0L,
        opponentNames = emptyList(),
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
}
