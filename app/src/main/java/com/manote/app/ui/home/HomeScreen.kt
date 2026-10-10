package com.manote.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.ManoteEntryButton
import com.manote.app.ui.components.ManoteNoticeCard
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.theme.ManoteTheme

/**
 * 首页骨架。
 *
 * 规格 3.1 要求首页显示本人昵称与累计赢钱、累计输钱、净输赢、已记录场数。
 * 这些数值必须来自 SessionResult 的实时汇总（规格 2.4），P0 尚无数据层，
 * 因此此处不渲染任何金额，避免出现与数据库不一致的假数字。
 */
@Composable
fun HomeScreen(
    onRecordSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.app_name),
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ManoteNoticeCard(text = stringResource(R.string.home_skeleton_notice))

            Button(
                onClick = onRecordSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_record_session),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = stringResource(R.string.home_entries_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            ManoteEntryButton(
                label = stringResource(R.string.nav_history),
                onClick = onOpenHistory,
            )
            ManoteEntryButton(
                label = stringResource(R.string.nav_friends),
                onClick = onOpenFriends,
            )
            ManoteEntryButton(
                label = stringResource(R.string.nav_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

@Preview(name = "首页 浅色", showBackground = true)
@Composable
private fun HomeScreenPreviewLight() {
    ManoteTheme(darkTheme = false) {
        HomeScreen(
            onRecordSession = {},
            onOpenHistory = {},
            onOpenFriends = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "首页 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreviewDark() {
    ManoteTheme(darkTheme = true) {
        HomeScreen(
            onRecordSession = {},
            onOpenHistory = {},
            onOpenFriends = {},
            onOpenSettings = {},
        )
    }
}
