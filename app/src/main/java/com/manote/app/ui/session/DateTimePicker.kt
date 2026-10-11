package com.manote.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.model.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/** 滚轮单格高度。 */
private val WheelItemHeight: Dp = 40.dp

/**
 * 可见格数，必须是奇数，中间的即选中项。
 * 3 格既能看清上下各一项、又不会把对话框撑得过高。
 */
private const val WheelVisibleItems = 3

/**
 * 结束时间选择：年 / 月 / 日 / 时 / 分 五列上下滚轮（规格 3.2）。
 *
 * 默认停在传入的时间上；是否晚于当前时间由保存前的校验统一判定，
 * 这里不预先禁用未来，以免用户搞不清为什么滚不到。
 */
@Composable
fun PlayedAtPickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(zone) }
    val thisYear = remember(zone) { LocalDate.now(zone).year }

    // 允许往前翻几年补录旧账，也允许误选到明年的头一天（由校验拦下）；
    // 若原值落在这个区间外（例如导入的旧备份），把原值一并纳入。
    val years = remember(thisYear, initial.year) {
        (minOf(thisYear - 5, initial.year)..maxOf(thisYear + 1, initial.year)).toList()
    }

    var year by remember(initialMillis) { mutableIntStateOf(initial.year) }
    var month by remember(initialMillis) { mutableIntStateOf(initial.monthValue) }
    var day by remember(initialMillis) { mutableIntStateOf(initial.dayOfMonth) }
    var hour by remember(initialMillis) { mutableIntStateOf(initial.hour) }
    var minute by remember(initialMillis) { mutableIntStateOf(initial.minute) }

    val daysInMonth = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    // 从 31 天的月份滚到 30 天（或 2 月）的月份时，把超出的日期收回当月最后一天
    LaunchedEffect(daysInMonth) { if (day > daysInMonth) day = daysInMonth }

    val yearUnit = stringResource(R.string.session_time_unit_year)
    val monthUnit = stringResource(R.string.session_time_unit_month)
    val dayUnit = stringResource(R.string.session_time_unit_day)
    val hourUnit = stringResource(R.string.session_time_unit_hour)
    val minuteUnit = stringResource(R.string.session_time_unit_minute)

    val yearItems = remember(years, yearUnit) { years.map { "$it$yearUnit" } }
    val monthItems = remember(monthUnit) { (1..12).map { "$it$monthUnit" } }
    val dayItems = remember(daysInMonth, dayUnit) { (1..daysInMonth).map { "%02d%s".format(Locale.US, it, dayUnit) } }
    val hourItems = remember(hourUnit) { (0..23).map { "%02d%s".format(Locale.US, it, hourUnit) } }
    val minuteItems = remember(minuteUnit) { (0..59).map { "%02d%s".format(Locale.US, it, minuteUnit) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_time_pick_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WheelColumn(
                    items = yearItems,
                    selectedIndex = years.indexOf(year),
                    onSelected = { year = years[it] },
                    modifier = Modifier.weight(1.7f),
                )
                WheelColumn(
                    items = monthItems,
                    selectedIndex = month - 1,
                    onSelected = { month = it + 1 },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = dayItems,
                    selectedIndex = day - 1,
                    onSelected = { day = it + 1 },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                WheelColumn(
                    items = hourItems,
                    selectedIndex = hour,
                    onSelected = { hour = it },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = minuteItems,
                    selectedIndex = minute,
                    onSelected = { minute = it },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        UiText.combineDateAndTime(
                            dateUtcMillis = LocalDate.of(year, month, day)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli(),
                            hour = hour,
                            minute = minute,
                            zoneId = zone,
                        ),
                    )
                },
            ) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 单列滚轮：中间一格即选中项，上下各露出 [WheelVisibleItems] / 2 格。
 *
 * [selectedIndex] 是「应该停在哪一格」的唯一来源，用户滚动由这里上报、
 * 外部修正（换月后日期变短）也由这里同步回滚轮，双向都只走这一个值，
 * 避免两个方向互相触发。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Spacer(modifier = modifier.height(WheelItemHeight * WheelVisibleItems))
        return
    }

    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(items.indices),
    )
    val itemHeightPx = with(LocalDensity.current) { WheelItemHeight.toPx() }
    // 滚到一半时用小数位置算透明度，视觉上才连得上
    val centerFraction by remember(state, itemHeightPx) {
        derivedStateOf {
            state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemHeightPx
        }
    }

    // 滚动停下后把居中的一格报上去
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .collect { (scrolling, index) ->
                if (!scrolling) {
                    onSelected(index.coerceIn(items.indices))
                }
            }
    }

    // 外部改了选中项（或被上面的取值夹紧）时，把滚轮挪过去
    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(items.indices)
        if (state.firstVisibleItemIndex != target) {
            state.scrollToItem(target)
        }
    }

    Box(
        modifier = modifier.height(WheelItemHeight * WheelVisibleItems),
        contentAlignment = Alignment.Center,
    ) {
        // 中央选中行的底色，让「选中的是哪一格」一眼可见
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )

        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = WheelItemHeight * (WheelVisibleItems / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            itemsIndexed(items = items) { index, label ->
                val distance = abs(index - centerFraction)
                val itemAlpha = (1f - distance * 0.45f).coerceIn(0.25f, 1f)
                val itemScale = (1f - distance * 0.12f).coerceIn(0.7f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = itemAlpha
                            scaleX = itemScale
                            scaleY = itemScale
                        },
                    )
                }
            }
        }
    }
}
