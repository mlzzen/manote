package com.manote.app.ui.model

import com.manote.app.domain.AmountParseResult
import com.manote.app.domain.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额输入过滤。
 *
 * 关键约定：只剔除非法字符，**不**自动截断小数位，
 * 让 `1.001` 交给解析器明确拒绝（规格 2.1）。
 */
class AmountInputTest {

    @Test
    fun keepsDigitsAndSingleDot() {
        assertEquals("12.3", filterAmountInput("12.3"))
        assertEquals("1", filterAmountInput("1"))
    }

    @Test
    fun dropsNonNumericCharacters() {
        assertEquals("123", filterAmountInput("1a2b3"))
        assertEquals("1.5", filterAmountInput("1.5 元"))
        assertEquals("12", filterAmountInput("¥12"))
    }

    @Test
    fun keepsOnlyFirstDot() {
        assertEquals("1.23", filterAmountInput("1.2.3"))
        assertEquals("1.", filterAmountInput("1.."))
    }

    @Test
    fun doesNotTruncateExtraDecimals() {
        // 不能被悄悄改成 1.00，必须原样保留以便解析器报「小数点后最多两位」
        assertEquals("1.001", filterAmountInput("1.001"))
        val parsed = AmountParser.parse(filterAmountInput("1.001"))
        assertTrue("1.001 必须被拒绝", parsed is AmountParseResult.Failure)
    }

    @Test
    fun filteredTextIsAcceptedForNormalInputs() {
        listOf("1", "1.2", "1.20", "0", "0.30", "10000.00").forEach { raw ->
            val parsed = AmountParser.parse(filterAmountInput(raw))
            assertTrue("$raw 过滤后应仍可解析", parsed is AmountParseResult.Success)
        }
    }
}
