package com.manote.app.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.ui.components.AnimatedSignedYuan
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManoteRoundIconButton
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.SessionFilters
import com.manote.app.ui.model.SessionMonthGroup
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.messageRes
import com.manote.app.ui.theme.ManoteMotion
import com.manote.app.ui.theme.manote

/**
 * 场次列表（规格 2.4、3.3）。
 *
 * 历史按 playedAt 倒序、同时间按 Session ID 稳定排序，并按自然月分组显示。
 *
 * 筛选全部收进右上角那颗按钮里：时段、年、月、日、牌友、输赢六个维度放在同一个弹层，
 * 页面上不再常驻搜索框与「全部 / 本月 / 上月」分段控件——那三颗药丸只覆盖了六个维度里的一小半，
 * 却占着整页最显眼的一行。条件改了列表立刻跟着变，顶部那条窄带给出「筛出来这批打得怎么样」。
 *
 * 多选删除是这一页的第二种模式：右上角「选择」进去后，行的点击含义从「打开详情」
 * 变成「勾选这一场」，顶栏换成「已选 N 场」+ 全选 + 删除，返回键/返回箭头退出模式。
 * 删除对象永远是**当前筛选结果里被勾中的那些**，并且一定会先弹一次确认框——
 * 这一步删掉的是整场（含四人的结果），即便做得成回收站，也不该一点就走。
 *
 * 全部在内存里完成，不额外查询数据库。
 */
