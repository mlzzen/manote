package com.manote.app.ui.model

import androidx.annotation.StringRes
import com.manote.app.R
import com.manote.app.domain.AmountParseFailure
import com.manote.app.domain.BackupRejectReason
import com.manote.app.domain.DurationParseFailure
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
    ManoteError.INVALID_DURATION -> R.string.error_invalid_duration
    ManoteError.INVALID_SEAT_SET -> R.string.error_invalid_seat_set
    ManoteError.DUPLICATE_PLAYER -> R.string.error_duplicate_player
    ManoteError.SEAT_ZERO_NOT_SELF -> R.string.error_seat_zero_not_self
    ManoteError.NON_SELF_SEAT_NOT_FRIEND -> R.string.error_non_self_seat_not_friend
    ManoteError.FRIEND_ARCHIVED -> R.string.error_friend_archived
    ManoteError.AMOUNT_OUT_OF_RANGE -> R.string.error_amount_out_of_range
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

/** 时长解析失败原因 → 提示文案。 */
@StringRes
fun DurationParseFailure.messageRes(): Int = when (this) {
    DurationParseFailure.EMPTY -> R.string.duration_empty
    DurationParseFailure.INVALID_FORMAT -> R.string.duration_invalid
    DurationParseFailure.TOO_MANY_DECIMALS -> R.string.duration_too_many_decimals
    DurationParseFailure.OUT_OF_RANGE -> R.string.duration_out_of_range
}

/**
 * 备份被拒绝的原因 → 提示文案。
 *
 * 与错误码一样用穷尽 `when`：新增拒绝原因会编译报错，不会出现「导入失败但说不出为什么」。
 */
@StringRes
fun BackupRejectReason.messageRes(): Int = when (this) {
    BackupRejectReason.UNSUPPORTED_VERSION -> R.string.backup_reject_unsupported_version
    BackupRejectReason.TOO_LARGE -> R.string.backup_reject_too_large
    BackupRejectReason.MALFORMED -> R.string.backup_reject_malformed
    BackupRejectReason.INVALID_SELF -> R.string.backup_reject_invalid_self
    BackupRejectReason.INVALID_ID -> R.string.backup_reject_invalid_id
    BackupRejectReason.ORPHAN_REFERENCE -> R.string.backup_reject_orphan_reference
    BackupRejectReason.INCOMPLETE_SEATS -> R.string.backup_reject_incomplete_seats
    BackupRejectReason.KIND_MISMATCH -> R.string.backup_reject_kind_mismatch
    BackupRejectReason.AMOUNT_OUT_OF_RANGE -> R.string.backup_reject_amount_out_of_range
    BackupRejectReason.INVALID_TEXT -> R.string.backup_reject_invalid_text
    BackupRejectReason.INVALID_TIME -> R.string.backup_reject_invalid_time
    BackupRejectReason.INVALID_DURATION -> R.string.backup_reject_invalid_duration
    BackupRejectReason.INVALID_REVISION -> R.string.backup_reject_invalid_revision
    BackupRejectReason.LIMIT_EXCEEDED -> R.string.backup_reject_limit_exceeded
}
