package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 展示文本的格式化。
 *
 * 涉及时间的断言一律不依赖跑测机器的时区：
 * 要么「按设备时区构造、按设备时区显示」（两边同源，任何机器都成立），
 * 要么显式传 [ZoneId]（跨时区的往返）。CI 的 runner 是 UTC，
 * 若在这里隐式依赖系统时区，本地东八区全绿、CI 上必然挂。
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
    fun monthSlashDay_neverPadsWithZero() {
        // 柱状图横轴的写法：6 月 2 日就是 6/2，补零会白占一个字的位置
        assertEquals("6/2", UiText.monthSlashDay(millisOf(2026, 6, 2, 21, 0)))
        assertEquals("10/5", UiText.monthSlashDay(millisOf(2026, 10, 5, 21, 0)))
        assertEquals("1/9", UiText.monthSlashDay(millisOf(2026, 1, 9, 8, 5)))
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
        // 必须按同一个时区读回，否则在 UTC 机器上会显示成 13:30
        assertEquals("2026.10.05 21:30", UiText.dateTime(combined, shanghai))
    }

    @Test
    fun combineDateAndTime_doesNotShiftTheDayInPositiveOffsetZones() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 11-01 的 UTC 零点 + 当地 00:30：若把 UTC 零点当成当地零点，会整体退到 10 月
        val dateUtcMillis = LocalDateTime.of(2026, 11, 1, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

        val combined = UiText.combineDateAndTime(dateUtcMillis, hour = 0, minute = 30, zoneId = shanghai)

        // 按同一时区读回：若把 UTC 零点当成当地零点，这里会变成 10-31 16:30
        assertEquals("2026.11.01 00:30", UiText.dateTime(combined, shanghai))
    }

    @Test
    fun shortId_takesFirstEightCharacters() {
        assertEquals("3f9a1c2b", UiText.shortId("3f9a1c2b-7d4e-4a11-9f00-1b2c3d4e5f60"))
    }

    @Test
    fun shortId_toleratesShortAndEmptyIds() {
        // 极端情况下也不能抛异常：短串原样返回，空串返回空串
        assertEquals("abc", UiText.shortId("abc"))
        assertEquals("", UiText.shortId(""))
    }

    @Test
    fun compactSignedYuan_keepsOneDecimalBelowTenYuan() {
        // 月历格子只有 40dp 宽：不带货币符号、不带千分位
        assertEquals("+12.3", UiText.compactSignedYuan(1_230L))
        assertEquals("-4.5", UiText.compactSignedYuan(-450L))
        assertEquals("+12", UiText.compactSignedYuan(1_200L))
        assertEquals("0", UiText.compactSignedYuan(0L))
    }

    @Test
    fun compactSignedYuan_dropsDecimalsOnceFourDigitsAreNeeded() {
        // 1234.56 元：整数元就够，多一位小数会撑出格子
        assertEquals("+1235", UiText.compactSignedYuan(123_456L))
        assertEquals("-1235", UiText.compactSignedYuan(-123_456L))
    }

    @Test
    fun compactSignedYuan_usesWanForLargeAmounts() {
        assertEquals("+1.2万", UiText.compactSignedYuan(1_234_500L))
        assertEquals("-1万", UiText.compactSignedYuan(-1_000_000L))
        assertEquals("+12.3万", UiText.compactSignedYuan(12_345_678L))
    }

    @Test
    fun compactSignedYuan_neverRoundsAPositiveIntoNegativeOrZeroSign() {
        // 不足一分的方向也必须看得出：四舍五入到 0 之后符号还留着，
        // 否则「赢了 4 分」会被画成一个中性的 0
        assertEquals("+0", UiText.compactSignedYuan(4L))
        assertEquals("-0", UiText.compactSignedYuan(-4L))
    }
}
