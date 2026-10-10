package com.manote.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.manote.app.R
import com.manote.app.ui.model.UiText
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 打完时间选择：先选日期、再选时间。
 *
 * 规格 3.2 要求默认现在、可选过去；是否晚于当前时间由保存前的校验统一判定，
 * 这里不预先禁用未来的日期，以免用户搞不清为什么选不了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayedAtPickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(zone) }

    var pickedDateUtcMillis by remember(initialMillis) {
        mutableLongStateOf(
            initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    }
    var pickedHour by remember(initialMillis) { mutableIntStateOf(initial.hour) }
    var pickedMinute by remember(initialMillis) { mutableIntStateOf(initial.minute) }
    var stage by remember { mutableStateOf(Stage.DATE) }

    when (stage) {
        Stage.DATE -> {
            val dateState = rememberDatePickerState(initialSelectedDateMillis = pickedDateUtcMillis)
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDateUtcMillis = dateState.selectedDateMillis ?: pickedDateUtcMillis
                            stage = Stage.TIME
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
            ) {
                DatePicker(state = dateState)
            }
        }

        Stage.TIME -> {
            val timeState = rememberTimePickerState(
                initialHour = pickedHour,
                initialMinute = pickedMinute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(text = stringResource(R.string.session_time_pick_title)) },
                text = {
                    Column {
                        TimePicker(state = timeState)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedHour = timeState.hour
                            pickedMinute = timeState.minute
                            onConfirm(
                                UiText.combineDateAndTime(
                                    dateUtcMillis = pickedDateUtcMillis,
                                    hour = timeState.hour,
                                    minute = timeState.minute,
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
    }
}

private enum class Stage { DATE, TIME }
