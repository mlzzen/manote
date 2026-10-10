package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 金额显示（规格 2.1）。 */
class MoneyTest {

    @Test
    fun formatsMinorAsTwoDecimalYuan() {
        assertEquals("12.30", Money.formatMinor(1230))
        assertEquals("5.00", Money.formatMinor(500))
        assertEquals("0.05", Money.formatMinor(5))
        assertEquals("0.00", Money.formatMinor(0))
        assertEquals("-5.00", Money.formatMinor(-500))
        assertEquals("-12.30", Money.formatMinor(-1230))
        assertEquals("10000.00", Money.formatMinor(Money.MAX_NET_MINOR))
        assertEquals("-10000.00", Money.formatMinor(Money.MIN_NET_MINOR))
    }

    @Test
    fun formatsSignedYuanWithPlusForWins() {
        assertEquals("+12.30", Money.formatSigned(1230))
        assertEquals("-5.00", Money.formatSigned(-500))
        assertEquals("0.00", Money.formatSigned(0))
        assertEquals("+10.00", Money.formatSigned(1000))
    }

    @Test
    fun describesOutcomeInWordsNotOnlyColor() {
        assertEquals("赢 12.30 元", Money.describe(1230))
        assertEquals("输 5.00 元", Money.describe(-500))
        assertEquals("持平 0.00 元", Money.describe(0))
    }

    @Test
    fun rangeCoversEndpointsOnly() {
        assertEquals(true, Money.isWithinRange(Money.MAX_NET_MINOR))
        assertEquals(true, Money.isWithinRange(Money.MIN_NET_MINOR))
        assertEquals(true, Money.isWithinRange(0))
        assertEquals(false, Money.isWithinRange(Money.MAX_NET_MINOR + 1))
        assertEquals(false, Money.isWithinRange(Money.MIN_NET_MINOR - 1))
    }

    @Test
    fun roundTripsThroughParserOnRepresentativeValues() {
        // 解析与格式化必须互为逆运算，避免出现无法回读的金额。
        for (minor in listOf(0L, 1L, 99L, 100L, 1230L, 1_000_000L, -1L, -99L, -100L, -1230L, -1_000_000L)) {
            val text = Money.formatMinor(minor)
            val parsed = AmountParser.parse(text)
            assertEquals("往返失败：$minor -> '$text'", true, parsed is AmountParseResult.Success)
            assertEquals(minor, (parsed as AmountParseResult.Success).minor)
        }
    }
}
