package com.manote.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.ManoteMotion

/**
 * 悬浮式底部导航：白色胶囊 + 中心抬起的「记一场」按钮。
 *
 * 顶部预留 22dp 让圆形按钮凸出，并被计入 Scaffold 的底栏高度，
 * 因此不会遮住页面内容。底部自带导航栏内边距。
 *
 * 选中态是渐变出来的：页签的底色块、图标与文字颜色都走
 * [animateManoteSelectionColor] 的同一档时长，翻页签时颜色是「流」过去的；
 * 图标再带一点弹性缩放，让新选中的那枚有一下的「跳」。
 * 每个页签的点击区约 62dp 高、宽度为四分之一屏（规格 3.6：至少 48dp）；
 * 图标不单独设可访问描述，页签的文字标签就是它的无障碍名称。
 */
@Composable
fun ManoteBottomBar(
    current: ManoteTab?,
    onSelect: (ManoteTab) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 6.dp),
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
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
    current: ManoteTab?,
    onSelect: (ManoteTab) -> Unit,
) {
    val selected = current == tab
    val interaction = remember { MutableInteractionSource() }

    // 三处颜色同一步渐变：底色块、图标、文字。选中与未选中各取一端。
    val containerColor = animateManoteSelectionColor(
        target = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    val iconTint = animateManoteSelectionColor(
        target = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    val labelColor = animateManoteSelectionColor(
        target = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    // 选中那枚图标轻微放大：颜色之外的第二条线索，翻页签时先看到「跳」再看清色。
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = ManoteMotion.pressSpring(),
        label = "tabIconScale",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .manotePress(interaction, pressedScale = 0.92f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { onSelect(tab) },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
        )
    }
}
