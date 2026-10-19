package com.manote.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.ManoteElevation
import com.manote.app.ui.theme.ManoteMotion
import com.manote.app.ui.theme.manote
import kotlin.math.roundToInt

/** 底栏里的槽位总数：四个页签 + 中间留给「记一场」的那一格。 */
private const val BAR_SLOTS = 5

/**
 * 悬浮式底部导航：磨砂玻璃胶囊 + 中心抬起的「记一场」按钮。
 *
 * 选中态是一块**深墨绿圆角块**，未选中项完全透明。参考稿把这件事讲得很清楚：
 * 一排中性图标里只有一个是深色实块，眼睛先落到它上面，再去看它下面那行字——
 * 这比「图标换个颜色」强得多，尤其在暖光背景上，深块是整屏对比最强的一处。
 *
 * **这块现在是独立的一层，不再分别画在每一项里。** 因为页面改成横向滑动之后，
 * 底栏必须跟着手指走：滑到一半时深色块应该正停在两格之间，而不是等松手才「变」过去。
 * 画在 item 里的块做不到这件事——它只能整块出现在某一格。
 * 所以块被提到 Row 之外单独定位，位置由 [pageOffset] 给出的连续页码算出来。
 *
 * 位置读取刻意放在 [Modifier.offset] 的 lambda 里（布局阶段执行）：
 * 拖动时每帧只需要重新摆放这一块，底栏的其他部分不会跟着重组。
 * 反过来说，**图标与文字的颜色不能这样读**——那会变成整条底栏每帧重组；
 * 它们的颜色仍走 [animateManoteSelectionColor]，以「当前停靠页」为目标渐变。
 *
 * 顶部预留 22dp 让圆形按钮凸出，并被计入 Scaffold 的底栏高度，
 * 因此不会遮住页面内容。底部自带导航栏内边距。
 *
 * 每个页签的点击区约 56dp 高、宽度为五分之一屏（规格 3.6：至少 48dp）；
 * 图标不单独设可访问描述，页签的文字标签就是它的无障碍名称。
 */
@Composable
fun ManoteBottomBar(
    current: ManoteTab,
    onSelect: (ManoteTab) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 页面的连续位置相对 [current] 的偏移，范围 -0.5～0.5。
     *
     * 传的是函数而不是值：调用方若直接传 `pagerState.currentPageOffsetFraction`，
     * 这个值每帧都在变，底栏就会每帧重组一次——拖动时正好是最吃帧的时候。
     * 包成 lambda 之后，读取发生在布局阶段，只有选中块的位置会跟着更新。
     */
    pageOffset: () -> Float = { 0f },
) {
    val colors = MaterialTheme.manote
    val barShape = RoundedCornerShape(30.dp)
    val itemShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
    ) {
        Column {
            Spacer(modifier = Modifier.height(22.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .manoteSoftShadow(barShape, ManoteElevation.floatingBar),
                shape = barShape,
                // 底色画在内层：Surface 只收一个平色，而这里要的是卡片那道玻璃渐变。
                // 这一行不能省——省掉之后 Surface 只剩一圈描边，整条底栏看上去就是
                // 「页面上飘着一根白线」，而不是一块玻璃。
                color = Color.Transparent,
                border = BorderStroke(1.dp, colors.glassBorder),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.cardSurface),
                ) {
                    // 五个等宽槽位，横向内边距 6+6 之外才是可分配宽度
                    val slotWidth = (maxWidth - 12.dp) / BAR_SLOTS

                    // 选中块先画，图标与文字压在上面
                    Box(
                        modifier = Modifier
                            .offset {
                                val position =
                                    ManoteTab.barSlotAt(current.ordinal + pageOffset())
                                IntOffset(
                                    x = (8.dp.roundToPx() + position * slotWidth.toPx()).roundToInt(),
                                    y = 5.dp.roundToPx(),
                                )
                            }
                            .size(width = slotWidth - 4.dp, height = 54.dp)
                            .background(colors.heroSurface, itemShape),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ManoteTabItem(ManoteTab.HOME, current, onSelect)
                        ManoteTabItem(ManoteTab.SESSIONS, current, onSelect)
                        // 中心留给抬起的「记一场」按钮
                        Spacer(modifier = Modifier.weight(1f))
                        ManoteTabItem(ManoteTab.FRIENDS, current, onSelect)
                        ManoteTabItem(ManoteTab.SETTINGS, current, onSelect)
                    }
                }
            }
        }

        val fabInteraction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = onNewSession,
            interactionSource = fabInteraction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .manotePress(fabInteraction, pressedScale = 0.92f)
                // 一圈背景色描边把按钮从浅色胶囊上「抬」出来。
                // 只加 elevation 在浅色底上几乎看不出来，这道环才是它凸出的依据。
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                .padding(3.dp),
            shape = CircleShape,
            containerColor = colors.heroSurface,
            contentColor = colors.onHero,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.action_record_session),
            )
        }
    }
}

@Composable
private fun RowScope.ManoteTabItem(
    tab: ManoteTab,
    current: ManoteTab,
    onSelect: (ManoteTab) -> Unit,
) {
    val colors = MaterialTheme.manote
    val isSelected = current == tab
    val interaction = remember { MutableInteractionSource() }
    val itemShape = RoundedCornerShape(20.dp)

    // 前景只有一条动画：图标和文字的目标色永远相同，拆成两条只会让这一项
    // 每帧多做一次无效的状态读取与重组。底色块已经提到外面单独画了。
    val foregroundColor = animateManoteSelectionColor(
        target = if (isSelected) colors.onHero else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 选中那枚图标轻微放大：颜色之外的第二条线索，翻页签时先看到「跳」再看清色。
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.9f,
        animationSpec = ManoteMotion.pressSpring(),
        label = "tabIconScale",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // 2dp 的水平内缩让深色块与相邻页签之间留出缝，块才不会连成一片
            .padding(horizontal = 2.dp)
            .clip(itemShape)
            .manotePress(interaction, pressedScale = 0.94f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { onSelect(tab) },
            )
            .semantics { this.selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = foregroundColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = foregroundColor,
                maxLines = 1,
            )
        }
    }
}
