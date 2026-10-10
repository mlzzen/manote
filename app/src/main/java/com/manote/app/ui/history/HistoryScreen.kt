package com.manote.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.ManoteNoticeCard
import com.manote.app.ui.components.ManoteScaffold

/**
 * 我的历史骨架。
 *
 * 规格 3.3 要求展示本人统计与按 playedAt 倒序的场次列表；
 * 列表与空态需要真实查询结果，P0 不渲染任何场次卡片，避免出现演示数据。
 */
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.nav_history),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ManoteNoticeCard(text = stringResource(R.string.history_skeleton_notice))
        }
    }
}
