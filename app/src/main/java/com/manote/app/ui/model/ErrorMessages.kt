package com.manote.app.ui.model

import androidx.annotation.StringRes
import com.manote.app.R
import com.manote.app.domain.AmountParseFailure
import com.manote.app.domain.ManoteError

/**
 * 错误码 → 提示文案。
 *
 * 界面只按键取文案，不把错误码直接暴露给用户；每个枚举都必须有对应文案，
 * 因此这里用穷尽 `when`（新增错误码时编译期即报错，不会静默漏提示）。
 */
@StringRes
fun ManoteError.messageRes(): Int = when (this) {
    ManoteError.INVALID_NICKNAME -> R.string.error_invalid_nickname
    ManoteError.INVALID_NOTE -> R.string.error_invalid_note
    ManoteError.INVALID_TITLE -> R.string.error_invalid_title
    ManoteError.PLAYED_AT_IN_FUTURE -> R.string.error_played_at_in_future
    ManoteError.INVALID_SEAT_SET -> R.string.error_invalid_seat_set
    ManoteError.DUPLICATE_PLAYER -> R.string.error_duplicate_player
    ManoteError.SEAT_ZERO_NOT_SELF -> R.string.error_seat_zero_not_self
    ManoteError.NON_SELF_SEAT_NOT_FRIEND -> R.string.error_non_self_seat_not_friend
    ManoteError.FRIEND_ARCHIVED -> R.string.error_friend_archived
    ManoteError.AMOUNT_OUT_OF_RANGE -> R.string.error_amount_out_of_range
    ManoteError.SUM_NOT_ZERO -> R.string.error_sum_not_zero
    ManoteError.SESSION_LIMIT_REACHED -> R.string.error_session_limit_reached
    ManoteError.FRIEND_LIMIT_REACHED -> R.string.error_friend_limit_reached
    ManoteError.SESSION_ID_ALREADY_EXISTS -> R.string.error_session_id_already_exists
    ManoteError.SESSION_NOT_FOUND -> R.string.error_session_not_found
    ManoteError.SESSION_REVISION_CONFLICT -> R.string.error_session_revision_conflict
    ManoteError.PLAYER_NOT_FOUND -> R.string.error_player_not_found
    ManoteError.PLAYER_IS_SELF -> R.string.error_player_is_self
    ManoteError.PLAYER_KIND_MISMATCH -> R.string.error_player_kind_mismatch
    ManoteError.INTERNAL_STATE_CORRUPTED -> R.string.error_internal_state_corrupted
}

/** 金额解析失败原因 → 提示文案。 */
@StringRes
fun AmountParseFailure.messageRes(): Int = when (this) {
    AmountParseFailure.EMPTY -> R.string.amount_empty
    AmountParseFailure.INVALID_FORMAT -> R.string.amount_invalid
    AmountParseFailure.TOO_MANY_DECIMALS -> R.string.amount_too_many_decimals
    AmountParseFailure.OUT_OF_RANGE -> R.string.amount_out_of_range
}
