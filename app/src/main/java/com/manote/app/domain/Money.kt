package com.manote.app.domain

/**
 * 金额换算与显示。
 *
 * 金额一律以整数「分」保存和计算，禁止 Float/Double 中转（规格 2.1）。
 */
object Money {

    /** 1 元 = 100 分。 */
    const val MINOR_PER_YUAN = 100L

    /** 单人单场净金额范围：±1,000,000 分，即 ±10,000.00 元，含端点。 */
    const val MAX_NET_MINOR = 1_000_000L
    const val MIN_NET_MINOR = -MAX_NET_MINOR

    /**
     * 转成两位小数的元，例如 1230 -> "12.30"、-500 -> "-5.00"、0 -> "0.00"。
     * 用于表格与详情，不带正号。
     */
    fun formatMinor(minor: Long): String {
        val negative = minor < 0
        val absolute = if (negative) -minor else minor
        val yuan = absolute / MINOR_PER_YUAN
        val cents = absolute % MINOR_PER_YUAN
        return buildString {
            if (negative) append('-')
            append(yuan)
            append('.')
            append(cents.toString().padStart(2, '0'))
        }
    }

    /**
     * 带符号的元，例如 1230 -> "+12.30"、-500 -> "-5.00"、0 -> "0.00"。
     * 用于四人合计提示。
     */
    fun formatSigned(minor: Long): String =
        if (minor > 0) "+${formatMinor(minor)}" else formatMinor(minor)

    /**
     * 文字化输赢（规格 2.1）：正数「赢 12.30 元」、负数「输 5.00 元」、零「持平 0.00 元」。
     * 输赢同时用文字表达，不能只依赖颜色。
     */
    fun describe(minor: Long): String = when {
        minor > 0 -> "赢 ${formatMinor(minor)} 元"
        minor < 0 -> "输 ${formatMinor(-minor)} 元"
        else -> "持平 ${formatMinor(0)} 元"
    }

    /** 该净金额是否落在允许范围内（含端点）。 */
    fun isWithinRange(minor: Long): Boolean = minor in MIN_NET_MINOR..MAX_NET_MINOR

    /** 赢、输、持平的中性表述，用于列表与导出。 */
    fun outcomeLabel(minor: Long): String = when {
        minor > 0 -> "赢"
        minor < 0 -> "输"
        else -> "持平"
    }
}
