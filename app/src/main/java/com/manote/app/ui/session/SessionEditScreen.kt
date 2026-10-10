package com.manote.app.ui.session

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
 * 记一场骨架。
 *
 * 规格 3.2 要求本页完成四人选择、金额填写与合计校验；
 * 选中校验、金额解析与事务写入分别在 P1／P2 实现，此处不放置表单占位控件，
 * 以免出现无法提交的假输入框。
 */
@Composable
fun SessionEditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.action_record_session),
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
            ManoteNoticeCard(text = stringResource(R.string.session_skeleton_notice))
        }
    }
}
