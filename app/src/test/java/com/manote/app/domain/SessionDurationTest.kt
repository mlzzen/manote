package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 时长本身的取值规则、反写与「开始时间」推算。
 *
 * 时段归类不准的根子在这里：只记结束时间时，14:00 打到 18:30 会被算成晚场，
 * 通宵到 06:00 会被算成早场。加上时长后，两者都按开始时间落回正确的时段。
 */
class SessionDurationTest {

    @Test
    fun defaultIsFourHours() {
        assertEquals(240, SessionDuration.DEFAULT_MINUTES)
    }

    @Test
    fun formatHoursDropsTrailingZeroes() {
        assertEquals("4", SessionDuration.formatHours(240))
        assertEquals("0.5", SessionDuration.formatHours(30))
        assertEquals("4.5", SessionDuration.formatHours(270))
        assertEquals("1.3", SessionDuration.formatHours(78))
        assertEquals("24", SessionDuration.formatHours(1440))
    }

    @Test
    fun formatHoursRoundTripsThroughParser() {
        // 反写回输入框的文本必须能被同一个解析器读回同一个值，
        // 否则「打开修改页、什么都不动、保存」会把时长悄悄改掉。
        listOf(30, 78, 240, 252, 270, 1440).forEach { minutes ->
            val text = SessionDuration.formatHours(minutes)
            val parsed = DurationParser.parse(text) as DurationParseResult.Success
            assertEquals("$minutes 分钟应能通过「$text」原样读回", minutes, parsed.minutes)
        }
    }

    @Test
    fun isValidAcceptsTheDocumentedRange() {
        assertTrue(SessionDuration.isValid(30))
        assertTrue(SessionDuration.isValid(240))
        assertTrue(SessionDuration.isValid(1440))
    }

    @Test
    fun isValidRejectsValuesOutsideTheRange() {
        assertFalse(SessionDuration.isValid(0))
        assertFalse(SessionDuration.isValid(24))
        assertFalse(SessionDuration.isValid(1446))
        assertFalse(SessionDuration.isValid(-240))
    }

    @Test
    fun isValidRejectsValuesFinerThanOneTenthOfAnHour() {
        // 31 分钟既不是 0.1 小时的整数倍，反写回输入框也只能显示成 0.5 小时——
        // 用户下次打开会看到一个自己没填过的数字，因此整条链路上直接判非法。
        assertFalse(SessionDuration.isValid(31))
        assertFalse(SessionDuration.isValid(245))
    }

    @Test
    fun startAtSubtractsTheDurationFromTheEndTime() {
        val end = 1_800_000_000_000L

        assertEquals(end - 4 * 60 * 60_000L, SessionDuration.startAt(end, 240))
        assertEquals(end - 30 * 60_000L, SessionDuration.startAt(end, 30))
        assertEquals(end, SessionDuration.startAt(end, 0))
    }

    @Test
    fun midpointSitsHalfwayBetweenStartAndEnd() {
        val end = 1_800_000_000_000L

        assertEquals(end - 2 * 60 * 60_000L, SessionDuration.midpointAt(end, 240))
        assertEquals(end - 15 * 60_000L, SessionDuration.midpointAt(end, 30))
        assertEquals(end, SessionDuration.midpointAt(end, 0))
    }

    @Test
    fun midpointHasNoRoundingErrorAtHalfHourDurations() {
        // 30 分钟的场次折半是 15 分钟，仍然落在整分钟上
        assertEquals(900_000L, SessionDuration.midpointAt(1_800_000L, 30))
        // 6 分钟（0.1 小时）的时长折半也有整毫秒表示，不会因取整偏到相邻时段
        assertEquals(1_800_000L - 3 * 60_000L, SessionDuration.midpointAt(1_800_000L, 6))
    }

    @Test
    fun startAtMovesAnOvernightSessionBackToThePreviousNight() {
        // 2026-10-06 01:00 收场、打了 4 小时 → 开始于 2026-10-05 21:00，属于晚场
        val zone = ZoneId.of("Asia/Shanghai")
        val end = LocalDateTime.of(2026, 10, 6, 1, 0).atZone(zone).toInstant().toEpochMilli()

        val start = SessionDuration.startAt(end, SessionDuration.DEFAULT_MINUTES)
        val startedAt = Instant.ofEpochMilli(start).atZone(zone)

        assertEquals(5, startedAt.dayOfMonth)
        assertEquals(21, startedAt.hour)
        assertEquals(TimeSlot.EVENING, TimeSlot.of(start, zone))
    }

    @Test
    fun midpointKeepsAnOvernightSessionInTheEveningSlot() {
        // 21:00 打到次日 01:00，中点 23:00 → 晚场。
        // 按结束时间（01:00）算也是晚场，但那是靠 00:00–04:59 归晚场的规则兜住的；
        // 中点不依赖这条兜底规则，口径更稳。
        val zone = ZoneId.of("Asia/Shanghai")
        val end = LocalDateTime.of(2026, 10, 6, 1, 0).atZone(zone).toInstant().toEpochMilli()

        val mid = SessionDuration.midpointAt(end, SessionDuration.DEFAULT_MINUTES)
        val at = Instant.ofEpochMilli(mid).atZone(zone)

        assertEquals(5, at.dayOfMonth)
        assertEquals(23, at.hour)
        assertEquals(TimeSlot.EVENING, TimeSlot.of(mid, zone))
    }

    @Test
    fun midpointTurnsAnEveningEndTimeIntoAnAfternoonSlot() {
        // 14:00 打到 18:30：结束时间是傍晚，但这一场主要发生在下午
        val zone = ZoneId.of("Asia/Shanghai")
        val end = LocalDateTime.of(2026, 10, 5, 18, 30).atZone(zone).toInstant().toEpochMilli()

        val mid = SessionDuration.midpointAt(end, 270)

        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(mid, zone))
        assertEquals("只看结束时间会得到晚场，这正是要修的那个错", TimeSlot.EVENING, TimeSlot.of(end, zone))
    }

    @Test
    fun midpointResolvesAGameThatSpansTheAfternoonAndTheEvening() {
        // 17:30 打到 21:30：开始时间落下午场、结束时间落晚场，中点 19:30 说明主要发生在晚上。
        // 这正是「按开始时间判定」会判错的场景，也是改用中点的理由。
        val zone = ZoneId.of("Asia/Shanghai")
        val end = LocalDateTime.of(2026, 10, 5, 21, 30).atZone(zone).toInstant().toEpochMilli()

        val start = SessionDuration.startAt(end, 240)
        val mid = SessionDuration.midpointAt(end, 240)
        val startAt = Instant.ofEpochMilli(start).atZone(zone)

        assertEquals(17, startAt.hour)
        assertEquals(30, startAt.minute)
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(start, zone))
        assertEquals(TimeSlot.EVENING, TimeSlot.of(mid, zone))
    }
}
