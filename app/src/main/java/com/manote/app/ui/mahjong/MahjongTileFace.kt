package com.manote.app.ui.mahjong

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manote.app.R
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.ui.components.manoteClickable
import com.manote.app.ui.components.manotePress
import com.manote.app.ui.theme.manote

/**
 * 牌面高度 / 宽度。真麻将牌是一块竖着的长方形，做成正方形会立刻不像牌。
 */
private const val TILE_ASPECT = 1.36f

/** 牌面里的数字占多宽：牌越窄字越小，但比例不变，一排九张才不会有的地方挤、有的地方空。 */
private const val RANK_FONT_RATIO = 0.46f

/** 花色名那一行的字号比例。它只是给数字作注，不能和数字抢位置。 */
private const val SUIT_FONT_RATIO = 0.24f

/** 角标的字号比例。比花色名再小一档：它是一句注解，不该和牌面的点数抢注意力。 */
private const val BADGE_FONT_RATIO = 0.21f

/**
 * 牌与牌、行与行之间的间隙。
 *
 * 键盘、手牌、记牌器三处共用同一个值：牌子本身已经带一道描边，
 * 间隙再大一点就会散成一堆独立的小方块，看不出「一手牌」。
 */
internal val MAHJONG_TILE_GAP = 4.dp

/** 花色的名字：万 / 条 / 筒。 */
@Composable
fun MahjongSuit.label(): String = stringResource(
    when (this) {
        MahjongSuit.WAN -> R.string.mahjong_suit_wan
        MahjongSuit.TIAO -> R.string.mahjong_suit_tiao
        MahjongSuit.TONG -> R.string.mahjong_suit_tong
    },
)

/** 花色的牌面颜色。 */
@Composable
fun MahjongSuit.accent(): Color {
    val colors = MaterialTheme.manote
    return when (this) {
        MahjongSuit.WAN -> colors.tileWan
        MahjongSuit.TIAO -> colors.tileTiao
        MahjongSuit.TONG -> colors.tileTong
    }
}

/**
 * 一块麻将牌。
 *
 * 牌理与记牌器共用它：两页上出现的每一种牌都必须是同一个形状、同一个色相，
 * 于是「三万」在键盘上、在手牌里、在进张列表里是同一块牌子，不必重新认。
 *
 * 牌面刻意不套用卡片那套玻璃材质：卡片是透的、是「一层界面」，
 * 牌是实的、是「一块东西」。所以这里是纯色底 + 一道米色描边，
 * 连投影都不加——一堆牌排在一起时，阴影只会把间距吃掉。
 *
 * @param width 牌宽；高度按 [TILE_ASPECT] 自动算出来。
 * @param badge 右上角的小数字，用来标「还剩几张」。
 * @param dimmed 压暗：这张牌已经用完了（四张全在手上，或已被点走）。
 * @param onClick 传了就整块可点，按压时微微回缩。
 * @param contentDescription 读屏读的一句话；不给就把数字与花色分开读。
 */
@Composable
fun MahjongTileFace(
    tile: ChengduTile,
    modifier: Modifier = Modifier,
    width: Dp = 34.dp,
    badge: String? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.manote
    val accent = tile.suit.accent()
    val shape = RoundedCornerShape(width * 0.20f)

    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .manotePress(interaction, pressedScale = 0.92f)
            .manoteClickable(interaction, shape, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(width = width, height = width * TILE_ASPECT)
            .alpha(if (dimmed) 0.34f else 1f)
            .background(colors.tileFace, shape)
            .border(1.dp, colors.tileBorder, shape)
            .then(clickModifier)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tile.rank.toString(),
                fontSize = (width.value * RANK_FONT_RATIO).sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = tile.suit.label(),
                fontSize = (width.value * SUIT_FONT_RATIO).sp,
                color = accent.copy(alpha = 0.72f),
            )
        }

        if (badge != null) {
            Text(
                text = badge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = width * 0.04f, end = width * 0.13f),
                fontSize = (width.value * BADGE_FONT_RATIO).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}
