package com.manote.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteFilterChip
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.SessionFilter
import com.manote.app.ui.model.SessionMonthGroup
import com.manote.app.ui.model.UiText
import com.manote.app.ui.navigation.ManoteTab

/**
 * 场次列表（规格 2.4、3.3）。
 *
 * 历史按 playedAt 倒序、同时间按 Session ID 稳定排序，并按自然月分组显示；
 * 支持标题或历史昵称关键词搜索，可叠加「全部 / 本月 / 上月」筛选。
 * 全部在内存里完成，不额外查询数据库。
 */
@Composable
fun SessionsScreen(
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        SessionsViewModel(container.sessionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SessionsContent(
        state = state,
        onSelectFilter = viewModel::setFilter,
        onQueryChange = viewModel::setQuery,
        onSelectTab = onSelectTab,
        onRecordSession = onRecordSession,
        onOpenSessionDetail = onOpenSessionDetail,
        modifier = modifier,
    )
}

@Composable
private fun SessionsContent(
    state: SessionsUiState,
    onSelectFilter: (SessionFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.nav_sessions),
        modifier = modifier,
        bottomBar = {
            ManoteBottomBar(
                current = ManoteTab.SESSIONS,
                onSelect = onSelectTab,
                onNewSession = onRecordSession,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(text = stringResource(R.string.sessions_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    shape = MaterialTheme.shapes.small,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionFilter.entries.forEach { filter ->
                        ManoteFilterChip(
                            label = stringResource(filter.labelRes()),
                            selected = state.filter == filter,
                            onClick = { onSelectFilter(filter) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 首帧还没读到时留白，避免闪一下空态
            if (state.loading) {
                return@Column
            }

            if (state.monthGroups.isEmpty()) {
                val searching = state.query.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(
                            when {
                                searching -> R.string.sessions_search_empty_title
                                state.hasAny -> R.string.sessions_empty_title
                                else -> R.string.sessions_empty_title
                            },
                        ),
                        description = stringResource(
                            when {
                                searching -> R.string.sessions_search_empty_desc
                                state.hasAny -> R.string.sessions_empty_desc
                                else -> R.string.home_empty_desc
                            },
                        ),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 按月分组：每组先一个月份小标题，再是本组场次（规格 3.3）
                    state.monthGroups.forEach { group ->
                        item(key = "month-${group.yearMonth}") {
                            SessionMonthHeader(group = group)
                        }
                        items(items = group.rows, key = { it.id }) { row ->
                            ManoteSessionRow(
                                row = row,
                                onClick = { onOpenSessionDetail(row.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 月份小标题：月份、本组场数、本人该月净额合计。
 *
 * 合计用符号与文字表达输赢，不只用颜色（规格 3.6）；
 * 它是从 [SessionMonthGroup] 现场累加的，不另存一份月度账本（规格 2.4）。
 */
@Composable
private fun SessionMonthHeader(group: SessionMonthGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.sessions_month_count_format, group.sessionCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(
                R.string.sessions_month_self_format,
                UiText.signedYuan(group.selfNetMinor),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SessionFilter.labelRes(): Int = when (this) {
    SessionFilter.ALL -> R.string.sessions_filter_all
    SessionFilter.THIS_MONTH -> R.string.sessions_filter_this_month
    SessionFilter.LAST_MONTH -> R.string.sessions_filter_last_month
}
