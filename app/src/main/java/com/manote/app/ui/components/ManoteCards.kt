package com.manote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manote.app.ui.theme.ManoteElevation
import com.manote.app.ui.theme.manote

/**
 * 卡片投影：把「浮起来一层」这件事收在一个修饰符里。
 *
 * 投影色不取纯黑而取 [ManoteExtendedColors.shadowColor]：米白底上的黑投影会显脏，
 * 偏暖的褐灰才像纸片挡住光。API 28 以下系统会退回黑投影，这只是颜色上的差别，
 * 高度关系仍然成立。
 *
 * `clip = false` 是刻意的：阴影要画在形状**之外**，卡片自身的内容才有地方溢出
 * （英雄卡的水印圆就靠这个特性被 Surface 裁掉，而不是被这里提前切一刀）。
 */
@Composable
fun Modifier.manoteSoftShadow(
    shape: Shape,
    elevation: Dp = ManoteElevation.card,
): Modifier {
    val shadowColor = MaterialTheme.manote.shadowColor
    return shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
}

/**
 * 内容卡片：磨砂玻璃底、大圆角、一圈白色高光边、一层很淡的投影。
 *
 * 参考稿里卡片不是「白纸上画框」，而是三样东西叠出来的：
 * 比页面亮一档的底、边缘一道几乎看不见的白线、以及一圈把卡片托离页面的阴影。
 * 描边换成白色后，卡片在暖色背景上有了「玻璃边缘」，而不只是画了一条分割线。
 *
 * 底色不是一块平色而是**玻璃渐变**（见 [ManoteExtendedColors.cardSurface]）：
 * 带一点透，背后的晨光会渗上来，卡片才不会像一块贴上去的白纸。
 *
 * 传了 [onClick] 时整张卡可点击（规格 3.6：点击区域至少 48dp，
 * 卡片内边距加内容高度必然超过这个下限）。
 *
 * @param containerColor 传了就整块用这个平色（例如需要与画布区分的地方）；
 *   不传则用卡片默认的玻璃渐变。
 */
@Composable
fun ManoteCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color = MaterialTheme.manote.glassBorder,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.manote
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .manoteSoftShadow(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .manotePress(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        // 底色画在内层：底色是一道渐变，而 Surface 只收一个平色。
        // 内容色显式给 onSurface，替换掉原先由 surface 推导出来的那一份。
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (containerColor == null) {
                        Modifier.background(colors.cardSurface)
                    } else {
                        Modifier.background(containerColor)
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 英雄卡：一块深墨绿实底、比其他卡片更圆，用来托住整页最大的那个数字。
 *
 * 各页顶部固定出现一张，于是「最重要的数在哪里」在全应用是同一个位置，
 * 不必每翻一页重新找。卡内文字默认走 [ManoteExtendedColors.onHero]，
 * 调用方只在需要次级色（如小标签）时才显式指定 [ManoteExtendedColors.onHeroMuted]。
 *
 * 卡面分三段：顶端受光、卡身、下缘背光（[ManoteExtendedColors.heroSurfaceBottom]）。
 * 两段渐变只能画出「斜着亮一点」，三段才画得出「一块有厚度的牌子」。
 * 在受光那一侧再加一团极淡的圆光、右上角两枚圆是「二筒」的水印——
 * 与顶栏的品牌标记、空态里的牌面同一意象。
 * 与 [ManoteCard] 一样不投阴影：深色底本身已经是最强的一层对比，
 * 再叠阴影只会在深色主题下糊成一团。
 */
@Composable
fun ManoteHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.manote
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.heroSurface,
        contentColor = colors.onHero,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to colors.heroSurfaceTop,
                            0.55f to colors.heroSurface,
                            1f to colors.heroSurfaceBottom,
                        ),
                    )
                    // 受光侧的一团柔光：圆心在卡片左上角外侧，只把左上角提亮一点。
                    val glowRadius = size.width * 0.85f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.onHeroMuted.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            center = Offset(-size.width * 0.08f, -glowRadius * 0.55f),
                            radius = glowRadius,
                        ),
                    )
                    // 二筒水印：两枚圆压在右上角，超出边界的部分被 Surface 的圆角裁掉。
                    // 颜色从 onHeroMuted 压低透明度派生，两个主题共用同一份参数。
                    val watermark = colors.onHeroMuted.copy(alpha = 0.10f)
                    val radius = 46.dp.toPx()
                    drawCircle(
                        color = watermark,
                        radius = radius,
                        center = Offset(size.width - radius * 0.7f, -radius * 0.35f),
                    )
                    drawCircle(
                        color = watermark,
                        radius = radius,
                        center = Offset(size.width - radius * 2.0f, radius * 0.55f),
                    )
                }
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 区块标题：左边一枚品牌色圆点 + 标题，右侧可放一个动作入口。
 *
 * 早先这里是一道竖色条。竖条在密集列表里很像「引用块」，
 * 换成圆点之后它退成一句「这一段从这里开始」，标题本身才是主角；
 * 圆点的直径与卡内小标签前的记号同尺寸，于是全页的分组记号是一致的。
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
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.size(9.dp))
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
