package com.manote.app.ui.mahjong

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.domain.mahjong.TileTally
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManoteHeroChip
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteSegmentedControl
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.theme.manote

/**
 * 记牌器：一种花色里，九个点数各剩几张。
 *
 * 成都麻将只打三门、没有字牌，所以「还剩什么牌」这件事比日麻简单得多，
 * 也更容易在脑子里记岔——一门九种、每种四张，看着简单，
 * 打到中盘连自己手上攥着几张都会数错。这里把它摊成上下两块：
 * 上面是**未出现**的那一池牌，一枚牌子就是一张还活着的牌，点一下就算它出现；
 * 点下面那块可以撤回。一来一回都在同一屏里，不必再记「我刚才点了什么」。
 *
 * 上面那一池刻意不做成「九个格子各带一个剩余数」：那样读到的是九个数字，
 * 还得在脑子里展开成「还剩哪些牌」——而后者正是要看的东西。
 * 摊成牌面之后，一眼念过去就是 118899，能不能对手上的搭子也当场看得出来。
 *
 * 只做**当前这一门**：三门同时摊开要 27 块牌子，一屏放不下，
 * 而且打牌时通常是「这一门有人在做清一色」才需要盯，三门一起盯是另一种用法。
 * 切换花色不丢数据——三门的记录一直在，切回来还是原样。
 */
@Composable
fun TallyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tally by rememberSaveable(stateSaver = TallySaver) { mutableStateOf(TileTally.empty) }
    var suitOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val suit = MahjongSuit.entries[suitOrdinal]

    TallyContent(
        tally = tally,
        suit = suit,
        onNavigateBack = onNavigateBack,
        onSelectSuit = { suitOrdinal = it.ordinal },
        onMark = { tally = tally.mark(it) },
        onUnmark = { tally = tally.unmark(it) },
        onClear = { tally = tally.cleared(suit) },
        modifier = modifier,
    )
}

/**
 * 记牌页的纯内容层：记录与花色由外部给。
 *
 * 与牌理页同一个理由——渲染测试要能画「已经点掉了好几张」的样子，
 * 而那种状态不该为了测试住进页面里。
 */
