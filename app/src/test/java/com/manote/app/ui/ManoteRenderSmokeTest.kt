package com.manote.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManoteCard
import com.manote.app.ui.components.ManoteHeroCard
import com.manote.app.ui.components.ManotePageBackground
import com.manote.app.ui.components.ManotePill
import com.manote.app.ui.components.ManoteReadout
import com.manote.app.ui.components.ManoteResultTile
import com.manote.app.ui.components.ManoteScaffold
import com.manote.app.ui.components.ManoteSearchField
import com.manote.app.ui.components.ManoteSectionHeader
import com.manote.app.ui.components.ManoteSegmentedControl
import com.manote.app.ui.components.ManoteSessionRow
import com.manote.app.ui.components.ManoteShareBar
import com.manote.app.ui.components.manoteTextFieldColors
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.model.UiText
import com.manote.app.ui.navigation.ManoteTab
import com.manote.app.ui.theme.DarkExtendedColors
import com.manote.app.ui.theme.LightExtendedColors
import com.manote.app.ui.theme.ManoteTheme
import com.manote.app.ui.theme.manote
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * 渲染冒烟测试：真的把一屏界面画到 Canvas 上，再检查画出来的像素。
 *
 * 本项目的开发机没有可运行的模拟器（无硬件加速），`connectedDebugAndroidTest`
 * 从未执行过，因此「改完界面长什么样」长期只能靠代码评审。Robolectric 的
 * NATIVE 图形模式用 Skia 真实绘制，这里借它补上这一环：
 * - 场景用到了改版后的全部新组件（晨光背景、玻璃卡、英雄卡、分段控件、悬浮导航）；
 * - 断言图像里**确实出现了**英雄卡的墨绿与页面顶部的暖色，
 *   以及上下两端背景不是同一个颜色（渐变真的画上去了）；
 * - 顺手把 PNG 写进 `build/screenshots/`，需要肉眼核对时直接打开看。
 *
 * 断言只针对「有没有画出来 / 画在不在该在的位置」，不比对具体像素值：
 * 色值一改测试就红的那种断言，维护成本比它挡住的回归还高。
 */
