package com.manote.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manote.app.R
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.model.UiText
import com.manote.app.ui.model.timeSlotLabel
import com.manote.app.ui.theme.ManoteElevation
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

/**
 * 一栏结果统计块：顶部一道结果色短条，下面是标签、场数与金额。
 *
 * 那道短条是这一块里唯一的高饱和色。三块并排时，先看到的是三道红绿黄，
 * 于是「赢得多还是输得多」在读到数字之前就已经成立。
 *
 * 首页「战绩概览」与牌友详情「累计战绩」共用它：两页在对同一件事
 * （赢 / 输 / 平各自几场、各多少钱）时用的是同一种符号与同一种节奏。
 *
 * @param amountMinor 这一栏的金额（分）。输的那一栏由调用方传入**负值**，
 *   这样右下的金额与短条的颜色说的是同一个方向。
 */
@Composable
fun ManoteResultTile(
    label: String,
    count: Int,
    amountMinor: Long,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    countStyle: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = countStyle,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = UiText.signedYuan(amountMinor),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * 搜索框：一条无边框的浅色胶囊。
 *
 * 参考稿里搜索框就是「一块比页面略深的底色」，没有描边、没有填充高亮、没有标签。
 * M3 的 OutlinedTextField 自带 56dp 高度、四角描边与浮动标签，放进这一屏
 * 会比上下两张卡片都显眼——所以这里用 [BasicTextField] 自己铺装饰层：
 * 高度固定 48dp、圆角取满、底色走 `surfaceVariant`，与筛选胶囊同一种材质。
 *
 * 无边框不等于无焦点提示：光标用品牌色，聚焦时整条胶囊的底色提亮一档。
 * 无障碍上，[placeholder] 就是输入框的可见标签（读屏会把它当描述读出来）。
 */
@Composable
fun ManoteSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (focused) colors.surface else colors.surfaceVariant,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(9.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

/**
 * 表单输入框的统一配色：软填充、无常态描边。
 *
 * 默认的 M3 `OutlinedTextField` 是一圈细线加透明底，放在改版后的大圆角玻璃卡上
 * 会显得像张表格——描边是这套设计里最不该出现的东西。这里改成：
 * - 常态是**一块比卡片略深的软底**，靠底色而不是线框划出输入区；
 * - 聚焦时底色不变，只把描边点亮成品牌绿，光标也是同一个颜色；
 * - 出错时描边换成 error，与下面那行错误文案同一色。
 *
 * 形状仍由各调用点传 `MaterialTheme.shapes.small`，与胶囊、卡片同一套圆角语言。
 */
@Composable
fun manoteTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = MaterialTheme.colorScheme.error,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/**
 * 分段控件：一个浅色胶囊里排几段，选中那段是浮起来的白色药丸。
 *
 * 与「一排各自独立的胶囊」相比，它把「这几个选项互斥」写进了形状里——
 * 独立胶囊看起来像可以多选。参考稿的 1月 / 3月 / 全部 就是这一种。
 *
 * 整条高度 48dp（内层 40dp + 上下各 4dp 留白），满足规格 3.6 的最小点击区；
 * 每段等宽（`weight(1f)`），所以切换时文字长短不会让药丸抖动。
 * 选中态的白色药丸带一点投影，它才是「浮在上面」的那个平面。
 */
@Composable
fun ManoteSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(
                        if (selected) {
                            Modifier.manoteSoftShadow(CircleShape, ManoteElevation.control)
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .manotePress(interaction, pressedScale = 0.96f)
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 顶栏的圆形图标按钮：白底、小投影、40dp 视觉尺寸。
 *
 * 参考稿的顶栏右侧是一排这样的圆钮，而不是一个挨一个的扁平图标——
 * 它们因此像「桌上的几枚筹码」浮在页面之上，与下面那些卡片同一个高度语言。
 * 视觉 40dp、点击区 48dp（外层 Box 撑满），两者分开，圆钮才不会被撑大。
 */
@Composable
fun ManoteRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .manotePress(interaction, pressedScale = 0.9f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .manoteSoftShadow(CircleShape, ManoteElevation.control)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
