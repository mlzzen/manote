package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 展示文本的格式化。
 *
 * 时间断言都基于「同一个时区构造、同一个时区显示」，因此在任何机器时区下都成立。
 */
class UiTextTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun yuan_hasTwoDecimalsAndNoSign() {
        assertEquals("¥12.30", UiText.yuan(1_230L))
        assertEquals("¥-5.00", UiText.yuan(-500L))
        assertEquals("¥0.00", UiText.yuan(0L))
    }

    @Test
    fun signedYuan_addsPlusOnlyForPositive() {
        assertEquals("+¥12.30", UiText.signedYuan(1_230L))
        assertEquals("-¥5.00", UiText.signedYuan(-500L))
        assertEquals("¥0.00", UiText.signedYuan(0L))
    }

    @Test
    fun dateAndTime_useDeviceTimeZone() {
        val playedAt = millisOf(2026, 10, 5, 21, 30)
        assertEquals("2026.10.05", UiText.date(playedAt))
        assertEquals("2026.10.05 21:30", UiText.dateTime(playedAt))
        assertEquals("10.05", UiText.monthDay(playedAt))
    }

    @Test
    fun date_padsSingleDigitMonthAndDay() {
        val playedAt = millisOf(2026, 1, 9, 8, 5)
        assertEquals("2026.01.09", UiText.date(playedAt))
        assertEquals("2026.01.09 08:05", UiText.dateTime(playedAt))
    }

    @Test
    fun percent_roundsToNearestInteger() {
        assertEquals(10, UiText.percent(1, 10))
        assertEquals(90, UiText.percent(9, 10))
        assertEquals(67, UiText.percent(2, 3))
        assertEquals(33, UiText.percent(1, 3))
        assertEquals(100, UiText.percent(10, 10))
        assertEquals(0, UiText.percent(0, 10))
    }

    @Test
    fun percent_handlesZeroTotal() {
        assertEquals(0, UiText.percent(0, 0))
        assertEquals(0, UiText.percent(5, 0))
    }

    @Test
    fun combineDateAndTime_interpretsPickedDateInDeviceZone() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 日期选择器给的是「这一天的 UTC 零点」
        val dateUtcMillis = LocalDateTime.of(2026, 10, 5, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

        val combined = UiText.combineDateAndTime(dateUtcMillis, hour = 21, minute = 30, zoneId = shanghai)

        assertEquals(
            LocalDateTime.of(2026, 10, 5, 21, 30).atZone(shanghai).toInstant().toEpochMilli(),
            combined,
        )
        assertEquals("2026.10.05 21:30", UiText.dateTime(combined))
    }

    @Test
    fun combineDateAndTime_doesNotShiftTheDayInPositiveOffsetZones() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 11-01 的 UTC 零点 + 当地 00:30：若把 UTC 零点当成当地零点，会整体退到 10 月
        val dateUtcMillis = LocalDateTime.of(2026, 11, 1, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

        val combined = UiText.combineDateAndTime(dateUtcMillis, hour = 0, minute = 30, zoneId = shanghai)

        assertEquals("2026.11.01 00:30", UiText.dateTime(combined))
    }
}