@RunWith(AndroidJUnit4::class)
// 语言必须钉死：底部导航的四个标签是「首页 / 场次 / 牌友 / 我的」，
// 换成英文（Sessions / Players）宽度会变，选中块是否挤爆只能在中文下验证。
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManoteRenderSmokeTest {

    private val width = 784
    private val height = 1720

    @Test
    fun lightScreen_drawsThePageHeroCardAndFloatingBar() {
        val pixels = render(dark = false, name = "light")
        assertContainsColor(pixels, LightExtendedColors.heroSurface, "英雄卡的墨绿没有画出来")
        assertBackgroundIsAGradient(pixels, "页面背景的晨光渐变没有画出来")
        // 底栏的选中块用的是同一族墨绿，所以这里不止断言「图里有这个颜色」，
        // 而是要求它在最下面那一段里：底栏被内容盖住、或者没被排到底部，都会红。
        assertContainsColor(
            pixels = pixels,
            target = LightExtendedColors.heroSurface,
            message = "底部导航的选中块没有画在最下方",
            fromRatio = 0.86f,
        )
        assertFloatingBarHasItsOwnFill(pixels)
    }

    @Test
    fun darkScreen_drawsThePageAndTheHeroCard() {
        val pixels = render(dark = true, name = "dark")
        assertContainsColor(pixels, DarkExtendedColors.heroSurface, "英雄卡的墨绿没有画出来")
        assertBackgroundIsAGradient(pixels, "页面背景的晨光渐变没有画出来")
        assertFloatingBarHasItsOwnFill(pixels)
    }

    /**
     * 滑动到一半的那一帧：两页并排，各露出一半。
     *
     * 这是横向分页最容易出错的地方——**页面透光**。四个页签的内容层是半透明的卡片，
     * 若某一页的根节点不画背景，滑到中间就会同时看见两个页面的卡片叠在一起，
     * 像花屏。代码里看不出来：每页的 `ManoteScaffold` 都「有」背景，
     * 只是它们以为外层已经铺过了（`LocalPageBackgroundProvided`）。
     *
     * 断言方式：整张图上不能有 alpha 为 0 的像素。窗口底色是透明的，
     * 只要有一页漏了背景，那片区域就会保持透明——这是最直接的证据。
     */
    @Test
    fun swipingPages_areBothOpaque() {
        val pixels = renderScene("swipe") {
            ManoteTheme(darkTheme = false) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ManotePageBackground(modifier = Modifier.weight(1f)) {
                        SamplePage(title = "麻记")
                    }
                    ManotePageBackground(modifier = Modifier.weight(1f)) {
                        SamplePage(title = "场次")
                    }
                }
            }
        }

        val transparent = pixels.count { (it ushr 24) == 0 }
        assertTrue("滑动时露出了未绘制的区域（${transparent} 个透明像素）：页面背景没有铺满", transparent == 0)
        // 两页都要真的画出来，而不是只有左边那页在画
        assertContainsColor(
            pixels = pixels,
            target = LightExtendedColors.heroSurface,
            message = "左半页没有画出来",
            xFromRatio = 0f,
            xToRatio = 0.5f,
        )
        assertContainsColor(
            pixels = pixels,
            target = LightExtendedColors.heroSurface,
            message = "右半页没有画出来",
            xFromRatio = 0.5f,
            xToRatio = 1f,
        )
    }

    /** 用真实的 Activity + ComposeView 绘制一屏，返回整张图的像素。 */
    private fun render(dark: Boolean, name: String): IntArray =
        renderScene(name) { ManoteTheme(darkTheme = dark) { SampleScreen() } }

    /** 绘制任意一屏到 PNG，返回整张图的像素。 */
    private fun renderScene(name: String, content: @Composable () -> Unit): IntArray {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent(content)
        }
        activity.setContentView(view)
        // Robolectric 的主 looper 是暂停的：不推一次，组合还没跑完就取像素会得到空白图
        shadowOf(Looper.getMainLooper()).idle()

        val root: View = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "render-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    /**
     * 底栏必须画自己的底色。
     *
     * 这不是「细节」：底栏是半透的玻璃胶囊，底色一旦漏画，`Surface` 就只剩
     * 一圈 1dp 白描边——在暖白的页面上，整条底栏看上去就是「飘着一根横向白线」。
     * 2026-10-19 真的犯过这个错（`color = Color.Transparent` 之后忘了给内层画渐变），
     * 而代码读起来完全正常：`Surface` 的注释还写着「玻璃底」。
     *
     * 判据取同一行里「胶囊内」与「胶囊外」的绿通道差：有底色时胶囊比页面亮一档，
     * 漏画时两者恰好完全相同。样张是 xhdpi，1dp = 2px；取底栏下三分之一处，
     * 横向落在右侧空白区，避开中间凸起的「记一场」与左边的深色选中块。
     */
    private fun assertFloatingBarHasItsOwnFill(pixels: IntArray) {
        val y = 820 * 2
        val inside = (pixels[y * width + 350 * 2] shr 8) and 0xFF
        val outside = (pixels[y * width + 3 * 2] shr 8) and 0xFF
        assertTrue(
            "底栏没有画自己的底色（胶囊内 G=$inside，页面 G=$outside）：只剩一圈描边，看起来像一根白线",
            inside - outside >= 6,
        )
    }

    /**
     * 图里是否存在接近 [target] 的颜色。
     *
     * [tolerance] 放到 16：英雄卡本身是渐变，卡面中央的像素与 [target] 差几个色阶，
     * 逐一相等地找会永远找不到。
     *
     * [fromRatio] 传了就只搜这一行以下（按图高取比例），用来问「这个颜色出没出现在某个位置」，
     * 而不只是「有没有」。[xFromRatio] / [xToRatio] 同理，按图宽取左右范围——
     * 滑动样张要靠它分别检查左右两半。
     */
    private fun assertContainsColor(
        pixels: IntArray,
        target: Color,
        message: String,
        fromRatio: Float = 0f,
        xFromRatio: Float = 0f,
        xToRatio: Float = 1f,
    ) {
        val tr = (target.red * 255f).toInt()
        val tg = (target.green * 255f).toInt()
        val tb = (target.blue * 255f).toInt()
        val yFrom = (height * fromRatio).toInt()
        val xFrom = (width * xFromRatio).toInt()
        val xTo = (width * xToRatio).toInt()
        val found = (yFrom until height).any { y ->
            (xFrom until xTo).any { x ->
                val p = pixels[y * width + x]
                abs(((p shr 16) and 0xFF) - tr) <= 16 &&
                    abs(((p shr 8) and 0xFF) - tg) <= 16 &&
                    abs((p and 0xFF) - tb) <= 16
            }
        }
        assertTrue(message, found)
    }

    /**
     * 页面是纵向渐变，所以「顶部一行的颜色」与「底部一行的颜色」必须不同。
     *
     * 取最左右各 4 列、避开圆角与状态栏：这两处不会出现卡片，
     * 读到的一定是页面背景本身。如果哪天渐变被改回平色，这条会红。
     */
    private fun assertBackgroundIsAGradient(pixels: IntArray, message: String) {
        fun rowColor(y: Int): Int {
            // 取右侧边缘往里 4px 的像素：那里既没有卡片，也没有左上角的品牌标记
            return pixels[y * width + (width - 5)]
        }
        val top = rowColor(4)
        val bottom = rowColor(height - 4)
        assertTrue(message, top != bottom)
    }
}

