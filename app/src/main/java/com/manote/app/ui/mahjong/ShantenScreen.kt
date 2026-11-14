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
import androidx.compose.foundation.layout.width
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
import com.manote.app.domain.mahjong.ChengduHand
import com.manote.app.domain.mahjong.ChengduShanten
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.DiscardOption
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteFilterChip
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManoteHeroChip
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteTextAction
import com.manote.app.ui.theme.manote

/**
 * 牌理：输进手里剩下的牌，看还差几步听牌、摸哪些牌有用。
 *
 * 参考的是 tenhou 的牌理工具，按成都麻将改了三处：
 * - **没有字牌**，只有万、条、筒三门 1-9，因此键盘就是三行九列；
 * - **可以定缺**。定缺的那一门不参与拆牌，也不进进张列表——
 *   摸到它只能打出去，把它算成「有效」会让人以为摸到就有用；
 * - **碰过、杠过也照样算**。手牌只填还没摊出去的那些，副露占掉的面子
 *   由张数倒推（见 `ChengduShanten.meldsOf`），所以十张、七张、
 *   四张甚至只剩一张单钓都能给出向听。
 *
 * 页面自上而下：向听（整页唯一的结论）→ 定缺 → 手牌与键盘 → 进张或打牌建议。
 * 手牌状态用 `rememberSaveable` 存着，转屏回来不会丢——打牌时转个屏就要重输十三张，
 * 那种事发生一次就不会再打开这个页面了。
 */
@Composable
fun ShantenScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hand by rememberSaveable(stateSaver = HandSaver) { mutableStateOf(ChengduHand.empty) }
    // 用序号而不是枚举本身：rememberSaveable 只认得基本类型，
    // -1 表示「不限」，于是「没定缺」也有一个明确的值，不必再套一层可空。
    var bannedOrdinal by rememberSaveable { mutableIntStateOf(-1) }
    val banned = MahjongSuit.entries.getOrNull(bannedOrdinal)

    ShantenContent(
        hand = hand,
        banned = banned,
        onNavigateBack = onNavigateBack,
        onAdd = { hand = hand.plus(it) },
        onRemove = { hand = hand.minus(it) },
        onClear = { hand = ChengduHand.empty },
        onBannedChange = { bannedOrdinal = it?.ordinal ?: -1 },
        modifier = modifier,
    )
}

/**
 * 牌理页的纯内容层：手牌与定缺由外部给，自己不持有状态。
 *
 * 拆成 Screen + Content 是这一套页面的惯例（见月历页、首页）：
 * 渲染测试要在没有 ViewModel、没有数据库的前提下把页面真画一遍，
 * 状态若长在 Screen 里，就画不出「已经输了十几张牌」的样子。
 */
