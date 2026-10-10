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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
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
import com.manote.app.ui.navigation.ManoteTab

/**
 * 场次列表（规格 2.4：历史按 playedAt 倒序、同时间按 Session ID 稳定排序）。
 *
 * 全部 / 本月 / 上月 只在内存里筛选，不额外查询数据库。
 * 搜索、修改与删除属 P3，本页暂不提供，避免放置点了没有反应的入口。
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionFilter.entries.forEach { filter ->
                    ManoteFilterChip(
                        label = stringResource(filter.labelRes()),
                        selected = state.filter == filter,
                        onClick = { onSelectFilter(filter) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 首帧还没读到时留白，避免闪一下空态
            if (state.loading) {
                return@Column
            }

            if (state.visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(
                            if (state.hasAny) {
                                R.string.friend_picker_empty
                            } else {
                                R.string.sessions_empty_title
                            },
                        ),
                        description = stringResource(
                            if (state.hasAny) {
                                R.string.sessions_empty_desc
                            } else {
                                R.string.home_empty_desc
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
                    items(items = state.visible, key = { it.id }) { row ->
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

private fun SessionFilter.labelRes(): Int = when (this) {
    SessionFilter.ALL -> R.string.sessions_filter_all
    SessionFilter.THIS_MONTH -> R.string.sessions_filter_this_month
    SessionFilter.LAST_MONTH -> R.string.sessions_filter_last_month
}
