package com.manote.app.domain

/**
 * 单个座位的输入 → 带符号净额。
 *
 * 界面用「赢 / 输」切换配合非负金额输入，最终必须还原成带符号的整数分。
 * 零不受赢/输切换影响（规格 2.1），否则会出现「输 0.00 元」这种自相矛盾的表述。
 */
object SeatEntry {

    fun signedMinor(absoluteMinor: Long, isWin: Boolean): Long = when {
        absoluteMinor == 0L -> 0L
        isWin -> absoluteMinor
        else -> -absoluteMinor
    }
}
