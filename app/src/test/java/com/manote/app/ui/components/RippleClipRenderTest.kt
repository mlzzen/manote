package com.manote.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.ui.renderScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 水波（ripple）必须被裁进控件自己的形状里。
 *
 * `clickable` 的水波沿**节点的矩形边界**绘制，圆角只有靠写在它之前的 `clip` 才裁得出来。
 * 这个错误不会在静态截图里露头——不按下去什么也看不见——所以这里把 `LocalIndication`
 * 换成一层「把节点整个铺满纯黑」的假指示，让水波的**实际绘制范围**直接显形：
 * 被裁过的话，铺色区域的外接矩形四个角一定是空的；没裁过，四角就是黑的。
 *
 * 断言只认「外接矩形的角」与「矩形中心」，不写死任何像素坐标，
 * 因此换屏幕密度、换控件尺寸都仍然成立。
 *
 * PNG 落在 `app/build/screenshots/`，可以肉眼核对圆角有没有被啃掉。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RippleClipRenderTest {

    @Test
    fun card_rippleIsClippedToItsRoundedCorners() {
        val bitmap = renderScreen(width = 640, height = 320, fileName = "render-ripple-card.png") {
            CompositionLocalProvider(LocalIndication provides OpaqueIndication) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    ManoteCard(onClick = {}) {
                        Text(text = "一张可点击的卡片", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        assertRippleIsRounded(bitmap, "卡片")
    }

    @Test
    fun filterChip_rippleStaysInsideThePill() {
        val bitmap = renderScreen(width = 640, height = 240, fileName = "render-ripple-chip.png") {
            CompositionLocalProvider(LocalIndication provides OpaqueIndication) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    ManoteFilterChip(label = "下午场", selected = true, onClick = {})
                }
            }
        }

        assertRippleIsRounded(bitmap, "筛选胶囊")
    }

    @Test
    fun roundIconButton_rippleIsACircle() {
        val bitmap = renderScreen(width = 320, height = 240, fileName = "render-ripple-round.png") {
            CompositionLocalProvider(LocalIndication provides OpaqueIndication) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    ManoteRoundIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "新增",
                        onClick = {},
                    )
                }
            }
        }

        assertRippleIsRounded(bitmap, "圆形图标按钮")
    }

    /**
     * 断言水波真的画出来了，并且它的外接矩形四角是空的（= 被圆角裁掉）。
     */
    private fun assertRippleIsRounded(bitmap: Bitmap, label: String) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        var filled = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (!isIndicationPixel(bitmap.getPixel(x, y))) continue
                filled++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        assertTrue("$label 上没有画出水波，指示层多半没接上", filled > 500)

        // 四个角各取内缩 1px 的一点：这一刀裁剪真的生效时，那里应该露出页面底色
        val corners = listOf(
            minX + 1 to minY + 1,
            maxX - 1 to minY + 1,
            minX + 1 to maxY - 1,
            maxX - 1 to maxY - 1,
        )
        corners.forEach { (x, y) ->
            assertFalse(
                "$label 的水波铺到了外接矩形的角上（$x, $y）——没写 clip，按下去是一块方块",
                isIndicationPixel(bitmap.getPixel(x, y)),
            )
        }

        // 中心必须仍然是水波，否则上面那条「角是空的」可能只是因为整块都没画
        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2
        assertTrue(
            "$label 的水波中心没有铺开，断言角为空没有意义",
            isIndicationPixel(bitmap.getPixel(centerX, centerY)),
        )
    }

    /** 假指示画的是纯黑，字色与阴影都到不了这个亮度，不会混进来。 */
    private fun isIndicationPixel(pixel: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return red < 12 && green < 12 && blue < 12
    }
}

/**
 * 把可点击节点整个铺满纯黑的「水波」。
 *
 * 真水波是半透明的、还在动，静态渲染里几乎看不见；换成这层不透明填充后，
 * 它的**边界**就是水波真正能画到的边界，圆角有没有被裁掉一眼即知。
 */
private object OpaqueIndication : androidx.compose.foundation.IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
                drawRect(Color.Black)
            }
        }

    override fun hashCode(): Int = 0x4D414E4F

    override fun equals(other: Any?): Boolean = other === this
}
