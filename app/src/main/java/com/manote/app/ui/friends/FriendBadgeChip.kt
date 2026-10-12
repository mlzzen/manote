package com.manote.app.ui.friends

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.model.FriendBadge
import com.manote.app.ui.theme.manote

/**
 * 牌友标签（最苦主 / 福星 / 牌王）的小胶囊。
 *
 * 牌友列表把它挂在昵称旁，牌友详情把它挂在英雄卡上，两处共用这一个组件：
 * 同一个标签在两页的字号、内边距与配色因此完全一致，改文案或调色也只需动这里。
 *
 * 文案本身就是含义（最苦主 / 福星 / 牌王），颜色只负责让它在名单里跳出来，
 * 与全应用「不靠颜色单独表达信息」的做法一致。
 * 配色各挑一个语义：最苦主用中性的米黄、福星用金黄（好事）、牌王用品牌墨绿的浅底。
 *
 * @param onHero 是否压在英雄卡的深墨绿上。深底需要另一组亮色，理由与英雄卡里的输赢色相同：
 *   卡面在两个主题下都是深底，而列表上那套「浅底深字」的容器色在深色主题里本身就偏暗，
 *   品牌容器色更是与卡面几乎同色，压上去等于没画。
 */
@Composable
internal fun FriendBadgeChip(badge: FriendBadge, onHero: Boolean = false) {
    val colors = MaterialTheme.manote
    val background: Color
    val content: Color
    if (onHero) {
        background = when (badge) {
            FriendBadge.WORST_RIVAL -> colors.heroBadgeNeutral
            FriendBadge.LUCKY_STAR -> colors.heroBadgeGold
            FriendBadge.TABLE_KING -> colors.heroBadgeBrand
        }
        content = when (badge) {
            FriendBadge.WORST_RIVAL -> colors.onHeroBadgeNeutral
            FriendBadge.LUCKY_STAR -> colors.onHeroBadgeGold
            FriendBadge.TABLE_KING -> colors.onHeroBadgeBrand
        }
    } else {
        background = when (badge) {
            FriendBadge.WORST_RIVAL -> MaterialTheme.colorScheme.tertiaryContainer
            FriendBadge.LUCKY_STAR -> colors.goldContainer
            FriendBadge.TABLE_KING -> MaterialTheme.colorScheme.primaryContainer
        }
        content = when (badge) {
            FriendBadge.WORST_RIVAL -> MaterialTheme.colorScheme.onTertiaryContainer
            FriendBadge.LUCKY_STAR -> colors.onGoldContainer
            FriendBadge.TABLE_KING -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    ManotePill(
        text = badge.label(),
        shape = MaterialTheme.shapes.small,
        containerColor = background,
        contentColor = content,
        style = MaterialTheme.typography.labelSmall,
        horizontalPadding = 6.dp,
        verticalPadding = 2.dp,
    )
}

/**
 * 标签 → 文案。
 *
 * 只有 @Composable 里才能调 stringResource，因此不能直接给枚举加一个属性；
 * 这里用穷尽 `when`，将来加标签时编译期就会提醒补文案。
 */
@Composable
internal fun FriendBadge.label(): String = stringResource(
    when (this) {
        FriendBadge.WORST_RIVAL -> R.string.friends_worst_rival_badge
        FriendBadge.LUCKY_STAR -> R.string.friends_lucky_badge
        FriendBadge.TABLE_KING -> R.string.friends_table_king_badge
    },
)
