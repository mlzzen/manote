package com.manote.app.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.manoteTextFieldColors
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.model.UiText

/**
 * 「记一场 / 修改本场」表单里的**时间部分**：修改模式的说明条，以及结束时间 + 时长。
 *
 * 从 `SessionEditScreen.kt` 里拆出来：这个文件原先近九百行，
 * 时间、座位、合计、对话框四类互不相干的内容挤在一处，改动其中一类都要在一屏之外找引用。
 * 拆开之后每块各管一件事，`SessionEditScreen` 只负责把它们拼起来。
 */

/** 修改模式的说明条：明确告诉用户人员与位置改不了（规格 3.3）。 */
@Composable
internal fun RosterLockedNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.session_edit_roster_locked),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 结束时间与时长（规格 1.3、3.2）。
 *
 * 只填结束时间是刻意的——打完这一刻是确定的，开始时间靠时长倒推，
 * 用户不必为了记一场去回忆几点开局。推算结果直接写在下面，
 * 避免「为什么这一场被算成下午场」变成一个看不见的黑箱。
 */
@Composable
internal fun PlayedAtRow(
    playedAt: Long,
    durationText: String,
    durationError: Int?,
    durationHint: String?,
    onPickTime: () -> Unit,
    onDurationChange: (String) -> Unit,
) {
    ManoteCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onPickTime)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.session_played_at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = UiText.dateTime(playedAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = durationText,
            onValueChange = onDurationChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = durationError != null,
            label = { Text(text = stringResource(R.string.session_duration_label)) },
            placeholder = { Text(text = stringResource(R.string.session_duration_hint)) },
            suffix = { Text(text = stringResource(R.string.session_duration_unit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            colors = manoteTextFieldColors(),
        )

        when {
            durationError != null -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(durationError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            durationHint != null -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = durationHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
