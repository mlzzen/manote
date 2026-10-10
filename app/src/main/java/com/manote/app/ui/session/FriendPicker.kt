package com.manote.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.model.UiText
import kotlinx.coroutines.delay

/**
 * 麻友选择器（规格 3.2）。
 *
 * 支持搜索昵称 / 备注、选择已有档案、或即时新建。
 * 已被其它座位选走的牌友会置灰，保证一场不会重复选到同一个人；
 * 当前座位已选的那位不算「被占」，可以重复点选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendPickerSheet(
    forSeat: Int,
    options: List<FriendOptionUi>,
    onDismiss: () -> Unit,
    onPick: (FriendOptionUi) -> Unit,
    onCreateNew: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filtered = remember(options, query) {
        if (query.isBlank()) {
            options
        } else {
            options.filter {
                it.nickname.contains(query, ignoreCase = true) ||
                    it.note.contains(query, ignoreCase = true)
            }
        }
    }

    // 同名档案同时出现时补一个 ID 短串，否则两行看起来一模一样（规格 3.4）
    val ambiguousNames = remember(options) {
        options.groupingBy { it.nickname }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.friend_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.friend_picker_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                shape = MaterialTheme.shapes.small,
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                options.isEmpty() -> PickerHint(text = stringResource(R.string.friend_picker_none))
                filtered.isEmpty() -> PickerHint(text = stringResource(R.string.friend_picker_empty))
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(items = filtered, key = { it.id }) { option ->
                            FriendOptionRow(
                                option = option,
                                forSeat = forSeat,
                                showId = option.nickname in ambiguousNames,
                                onPick = onPick,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ManotePrimaryButton(
                text = stringResource(R.string.friend_create_title),
                onClick = onCreateNew,
            )
        }
    }
}

@Composable
private fun PickerHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FriendOptionRow(
    option: FriendOptionUi,
    forSeat: Int,
    showId: Boolean,
    onPick: (FriendOptionUi) -> Unit,
) {
    val takenByOtherSeat = option.takenBySeat != null && option.takenBySeat != forSeat
    val subtitle = if (takenByOtherSeat) {
        stringResource(R.string.friend_selected_elsewhere)
    } else {
        // 备注 + （同名时）ID 短串，两者都没有就不占这一行
        listOfNotNull(
            option.note.takeIf { it.isNotBlank() },
            stringResource(R.string.friend_id_short_format, UiText.shortId(option.id))
                .takeIf { showId },
        ).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !takenByOtherSeat) { onPick(option) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(
            name = option.nickname,
            size = 40.dp,
            containerColor = if (takenByOtherSeat) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (takenByOtherSeat) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.nickname,
                style = MaterialTheme.typography.titleMedium,
                color = if (takenByOtherSeat) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 即时新建牌友（规格 2.2、3.2）。
 *
 * 同名时给出提示但不阻止：昵称允许重名，统计按 ID 分开。
 * 新建成功由调用方自动选中到当前座位。
 */
@Composable
fun CreateFriendDialog(
    countSameNickname: suspend (String) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (nickname: String, note: String) -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var duplicateCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(nickname) {
        if (nickname.isBlank()) {
            duplicateCount = 0
            return@LaunchedEffect
        }
        // 输入抖动期间不查库；键变化会取消上一个协程
        delay(150)
        duplicateCount = countSameNickname(nickname)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.friend_create_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.friend_nickname_label)) },
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.friend_note_label)) },
                    placeholder = { Text(text = stringResource(R.string.friend_note_hint)) },
                    shape = MaterialTheme.shapes.small,
                )
                if (duplicateCount > 0) {
                    Text(
                        text = stringResource(R.string.friend_duplicate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nickname.isNotBlank(),
                onClick = { onConfirm(nickname, note) },
            ) {
                Text(text = stringResource(R.string.friend_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
