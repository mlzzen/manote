package com.manote.app.domain

/**
 * 纯输入的格式与取值校验（规格 1.3、2.1、2.2）。
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

    /** 结束时间不得晚于提交时的设备当前时间，允许补录过去（规格 2.1）。 */
    fun validatePlayedAt(playedAt: Long, now: Long): ManoteError? =
        if (playedAt > now) ManoteError.PLAYED_AT_IN_FUTURE else null

    /**
     * 时长必须在 0.5～24 小时之间（且为 0.1 小时的整数倍）。
     *
     * 用户留空时界面会先补上默认值 4 小时再提交，**不会**把「没填」当成 0；
     * 因此这里对 0 一律判为非法，避免一场 0 小时的记录溜进库里。
     */
    fun validateDuration(minutes: Int): ManoteError? =
        if (SessionDuration.isValid(minutes)) null else ManoteError.INVALID_DURATION

    /**
     * 校验座位集合（规格 1.3、5.1）：非空、都落在 0～3 之内，且必须包含 seat 0。
     *
     * **不再要求四个位置齐全**：一场可以只记录 1～4 个位置，
     * 例如只知道自己和上家的输赢时，就不必为另外两位编造数字。
     */
    fun validateSeatSet(seats: Set<Int>): ManoteError? {
        val allowed = (0 until SessionRules.SEAT_COUNT).toSet()
        if (seats.isEmpty()) return ManoteError.INVALID_SEAT_SET
        if (!allowed.containsAll(seats)) return ManoteError.INVALID_SEAT_SET
        if (SessionRules.SEAT_SELF !in seats) return ManoteError.INVALID_SEAT_SET
        return null
    }

    /**
     * 校验各已记录座位的净金额：座位集合合法，且每个金额都在允许范围内。
     *
     * **合计不再要求为 0**（规格 1.3）：可以只记一部分人的输赢，因此合计不为零是正常情况，
     * 由界面提示而不阻拦保存。这里同样不做任何「凑平」处理。
     */
    fun validateSeatNets(netsBySeat: Map<Int, Long>): ManoteError? {
        validateSeatSet(netsBySeat.keys)?.let { return it }
        if (netsBySeat.values.any { !Money.isWithinRange(it) }) return ManoteError.AMOUNT_OUT_OF_RANGE
        return null
    }

    /** 已记录座位的合计（分），用于底部实时提示。 */
    fun totalMinor(netsBySeat: Map<Int, Long>): Long = netsBySeat.values.sum()
}
