package com.manote.app.ui.model

/**
 * 金额输入过滤：只保留数字与至多一个小数点。
 *
 * 刻意**不做**自动截断小数位——`1.001` 应当由 [com.manote.app.domain.AmountParser]
 * 明确拒绝并给出提示，而不是被悄悄改成 `1.00`（规格 2.1 把 `1.001` 列为必须拒绝的输入）。
 * 纯函数，便于单元测试。
 */
fun filterAmountInput(raw: String): String {
    val builder = StringBuilder()
    var dotUsed = false
    for (ch in raw) {
        when {
            ch in '0'..'9' -> builder.append(ch)
            ch == '.' && !dotUsed -> {
                dotUsed = true
                builder.append(ch)
            }
        }
    }
    return builder.toString()
}
