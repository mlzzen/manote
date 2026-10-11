package com.manote.app.domain

/** 时长文本解析失败的原因，供 UI 给出针对性提示。 */
enum class DurationParseFailure {
    /** 空串或只有空白。 */
    EMPTY,

    /** 格式非法：单独小数点、`4.`、负数、非数字字符、内部空白等。 */
    INVALID_FORMAT,

    /** 小数点后超过一位（时长只到 0.1 小时）。 */
    TOO_MANY_DECIMALS,

    /** 超出 0.5～24 小时。 */
    OUT_OF_RANGE,
}

sealed interface DurationParseResult {
    data class Success(val minutes: Int) : DurationParseResult
    data class Failure(val reason: DurationParseFailure) : DurationParseResult
}

/**
 * 「小时」文本 → 整数分钟。
 *
 * 接受：`4`、`4.5`、`0.5`；两端空白会被去除。
 * 拒绝：空值、单独小数点、`4.`、`4.55`、负数、指数记法、非数字字符、超出 0.5～24 小时。
 *
 * 与 [AmountParser] 同样手工解析为 Int，不经过 Float/Double：
 * 0.1 小时恰好是 6 分钟，用浮点会引入 `4.2 × 60 = 251.999…` 这类误差。
 */
object DurationParser {

    fun parse(text: String): DurationParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return DurationParseResult.Failure(DurationParseFailure.EMPTY)

        var index = 0
        val integerStart = index
        while (index < trimmed.length && trimmed[index] in '0'..'9') index++
        val integerDigits = index - integerStart
        if (integerDigits == 0) {
            // 负号、小数点开头、字母开头都落在这里
            return DurationParseResult.Failure(DurationParseFailure.INVALID_FORMAT)
        }

        // 可选的小数部分：`.` 后必须有数字，且最多一位
        var tenths = 0
        if (index < trimmed.length && trimmed[index] == '.') {
            index++
            val fractionStart = index
            while (index < trimmed.length && trimmed[index] in '0'..'9') index++
            val fractionDigits = index - fractionStart
            if (fractionDigits == 0) return DurationParseResult.Failure(DurationParseFailure.INVALID_FORMAT)
            if (fractionDigits > 1) return DurationParseResult.Failure(DurationParseFailure.TOO_MANY_DECIMALS)
            tenths = trimmed[fractionStart] - '0'
        }

        // 仍有剩余字符 → 内部空白、第二个小数点、单位后缀等
        if (index != trimmed.length) {
            return DurationParseResult.Failure(DurationParseFailure.INVALID_FORMAT)
        }

        // 先用位数兜住溢出，再逐位累加
        if (integerDigits > 3) return DurationParseResult.Failure(DurationParseFailure.OUT_OF_RANGE)

        var hours = 0
        for (position in integerStart until integerStart + integerDigits) {
            hours = hours * 10 + (trimmed[position] - '0')
        }
        val minutes = hours * SessionDuration.MINUTES_PER_HOUR +
            tenths * SessionDuration.MINUTES_PER_TENTH_HOUR

        if (!SessionDuration.isValid(minutes)) {
            return DurationParseResult.Failure(DurationParseFailure.OUT_OF_RANGE)
        }
        return DurationParseResult.Success(minutes)
    }
}
