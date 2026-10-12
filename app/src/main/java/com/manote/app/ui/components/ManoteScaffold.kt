package com.manote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manote.app.R

/**
 * 各页面共用的骨架：顶栏 + 可选底部栏。
 *
 * 顶栏有两种形态，由 [branded] 决定：
 * - `branded = true`（四个页签的根页面）：左侧是牌面标记 + 页面标题，左对齐。
 *   首页的标题就是应用名「麻记」，于是品牌标记出现在每一页的同一个位置，
 *   翻页时那个方块不动，只有它右边的字在变。
 * - `branded = false`（详情页、编辑页）：沿用居中式标题 + 返回键，
 *   与根页面明确区分开——居中的标题读起来就是「这是一层子页面」。
 *
 * 内容区通过 [content] 拿到已扣减顶栏/底栏内边距的 [PaddingValues]。
 * 系统栏处理：顶栏自带状态栏内边距；底部栏由调用方负责导航栏内边距；
 * 内容区只保留横向安全区，避免横屏挖孔遮挡。
 */
@Composable
fun ManoteScaffold(
    title: String,
    modifier: Modifier = Modifier,
    branded: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            ManoteTopBar(
                title = title,
                branded = branded,
                onNavigateBack = onNavigateBack,
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/** 居中式顶栏。返回按钮固定带可访问标签（规格 3.6）。 */
@Composable
fun ManoteTopBar(
    title: String,
    modifier: Modifier = Modifier,
    branded: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (branded && onNavigateBack == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManoteBrandMark()
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            return@Box
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 64.dp),
        )

        if (onNavigateBack != null) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * 品牌标记：一个墨绿圆角方块里放两颗浅绿圆点，取自「二筒」。
 *
 * 它替代了传统的文字 Logo —— 在一屏只有 24dp 的位置上，
 * 一个能反复认出的色块比三个汉字更省地方，也让顶栏和底部栏的选中态、
 * 空态背景里的牌面用同一套形状语言。
 */
@Composable
fun ManoteBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.12f)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(size * 0.19f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}
