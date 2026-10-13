package com.manote.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote

/**
 * 底部合计与保存（规格 1.3）。
 *
 * 合计不为 0 时只用琥珀色提示，**不再禁用保存**：允许只记一部分人的输赢。
 * 真正会挡住保存的只有「金额填错」这类笔误，以及没有可记录的位置。
 *
 * 从 `SessionEditScreen.kt` 里拆出来：它是唯一挂在底栏上的东西，
 * 与表单主体没有共享状态，只是读同一份 [SessionEditUiState]。
 */
@Composable
internal fun SaveBar(state: SessionEditUiState, onSave: () -> Unit) {
    val total = state.totalMinor
    val canSave = state.amountsValid && !state.saving
    val message: String
    val messageColor: Color

    when {
        !state.amountsValid -> {
            message = stringResource(R.string.session_total_incomplete)
            messageColor = MaterialTheme.colorScheme.error
        }

        total == 0L -> {
            message = stringResource(R.string.session_total_ok)
            messageColor = MaterialTheme.colorScheme.onSurfaceVariant
        }

        else -> {
            message = stringResource(R.string.session_total_warning, UiText.signedYuan(total ?: 0L))
            messageColor = MaterialTheme.manote.draw
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.session_total_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (total != null) {
                    SignedAmount(netMinor = total, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(
                        text = stringResource(R.string.common_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = messageColor,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ManotePrimaryButton(
                text = stringResource(
                    when {
                        state.saving -> R.string.session_saving
                        state.isEditing -> R.string.session_edit_save
                        else -> R.string.session_save
                    },
                ),
                onClick = onSave,
                enabled = canSave,
            )
        }
    }
}
