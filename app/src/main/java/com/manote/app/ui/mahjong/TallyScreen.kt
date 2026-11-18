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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.InferredHand
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.domain.mahjong.TenpaiInference
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
 *
 * 池子下面还挂着一块**推算**：填上对手大概有几张，牌山只剩三张以内时，
 * 这一池牌的每一种分配都会摆出来，凡是能听牌的就连同「听什么」一起列。
 * 那是记这些牌的目的——记牌本身不是目的，「他可能在等哪张」才是。
 */
@Composable
fun TallyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tally by rememberSaveable(stateSaver = TallySaver) { mutableStateOf(TileTally.empty) }
    var suitOrdinal by rememberSaveable { mutableIntStateOf(0) }
    // 0 表示「未填」。用序号而不是可空 Int：rememberSaveable 只认基本类型，
    // 而「没填」本来就是一种明确的状态，不必再套一层可空。
    var opponentHand by rememberSaveable { mutableIntStateOf(0) }
    val suit = MahjongSuit.entries[suitOrdinal]

    TallyContent(
        tally = tally,
        suit = suit,
        opponentHand = opponentHand,
        onNavigateBack = onNavigateBack,
        onSelectSuit = { suitOrdinal = it.ordinal },
        onMark = { tally = tally.mark(it) },
        onUnmark = { tally = tally.unmark(it) },
        onClear = { tally = tally.cleared(suit) },
        onOpponentChange = { opponentHand = it },
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
    opponentHand: Int,
    onNavigateBack: () -> Unit,
    onSelectSuit: (MahjongSuit) -> Unit,
    onMark: (ChengduTile) -> Unit,
    onUnmark: (ChengduTile) -> Unit,
    onClear: () -> Unit,
    onOpponentChange: (Int) -> Unit,
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

            // 推算紧跟着池子：上面点掉一张，下面的可能牌型当场跟着变，
            // 中间隔着一张「已出现」的账本，改完还得往下翻才看得见结论。
            InferCard(
                tally = tally,
                suit = suit,
                opponentHand = opponentHand,
                onOpponentChange = onOpponentChange,
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
            // 牌宽按「一行最坏摆得下三组、合起来八张」算：满池时一行两组四张，
            // 池子越空 FlowRow 越往里塞第三组。
            //
            // 三组比两组多一道组间空档：只按「两组四张」算宽度（少算那一档 14dp），
            // `1111 22` 之后再放 `33` 就差那么几个像素放不下，第三组被推到下一行——
            // 明明还有空间，看着像是排版坏了。这里把最紧的那一行算足。
            val gaps = GROUP_GAP * GROUP_GAPS_PER_ROW +
                MAHJONG_TILE_GAP * (TILES_PER_ROW - GROUP_GAPS_PER_ROW - 1)
            val tileWidth = ((maxWidth - gaps) / TILES_PER_ROW).coerceAtLeast(MIN_TILE_WIDTH)

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
 * 对手可能听什么。
 *
 * 记牌器记的是「这一门还剩哪些牌」，而下一句往往才是想知道的：对手手里那几张
 * 可能是什么牌型、听什么、我这张打出去会不会点炮。未知的牌只剩一点点
 * （牌山三张以内）时，这一池牌的分配方式是可以穷举的：绝大多数在他手里、
 * 剩下几张在牌山。每种分配都摆出来，凡是能听牌的就列在这里，
 * 连同「听什么」与「牌山剩什么」——听的那张若不在牌山里，他就只能等别家打出来。
 *
 * 对手有几张只能猜（碰过几组就少三张），所以问的是「如果他有七张」，
 * 结论是几种可能并列，不是一句判断。因此这里先把可能性摆全，再让人自己挑。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InferCard(
    tally: TileTally,
    suit: MahjongSuit,
    opponentHand: Int,
    onOpponentChange: (Int) -> Unit,
) {
    val unseen = tally.remainingTiles(suit)
    // 池子每变一次就要重算一遍，但「点了别的点数的牌」不该触发这棵搜索。
    val combos = remember(unseen, opponentHand) { TenpaiInference.infer(unseen, opponentHand) }
    val leftover = unseen.size - opponentHand

    ManoteCard {
        ManoteSectionHeader(title = stringResource(R.string.mahjong_tally_infer_title))
        Spacer(modifier = Modifier.height(10.dp))
        MutedNote(stringResource(R.string.mahjong_tally_infer_hint))
        Spacer(modifier = Modifier.height(12.dp))

        // 六颗胶囊在这个宽度上一定折成两行（一行最多五颗），换成等分的分段控件：
        // 一组里选一个，本来就该长得像「一格里选一个」，而它又正好与上面的花色切换同形。
        ManoteSegmentedControl(
            options = listOf(stringResource(R.string.mahjong_tally_opponent_none)) +
                OPPONENT_HAND_SIZES.map { it.toString() },
            selectedIndex = OPPONENT_HAND_CHOICES.indexOf(opponentHand),
            onSelect = { onOpponentChange(OPPONENT_HAND_CHOICES[it]) },
        )

        if (opponentHand == 0) return@ManoteCard

        Spacer(modifier = Modifier.height(14.dp))
        when {
            unseen.size < opponentHand -> MutedNote(
                stringResource(
                    R.string.mahjong_tally_infer_short_format,
                    unseen.size,
                    opponentHand,
                ),
            )

            leftover > TenpaiInference.MAX_LEFTOVER -> MutedNote(
                stringResource(
                    R.string.mahjong_tally_infer_overflow_format,
                    leftover,
                    TenpaiInference.MAX_LEFTOVER,
                ),
            )

            combos.isEmpty() -> MutedNote(stringResource(R.string.mahjong_tally_infer_none))

            else -> {
                MutedNote(
                    stringResource(
                        R.string.mahjong_tally_infer_note_format,
                        unseen.size,
                        opponentHand,
                        leftover,
                    ),
                )
                if (combos.size > MAX_INFERRED_ROWS) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MutedNote(
                        stringResource(
                            R.string.mahjong_tally_infer_total_format,
                            combos.size,
                            MAX_INFERRED_ROWS,
                        ),
                    )
                }
                combos.take(MAX_INFERRED_ROWS).forEachIndexed { index, combo ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InferredRow(combo)
                }
            }
        }
    }
}

/**
 * 一种摆法：先是他那一手牌，再是「听什么」与「牌山剩什么」。
 *
 * 三排都用牌面而不是文字，因为要看的就是牌：写成「2234566」还得再读一遍
 * 才知道那是两个二、一个三。牌山里的牌单独一排，才能一眼比出来
 * 「他听的那张到底还在不在」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InferredRow(inferred: InferredHand) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
        ) {
            inferred.hand.forEach { tile ->
                MahjongTileFace(
                    tile = tile,
                    width = INFER_TILE_WIDTH,
                    contentDescription = tileLabel(tile),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
        ) {
            RowLabel(
                text = stringResource(R.string.mahjong_tally_infer_wait_label),
                color = MaterialTheme.colorScheme.primary,
            )
            inferred.waits.forEach { tile ->
                MahjongTileFace(
                    tile = tile,
                    width = INFER_TILE_WIDTH,
                    contentDescription = tileLabel(tile),
                )
            }
            RowLabel(
                text = stringResource(R.string.mahjong_tally_infer_wall_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
            if (inferred.leftovers.isEmpty()) {
                RowLabel(
                    text = stringResource(R.string.mahjong_tally_infer_wall_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                inferred.leftovers.forEach { tile ->
                    MahjongTileFace(
                        tile = tile,
                        width = INFER_TILE_WIDTH,
                        contentDescription = tileLabel(tile),
                    )
                }
            }
        }
    }
}

/** 牌面前面那两个字的标签：压一行，跟牌面对齐。 */
@Composable
private fun RowLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** 卡片里那句说明：比正文小一档，颜色退到次级。 */
@Composable
private fun MutedNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 读屏用的一句话，例如「3 筒」。 */
@Composable
private fun tileLabel(tile: ChengduTile): String =
    stringResource(R.string.mahjong_tile_format, tile.rank, tile.suit.label())

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
 * 对手隐藏的手牌只可能是这几种张数：`3n+1`。
 *
 * 没碰过是十三张，碰一组少三张，四组全碰完只剩一张单钓。十四张那个张数
 * 是「刚摸完还没打」，谈听牌没有意义，所以不列出来。
 */
private val OPPONENT_HAND_SIZES = listOf(13, 10, 7, 4, 1)

/** 分段控件的六格：第一格是「未填」（0），后面才是张数。 */
private val OPPONENT_HAND_CHOICES = listOf(0) + OPPONENT_HAND_SIZES

/**
 * 最多列几种摆法。
 *
 * 牌山只剩三张以内时，摆法本来就不多（九张池子分给七张手牌是九种）；
 * 手牌十三张、牌山三张时能到几十种，全摊出来反而看不出结论。
 * 超过这个数就在上面写明一共有几种。
 */
private const val MAX_INFERRED_ROWS = 12

/** 推算区里的牌宽：一手十三张也只占两行，点数仍然认得出来。 */
private val INFER_TILE_WIDTH = 26.dp

/**
 * 未出现那一池里，两个点数之间的空档。
 *
 * 它必须明显大于同一点数内部的 [MAHJONG_TILE_GAP]，否则 1 万的四张会和 2 万的四张
 * 连成一片八个方块，「哪几张是同一门点数」就看不出来了。
 */
private val GROUP_GAP = 14.dp

/** 算牌宽时按一行摆几组四张来估：满池时一行两组，九组正好五行。 */
private const val GROUPS_PER_ROW = 2

/** 一行最多能塞几组：三组时组间空档要算两道，这是牌宽能撑住的最紧一行。 */
private const val GROUP_GAPS_PER_ROW = 2

/** 一行最多摆几张：八张。再多就得靠折行，牌子也不能再小。 */
private const val TILES_PER_ROW = GROUPS_PER_ROW * ChengduTile.COPIES

/** 牌宽下限：再窄下去牌面上的点数就糊成一团，宁可让它少摆一组换行。 */
private val MIN_TILE_WIDTH = 28.dp
