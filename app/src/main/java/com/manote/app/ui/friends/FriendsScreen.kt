package com.manote.app.ui.friends

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.domain.Rivalry
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.PlayerAvatar
import com.manote.app.ui.components.SignedAmount
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.FriendBadge
import com.manote.app.ui.model.FriendRowUi
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.session.CreateFriendDialog
import com.manote.app.ui.theme.manote
import kotlinx.coroutines.launch

/**
 * 牌友页（规格 3.4）。
 *
 * 搜索昵称/备注，默认只显示未归档，可切换「包含已归档」。
 * 每行显示昵称、备注摘要、场数与净输赢；数字都来自 `session_result` 的实时聚合。
 * 同场满 [com.manote.app.domain.Rivalry.MIN_COMMON_SESSIONS] 场时，行上会挂出
 * 「最苦主」（我在他面前场均最低）、「福星」（我在他面前场均最高）、
 * 「牌王」（他自己的场均最高）三个标签，可同时满足多个。
 * 点按一行进入麻友详情，编辑与归档在那里；新增入口在顶栏右上角。
 */
@Composable
fun FriendsScreen(
    onSelectTab: (ManoteTab) -> Unit,
    onRecordSession: () -> Unit,
    onOpenFriend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = manoteViewModel { container, _ ->
        FriendsViewModel(container.playerRepository, container.sessionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FriendsEvent.Created -> showCreateDialog = false
                is FriendsEvent.Failed -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    ManoteScaffold(
        title = stringResource(R.string.nav_friends),
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        actions = {
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.friends_add),
                )
            }
        },
        bottomBar = {
            ManoteBottomBar(
                current = ManoteTab.FRIENDS,
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
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(text = stringResource(R.string.friends_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    shape = MaterialTheme.shapes.small,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.loading) {
                            ""
                        } else if (state.totalArchived == 0) {
                            // 没有已归档麻友时不显示「含已归档 0 位」，避免无意义的噪声
                            stringResource(R.string.friends_count_format, state.totalActive)
                        } else {
                            stringResource(
                                R.string.friends_count_with_archived_format,
                                state.totalActive,
                                state.totalArchived,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.friends_include_archived),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = state.includeArchived,
                        onCheckedChange = viewModel::setIncludeArchived,
                    )
                }

                // 打了标就要说清楚标是怎么来的，否则「最苦主」三个字只会让人莫名其妙
                if (state.visible.any { it.badges.isNotEmpty() }) {
                    Text(
                        text = stringResource(
                            R.string.friends_badge_hint,
                            Rivalry.MIN_COMMON_SESSIONS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 首帧还没读到时留白占位，避免闪一下空态
            when {
                state.loading -> Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                state.visible.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ManoteEmptyState(
                        icon = Icons.Filled.Person,
                        title = stringResource(
                            if (state.hasAny) {
                                R.string.friends_search_empty_title
                            } else {
                                R.string.friends_empty_title
                            },
                        ),
                        description = stringResource(
                            if (state.hasAny) {
                                R.string.friends_search_empty_desc
                            } else {
                                R.string.friends_empty_desc
                            },
                        ),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = state.visible, key = { it.id }) { friend ->
                        FriendRow(friend = friend, onClick = { onOpenFriend(friend.id) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFriendDialog(
            countSameNickname = viewModel::countSameNickname,
            onDismiss = { showCreateDialog = false },
            onConfirm = { nickname, note -> viewModel.createFriend(nickname, note) },
        )
    }
}

/** 一行麻友：昵称、备注摘要、场数与净输赢（规格 3.4）。整行可点击进入详情。 */
@Composable
private fun FriendRow(friend: FriendRowUi, onClick: () -> Unit) {
    ManoteCard(
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = friend.nickname,
                size = 42.dp,
                containerColor = if (friend.archived) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (friend.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = friend.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (friend.archived) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.friends_archived_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    // 按枚举声明顺序渲染，标签的先后不会随 Set 的实现方式变化
                    FriendBadge.entries.forEach { badge ->
                        if (badge in friend.badges) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FriendBadgeChip(badge = badge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = friendSubtitle(friend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            if (friend.sessionCount == 0) {
                Text(
                    text = stringResource(R.string.friends_no_sessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SignedAmount(
                    netMinor = friend.netMinor,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/** 备注摘要 + 场数；备注为空时只显示场数，不显示空占位。 */
@Composable
private fun friendSubtitle(friend: FriendRowUi): String {
    val count = stringResource(R.string.friends_stat_format, friend.sessionCount)
    return if (friend.note.isBlank()) {
        count
    } else {
        friend.note + " · " + count
    }
}

/**
 * 标签小胶囊。
 *
 * 文案本身就是含义（最苦主 / 福星 / 牌王），颜色只负责让它在名单里跳出来，
 * 与全应用「不靠颜色单独表达信息」的做法一致。
 * 配色各挑一个语义：最苦主用中性的米黄、福星用金黄（好事）、牌王用品牌墨绿的浅底。
 */
@Composable
private fun FriendBadgeChip(badge: FriendBadge) {
    val colors = MaterialTheme.manote
    val background: Color
    val content: Color
    when (badge) {
        FriendBadge.WORST_RIVAL -> {
            background = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }

        FriendBadge.LUCKY_STAR -> {
            background = colors.gold
            content = colors.onGold
        }

        FriendBadge.TABLE_KING -> {
            background = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    Text(
        text = badge.label(),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 标签 → 文案。
 *
 * 只有 @Composable 里才能调 stringResource，因此不能直接给枚举加一个属性；
 * 这里用穷尽 `when`，将来加标签时编译期就会提醒补文案。
 */
@Composable
private fun FriendBadge.label(): String = stringResource(
    when (this) {
        FriendBadge.WORST_RIVAL -> R.string.friends_worst_rival_badge
        FriendBadge.LUCKY_STAR -> R.string.friends_lucky_badge
        FriendBadge.TABLE_KING -> R.string.friends_table_king_badge
    },
)
