package com.manote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.manote.app.ui.model.UiText
import com.manote.app.ui.theme.manote

/**
 * 玩家头像：圆角方块里放昵称首字，模仿麻将牌的方形轮廓。
 * 传空昵称时回落为「?」，不崩溃也不留空白。
 */
@Composable
fun PlayerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(size * 0.34f),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 麻将牌：竖圆角方块 + 一个牌面字符，用于头图卡的装饰。 */
@Composable
fun MahjongTile(
    glyph: String,
    modifier: Modifier = Modifier,
    faceColor: Color,
    glyphColor: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(faceColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
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
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
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
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
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

/** 主操作按钮。最小高度 52dp，禁用态由调用方通过 [enabled] 控制。 */
@Composable
fun ManotePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 空态：一个柔和的圆形图标 + 标题 + 说明。 */
@Composable
fun ManoteEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
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

/** 取首个 Unicode 码点，避免把 emoji 等代理对截成半个字符。 */
private fun String.firstCodePointOrNull(): String? {
    if (isEmpty()) return null
    return String(Character.toChars(codePointAt(0)))
}
