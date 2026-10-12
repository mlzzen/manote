package com.manote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.manote.app.ui.theme.manote

/**
 * 内容卡片：白底、大圆角、一圈很淡的描边。
 * 描边比阴影更贴合参考稿，也能在深色主题下有稳定边界。
 *
 * 传了 [onClick] 时整张卡可点击（规格 3.6：点击区域至少 48dp，
 * 卡片内边距加内容高度必然超过这个下限）。
 */
@Composable
fun ManoteCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 英雄卡：一块深墨绿实底、比其他卡片更圆，用来托住整页最大的那个数字。
 *
 * 各页顶部固定出现一张，于是「最重要的数在哪里」在全应用是同一个位置，
 * 不必每翻一页重新找。卡内文字默认走 [ManoteExtendedColors.onHero]，
 * 调用方只在需要次级色（如小标签）时才显式指定 [ManoteExtendedColors.onHeroMuted]。
 *
 * 与 [ManoteCard] 一样不投阴影：深色底本身已经是最强的一层对比，
 * 再叠阴影只会在深色主题下糊成一团。
 */
@Composable
fun ManoteHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.manote
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.heroSurface,
        contentColor = colors.onHero,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 区块标题：左侧一道品牌色短条 + 标题，右侧可放一个动作入口。
 *
 * 短条替代了原来的纯文字标题。全应用的区块很多（走势、分时段、连输连赢、
 * 最近场次……），加一道色条之后它们的层级一眼可见，也不必靠加大字号去压内容。
 */
@Composable
fun ManoteSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * 文字动作（如「查看全部」），点击区最小 48dp。
 *
 * [contentColor] 可以让它落在深色底上（英雄卡里的「修改昵称」），
 * 默认仍是品牌主色。
 */
@Composable
fun ManoteTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 一栏小读数：标签在上、数值在下，可再挂一行小字说明（例如「10.05 · 2 场」）。
 *
 * 字号刻意比 [Statistic]-类的大数字小一档：这类读数常常三栏并排，
 * 用 titleMedium 会让「+¥1,234.00」在窄栏里被挤掉。
 * 放进 Row 时由调用方给 `Modifier.weight(1f)`，各栏因此严格等宽。
 */
@Composable
fun ManoteReadout(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
