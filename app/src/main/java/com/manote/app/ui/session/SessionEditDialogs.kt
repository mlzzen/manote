package com.manote.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.SessionRules
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.model.sessionTimeRange
import com.manote.app.ui.theme.manote

/**
 * 表单上两个对话框：提交前预览、有改动时确认放弃。
 *
 * 从 `SessionEditScreen.kt` 里拆出来，让主文件只保留「页面骨架 + 状态接线」。
 */

/** 提交前预览（规格 3.2）；全零时在同一处追加确认语，不弹第二个对话框。 */
@Composable
internal fun SessionPreviewDialog(
    state: SessionEditUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 时间与时长也在这里复述一遍：确认框是最后一次看清单的机会，
                // 时段是按开始时间判定的，只写结束时间说不清它会被归到哪一段
                Text(
                    text = sessionTimeRange(
                        startedAt = SessionDuration.startAt(
                            state.playedAt,
                            state.effectiveDurationMinutes,
                        ),
                        playedAt = state.playedAt,
                        durationMinutes = state.effectiveDurationMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 只列出本场实际记录的位置；没选牌友的座位不入库，也就没必要在这里出现
                state.seats.filter { it.playerId != null }.forEach { draft ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = draft.nickname,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = seatLabel(draft.seat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SignedAmount(
                            netMinor = state.signedBySeat[draft.seat] ?: 0L,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                if (state.allZero) {
                    Text(
                        text = stringResource(R.string.session_all_zero_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 合计不为 0 是可保存的（规格 1.3），但保存前再提醒一次，避免看漏
                if (state.unbalanced) {
                    Text(
                        text = stringResource(
                            R.string.session_preview_unbalanced,
                            UiText.signedYuan(state.totalMinor ?: 0L),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.manote.draw,
                    )
                }

                // 只记录了部分位置时说明清楚，免得以为另外几位本场是 0
                if (state.recordedSeatCount < SessionRules.SEAT_COUNT) {
                    Text(
                        text = stringResource(
                            R.string.session_preview_partial_format,
                            state.recordedSeatCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.session_preview_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun DiscardDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_discard_title)) },
        text = { Text(text = stringResource(R.string.session_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.session_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.session_discard_stay))
            }
        },
    )
}
