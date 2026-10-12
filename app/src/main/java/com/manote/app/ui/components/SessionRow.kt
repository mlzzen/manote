package com.manote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote

/**
 * 场次列表的一行：结果色边、日期、时段标签与标题、其余三位牌友头像、本人结果标签与金额。
 *
 * 左侧那道 4dp 的色条按本人这一场的输赢上色，是整行里最先被看到的东西：
 * 一列场次排下来，滚动的过程中先读到的是一条红绿相间的条纹，
 * 而不是逐行去读右下角的小字。颜色只是加速器——结果标签与金额仍然同时在场，
 * 不依赖颜色单独表达输赢（规格 2.1、3.6）。
 *
 * 首页「最近场次」和场次列表共用，保证两处观感与信息一致。
 * 整行可点击，点击区远超 48dp（规格 3.6）。
 */
@Composable
fun ManoteSessionRow(
    row: SessionRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.manote
    val stripeColor = when {
        row.selfNetMinor > 0L -> colors.win
        row.selfNetMinor < 0L -> colors.loss
        else -> colors.draw
    }
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 整张行一起缩放：结果色边、头像与金额是一块内容，按下时同进同退。
            .manotePress(interaction),
        shape = shape,
        // 与内容卡片同一层顶光；底色画在内层，顺序因此是「底光 → 结果色边 → 内容」
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.manote.cardSurface)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                // 画在内容之下、按当前节点（含内边距）的完整高度铺满，
                // 并被上面的 clip 裁成左侧的圆角边，不必额外的容器去承载它。
                .drawBehind {
                    drawRect(
                        color = stripeColor,
                        size = Size(STRIPE_WIDTH.toPx(), size.height),
                    )
                }
                .padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UiText.date(row.playedAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeSlotTag(slot = row.timeSlot)
                    if (row.hasCustomTitle) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 标题自己省略，而不是把左边的时段标签挤掉
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.opponentNames.forEach { name ->
                    PlayerAvatar(name = name, size = 24.dp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
            ResultBadge(netMinor = row.selfNetMinor)
            Spacer(modifier = Modifier.width(6.dp))
            SignedAmount(netMinor = row.selfNetMinor)
        }
    }
}

/** 结果色边的宽度。 */
private val STRIPE_WIDTH = 4.dp
