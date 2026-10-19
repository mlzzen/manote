package com.manote.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.components.ManoteFilterChip
import com.manote.app.ui.components.ManotePrimaryButton
import com.manote.app.ui.model.SessionFilters
import com.manote.app.ui.model.SessionFriendOption
import com.manote.app.ui.model.SessionOutcome
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.timeSlotLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 场次筛选弹层。
 *
 * 六个维度按「先想哪一件」的顺序排：什么时候打的（时段 / 年 / 月 / 日）、
 * 和谁打的、赢还是输。每组的第一个胶囊都是「不限」，于是「这一组我正在筛什么」
 * 与「怎么撤回这一组」写在同一个位置上。
 *
 * **不设「确定」草稿态**：每点一下都直接改到上层条件里，列表在弹层后面同步变化，
 * 底部那颗按钮上的场次数就是结果本身。这样既不会出现「选了却没生效」，
 * 也不会出现「关掉弹层后忘了刚才选的是什么」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionFilterSheet(
    filters: SessionFilters,
    yearOptions: List<Int>,
    friendOptions: List<SessionFriendOption>,
    resultCount: Int,
    onChange: (SessionFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SessionFilterContent(
            filters = filters,
            yearOptions = yearOptions,
            friendOptions = friendOptions,
            resultCount = resultCount,
            onChange = onChange,
            onReset = onReset,
            onDismiss = onDismiss,
            // 弹层自身不管左右留白；内容区统一 16dp，与其他页的卡片对齐
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 弹层里的全部内容。
 *
 * 与 [ModalBottomSheet] 解耦，是因为弹层外壳（拖拽、遮罩）没法在 JVM 上渲染，
 * 而这一块版式恰恰是最需要看一眼的：六组胶囊会不会把屏幕挤爆、底部按钮会不会被推下去。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionFilterContent(
    filters: SessionFilters,
    yearOptions: List<Int>,
    friendOptions: List<SessionFriendOption>,
    resultCount: Int,
    onChange: (SessionFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sessions_filter_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (filters.isActive) {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.sessions_filter_reset))
                }
            }
        }

        // 内容自己滚：六组胶囊在小屏上一屏放不下，而底部那颗按钮必须始终可见。
        // 上限按屏高算而不是写死 dp——常见的 860dp 屏能一次放下全部六组，
        // 小屏或横屏则退化成滚动；`heightIn` 会被父级约束收紧，放不下时自动让位。
        val scrollMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
        Column(
            modifier = Modifier
                .heightIn(max = scrollMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterSection(title = stringResource(R.string.sessions_filter_section_time_slot)) {
                FilterChipGroup(
                    anySelected = filters.timeSlots.isEmpty(),
                    onSelectAny = { onChange(filters.copy(timeSlots = emptySet())) },
                ) {
                    TimeSlot.displayOrder.forEach { slot ->
                        ManoteFilterChip(
                            label = timeSlotLabel(slot),
                            selected = slot in filters.timeSlots,
                            onClick = { onChange(filters.toggleTimeSlot(slot)) },
                        )
                    }
                }
            }

            FilterSection(title = stringResource(R.string.sessions_filter_section_year)) {
                FilterChipGroup(
                    anySelected = filters.year == null,
                    onSelectAny = { onChange(filters.withYear(null)) },
                ) {
                    yearOptions.forEach { year ->
                        ManoteFilterChip(
                            label = year.toString(),
                            selected = filters.year == year,
                            onClick = { onChange(filters.withYear(year)) },
                        )
                    }
                }
            }

            FilterSection(title = stringResource(R.string.sessions_filter_section_month)) {
                FilterChipGroup(
                    anySelected = filters.month == null,
                    onSelectAny = { onChange(filters.withMonth(null)) },
                ) {
                    (1..MONTHS_IN_YEAR).forEach { month ->
                        ManoteFilterChip(
                            label = stringResource(R.string.sessions_filter_month_format, month),
                            selected = filters.month == month,
                            onClick = { onChange(filters.withMonth(month)) },
                        )
                    }
                }
            }

            FilterSection(title = stringResource(R.string.sessions_filter_section_date)) {
                FilterChipGroup(
                    anySelected = filters.date == null,
                    onSelectAny = { onChange(filters.withDate(null)) },
                ) {
                    DateFilterChip(
                        date = filters.date,
                        onPick = { onChange(filters.withDate(it)) },
                    )
                }
            }

            FilterSection(title = stringResource(R.string.sessions_filter_section_friend)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterChipGroup(
                        anySelected = filters.friendIds.isEmpty(),
                        onSelectAny = { onChange(filters.copy(friendIds = emptySet())) },
                    ) {
                        friendOptions.forEach { option ->
                            ManoteFilterChip(
                                label = if (option.showId) {
                                    option.nickname + stringResource(
                                        R.string.sessions_filter_friend_id_format,
                                        UiText.shortId(option.id),
                                    )
                                } else {
                                    option.nickname
                                },
                                selected = option.id in filters.friendIds,
                                onClick = { onChange(filters.toggleFriend(option.id)) },
                            )
                        }
                    }
                    if (friendOptions.isEmpty()) {
                        // 还没同桌过任何人时，这一组只有「不限」：说明白为什么空，比留一片白好
                        Text(
                            text = stringResource(R.string.sessions_filter_friend_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            FilterSection(title = stringResource(R.string.sessions_filter_section_result)) {
                FilterChipGroup(
                    anySelected = filters.outcomes.isEmpty(),
                    onSelectAny = { onChange(filters.copy(outcomes = emptySet())) },
                ) {
                    SessionOutcome.entries.forEach { outcome ->
                        ManoteFilterChip(
                            label = stringResource(outcome.labelRes()),
                            selected = outcome in filters.outcomes,
                            onClick = { onChange(filters.toggleOutcome(outcome)) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ManotePrimaryButton(
            text = stringResource(R.string.sessions_filter_show_format, resultCount),
            onClick = onDismiss,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 一组筛选：一行小标题，下面是一片可以换行的胶囊。 */
@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        content()
    }
}

/**
 * 胶囊组：「不限」永远排第一个。
 *
 * 组内多选，因此 [content] 里每颗胶囊自己决定选中态；
 * 「不限」的选中态由调用方按「这一组是否为空」算好传进来——
 * 空集合就是不限，不需要再存一个「全部」的枚举值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipGroup(
    anySelected: Boolean,
    onSelectAny: () -> Unit,
    content: @Composable () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ManoteFilterChip(
            label = stringResource(R.string.sessions_filter_any),
            selected = anySelected,
            onClick = onSelectAny,
        )
        content()
    }
}

/**
 * 「某一天」胶囊：没选时是一句提示，选了就显示那一天，再点可以改。
 *
 * 日期用系统日历弹窗挑，与「记一场」里的五列滚轮不是同一个控件——
 * 那里要同时选时分，这里只是纯日期，点日历格比滚五列快得多。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChip(
    date: LocalDate?,
    onPick: (LocalDate?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    ManoteFilterChip(
        label = if (date == null) {
            stringResource(R.string.sessions_filter_pick_date)
        } else {
            // 与列表里的日期同一格式（2026.10.05），两处读起来对得上
            UiText.date(date)
        },
        selected = date != null,
        onClick = { picking = true },
    )

    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 日历给的是「那一天的 UTC 零点」，按 UTC 取回年月日才不会偏移一天
                        pickerState.selectedDateMillis?.let { millis ->
                            onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        picking = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun SessionOutcome.labelRes(): Int = when (this) {
    SessionOutcome.WIN -> R.string.result_win
    SessionOutcome.LOSS -> R.string.result_loss
    SessionOutcome.DRAW -> R.string.result_draw
}

private const val MONTHS_IN_YEAR = 12
