package com.manote.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.BuildConfig
import com.manote.app.R
import com.manote.app.data.repository.BackupSummary
import com.manote.app.data.repository.DataCounts
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.manote
import kotlinx.coroutines.launch

/**
 * 「我的」（规格 3.5）。
 *
 * 展示本人昵称与版本号、修改本人昵称，并提供 JSON 备份的导出与导入。
 * 文件读写一律走系统文件选择器，因此不需要任何存储权限；
 * 取消选择器不会有任何副作用，导出失败也不会影响数据库。
 */
@Composable
fun SettingsScreen(
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onImportCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        SettingsViewModel(
            playerRepository = container.playerRepository,
            backupRepository = container.backupRepository,
            fileStore = container.backupFileStore,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 导出：系统「创建文件」入口。取消（uri 为 null）什么都不做。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri.toString())
    }

    // 导入：系统「打开文件」入口。MIME 交给选择器过滤，实际格式由校验器判定。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.requestImport(uri.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Notify -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }

                // 规格 6.1：导入成功后回首页，离开停留在旧数据上的页面
                SettingsEvent.DataReplaced -> onImportCompleted()
            }
        }
    }

    ManoteScaffold(
        title = stringResource(R.string.nav_settings),
        branded = true,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            ManoteBottomBar(
                current = ManoteTab.SETTINGS,
                onSelect = onSelectTab,
                onNewSession = onRecordSession,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 本人档案用英雄卡：这个页面里唯一属于「我」的东西就是这张脸和这个名字，
            // 它值得占全页最重的一块底。深底上也正好压得住 52dp 的头像。
            ManoteHeroCard {
                val heroColors = MaterialTheme.manote
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(
                        name = state.selfNickname.orEmpty(),
                        size = 52.dp,
                        containerColor = heroColors.heroChip,
                        contentColor = heroColors.onHeroChip,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selfNickname.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            color = heroColors.onHero,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.settings_profile_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = heroColors.onHeroMuted,
                        )
                    }
                    ManoteTextAction(
                        label = stringResource(R.string.settings_rename_action),
                        onClick = viewModel::requestRename,
                        contentColor = heroColors.onHeroChip,
                    )
                }
            }

            ManoteCard {
                ManoteSectionHeader(title = stringResource(R.string.settings_data_title))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.settings_data_current_format,
                        state.counts.sessionCount,
                        state.counts.friendCount,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_data_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (state.working) {
                    Text(
                        text = stringResource(R.string.settings_data_working),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 处理中不允许再点，避免同时导出/导入
                    ManoteTextAction(
                        label = stringResource(R.string.settings_export_action),
                        onClick = {
                            if (!state.working) exportLauncher.launch(viewModel.exportFileName())
                        },
                    )
                    ManoteTextAction(
                        label = stringResource(R.string.settings_import_action),
                        onClick = {
                            if (!state.working) importLauncher.launch(SUGGESTED_OPEN_MIME_TYPES)
                        },
                    )
                }
            }

            ManoteCard {
                ManoteSectionHeader(title = stringResource(R.string.settings_about_title))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.renameVisible) {
        RenameSelfDialog(
            initialNickname = state.selfNickname.orEmpty(),
            saving = state.saving,
            onDismiss = viewModel::dismissRename,
            onConfirm = viewModel::rename,
        )
    }

    state.pendingImport?.let { summary ->
        ImportConfirmDialog(
            summary = summary,
            counts = state.counts,
            working = state.working,
            onDismiss = viewModel::dismissImport,
            onConfirm = viewModel::confirmImport,
        )
    }
}

/**
 * 导入确认：先摆清楚「现在有什么、导入后会变成什么」，确认后才整体替换（规格 3.5）。
 * 覆盖的是标题、时间、金额、人数等全部数据，因此文案必须写明无法撤销。
 */
@Composable
private fun ImportConfirmDialog(
    summary: BackupSummary,
    counts: DataCounts,
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(text = stringResource(R.string.settings_import_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_import_confirm_current_format,
                        counts.sessionCount,
                        counts.friendCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.settings_import_confirm_incoming_format,
                        summary.sessionCount,
                        summary.friendCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.settings_import_confirm_self_format,
                        summary.selfNickname,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_import_confirm_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.settings_import_confirm_action))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !working,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 修改本人昵称。说明历史快照不会被改写，避免用户以为改了名历史也跟着变（T12）。 */
@Composable
private fun RenameSelfDialog(
    initialNickname: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var nickname by remember { mutableStateOf(initialNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_rename_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.friend_nickname_label)) },
                    shape = MaterialTheme.shapes.small,
                )
                Text(
                    text = stringResource(R.string.settings_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nickname.isNotBlank() && !saving,
                onClick = { onConfirm(nickname) },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val JSON_MIME_TYPE = "application/json"

/**
 * 导入时不做 MIME 过滤：不同文件管理器给 `.json` 报的类型并不统一
 * （`application/json`、`text/plain`、`application/octet-stream` 都见过），
 * 与其让用户找不到自己的备份文件，不如放开选择再用严格校验兜底。
 */
private val SUGGESTED_OPEN_MIME_TYPES = arrayOf("*/*")
