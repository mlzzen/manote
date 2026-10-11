package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时长文本解析。
 *
 * 与金额一样是手工解析、不经过浮点：0.1 小时恰好是 6 分钟，
 * 走 Float/Double 会出现 `4.2 × 60 = 251.999…` 这类误差。
 */
class DurationParserTest {

    private fun minutes(text: String): Int {
        val result = DurationParser.parse(text)
        return (result as DurationParseResult.Success).minutes
    }

    private fun failure(text: String): DurationParseFailure {
        val result = DurationParser.parse(text)
        return (result as DurationParseResult.Failure).reason
    }

    @Test
    fun parsesWholeHours() {
        assertEquals(60, minutes("1"))
        assertEquals(240, minutes("4"))
        assertEquals(1440, minutes("24"))
    }

    @Test
    fun parsesOneDecimalOfAnHour() {
        // 一位小数即 6 分钟，必须精确：0.5 小时 = 30 分钟，4.2 小时 = 252 分钟
        assertEquals(30, minutes("0.5"))
        assertEquals(252, minutes("4.2"))
        assertEquals(270, minutes("4.5"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(240, minutes("  4  "))
    }

    @Test
    fun rejectsEmptyInput() {
        assertEquals(DurationParseFailure.EMPTY, failure(""))
        assertEquals(DurationParseFailure.EMPTY, failure("   "))
    }

    @Test
    fun rejectsMalformedInput() {
        // 单独小数点、`4.`、负号、字母、内部空白、第二个小数点、带单位
        listOf(".", "4.", "-4", "四", "4 5", "4.5.5", "4h").forEach { text ->
            assertEquals("应被拒绝：$text", DurationParseFailure.INVALID_FORMAT, failure(text))
        }
    }

    @Test
    fun rejectsMoreThanOneDecimal() {
        assertEquals(DurationParseFailure.TOO_MANY_DECIMALS, failure("4.55"))
        assertEquals(DurationParseFailure.TOO_MANY_DECIMALS, failure("0.25"))
    }

    @Test
    fun rejectsOutOfRange() {
        // 下限 0.5 小时（30 分钟）、上限 24 小时；0 也不合法——「没填」由界面补默认值
        assertEquals(DurationParseFailure.OUT_OF_RANGE, failure("0"))
        assertEquals(DurationParseFailure.OUT_OF_RANGE, failure("0.4"))
        assertEquals(DurationParseFailure.OUT_OF_RANGE, failure("24.1"))
        assertEquals(DurationParseFailure.OUT_OF_RANGE, failure("999"))
    }

    @Test
    fun acceptsTheBoundaries() {
        assertEquals(30, minutes("0.5"))
        assertEquals(1440, minutes("24"))
    }

    @Test
    fun parsesWithoutFloatingPointDrift() {
        // 逐位手工解析的验证：4.2 小时 = 4×60 + 2×6 = 252 分钟，不多一分钟也不少一分钟
        assertEquals(252, minutes("4.2"))
        assertEquals(78, minutes("1.3"))
    }
}
