package com.manote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
 *
 * @param selected 多选模式下的勾选状态。传 null 表示不在多选模式（首页与平常的列表），
 *   此时行内不画勾选圈；传 true/false 时左侧多一枚圆圈，选中时整行描边换成品牌色——
 *   只靠一枚 20dp 的圆点表示「选中了」，扫视一列时很容易看漏。
 */
@Composable
fun ManoteSessionRow(
    row: SessionRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    val colors = MaterialTheme.manote
    val stripeColor = when {
        row.selfNetMinor > 0L -> colors.win
        row.selfNetMinor < 0L -> colors.loss
        else -> colors.draw
    }
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val cardBorder = when (selected) {
        true -> BorderStroke(SELECTED_STROKE, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, colors.glassBorder)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 与内容卡片同一层高度与同一圈白色高光边：
            // 场次行也是一张卡片，只是更矮，它不该看起来像另一种材质。
            .manoteSoftShadow(shape)
            // 整张行一起缩放：结果色边、头像与金额是一块内容，按下时同进同退。
            .manotePress(interaction),
        shape = shape,
        // 与内容卡片同一层顶光；底色画在内层，顺序因此是「底光 → 结果色边 → 内容」
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder,
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
            if (selected != null) {
                SelectionMark(checked = selected)
                Spacer(modifier = Modifier.width(10.dp))
            }

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

/** 选中时整行的描边宽度：比默认那圈白线粗一点，扫视时才有「这一行被框住了」的感觉。 */
private val SELECTED_STROKE = 1.5.dp

/**
 * 多选模式的勾选圈。
 *
 * 未选中是一个空心圆，选中是品牌色实心圆加一枚勾——两种状态共用一个轮廓尺寸，
 * 勾上勾下时行内其它内容不会左右挪动。
 */
@Composable
private fun SelectionMark(checked: Boolean) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(
                if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
