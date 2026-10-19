package com.manote.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.manote.app.ui.theme.ManoteTheme
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * 把一屏 Compose 内容真画成一张位图，并留一张 PNG 供肉眼核对。
 *
 * 开发机上没有可运行的模拟器，`connectedDebugAndroidTest` 从未执行过，
 * 布局层面的问题（卡片被挤出屏幕、格子挤在一起、颜色压在底色上看不见）只能靠
 * Robolectric 真画一遍来发现。这里只负责「画出来 + 存图」，断言留给各个测试自己写。
 *
 * 包在 [ManoteTheme] 里是刻意的：主题提供字体、圆角与全部具名色值，
 * 裸着组合出来的东西与真机上根本不是同一个样子。
 */
internal fun renderScreen(
    width: Int,
    height: Int,
    fileName: String,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
): Bitmap {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val view = ComposeView(activity).apply {
        setContent {
            ManoteTheme(darkTheme = darkTheme) { content() }
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

    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, fileName).outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return bitmap
}

/**
 * 统计与给定颜色接近的像素个数（每个通道容差 [tolerance]），用来判断某块色真的铺开了。
 *
 * 收 Compose 的 [Color]（主题里的具名色值都是这个类型），内部换成 ARGB 再比。
 */
internal fun Bitmap.countPixelsNear(color: Color, tolerance: Int = 12): Int {
    val target = color.toArgb()
    var count = 0
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            if (channelDistance(getPixel(x, y), target) <= tolerance) count++
        }
    }
    return count
}

private fun channelDistance(a: Int, b: Int): Int {
    val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
    val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
    val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
    return maxOf(dr, dg, db)
}
