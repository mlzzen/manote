package com.manote.app.ui.friends

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
 * 麻友管理骨架。
 *
 * 规格 3.4 要求列表搜索、归档开关与逐场统计，全部依赖 Player 与 SessionResult 查询，
 * 因此 P0 不渲染麻友列表，避免出现虚构档案。
 */
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.nav_friends),
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
            ManoteNoticeCard(text = stringResource(R.string.friends_skeleton_notice))
        }
    }
}
