package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 金额文本解析（规格 2.1、验收用例 T04）。
 * 断言一律使用整数分。
 */
class AmountParserTest {

    private fun minor(text: String): Long {
        val result = AmountParser.parse(text)
        assertEquals("应解析成功：'$text' 得到 $result", true, result is AmountParseResult.Success)
        return (result as AmountParseResult.Success).minor
    }

    private fun failure(text: String): AmountParseFailure {
        val result = AmountParser.parse(text)
        assertEquals("应解析失败：'$text' 得到 $result", true, result is AmountParseResult.Failure)
        return (result as AmountParseResult.Failure).reason
    }

    @Test
    fun parsesWholeAndDecimalYuan() {
        // T04：1、1.2、1.20 都对应 100、120、120 分
        assertEquals(100L, minor("1"))
        assertEquals(120L, minor("1.2"))
        assertEquals(120L, minor("1.20"))
    }

    @Test
    fun parsesZeroAndNegativeValues() {
        assertEquals(0L, minor("0"))
        assertEquals(0L, minor("0.00"))
        assertEquals(0L, minor("-0"))
        assertEquals(-500L, minor("-5"))
        assertEquals(-550L, minor("-5.5"))
        assertEquals(-550L, minor("-5.50"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(1230L, minor("  12.30  "))
        assertEquals(100L, minor("\t1\n"))
    }

    @Test
    fun acceptsRangeEndpoints() {
        assertEquals(Money.MAX_NET_MINOR, minor("10000"))
        assertEquals(Money.MAX_NET_MINOR, minor("10000.00"))
        assertEquals(Money.MIN_NET_MINOR, minor("-10000"))
        assertEquals(Money.MIN_NET_MINOR, minor("-10000.00"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertEquals(AmountParseFailure.EMPTY, failure(""))
        assertEquals(AmountParseFailure.EMPTY, failure("   "))
    }

    @Test
    fun rejectsSignsAndDotsWithoutDigits() {
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("-"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("."))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("+"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("-."))
    }

    @Test
    fun rejectsTrailingDecimalPoint() {
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1."))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1.-"))
    }

    @Test
    fun rejectsMoreThanTwoDecimals() {
        assertEquals(AmountParseFailure.TOO_MANY_DECIMALS, failure("1.001"))
        assertEquals(AmountParseFailure.TOO_MANY_DECIMALS, failure("0.000"))
    }

    @Test
    fun rejectsScientificNotationAndDigitSeparators() {
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1e3"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1E3"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1,000"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1 000"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("1.2.3"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("abc"))
        assertEquals(AmountParseFailure.INVALID_FORMAT, failure("１２")) // 全角数字
    }

    @Test
    fun rejectsOutOfRangeValues() {
        assertEquals(AmountParseFailure.OUT_OF_RANGE, failure("10000.01"))
        assertEquals(AmountParseFailure.OUT_OF_RANGE, failure("-10000.01"))
        assertEquals(AmountParseFailure.OUT_OF_RANGE, failure("999999999"))
        assertEquals(AmountParseFailure.OUT_OF_RANGE, failure("999999999999999999999"))
    }

    @Test
    fun parsesExactCentsWithoutFloatingPointDrift() {
        // T03：0.30 元等金额必须精确落在分上
        assertEquals(30L, minor("0.30"))
        assertEquals(10L, minor("0.10"))
        assertEquals(1L, minor("0.01"))
        assertEquals(99L, minor("0.99"))
    }
}