/**
 * 一屏样例：把改版涉及的组件按真实页面的顺序摆一遍。
 *
 * 它不是某个真实页面的副本（真实页面要 ViewModel），而是「同一套版式语言」的样本：
 * 背景、顶栏、英雄卡、内容卡、搜索框、分段控件、场次行、悬浮导航各一次。
 * 这样任何一处画崩都会反映在这张图上。
 *
 * **嵌套顺序与 [com.manote.app.ui.navigation.ManoteNavHost] 逐层对齐**：
 * 外层 `ManotePageBackground` → `Scaffold`（只挂底栏）→ 内层 `ManoteScaffold`（只画顶栏）。
 * 这条顺序本身是要验证的东西：底栏必须由外层给高度、内容靠 innerPadding 让开，
 * 否则底部那排场次行会被导航条盖住——而这种事在代码里读起来完全正常。
 */
@Composable
private fun SampleScreen() {
    ManotePageBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ManoteBottomBar(current = ManoteTab.HOME, onSelect = {}, onNewSession = {})
            },
        ) { outer ->
            // 与 pager 的一页同构：外层管底栏，每一页再自己铺一层整页背景。
            // 这层背景不是多余的——滑动时它就是两页之间不互相透光的那道底。
            ManotePageBackground(modifier = Modifier.padding(outer)) {
                SamplePage()
            }
        }
    }
}

/** 与四个根页面同构的一页：内层骨架只负责顶栏，内容全部在它之下。 */
@Composable
private fun SamplePage(
    modifier: Modifier = Modifier,
    title: String = "麻记",
) {
    val colors = MaterialTheme.manote
    ManoteScaffold(
        title = title,
        branded = true,
        modifier = modifier,
        actions = {
            ManotePill(
                text = "10.19 · 周一",
                modifier = Modifier.padding(end = 8.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                horizontalPadding = 12.dp,
                verticalPadding = 6.dp,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ManoteHeroCard {
                Text(
                    text = "累计净输赢",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onHeroMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = UiText.signedYuan(-123_450L),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onHero,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("已记 12 场", "胜率 33%", "净胜 -4").forEach {
                        ManotePill(
                            text = it,
                            containerColor = colors.heroChip,
                            contentColor = colors.onHeroChip,
                            style = MaterialTheme.typography.labelSmall,
                            verticalPadding = 4.dp,
                        )
                    }
                }
            }

            ManoteSectionHeader(title = "战绩概览")
            ManoteCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ManoteResultTile(
                        label = "赢",
                        count = 4,
                        amountMinor = 12_600L,
                        containerColor = colors.winContainer,
                        contentColor = colors.onWinContainer,
                        accentColor = colors.win,
                        modifier = Modifier.weight(1f),
                    )
                    ManoteResultTile(
                        label = "输",
                        count = 8,
                        amountMinor = -136_050L,
                        containerColor = colors.lossContainer,
                        contentColor = colors.onLossContainer,
                        accentColor = colors.loss,
                        modifier = Modifier.weight(1f),
                    )
                    ManoteResultTile(
                        label = "持平",
                        count = 0,
                        amountMinor = 0L,
                        containerColor = colors.drawContainer,
                        contentColor = colors.onDrawContainer,
                        accentColor = colors.draw,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                ManoteShareBar(winCount = 4, lossCount = 8, drawCount = 0)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ManoteReadout(
                        label = "场均",
                        value = UiText.signedYuan(-10_287L),
                        valueColor = colors.loss,
                        modifier = Modifier.weight(1f),
                    )
                    ManoteReadout(
                        label = "单场最多",
                        value = UiText.signedYuan(6_600L),
                        valueColor = colors.win,
                        modifier = Modifier.weight(1f),
                    )
                    ManoteReadout(
                        label = "单场最多",
                        value = UiText.signedYuan(-9_900L),
                        valueColor = colors.loss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ManoteSectionHeader(title = "最近场次")
            ManoteSearchField(
                value = "",
                onValueChange = {},
                placeholder = "搜标题或牌友",
            )
            ManoteSegmentedControl(
                options = listOf("全部", "本月", "上月"),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
            // 表单输入框也进样张：它用的是自绘的软填充配色，
            // 与卡片、搜索框是不是同一门语言，只有画出来才看得出来。
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = "本场备注") },
                placeholder = { Text(text = "例如：老王家，输赢都记了") },
                shape = MaterialTheme.shapes.small,
                colors = manoteTextFieldColors(),
            )
            ManoteSessionRow(
                row = SessionRowUi(
                    id = "1",
                    title = "周五晚局",
                    note = "",
                    playedAt = 1_791_000_000_000L,
                    timeSlot = TimeSlot.EVENING,
                    selfNetMinor = -200L,
                    opponentNames = listOf("次小", "次建米", "麻米建"),
                    searchHaystack = "",
                ),
                onClick = {},
            )
            ManoteSessionRow(
                row = SessionRowUi(
                    id = "2",
                    title = "麻将对局",
                    note = "",
                    playedAt = 1_790_000_000_000L,
                    timeSlot = TimeSlot.AFTERNOON,
                    selfNetMinor = 2_600L,
                    opponentNames = listOf("阿强", "小美"),
                    searchHaystack = "",
                ),
                onClick = {},
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
