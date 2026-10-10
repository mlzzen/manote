package com.manote.app.domain

/** 金额文本解析失败的原因，供 UI 给出针对性提示。 */
enum class AmountParseFailure {
    /** 空串或只有空白。 */
    EMPTY,

    /** 格式非法：单独符号、单独小数点、`1.`、指数记法、千位分隔符、内部空白、非数字字符等。 */
    INVALID_FORMAT,

    /** 小数点后超过两位。 */
    TOO_MANY_DECIMALS,

    /** 超出 ±1,000,000 分（即 ±10,000.00 元）。 */
    OUT_OF_RANGE,
}

sealed interface AmountParseResult {
    data class Success(val minor: Long) : AmountParseResult
    data class Failure(val reason: AmountParseFailure) : AmountParseResult
}

/**
 * 人民币元文本 → 整数分（规格 2.1）。
 *
 * 接受：`1`、`1.2`、`1.20`、`-5`、`-5.5`；两端空白会被去除。
 * 拒绝：空值、单独符号、单独小数点、`1.`、`1.001`、指数记法、千位分隔符、超界值。
 *
 * 全程手工解析为 [Long]，不经过 Float/Double。
 */
object AmountParser {

    fun parse(text: String): AmountParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParseResult.Failure(AmountParseFailure.EMPTY)

        var index = 0
        val negative = trimmed[0] == '-'
        if (negative) {
            index = 1
            // 单独负号
            if (index == trimmed.length) return AmountParseResult.Failure(AmountParseFailure.INVALID_FORMAT)
        }

        // 整数部分至少一位数字
        val integerStart = index
        while (index < trimmed.length && trimmed[index] in '0'..'9') index++
        val integerDigits = index - integerStart
        if (integerDigits == 0) return AmountParseResult.Failure(AmountParseFailure.INVALID_FORMAT)

        // 可选的小数部分：`.` 后必须有 1～2 位数字
        var fractionDigits = 0
        if (index < trimmed.length && trimmed[index] == '.') {
            index++
            val fractionStart = index
            while (index < trimmed.length && trimmed[index] in '0'..'9') index++
            fractionDigits = index - fractionStart
            if (fractionDigits == 0) return AmountParseResult.Failure(AmountParseFailure.INVALID_FORMAT)
            if (fractionDigits > 2) return AmountParseResult.Failure(AmountParseFailure.TOO_MANY_DECIMALS)
        }

        // 仍有剩余字符 → 千位分隔符、指数记法、内部空白、第二个小数点等
        if (index != trimmed.length) return AmountParseResult.Failure(AmountParseFailure.INVALID_FORMAT)

        // 先用位数兜住溢出，再逐位累加
        if (integerDigits > 9) return AmountParseResult.Failure(AmountParseFailure.OUT_OF_RANGE)

        var minor = 0L
        for (position in integerStart until integerStart + integerDigits) {
            minor = minor * 10 + (trimmed[position] - '0')
        }
        minor *= Money.MINOR_PER_YUAN
        if (fractionDigits == 1) {
            minor += (trimmed[index - 1] - '0') * 10
        } else if (fractionDigits == 2) {
            minor += (trimmed[index - 2] - '0') * 10 + (trimmed[index - 1] - '0')
        }
        if (negative) minor = -minor

        if (!Money.isWithinRange(minor)) return AmountParseResult.Failure(AmountParseFailure.OUT_OF_RANGE)
        return AmountParseResult.Success(minor)
    }
}
