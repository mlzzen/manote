package com.manote.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manote.app.R
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.timeSlotLabel
import com.manote.app.ui.theme.manote

/**
 * 玩家头像：圆角方块里放昵称首字，模仿麻将牌的方形轮廓。
 * 传空昵称时回落为「?」，不崩溃也不留空白。
 *
 * 默认用品牌容器色而不是中性灰：一屏里往往要并排三个头像，
 * 灰底方块会连成一片看不出边界，浅绿底既能分开彼此，也把品牌色带进了列表。
 */
@Composable
fun PlayerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(size * 0.34f),
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val initial = remember(name) { name.trim().firstCodePointOrNull() ?: "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = contentColor,
            fontSize = (size.value * 0.40f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 输赢标签。同时给出文字（赢 / 输 / 持平）与颜色，
 * 颜色只是辅助，不是唯一线索（规格 2.1、3.6）。
 */
@Composable
fun ResultBadge(
    netMinor: Long,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.manote
    val label = stringResource(
        when {
            netMinor > 0L -> R.string.result_win
            netMinor < 0L -> R.string.result_loss
            else -> R.string.result_draw
        },
    )
    val background = when {
        netMinor > 0L -> colors.winBadge
        netMinor < 0L -> colors.lossBadge
        else -> colors.drawBadge
    }
    val content = when {
        netMinor > 0L -> colors.onWinBadge
        netMinor < 0L -> colors.onLossBadge
        else -> colors.onDrawBadge
    }
    ManotePill(
        text = label,
        modifier = modifier,
        containerColor = background,
        contentColor = content,
        verticalPadding = 3.dp,
    )
}

/**
 * 时段标签：早场 / 下午场 / 晚场。
 *
 * 用品牌容器色的底块而不是纯文字或中性灰块，让它在日期旁边一眼可辨，
 * 又不会抢金额和输赢标签的位置。场次列表、最近场次与场次详情共用，
 * 分时段战绩表也复用同一个标签，保证四处在讲同一件事时用的是同一个符号。
 */
@Composable
fun TimeSlotTag(slot: TimeSlot, modifier: Modifier = Modifier) {
    ManotePill(
        text = timeSlotLabel(slot),
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelSmall,
        horizontalPadding = 6.dp,
        verticalPadding = 1.dp,
    )
}

/**
 * 通用胶囊标签：一块底色 + 一行小字。
 *
 * 时段标签、英雄卡里的「已记 N 场 / 胜率」，以及各类计数徽标都落在它上面，
 * 于是全应用的小标签只有一种圆角、一种内边距节奏，不再各写各的 Box。
 * 默认横向内边距比纵向大得多，贴近胶囊而不是按钮。
 */
@Composable
fun ManotePill(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp,
    maxLines: Int = 1,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = style,
            maxLines = maxLines,
        )
    }
}

/** 带符号金额，例如 `+¥12.30`、`-¥5.00`、`¥0.00`。颜色随输赢变化。 */
@Composable
fun SignedAmount(
    netMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
) {
    val colors = MaterialTheme.manote
    val color = when {
        netMinor > 0L -> colors.win
        netMinor < 0L -> colors.loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = UiText.signedYuan(netMinor),
        style = style ?: MaterialTheme.typography.titleMedium,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 筛选胶囊。外层点击区最小 48dp（规格 3.6），内层视觉胶囊 38dp，
 * 兼顾手指命中率与视觉密度。
 */
@Composable
fun ManoteFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .manotePress(interaction, pressedScale = 0.95f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .wrapContentHeight(Alignment.CenterVertically),
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.height(38.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 主操作按钮。最小高度 52dp，禁用态由调用方通过 [enabled] 控制。按下时轻微回缩。 */
@Composable
fun ManotePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .manotePress(interaction, pressedScale = 0.98f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * 空态：两张斜放的「牌」做底，前面一枚圆形图标，再跟标题与说明。
 *
 * 背后两张牌是全应用唯一的纯装饰元素 —— 空态本来就没有信息可读，
 * 与其留一大片白，不如把「麻记」这个身份放上去。
 * 装饰用 `alpha` 压到很淡，且不参与语义，读屏时会被跳过（`contentDescription = null`）。
 */
@Composable
fun ManoteEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 96.dp, height = 72.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStateTile(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-14).dp, y = (-4).dp)
                    .rotate(-12f),
                color = brand.copy(alpha = 0.10f),
            )
            EmptyStateTile(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 14.dp, y = 4.dp)
                    .rotate(10f),
                color = brand.copy(alpha = 0.16f),
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空态背景里的一张「牌」：一个竖长的圆角方块。 */
@Composable
private fun EmptyStateTile(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
    )
}

/**
 * 胜 / 负 / 平 占比条。
 *
 * 段与段之间留 2dp 空隙：三色紧贴时相邻两段的边界会糊在一起，
 * 尤其在红绿这对互补色之间。空隙让「哪一段有多长」读得出来。
 * 全部为 0 时只画一条空轨道。
 */
@Composable
fun ManoteShareBar(
    winCount: Int,
    lossCount: Int,
    drawCount: Int,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val colors = MaterialTheme.manote
    val total = winCount + lossCount + drawCount
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (total <= 0) return@Box
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (winCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(winCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.win),
                )
            }
            if (lossCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(lossCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.loss),
                )
            }
            if (drawCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(drawCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.draw),
                )
            }
        }
    }
}

/** 取首个 Unicode 码点，避免把 emoji 等代理对截成半个字符。 */
private fun String.firstCodePointOrNull(): String? {
    if (isEmpty()) return null
    return String(Character.toChars(codePointAt(0)))
}
