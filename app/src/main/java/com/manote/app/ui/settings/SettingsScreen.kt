package com.manote.app.ui.settings

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
 * 设置骨架。
 *
 * 规格 3.5 要求本人昵称编辑、JSON 备份恢复与 CSV 导出；这些操作都会写库或产出文件，
 * P0 不放置无响应入口，避免按钮点了没反应。
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.nav_settings),
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
            ManoteNoticeCard(text = stringResource(R.string.settings_skeleton_notice))
        }
    }
}
