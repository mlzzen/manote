package com.manote.app.ui.sessions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.components.ManotePageBackground
import com.manote.app.ui.model.SessionFilters
import com.manote.app.ui.model.SessionFriendOption
import com.manote.app.ui.model.SessionOutcome
import com.manote.app.ui.theme.ManoteTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 场次筛选弹层的渲染冒烟测试。
 *
 * 开发机上没有可运行的模拟器，`connectedDebugAndroidTest` 从未执行过，
 * 这一屏（六组胶囊 + 底部按钮）因此只能靠 Robolectric 真画一遍来看看：
 * - 六组内容在常见屏高下一次放得下，底部那颗按钮没有被挤出屏幕；
 * - 顶部那条窄读数真的画出来了（深墨绿卡面比页面背景暗得多）。
 *
 * 弹层的壳（[androidx.compose.material3.ModalBottomSheet] 的遮罩与拖拽）走的是
 * 另一个窗口，这样渲染不到，所以这里直接画弹层的内容区。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionFilterRenderSmokeTest {

    private val width = 784
    private val height = 1500

    @Test
    fun filterSheet_andSummaryStrip_areDrawn() {
        val bitmap = render()
        assertTrue(
            "顶部那条净输赢读数没有画出来",
            hasDarkPixelBetween(bitmap, fromY = 0, toY = 80),
        )
        assertTrue(
            "底部「查看 N 场」按钮没有画出来（多半被挤出屏幕）",
            hasDarkPixelBetween(bitmap, fromY = height / 2, toY = height),
        )
        assertTrue("弹层内容几乎是一张白纸，多半没画出来", distinctColorCount(bitmap) > 20)
    }

    /** 全选状态都打开，六组同时出现——最坏情况放得下，其它情况自然放得下。 */
    private fun render(): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent {
                ManoteTheme(darkTheme = false) {
                    ManotePageBackground {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SessionsSummaryStrip(
                                count = 12,
                                netMinor = -123_456L,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            SessionFilterContent(
                                filters = SessionFilters(
                                    timeSlots = setOf(TimeSlot.EVENING),
                                    year = 2026,
                                    month = 10,
                                    date = LocalDate.of(2026, 10, 5),
                                    friendIds = setOf("p-wang"),
                                    outcomes = setOf(SessionOutcome.WIN),
                                ),
                                yearOptions = listOf(2026, 2025, 2024),
                                friendOptions = listOf(
                                    SessionFriendOption("p-wang", "老王"),
                                    SessionFriendOption("p-li", "小李"),
                                    SessionFriendOption("p-chen", "小陈"),
                                    SessionFriendOption("p-1", "阿强", showId = true),
                                ),
                                resultCount = 12,
                                onChange = {},
                                onReset = {},
                                onDismiss = {},
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
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

        // 顺手留一张 PNG：需要肉眼核对时直接打开 build/screenshots/
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "render-session-filter.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    /**
     * 指定纵向范围内是否出现深色像素。
     *
     * 窄条读数与底部按钮都是深墨绿实底，页面背景是暖白渐变——
     * 用「暗」这个判据就够区分「画了」与「没画」，不必钉死具体色值。
     */
    private fun hasDarkPixelBetween(bitmap: Bitmap, fromY: Int, toY: Int): Boolean =
        (fromY until toY).any { y ->
            (0 until width).any { x ->
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                red + green + blue < 360
            }
        }

    private fun distinctColorCount(bitmap: Bitmap): Int {
        val colors = HashSet<Int>()
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                colors.add(bitmap.getPixel(x, y))
            }
        }
        return colors.size
    }
}
