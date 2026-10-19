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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manote.app.R
import com.manote.app.ui.components.AnimatedSignedYuan
import com.manote.app.ui.components.ManoteEmptyState
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSearchField
import com.manote.app.ui.components.ManoteSegmentedControl
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.manoteViewModel
import com.manote.app.ui.model.SessionFilter
import com.manote.app.ui.model.SessionMonthGroup
import com.manote.app.ui.model.UiText
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.ManoteMotion
import com.manote.app.ui.theme.manote

/**
 * 场次列表（规格 2.4、3.3）。
 *
 * 历史按 playedAt 倒序、同时间按 Session ID 稳定排序，并按自然月分组显示；
 * 支持标题或历史昵称关键词搜索，可叠加「全部 / 本月 / 上月」筛选。
 * 全部在内存里完成，不额外查询数据库。
 */
@Composable
fun SessionsScreen(
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
        onOpenSessionDetail = onOpenSessionDetail,
        modifier = modifier,
    )
}

@Composable
private fun SessionsContent(
    state: SessionsUiState,
    onSelectFilter: (SessionFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶栏原本挂着一个「N 场」的胶囊，现在并进头部卡里：
    // 场数与净额是同一件事的两个面（这批场次打了多少、赢了还是输了），
    // 摆在一起才读得通，分在屏幕两端只会让人以为它们无关。
    val visibleCount = state.monthGroups.sumOf { it.sessionCount }
    val visibleNetMinor = state.monthGroups.sumOf { it.selfNetMinor }

    ManoteScaffold(
        title = stringResource(R.string.nav_sessions),
        branded = true,
        modifier = modifier,
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
                // 首帧还没读到时不显示头部，避免先闪一个 +¥0.00
                if (!state.loading && visibleCount > 0) {
                    SessionsHeroCard(count = visibleCount, netMinor = visibleNetMinor)
                }

                ManoteSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.sessions_search_hint),
                )

                // 三个筛选项互斥，用分段控件而不是三颗独立胶囊：
                // 后者看起来像可以多选，前者把「只能选一个」直接写进了形状里。
                val filters = SessionFilter.entries
                ManoteSegmentedControl(
                    options = filters.map { stringResource(it.labelRes()) },
                    selectedIndex = filters.indexOf(state.filter),
                    onSelect = { onSelectFilter(filters[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    // 按月分组：每组先一个月份小标题，再是本组场次（规格 3.3）。
                    // animateItem 负责重排与增删：换筛选或搜索后，卡片是「滑」到新位置的，
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
                                onClick = { onOpenSessionDetail(row.id) },
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
}

/**
 * 场次页头部：当前筛选与搜索之下，这批场次的本人净额合计。
 *
 * 它跟着下面的搜索框与筛选胶囊实时变化，回答的是「筛出来的这批到底打得怎么样」。
 * 下面按月分组的那些「本人合计」加起来就是它，两处同源，不另存一份账本（规格 2.4）。
 */
@Composable
private fun SessionsHeroCard(count: Int, netMinor: Long) {
    val colors = MaterialTheme.manote
    ManoteHeroCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sessions_hero_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onHeroMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedSignedYuan(
                    netMinor = netMinor,
                    style = MaterialTheme.typography.headlineLarge,
                    color = when {
                        netMinor > 0L -> colors.heroWin
                        netMinor < 0L -> colors.heroLoss
                        else -> colors.onHero
                    },
                )
            }
            ManotePill(
                text = stringResource(R.string.sessions_month_count_format, count),
                containerColor = colors.heroChip,
                contentColor = colors.onHeroChip,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
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

private fun SessionFilter.labelRes(): Int = when (this) {
    SessionFilter.ALL -> R.string.sessions_filter_all
    SessionFilter.THIS_MONTH -> R.string.sessions_filter_this_month
    SessionFilter.LAST_MONTH -> R.string.sessions_filter_last_month
}