@Composable
internal fun TallyContent(
    tally: TileTally,
    suit: MahjongSuit,
    onNavigateBack: () -> Unit,
    onSelectSuit: (MahjongSuit) -> Unit,
    onMark: (ChengduTile) -> Unit,
    onUnmark: (ChengduTile) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.mahjong_tally_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TallyHeroCard(tally = tally, suit = suit)

            ManoteSegmentedControl(
                options = MahjongSuit.entries.map { it.label() },
                selectedIndex = suit.ordinal,
                onSelect = { onSelectSuit(MahjongSuit.entries[it]) },
            )

            UnseenCard(
                tally = tally,
                suit = suit,
                onMark = onMark,
                onClear = onClear,
            )

            SeenCard(
                tally = tally,
                suit = suit,
                onUnmark = onUnmark,
            )

            Text(
                text = stringResource(R.string.mahjong_tally_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** 记牌器的存档：同样是 27 个计数。 */
private val TallySaver = listSaver<TileTally, Int>(
    save = { it.seen },
    restore = { TileTally(it) },
)

/**
 * 英雄卡：这一门还剩几张没露面。
 *
 * 报「还剩」而不是「已出现」：打牌时真正要算的是「我要的那张还有没有」，
 * 已出现多少只是它的另一面。已出现的张数挂在胶囊上，两者不必抢同一个位置。
 */
@Composable
private fun TallyHeroCard(tally: TileTally, suit: MahjongSuit) {
    val colors = MaterialTheme.manote
    val remaining = tally.remainingCount(suit)
    val unseenKinds = (1..ChengduTile.RANKS).count { rank ->
        tally.countOf(ChengduTile(suit, rank)) < ChengduTile.COPIES
    }

    ManoteHeroCard {
        Text(
            text = stringResource(R.string.mahjong_tally_hero_label, suit.label()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHeroMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = remaining.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onHero,
            )
            Text(
                text = stringResource(R.string.mahjong_tally_remaining_unit),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onHeroMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ManoteHeroChip(
                stringResource(R.string.mahjong_tally_seen_format, tally.seenCount(suit)),
            )
            ManoteHeroChip(
                stringResource(R.string.mahjong_tally_unseen_kinds_format, unseenKinds),
            )
        }
    }
}

/**
 * 未出现的牌：**池子里还在的每一张，各占一块牌子**。
 *
 * 这里刻意不用「九个点数各一块、右上角标还剩几张」那种格子。那种摆法的读数
 * 是九个数字，要拼出「还剩哪些牌」还得多做一次展开，而那句话才是真正要问的：
 * 还剩 1、1、8、8、9、9，就说明 234 已经绝张、789 还有得摸。
 * 摊成牌面之后，从左往右念一遍就是 118899，不必再算。
 *
 * 同一点数的几张挨在一起、点数与点数之间留一道空档：
 * 一组变短就是「这个点出了几张」，整组不见了就是「这个点已经绝了」。
 * 点其中一张就记它出现——那一块当场从池子里消失，落到下面的「已出现」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnseenCard(
    tally: TileTally,
    suit: MahjongSuit,
    onMark: (ChengduTile) -> Unit,
    onClear: () -> Unit,
) {
    val groups = tally.remainingTiles(suit).groupBy { it.rank }.values.toList()

    ManoteCard {
        ManoteSectionHeader(
            title = stringResource(R.string.mahjong_tally_unseen_label),
            trailing = {
                if (tally.seenCount(suit) > 0) {
                    ManoteTextAction(
                        label = stringResource(R.string.mahjong_tally_clear),
                        onClick = onClear,
                        chevron = false,
                    )
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.mahjong_tally_unseen_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManoteCard
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 牌宽按「一行最坏摆得下两组四张」算出来：满池时一行两组、九组五行，
            // 池子越空，FlowRow 自然往里塞第三组，不必自己判断还剩几行。
            val tileWidth = (
                (maxWidth - GROUP_GAP - MAHJONG_TILE_GAP * (GROUPS_PER_ROW * ChengduTile.COPIES - 1)) /
                    (GROUPS_PER_ROW * ChengduTile.COPIES)
                ).coerceAtLeast(MIN_TILE_WIDTH)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
                verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
            ) {
                groups.forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP)) {
                        group.forEach { tile ->
                            MahjongTileFace(
                                tile = tile,
                                width = tileWidth,
                                onClick = { onMark(tile) },
                                contentDescription = stringResource(
                                    R.string.mahjong_tile_format,
                                    tile.rank,
                                    suit.label(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.mahjong_tally_unseen_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 已出现的牌：一张一个位置，摊开来摆。
 *
 * 同一种牌出现两次就摆两块，不合并成「×2」。牌局里同一张牌出现第二回
 * 往往比第一回更值得注意（对手在等什么、这一门还剩几张），
 * 合并之后那层信息要减法才看得出来。
 *
 * 点一块就撤掉一张：记错了直接改，不必把这一门清空重来。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeenCard(
    tally: TileTally,
    suit: MahjongSuit,
    onUnmark: (ChengduTile) -> Unit,
) {
    val seen = tally.visibleTiles(suit)

    ManoteCard {
        ManoteSectionHeader(title = stringResource(R.string.mahjong_tally_seen_label))
        Spacer(modifier = Modifier.height(12.dp))

        if (seen.isEmpty()) {
            Text(
                text = stringResource(R.string.mahjong_tally_seen_empty, suit.label()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManoteCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
        ) {
            seen.forEach { tile ->
                MahjongTileFace(
                    tile = tile,
                    width = SEEN_TILE_WIDTH,
                    onClick = { onUnmark(tile) },
                    contentDescription = stringResource(
                        R.string.mahjong_tile_format,
                        tile.rank,
                        tile.suit.label(),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.mahjong_tally_seen_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 已出现的牌宽：一行能排下十来块，36 张也不会铺成一面墙。 */
private val SEEN_TILE_WIDTH = 30.dp

/**
 * 未出现那一池里，两个点数之间的空档。
 *
 * 它必须明显大于同一点数内部的 [MAHJONG_TILE_GAP]，否则 1 万的四张会和 2 万的四张
 * 连成一片八个方块，「哪几张是同一门点数」就看不出来了。
 */
private val GROUP_GAP = 14.dp

/** 算牌宽时按一行摆几组四张来估：满池时一行两组，九组正好五行。 */
private const val GROUPS_PER_ROW = 2

/** 牌宽下限：再窄下去牌面上的点数就糊成一团，宁可让它少摆一组换行。 */
private val MIN_TILE_WIDTH = 28.dp
