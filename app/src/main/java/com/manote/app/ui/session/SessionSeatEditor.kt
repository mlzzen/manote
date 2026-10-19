package com.manote.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.manoteTextFieldColors
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.model.seatLabel
import com.manote.app.ui.theme.manote

/**
 * 一个座位的编辑卡片，以及它右上角的「赢 / 输」开关。
 *
 * 从 `SessionEditScreen.kt` 里拆出来：座位卡片是三块内容里最重的一块
 * （选人、切输赢、填金额、焦点接力），单独成文件后改它不必翻过时间与对话框。
 */

@Composable
internal fun SeatEditor(
    draft: SeatDraft,
    errorRes: Int?,
    rosterLocked: Boolean,
    /** 刚刚选好这位牌友：金额框该自动获得焦点。 */
    requestAmountFocus: Boolean,
    /** 焦点已送过去，父级可以把标记清掉，免得下次再选同一个人时不再触发。 */
    onAmountFocusRequested: () -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onWinChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val selected = draft.playerId != null
    // 没选牌友的位置本场不记录：不显示金额与赢输开关，
    // 否则用户填完才发现被忽略（规格 1.3）
    val recorded = selected || draft.isSelfSeat

    // 点「赢 / 输」之后几乎一定紧接着要填金额，这里直接把焦点送过去，
    // 省掉「点开关 → 再点输入框」这一步。
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    ManoteCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                PlayerAvatar(
                    name = draft.nickname,
                    size = 42.dp,
                    containerColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (draft.isSelfSeat) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            } else {
                // 空位用品牌色的浅底 + 主色加号，而不是一块中性灰：
                // 四个位置里还没填的那几个应当是「等你来点」，不是「不可用」
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 修改模式人员只读，因此这里不再挂点击事件
            val clickable = !draft.isSelfSeat && !rosterLocked
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (clickable) Modifier.clickable(onClick = onPick) else Modifier),
            ) {
                Text(
                    text = if (selected) draft.nickname else stringResource(R.string.session_pick_friend),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ManotePill(
                    text = if (recorded) {
                        seatLabel(draft.seat)
                    } else {
                        stringResource(R.string.session_seat_not_recorded, seatLabel(draft.seat))
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    horizontalPadding = 6.dp,
                    verticalPadding = 1.dp,
                )
            }

            if (!draft.isSelfSeat && selected && !rosterLocked) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recorded) {
                WinLoseToggle(
                    isWin = draft.isWin,
                    onWinChange = { win ->
                        onWinChange(win)
                        // 输入框在同一个卡片里且恒为已组合状态，这里聚焦不会落空；
                        // 键盘再显式唤一次，避免个别机型聚焦后不弹键盘。
                        amountFocus.requestFocus()
                        keyboard?.show()
                    },
                )
            }
        }

        if (!recorded) return@ManoteCard

        // 选好牌友之后把焦点直接送到金额框：这一步紧接着就是填数字，不该再让用户点一次。
        //
        // 焦点请求必须放在这里、而不是选人那一刻：座位从「未记录」变成「已记录」，
        // 金额框是这一步才被组合出来的，选人的瞬间它还不存在，`requestFocus()` 会落空。
        // 依赖 recorded 也顺带覆盖了「新建牌友」——那位牌友是异步建好再选上的，
        // 标记先留着，等座位真的记上了再抢焦点。
        LaunchedEffect(requestAmountFocus, recorded) {
            if (requestAmountFocus && recorded) {
                amountFocus.requestFocus()
                keyboard?.show()
                onAmountFocusRequested()
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.amountText,
            onValueChange = onAmountChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(amountFocus),
            singleLine = true,
            isError = errorRes != null,
            placeholder = { Text(text = stringResource(R.string.session_amount_hint)) },
            suffix = { Text(text = stringResource(R.string.common_amount_unit)) },
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            colors = manoteTextFieldColors(),
        )

        if (errorRes != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 赢 / 输 切换。金额为 0 时不影响结果（规格 2.1）。 */
@Composable
private fun WinLoseToggle(isWin: Boolean, onWinChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.manote
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp),
    ) {
        ToggleHalf(
            label = stringResource(R.string.result_win),
            selected = isWin,
            selectedColor = colors.win,
            onClick = { onWinChange(true) },
        )
        ToggleHalf(
            label = stringResource(R.string.result_loss),
            selected = !isWin,
            selectedColor = colors.loss,
            onClick = { onWinChange(false) },
        )
    }
}

@Composable
private fun ToggleHalf(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = if (selected) selectedColor else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(34.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