@Composable
internal fun ShantenContent(
    hand: ChengduHand,
    banned: MahjongSuit?,
    onNavigateBack: () -> Unit,
    onAdd: (ChengduTile) -> Unit,
    onRemove: (ChengduTile) -> Unit,
    onClear: () -> Unit,
    onBannedChange: (MahjongSuit?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManoteScaffold(
        title = stringResource(R.string.mahjong_shanten_title),
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
            ShantenHeroCard(hand = hand, banned = banned)

            BannedSuitRow(banned = banned, onSelect = onBannedChange)

            HandCard(
                hand = hand,
                onAdd = onAdd,
                onRemove = onRemove,
                onClear = onClear,
            )

            ResultCard(hand = hand, banned = banned)

            Text(
                text = stringResource(R.string.mahjong_shanten_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** 手牌的存档：27 个计数本身就是最小的表示，压成列表存进 Bundle 即可。 */
private val HandSaver = listSaver<ChengduHand, Int>(
    save = { it.counts },
    restore = { ChengduHand(it) },
)

/**
 * 英雄卡：整页唯一被放大的数字。
 *
 * 显示的既可能是「2 向听」，也可能是「听牌 / 和了」——两字与三字混排时字号不跳，
 * 因为 headlineLarge 是固定的，变的只是字数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShantenHeroCard(hand: ChengduHand, banned: MahjongSuit?) {
    val colors = MaterialTheme.manote
    val shanten = ChengduShanten.of(hand, banned)
    val bannedLeft = banned?.let { hand.suitCount(it) } ?: 0
    // 张数为 3 的倍数时（十二张、九张……）这一手既不是「等摸」也不是「该打」，
    // 副露数没有意义，不如不报——报出来反而像是在替一个输错的牌型背书。
    val melds = if (hand.size % 3 == 0) 0 else ChengduShanten.meldsOf(hand)

    ManoteHeroCard {
        Text(
            text = stringResource(R.string.mahjong_shanten_hero_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHeroMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hand.isEmpty) {
                stringResource(R.string.common_unavailable)
            } else {
                shantenText(shanten)
            },
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onHero,
        )

        if (hand.isEmpty) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.mahjong_shanten_hero_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
            return@ManoteHeroCard
        }

        Spacer(modifier = Modifier.height(10.dp))
        // 四颗胶囊在窄屏上会顶出去，改用 FlowRow 让它自己折行。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ManoteHeroChip(
                stringResource(R.string.mahjong_shanten_count_format, hand.size),
            )
            if (melds > 0) {
                ManoteHeroChip(
                    stringResource(R.string.mahjong_shanten_melds_format, melds),
                )
            }
            ManoteHeroChip(
                text = banned?.let {
                    stringResource(R.string.mahjong_shanten_banned_format, it.label())
                } ?: stringResource(R.string.mahjong_shanten_banned_none),
            )
            if (bannedLeft > 0) {
                ManoteHeroChip(
                    stringResource(R.string.mahjong_shanten_banned_left_format, bannedLeft),
                )
            }
        }
    }
}

/**
 * 定缺：四颗胶囊，互斥。
 *
 * 不用卡片装：它只是英雄卡的一个参数，不是一块内容。
 * 装进卡片反而会让人以为它是「另一个设置区」，而它跟上面的向听是同一件事。
 */
@Composable
private fun BannedSuitRow(banned: MahjongSuit?, onSelect: (MahjongSuit?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.mahjong_shanten_banned_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ManoteFilterChip(
            label = stringResource(R.string.mahjong_shanten_banned_none),
            selected = banned == null,
            onClick = { onSelect(null) },
        )
        MahjongSuit.entries.forEach { suit ->
            ManoteFilterChip(
                label = suit.label(),
                selected = banned == suit,
                onClick = { onSelect(suit) },
            )
        }
    }
}

/**
 * 手牌与键盘。
 *
 * 两块放在同一张卡里：上面是「我已经有什么」，下面是「还能点什么」，
 * 中间用一道细分隔线断开。分成两张卡会让人以为它们分属两个步骤。
 *
 * 手牌是**摊开**的：同一种牌有几张就摆几张。四张三万摆成四块，
 * 一眼能看出「这一门已经快被我攥手里了」，而「3 万 ×4」写成角标还得读一遍数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandCard(
    hand: ChengduHand,
    onAdd: (ChengduTile) -> Unit,
    onRemove: (ChengduTile) -> Unit,
    onClear: () -> Unit,
) {
    ManoteCard {
        ManoteSectionHeader(
            title = stringResource(R.string.mahjong_shanten_hand_label),
            trailing = {
                if (!hand.isEmpty) {
                    ManoteTextAction(
                        label = stringResource(R.string.mahjong_shanten_clear),
                        onClick = onClear,
                        chevron = false,
                    )
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (hand.isEmpty) {
            Text(
                text = stringResource(R.string.mahjong_shanten_hand_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
                verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
            ) {
                hand.tiles().forEach { tile ->
                    repeat(hand.countOf(tile)) {
                        MahjongTileFace(
                            tile = tile,
                            width = HAND_TILE_WIDTH,
                            onClick = { onRemove(tile) },
                            contentDescription = tileDescription(tile),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // 卡片底色是暖白，`outlineVariant` 那道浅砂色压在上面几乎看不见，
        // 于是「手牌」和「键盘」会连成一片。这里改用 outline 降透明度：
        // 它要做的只是「断开」，不是「再画一条线」。
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(16.dp))

        TileKeyboard(hand = hand, onAdd = onAdd)

        Spacer(modifier = Modifier.height(12.dp))
        // 碰过、杠过的人最容易在这里输错：把副露那三张也点进手牌，
        // 张数就变成了十三张，算出来的是「没碰过的一手」。
        Text(
            text = stringResource(R.string.mahjong_shanten_melds_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 三门九列的键盘，每块牌子右上角标着「还能点几张」。
 *
 * 九张一行是这一屏的硬约束：再宽一行就放不下，只能折成两行，
 * 而折行之后「万」和「条」的边界会被行首吃掉，读起来要重新找对齐。
 * 因此牌宽不写死，一律按可用宽度等分——横屏、大屏、小屏都是九张一整行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileKeyboard(hand: ChengduHand, onAdd: (ChengduTile) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - MAHJONG_TILE_GAP * (ChengduTile.RANKS - 1)) / ChengduTile.RANKS

        Column(verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP)) {
            MahjongSuit.entries.forEach { suit ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
                ) {
                    (1..ChengduTile.RANKS).forEach { rank ->
                        val tile = ChengduTile(suit, rank)
                        val left = ChengduTile.COPIES - hand.countOf(tile)
                        MahjongTileFace(
                            tile = tile,
                            width = tileWidth,
                            // 四张都没动过时不写那个 4。开局 27 张牌子全挂着「4」，
                            // 一屏二十七个数字里没有一条信息；等真正用掉一张，数字才出现，
                            // 那一下本身就是「刚才那一点记在这儿了」的反馈。
                            badge = left.takeIf { it < ChengduTile.COPIES }?.toString(),
                            // 四张全在手上时这块牌子已经点不动了，压暗比直接消失好：
                            // 消失之后那一排会少一格，九个点数就不再对齐。
                            dimmed = left == 0,
                            onClick = if (left > 0) ({ onAdd(tile) }) else null,
                            contentDescription = tileDescription(tile),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 结果区：先看有没有和，再按张数分三种说法。
 *
 * `3n+1` 张（十三张、十张……）问「摸什么有用」，`3n+2` 张（十四张、十一张……）问「打哪张」，
 * 其余张数还没成整手，先说清楚还差几张——把一手十二张的牌硬算成「进张」，
 * 报出来的数对不上任何一次真实的摸牌。
 *
 * 张数带余数这件事本身就是「有副露」的信号：碰一组少三张，
 * 所以十张、十一张、七张、八张都是正当的牌局状态，不必刻意凑到十三张。
 * 只有余数为 0（十二张、九张……）才真的还没打完一轮。
 */
@Composable
private fun ResultCard(hand: ChengduHand, banned: MahjongSuit?) {
    if (hand.isEmpty) return

    val shanten = ChengduShanten.of(hand, banned)

    // 十一张也可能是和了（四副面子全齐、只剩将那一步已经补上），
    // 这时不该再劝人「打哪张」。
    if (shanten < 0) {
        ManoteCard {
            Text(
                text = stringResource(R.string.mahjong_shanten_win_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    when (hand.size % 3) {
        // 十四张、十一张、八张、五张、二张：先打一张
        2 -> DiscardCard(hand = hand, banned = banned)

        // 十三张、十张、七张、四张、一张：等摸一张
        1 -> WaitCard(hand = hand, banned = banned, shanten = shanten)

        else -> ManoteCard {
            Text(
                text = stringResource(R.string.mahjong_shanten_odd_count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 进张：摸到哪些牌能让这一手快一步，各自还剩几张。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitCard(hand: ChengduHand, banned: MahjongSuit?, shanten: Int) {
    val waits = ChengduShanten.effectiveTiles(hand, banned)

    ManoteCard {
        ManoteSectionHeader(title = stringResource(R.string.mahjong_shanten_waits_label))
        Spacer(modifier = Modifier.height(12.dp))

        when {
            shanten < 0 -> Text(
                text = stringResource(R.string.mahjong_shanten_win_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            waits.isEmpty() -> Text(
                text = stringResource(R.string.mahjong_shanten_waits_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                Text(
                    text = stringResource(
                        R.string.mahjong_shanten_waits_note_format,
                        waits.sumOf { it.remaining },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
                    verticalArrangement = Arrangement.spacedBy(MAHJONG_TILE_GAP),
                ) {
                    waits.forEach { wait ->
                        MahjongTileFace(
                            tile = wait.tile,
                            width = HAND_TILE_WIDTH,
                            badge = wait.remaining.toString(),
                            contentDescription = tileDescription(wait.tile),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 打牌建议：给出几条候选，好的排在前面。
 *
 * 只列前四条。第十四张牌有几种打法最多也就五六种，全都列出来反而要读完才敢动手；
 * 前四条已经覆盖了「稳妥」「搏一把」这些实际会选的走法。
 */
@Composable
private fun DiscardCard(hand: ChengduHand, banned: MahjongSuit?) {
    val options = ChengduShanten.discardOptions(hand, banned).take(MAX_DISCARD_OPTIONS)

    ManoteCard {
        ManoteSectionHeader(title = stringResource(R.string.mahjong_shanten_discards_label))
        Spacer(modifier = Modifier.height(10.dp))

        options.forEachIndexed { index, option ->
            DiscardRow(option = option)
            if (index != options.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DiscardRow(option: DiscardOption) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MahjongTileFace(
            tile = option.tile,
            width = DISCARD_TILE_WIDTH,
            contentDescription = tileDescription(option.tile),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shantenText(option.shanten),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (option.shanten < 0) {
                    stringResource(R.string.mahjong_shanten_win_note)
                } else {
                    stringResource(R.string.mahjong_shanten_ukeire_format, option.ukeire)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (option.bannedSuit) {
            Text(
                text = stringResource(R.string.mahjong_shanten_banned_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 向听的中文说法：-1 和了、0 听牌、其余报步数。 */
@Composable
private fun shantenText(shanten: Int): String = when {
    shanten < 0 -> stringResource(R.string.mahjong_shanten_win)
    shanten == 0 -> stringResource(R.string.mahjong_shanten_tenpai)
    else -> stringResource(R.string.mahjong_shanten_tiles_format, shanten)
}

/** 读屏用的一句话，例如「3 筒」。 */
@Composable
private fun tileDescription(tile: ChengduTile): String =
    stringResource(R.string.mahjong_tile_format, tile.rank, tile.suit.label())

/** 手牌与进张里的牌宽：比键盘略大一点，因为这里没有九张一行的硬约束。 */
private val HAND_TILE_WIDTH = 34.dp

/** 打牌建议里的牌宽：它跟着一行文字走，不能比那一行高太多。 */
private val DISCARD_TILE_WIDTH = 28.dp

/** 打牌建议最多列几条。 */
private const val MAX_DISCARD_OPTIONS = 4
