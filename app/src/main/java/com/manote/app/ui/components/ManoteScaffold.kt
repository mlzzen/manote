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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manote.app.R
import com.manote.app.ui.theme.ManoteScreenTitleStyle
import com.manote.app.ui.theme.PAGE_GLOW_CENTER_X
import com.manote.app.ui.theme.PAGE_GLOW_CENTER_Y
import com.manote.app.ui.theme.PAGE_GLOW_RADIUS_RATIO
import com.manote.app.ui.theme.manote

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
 *
 * **整页的背景默认由这里铺，页面自己不要画背景色。** 它是一道顶光而不是一块平色
 * （见 [ManoteExtendedColors.pageBackground]），铺在最外层才能从状态栏一直连到导航栏；
 * 顶栏因此刻意不画底色，否则会在离屏顶端一小段处把这道光切断。
 *
 * 例外是「被 [ManotePageBackground] 包住」的时候：那时背景已经在外层铺好，
 * 这里不再重复画一遍。重复画不只是浪费——切换页签时两个页面交叉淡入，
 * 两份半透明背景叠在一起会让整屏亮度跳一下，看起来就像掉帧。
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
    val backgroundAlreadyDrawn = LocalPageBackgroundProvided.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (backgroundAlreadyDrawn) Modifier else Modifier.manotePageBackground()),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // 透明而不是画布色：底下那层才是这一页真正的底
            containerColor = Color.Transparent,
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
}

/**
 * 外层是否已经铺过整页背景。
 *
 * 用 CompositionLocal 而不是给 [ManoteScaffold] 加参数：调用点有十来个，
 * 加参数就得逐个传，漏一个就会出现「两层背景」或「没有背景」，而这两种都很难一眼看出来。
 */
internal val LocalPageBackgroundProvided = staticCompositionLocalOf { false }

/**
 * 一页的整页背景：晨光渐变 + 右上角柔光。
 *
 * 它有两处用途，原因不同：
 *
 * 1. **导航宿主的最外层**——让背景不参与页面转场。背景若跟着子页面一起淡入淡出，
 *    转场中间会短暂露出窗口底色，整屏亮度先掉再回来，观感就是「卡了一下」。
 * 2. **横向分页里的每一页**——滑动时两页并排出现在屏幕上，若其中一页是透明的，
 *    就会透出邻页的卡片，两个页面叠在一起像花屏。每页各自铺满一道晨光，
 *    滑动看到的就是两张不透明的「纸」在换位。
 *
 * 包在这里的子树会被标记为「背景已经铺过」，内层的 [ManoteScaffold]
 * 因此不再重复画一遍——重复画不只是浪费，两份半透明渐变叠在一起还会让亮度跳一下。
 *
 * 绘制用的是 `drawWithCache`：渐变画刷按画布尺寸建一次、缓存到尺寸变化为止。
 * 原来写在 `drawBehind` 里每帧新建一个 [Brush.radialGradient]，滚动时每秒几十次分配，
 * 白白给 GC 添活。
 */
@Composable
fun ManotePageBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .manotePageBackground(),
    ) {
        CompositionLocalProvider(LocalPageBackgroundProvided provides true) {
            content()
        }
    }
}

/** 画一道晨光渐变，再叠一团右上角的柔光。 */
@Composable
private fun Modifier.manotePageBackground(): Modifier {
    val background = MaterialTheme.manote.pageBackground
    val glow = MaterialTheme.manote.pageGlow
    return this
        .background(background)
        .drawWithCache {
            // 柔光：圆心落在可见区域之外，页面只看到光的尾巴。
            // 半径按屏宽算而不是固定 dp，换到大屏上光斑才不会被拉成一条边。
            val glowBrush = Brush.radialGradient(
                0f to glow,
                0.5f to glow.copy(alpha = glow.alpha * 0.42f),
                1f to glow.copy(alpha = 0f),
                center = Offset(
                    x = size.width * PAGE_GLOW_CENTER_X,
                    y = size.height * PAGE_GLOW_CENTER_Y,
                ),
                radius = size.width * PAGE_GLOW_RADIUS_RATIO,
            )
            onDrawBehind { drawRect(brush = glowBrush) }
        }
}

/**
 * 顶栏。两种形态：
 *
 * - 根页面（`branded = true`）用**大标题**：30sp 的特粗字压在左上角，
 *   紧挨着品牌标记。参考稿里页面名就是整屏的第一行，标题一小，
 *   下面那堆卡片标题立刻跟它平起平坐，整页就没有主次了。
 * - 子页面沿用居中式小标题 + 左侧圆形返回键，读起来明确是「进去了一层」。
 *
 * 返回按钮固定带可访问标签（规格 3.6）。
 */
@Composable
fun ManoteTopBar(
    title: String,
    modifier: Modifier = Modifier,
    branded: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 品牌标记与返回键都用圆形/圆角浮层，所以顶栏统一给 16dp 横向留白：
    // 大标题的左缘、圆形按钮的右缘与下面的卡片严格对齐在同一条竖线上。
    if (branded && onNavigateBack == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                // 不画底色：顶栏正下方就是页面的晨光，画一块平色会在顶端切出一道横线
                .statusBarsPadding()
                .heightIn(min = 68.dp)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManoteBrandMark(size = 26.dp)
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = ManoteScreenTitleStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 不画底色：顶栏正下方就是页面的晨光，画一块平色会在顶端切出一道横线
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 64.dp),
        )

        if (onNavigateBack != null) {
            ManoteRoundIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