@Composable
fun SessionsScreen(
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        SessionsViewModel(
            sessionRepository = container.sessionRepository,
            playerRepository = container.playerRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // 删除结果是一条一次性事件：成功说「删了几场」，失败说原因。
    // 用事件而不是状态，是为了转屏重建时不会把同一条提示再弹一遍。
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is SessionsMessage.Deleted ->
                    context.getString(R.string.sessions_delete_done_format, message.count)

                is SessionsMessage.DeleteFailed ->
                    context.getString(
                        R.string.sessions_delete_failed_format,
                        context.getString(message.error.messageRes()),
                    )
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    SessionsContent(
        state = state,
        onChangeFilters = viewModel::setFilters,
        onResetFilters = viewModel::resetFilters,
        onOpenSessionDetail = onOpenSessionDetail,
        onStartSelecting = viewModel::startSelecting,
        onCancelSelecting = viewModel::cancelSelecting,
        onToggleSelected = viewModel::toggleSelected,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onConfirmDelete = viewModel::deleteSelected,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
internal fun SessionsContent(
    state: SessionsUiState,
    onChangeFilters: (SessionFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    onStartSelecting: () -> Unit,
    onCancelSelecting: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirmDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // 弹层开关跟着页面走：旋屏后仍然开着，用户不会丢掉刚看到的那一屏条件
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    // 删除确认框同理：正在犹豫时转个屏，不该把「你确定吗」弄丢
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    ManoteScaffold(
        title = if (state.selecting) {
            stringResource(R.string.sessions_selected_format, state.selectedIds.size)
        } else {
            stringResource(R.string.nav_sessions)
        },
        // 多选模式下换成居中小标题 + 返回箭头：那支箭头就是「退出多选」，
        // 比在顶栏挤一个「取消」更省地方，也让「这是一层临时模式」一眼可辨。
        branded = !state.selecting,
        onNavigateBack = if (state.selecting) onCancelSelecting else null,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (state.selecting) {
                ManoteTextAction(
                    label = stringResource(
                        if (state.allVisibleSelected) {
                            R.string.sessions_deselect_all
                        } else {
                            R.string.sessions_select_all
                        },
                    ),
                    onClick = onToggleSelectAll,
                    chevron = false,
                )
                ManoteRoundIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.sessions_delete_action),
                    onClick = { showDeleteDialog = true },
                    enabled = state.selectedIds.isNotEmpty(),
                    contentColor = MaterialTheme.colorScheme.error,
                )
            } else {
                ManoteTextAction(
                    label = stringResource(R.string.sessions_select_action),
                    onClick = onStartSelecting,
                    chevron = false,
                )
                SessionFilterButton(
                    active = state.filters.isActive,
                    onClick = { showFilterSheet = true },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 首帧还没读到时不显示，避免先闪一个 +¥0.00
            if (!state.loading && state.hasAny) {
                SessionsSummaryStrip(
                    count = state.visibleCount,
                    netMinor = state.visibleNetMinor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 首帧还没读到时留白，避免闪一下空态
            if (state.loading) {
                return@Column
            }

            if (state.monthGroups.isEmpty()) {
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
                                R.string.sessions_filter_empty_title
                            } else {
                                R.string.sessions_empty_title
                            },
                        ),
                        description = stringResource(
                            if (state.hasAny) {
                                R.string.sessions_filter_empty_desc
                            } else {
                                R.string.home_empty_desc
                            },
                        ),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 按月分组：每组先一个月份小标题，再是本组场次（规格 3.3）。
                    // animateItem 负责重排与增删：换筛选条件后，卡片是「滑」到新位置的，
                    // 而不是凭空消失再凭空出现 —— 位置感本身就是信息。
                    state.monthGroups.forEach { group ->
                        item(key = "month-${group.yearMonth}") {
                            SessionMonthHeader(
                                group = group,
                                modifier = Modifier.animateItem(
                                    placementSpec = ManoteMotion.placementSpring(),
                                ),
                            )
                        }
                        items(items = group.rows, key = { it.id }) { row ->
                            ManoteSessionRow(
                                row = row,
                                // 多选模式下点一行是勾选，不再是打开详情；
                                // 两件事都用「点这一行」，只是模式不同，反馈也不同
                                onClick = {
                                    if (state.selecting) {
                                        onToggleSelected(row.id)
                                    } else {
                                        onOpenSessionDetail(row.id)
                                    }
                                },
                                selected = if (state.selecting) row.id in state.selectedIds else null,
                                modifier = Modifier.animateItem(
                                    placementSpec = ManoteMotion.placementSpring(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        SessionFilterSheet(
            filters = state.filters,
            yearOptions = state.yearOptions,
            friendOptions = state.friendOptions,
            resultCount = state.visibleCount,
            onChange = onChangeFilters,
            onReset = onResetFilters,
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showDeleteDialog) {
        DeleteSessionsDialog(
            count = state.selectedIds.size,
            deleting = state.deleting,
            onConfirm = {
                showDeleteDialog = false
                onConfirmDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * 批量删除的确认框。
 *
 * 标题里写清「几场」，正文写清「进回收站、可还原」——这两件事决定了用户敢不敢按下去。
 * 确认按钮用错误色，与顶栏那颗删除按钮同一个颜色：同一个动作在任何地方都是同一个信号。
 */
@Composable
private fun DeleteSessionsDialog(
    count: Int,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sessions_delete_title, count)) },
        text = { Text(text = stringResource(R.string.sessions_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(
                    text = stringResource(R.string.sessions_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 顶部那条窄读数：当前筛选下这批场次的本人净额合计与场数。
 *
 * 原来是整张英雄卡，字号与页面标题一个量级。但场次页的主角其实是下面那一列记录，
 * 这条读数只是「筛选结果的一个脚注」，压到一行、字号收到 labelSmall / titleSmall，
 * 视线扫过去就知道输赢与场数，不会在读完标题后再撞上一个更大的标题。
 *
 * 金额与场数都跟着筛选实时变化，与下面按月分组的「本人合计」同源，不另存一份账本（规格 2.4）。
 */
@Composable
internal fun SessionsSummaryStrip(
    count: Int,
    netMinor: Long,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.manote
    ManoteHeroCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sessions_hero_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onHeroMuted,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedSignedYuan(
                netMinor = netMinor,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    netMinor > 0L -> colors.heroWin
                    netMinor < 0L -> colors.heroLoss
                    else -> colors.onHero
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sessions_month_count_format, count),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onHeroMuted,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 顶栏右上角的筛选入口。
 *
 * 有任一条件生效时右上角点一颗深色小圆点：它不显示「筛了几项」，
 * 因为那一刻要回答的只是「现在看到的是不是全部」。
 */
@Composable
private fun SessionFilterButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    Box {
        ManoteRoundIconButton(
            icon = FilterIcon,
            contentDescription = stringResource(R.string.sessions_filter_action),
            onClick = onClick,
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * 筛选图标：三道由长到短的横线（漏斗的抽象写法）。
 *
 * 图标库只引了 material-icons-core，里面没有 FilterList，而为这一颗按钮引入
 * 整个 extended 包（几 MB 图标）不值当，于是按同一套视觉手画三条 2dp 的圆角横线。
 */
private val FilterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ManoteFilter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 上：4 → 20
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            close()
            // 中：7 → 17
            moveTo(7f, 11f)
            horizontalLineTo(17f)
            verticalLineTo(13f)
            horizontalLineTo(7f)
            close()
            // 下：10 → 14
            moveTo(10f, 16f)
            horizontalLineTo(14f)
            verticalLineTo(18f)
            horizontalLineTo(10f)
            close()
        }
    }.build()
}

/**
 * 月份小标题：一道色条、月份、本组场数、本人该月净额合计。
 *
 * 铺一条浅浅的底色带，让它在两段场次卡片之间当分隔用——
 * 只有一行小字时，浏览时会分不清哪几场属于哪个月。
 *
 * 合计用符号与文字表达输赢，不只用颜色（规格 3.6）；
 * 它是从 [SessionMonthGroup] 现场累加的，不另存一份月度账本（规格 2.4）。
 */
@Composable
private fun SessionMonthHeader(group: SessionMonthGroup, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.manote
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(9.dp))
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
            color = when {
                group.selfNetMinor > 0L -> colors.win
                group.selfNetMinor < 0L -> colors.loss
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
