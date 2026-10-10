package com.manote.app.domain

/**
 * 纯输入的格式与取值校验（规格 2.1、2.2）。
 *
 * 这里只做与数据库无关的判断，因此同一份规则既能给 UI 做实时提示，
 * 也能在 Repository 事务内重新执行（规格 5.1：不用 UI 旧状态代替事务验证）。
 */
object SessionValidator {

    /** 标题：去除首尾空白；空白时用默认标题；超过 30 字报错。 */
    fun normalizeTitle(raw: String): ManoteResult<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ManoteResult.Success(SessionRules.DEFAULT_TITLE)
        if (SessionRules.textLength(trimmed) > SessionRules.MAX_TITLE_LENGTH) {
            return ManoteResult.Failure(ManoteError.INVALID_TITLE)
        }
        return ManoteResult.Success(trimmed)
    }

    /** 备注：去除首尾空白，可为空；超过 100 字报错。 */
    fun normalizeNote(raw: String): ManoteResult<String> {
        val trimmed = raw.trim()
        if (SessionRules.textLength(trimmed) > SessionRules.MAX_NOTE_LENGTH) {
            return ManoteResult.Failure(ManoteError.INVALID_NOTE)
        }
        return ManoteResult.Success(trimmed)
    }

    /** 麻友昵称：去除首尾空白，1～12 字。 */
    fun normalizeNickname(raw: String): ManoteResult<String> {
        val trimmed = raw.trim()
        val length = SessionRules.textLength(trimmed)
        if (length < SessionRules.MIN_NICKNAME_LENGTH || length > SessionRules.MAX_NICKNAME_LENGTH) {
            return ManoteResult.Failure(ManoteError.INVALID_NICKNAME)
        }
        return ManoteResult.Success(trimmed)
    }

    /** 打完时间不得晚于提交时的设备当前时间，允许补录过去（规格 2.1）。 */
    fun validatePlayedAt(playedAt: Long, now: Long): ManoteError? =
        if (playedAt > now) ManoteError.PLAYED_AT_IN_FUTURE else null

    /**
     * 校验四个座位的净金额：座位必须恰为 0/1/2/3，各自在允许范围内，且合计严格为 0 分。
     * 不做任何「凑平」处理（规格 2.1）。
     */
    fun validateSeatNets(netsBySeat: Map<Int, Long>): ManoteError? {
        val expectedSeats = (0 until SessionRules.SEAT_COUNT).toSet()
        if (netsBySeat.keys != expectedSeats) return ManoteError.INVALID_SEAT_SET
        if (netsBySeat.values.any { !Money.isWithinRange(it) }) return ManoteError.AMOUNT_OUT_OF_RANGE
        if (netsBySeat.values.sum() != 0L) return ManoteError.SUM_NOT_ZERO
        return null
    }

    /** 四人合计（分），用于底部实时提示。 */
    fun totalMinor(netsBySeat: Map<Int, Long>): Long = netsBySeat.values.sum()
}
