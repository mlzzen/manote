package com.manote.app.ui.mahjong

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.mahjong.ChengduHand
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.domain.mahjong.TileTally
import com.manote.app.ui.countPixelsNear
import com.manote.app.ui.renderScreen
import com.manote.app.ui.theme.HeroSurfaceLight
import com.manote.app.ui.theme.TileTongDark
import com.manote.app.ui.theme.TileWanLight
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 两个麻将小工具的渲染冒烟测试。
 *
 * 这两页是全应用唯一「用方块拼出来」的界面：九张牌子要严丝合缝地铺满一行、
 * 牌面上那三个字要压得住、英雄卡要照旧站在最上面。开发机上没有模拟器，
 * 只能靠 Robolectric 真画一遍确认它们没被挤没、没被裁掉。
 *
 * PNG 落在 `build/screenshots/` 下，需要肉眼核对时直接打开。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MahjongRenderSmokeTest {

    private val width = 784
    private val height = 2400

    @Test
    fun shantenPage_drawsTilesAndHeroCard() {
        // 十三张三门听牌，定缺筒：进张只剩两条，正好也验一遍「缺门不进进张」
        val hand = listOf(
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 2),
            ChengduTile(MahjongSuit.WAN, 3),
            ChengduTile(MahjongSuit.WAN, 4),
            ChengduTile(MahjongSuit.WAN, 5),
            ChengduTile(MahjongSuit.WAN, 6),
            ChengduTile(MahjongSuit.WAN, 7),
            ChengduTile(MahjongSuit.WAN, 8),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 6),
            ChengduTile(MahjongSuit.TIAO, 7),
        ).fold(ChengduHand.empty) { acc, tile -> acc.plus(tile) }

        val bitmap = renderScreen(width, height, "render-mahjong-shanten.png") {
            ShantenContent(
                hand = hand,
                banned = MahjongSuit.TONG,
                onNavigateBack = {},
                onAdd = {},
                onRemove = {},
                onClear = {},
                onBannedChange = {},
            )
        }

        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 500,
        )
        // 手牌里有九张万子，牌面上那个红色的点数至少该铺出几百个像素；
        // 数不到就说明牌子被挤成了一行薄片，或者牌面根本没画。
        assertTrue(
            "牌面上的点数没有画出来，牌子多半被压扁了",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    @Test
    fun tallyPage_drawsUnseenAndSeenRows() {
        val tally = listOf(
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 5),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.WAN, 9),
        ).fold(TileTally.empty) { acc, tile -> acc.mark(tile) }

        val bitmap = renderScreen(width, height, "render-mahjong-tally.png") {
            TallyContent(
                tally = tally,
                suit = MahjongSuit.WAN,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
            )
        }

        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 500,
        )
        // 上面那排九张未出现、下面那排七张已出现，两处都有万子的红点数
        assertTrue(
            "上下两块牌面都没有画出来",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 十四张时的那一块：打哪张。
     *
     * 与十三张的「进张」不是同一块内容——这里是一列候选（牌 + 打完之后的向听与进张），
     * 排版上要是塌了，四条候选会叠成一坨，而单看数字又发现不了。
     */
    @Test
    fun shantenPage_drawsDiscardCandidatesForFourteenTiles() {
        val hand = listOf(
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 2),
            ChengduTile(MahjongSuit.WAN, 3),
            ChengduTile(MahjongSuit.WAN, 4),
            ChengduTile(MahjongSuit.WAN, 5),
            ChengduTile(MahjongSuit.WAN, 6),
            ChengduTile(MahjongSuit.WAN, 7),
            ChengduTile(MahjongSuit.WAN, 8),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 6),
            ChengduTile(MahjongSuit.TIAO, 7),
            ChengduTile(MahjongSuit.TONG, 1),
        ).fold(ChengduHand.empty) { acc, tile -> acc.plus(tile) }

        val bitmap = renderScreen(width, height, "render-mahjong-discard.png") {
            ShantenContent(
                hand = hand,
                banned = null,
                onNavigateBack = {},
                onAdd = {},
                onRemove = {},
                onClear = {},
                onBannedChange = {},
            )
        }

        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 500,
        )
        assertTrue(
            "打牌建议里的牌面没有画出来",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 碰过一副的十张——牌局里「手牌比十四张少」最常见的样子。
     *
     * 三件事要一起成立：英雄卡多出一颗「副露 1 组」、结果区落到「进张」而不是
     * 「再点一张凑十四张」、单钓只报那一张。三者任何一处塌了，光看数字发现不了。
     */
    @Test
    fun shantenPage_drawsAMeldedHand() {
        val hand = listOf(
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 2),
            ChengduTile(MahjongSuit.WAN, 3),
            ChengduTile(MahjongSuit.WAN, 4),
            ChengduTile(MahjongSuit.WAN, 5),
            ChengduTile(MahjongSuit.WAN, 6),
            ChengduTile(MahjongSuit.WAN, 7),
            ChengduTile(MahjongSuit.WAN, 8),
            ChengduTile(MahjongSuit.WAN, 9),
            ChengduTile(MahjongSuit.TIAO, 5),
        ).fold(ChengduHand.empty) { acc, tile -> acc.plus(tile) }

        val bitmap = renderScreen(width, height, "render-mahjong-meld.png") {
            ShantenContent(
                hand = hand,
                banned = null,
                onNavigateBack = {},
                onAdd = {},
                onRemove = {},
                onClear = {},
                onBannedChange = {},
            )
        }

        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 500,
        )
        assertTrue(
            "手牌与进张的牌面没有画出来",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 暗色主题下的牌面。
     *
     * 牌是白的、页面是深的，这里最容易被写成「一块块发光的白方块」。
     * 暗色主题用的是另一组牌底与花色色，因此这一条单独守：
     * 花色色必须真的提亮过，压在深底上还读得出来。
     */
    @Test
    fun tallyPage_darkThemeKeepsTilesLegible() {
        val tally = listOf(
            ChengduTile(MahjongSuit.TONG, 3),
            ChengduTile(MahjongSuit.TONG, 3),
            ChengduTile(MahjongSuit.TONG, 7),
        ).fold(TileTally.empty) { acc, tile -> acc.mark(tile) }

        val bitmap = renderScreen(width, height, "render-mahjong-tally-dark.png", darkTheme = true) {
            TallyContent(
                tally = tally,
                suit = MahjongSuit.TONG,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
            )
        }

        assertTrue(
            "暗色下的牌面点数没有画出来",
            bitmap.countPixelsNear(TileTongDark, tolerance = 32) > 200,
        )
    }
}
