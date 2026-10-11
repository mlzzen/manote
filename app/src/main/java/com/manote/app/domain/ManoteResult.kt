package com.manote.app.domain

/**
 * 业务错误码。全部为「可预期」的失败，调用方（UI）按码给出提示，
 * 不使用异常控制流程，也不静默吞掉失败。
 */
enum class ManoteError {
    /** 昵称去除首尾空白后不在 1～12 字。 */
    INVALID_NICKNAME,

    /** 备注超过 100 字。 */
    INVALID_NOTE,

    /** 标题超过 30 字。 */
    INVALID_TITLE,

    /** 结束时间晚于提交时的设备当前时间。 */
    PLAYED_AT_IN_FUTURE,

    /** 时长不在 0.5～24 小时之间，或不是 6 分钟（0.1 小时）的整数倍。 */
    INVALID_DURATION,

    /** 座位集合不合法：为空、越出 0～3，或缺少本人所在的 seat 0（规格 1.3）。 */
    INVALID_SEAT_SET,

    /** 一场中同一个 playerId 出现在多个座位。 */
    DUPLICATE_PLAYER,

    /** seat=0 关联的不是 SELF。 */
    SEAT_ZERO_NOT_SELF,

    /** seat=1/2/3 关联的不是 FRIEND。 */
    NON_SELF_SEAT_NOT_FRIEND,

    /** 为新场次选择了已归档的麻友。 */
    FRIEND_ARCHIVED,

    /** 单个净金额超出 ±1,000,000 分。 */
    AMOUNT_OUT_OF_RANGE,

    /** 已达到 1,000 场上限。 */
    SESSION_LIMIT_REACHED,

    /** 已达到 5,000 个麻友上限（含归档）。 */
    FRIEND_LIMIT_REACHED,

    /** 该 Session ID 已存在，不再插入第二场。 */
    SESSION_ID_ALREADY_EXISTS,

    /** 场次不存在（已被删除）。 */
    SESSION_NOT_FOUND,

    /** 携带的 revision 与库中不一致，拒绝覆盖。 */
    SESSION_REVISION_CONFLICT,

    /** 档案不存在。 */
    PLAYER_NOT_FOUND,

    /** 该档案是本人，不允许归档或删除。 */
    PLAYER_IS_SELF,

    /** 档案类型与操作要求不符。 */
    PLAYER_KIND_MISMATCH,

    /** 数据库自身处于不应出现的状态（例如 SELF 多于一个）。 */
    INTERNAL_STATE_CORRUPTED,
}

/**
 * 写操作的统一返回类型。成功携带结果，失败携带 [ManoteError]。
 */
sealed interface ManoteResult<out T> {

    data class Success<out T>(val value: T) : ManoteResult<T>

    data class Failure(val error: ManoteError) : ManoteResult<Nothing>
}

/** 成功时取值，失败时返回 null。 */
fun <T> ManoteResult<T>.valueOrNull(): T? = (this as? ManoteResult.Success)?.value

/** 失败时取错误码，成功时返回 null。 */
fun <T> ManoteResult<T>.errorOrNull(): ManoteError? = (this as? ManoteResult.Failure)?.error
