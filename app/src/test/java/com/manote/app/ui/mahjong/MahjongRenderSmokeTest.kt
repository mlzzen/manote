package com.manote.app.ui.mahjong

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.mahjong.ChengduHand
import com.manote.app.domain.mahjong.ChengduShanten
import com.manote.app.domain.mahjong.ChengduTile
import com.manote.app.domain.mahjong.MahjongSuit
import com.manote.app.domain.mahjong.TileTally
import com.manote.app.ui.countPixelsNear
import com.manote.app.ui.renderScreen
import com.manote.app.ui.theme.HeroSurfaceLight
import com.manote.app.ui.theme.TileFaceLight
import com.manote.app.ui.theme.TileTongDark
import com.manote.app.ui.theme.TileWanLight
import com.manote.app.ui.theme.manote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
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

    /**
     * 「整页」截图的高度：够放下六条打牌建议。
     *
     * 一屏（800dp）只放得下四条候选，而这一手牌恰好有六种打法都能听牌——
     * 要核对「一条都没被截掉」，画布得比真机高一截。
     */
    private val TALL_PAGE_HEIGHT = 3600

    /** 推算那一屏更高：九种摆法、每种三排牌。 */
    private val INFER_PAGE_HEIGHT = 4400

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
                opponentHand = 0,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
                onOpponentChange = {},
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
     * 六种打法都能听牌的那一手（用户报的 `34445567778889m`）。
     *
     * 这一屏要同时成立三件事：六条候选一条不少、每条下面摊着它自己听的牌、
     * 行与行之间分得开。前两件是数据，最后一件只有真画一遍才看得出来——
     * 每行多了一排小牌之后，不加分隔线就会看成一行。
     */
    @Test
    fun shantenPage_drawsEveryDiscardAndItsWaits() {
        val hand = listOf(3, 4, 4, 4, 5, 5, 6, 7, 7, 7, 8, 8, 8, 9)
            .fold(ChengduHand.empty) { acc, rank ->
                acc.plus(ChengduTile(MahjongSuit.WAN, rank))
            }

        // 六条候选加上手牌与键盘，一屏放不下。这里画一张「整页」：
        // 真机上是滚出来的，但要核对「六条都在、每条都带着自己的听牌」，
        // 只能一次看全，截在一半就分不清是没画还是没滚到。
        val bitmap = renderScreen(width, TALL_PAGE_HEIGHT, "render-mahjong-discard-waits.png") {
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
     * 已经和了的那一手，照样要给出「打哪张、听什么」。
     *
     * 和了之后整页只剩一句「已经组成四副面子加一对将」，等于让人自己去想
     * 「换成哪张还能听」。而从和了的牌里换出一张，剩下的必然听牌——
     * 这一份表是看牌型、数剩余张数、跟别人的听牌对撞时才用得上的东西。
     */
    @Test
    fun shantenPage_stillOffersDiscardsWhenTheHandIsWon() {
        // 111m + 234m + 555t + 99t：三副面子加一对将，已经和了
        val hand = listOf(
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 1),
            ChengduTile(MahjongSuit.WAN, 2),
            ChengduTile(MahjongSuit.WAN, 3),
            ChengduTile(MahjongSuit.WAN, 4),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 5),
            ChengduTile(MahjongSuit.TIAO, 9),
            ChengduTile(MahjongSuit.TIAO, 9),
        ).fold(ChengduHand.empty) { acc, tile -> acc.plus(tile) }

        assertEquals("这一手本该是和了", -1, ChengduShanten.of(hand))

        val bitmap = renderScreen(width, TALL_PAGE_HEIGHT, "render-mahjong-won.png") {
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
        // 和了之后还有「打哪张」，说明那一段没被一句提示顶掉
        assertTrue(
            "和了之后没有给出打法",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 记牌器的推算：未知九张万子，对手七张。
     *
     * 用户报的那一池（223456679）一共九种摆法都能听牌，每一种带着自己的听牌
     * 与牌山。九块内容里有七张牌的整手、也有两排小牌，排版一挤就叠在一起，
     * 只能真画一遍才看得出来。
     */
    @Test
    fun tallyPage_listsEveryInferredTenpaiSplit() {
        // 只留这九张没见过：其余的全部记成已出现
        val pool = listOf(2, 2, 3, 4, 5, 6, 6, 7, 9)
        var tally = TileTally.empty
        (1..9).forEach { rank ->
            repeat(ChengduTile.COPIES - pool.count { it == rank }) {
                tally = tally.mark(ChengduTile(MahjongSuit.WAN, rank))
            }
        }
        assertEquals(9, tally.remainingCount(MahjongSuit.WAN))

        val bitmap = renderScreen(width, INFER_PAGE_HEIGHT, "render-mahjong-infer.png") {
            TallyContent(
                tally = tally,
                suit = MahjongSuit.WAN,
                opponentHand = 7,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
                onOpponentChange = {},
            )
        }

        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 500,
        )
        assertTrue(
            "推算区没有画出牌面",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 未出现的池子要尽量把一行填满。
     *
     * 用户报过这个：一行放得下 `1111 22`，再多一组 `33` 就被判成摆不下、单独换行，
     * 而那一行其实还剩着空档。原因是牌宽只按「两组四张」算，没算第三组多出来的
     * 那道组间空档。这里画的正是这个池子：正确的话第一行是 `1111 22 33`、
     * 第二行只剩 `4444`。
     */
    @Test
    fun tallyPage_packsThreeTileGroupsIntoOneRow() {
        // 只留 1111 22 33 4444 没见过
        val unseenCounts = mapOf(1 to 4, 2 to 2, 3 to 2, 4 to 4)
        var tally = TileTally.empty
        (1..9).forEach { rank ->
            repeat(ChengduTile.COPIES - (unseenCounts[rank] ?: 0)) {
                tally = tally.mark(ChengduTile(MahjongSuit.WAN, rank))
            }
        }
        assertEquals(12, tally.remainingCount(MahjongSuit.WAN))

        val bitmap = renderScreen(width, height, "render-mahjong-tally-pack.png") {
            TallyContent(
                tally = tally,
                suit = MahjongSuit.WAN,
                opponentHand = 0,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
                onOpponentChange = {},
            )
        }

        assertTrue(
            "未出现的牌面没有画出来",
            bitmap.countPixelsNear(TileWanLight, tolerance = 32) > 300,
        )
    }

    /**
     * 牌面里的字必须真的居中。
     *
     * 只看代码是看不出问题的：`Box(contentAlignment = Center)` 确实把那一列字摆在了
     * 几何中心，可每一行文字都带着字体自己的上伸与下伸，墨迹的重心并不在字框中心，
     * 于是「数字 + 花色」整块看着偏下半个字。所以这条用例把牌放大画一遍，
     * 量出墨迹的包围盒再比留白——量的是眼睛看到的那个中心。
     *
     * 顺带守住两行之间的距离：数字与花色之间空出将近一个字高时，
     * 这块牌读起来就成了两件不相干的东西。
     */
    @Test
    fun tileFace_centersItsContentAndKeepsTheTwoLinesTogether() {
        val bitmap = renderScreen(width, height, "render-mahjong-tile-face.png") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.manote.pageBackground),
                contentAlignment = Alignment.TopStart,
            ) {
                MahjongTileFace(
                    tile = ChengduTile(MahjongSuit.WAN, 3),
                    modifier = Modifier.padding(start = 40.dp, top = 40.dp),
                    width = 110.dp,
                )
            }
        }

        val measure = measureTile(bitmap)
        val tileH = measure.height
        val tileW = measure.width

        assertTrue(
            "上下留白差了 ${measure.topPad - measure.bottomPad}px（上 ${measure.topPad}，" +
                "下 ${measure.bottomPad}，牌高 $tileH）：整块字偏了",
            abs(measure.topPad - measure.bottomPad) <= tileH * 0.02f,
        )
        assertTrue(
            "左右留白差了 ${abs(measure.leftPad - measure.rightPad)}px" +
                "（左 ${measure.leftPad}，右 ${measure.rightPad}，牌宽 $tileW）",
            abs(measure.leftPad - measure.rightPad) <= tileW * 0.04f,
        )

        val gapRatio = measure.lineGap.toFloat() / tileH
        assertTrue(
            "数字与花色之间空了 ${measure.lineGap}px（牌高 $tileH），整块散开了",
            gapRatio <= 0.12f,
        )
        assertTrue(
            "数字与花色贴在一起了：${measure.lineGap}px",
            gapRatio >= 0.04f,
        )
    }

    /** 牌面的量测结果：牌本身的尺寸、墨迹到四边的留白、两行字之间的空行。 */
    private class TileMeasure(
        val width: Int,
        val height: Int,
        val topPad: Int,
        val bottomPad: Int,
        val leftPad: Int,
        val rightPad: Int,
        val lineGap: Int,
    )

    /**
     * 在截图里找出牌面与它的墨迹。
     *
     * 牌底是近白色（`TileFaceLight`），描边与页底都比它深，因此先用颜色找牌面矩形，
     * 再在矩形内圈找「不像牌底」的像素。四角的圆角和描边会混进来，
     * 所以只扫中段那一块——它是居中的，拿它算留白与整块牌是同一个结论。
     */
    private fun measureTile(bitmap: Bitmap): TileMeasure {
        val face = TileFaceLight.toArgb()
        fun distance(pixel: Int): Int = maxOf(
            abs(((pixel shr 16) and 0xFF) - ((face shr 16) and 0xFF)),
            abs(((pixel shr 8) and 0xFF) - ((face shr 8) and 0xFF)),
            abs((pixel and 0xFF) - (face and 0xFF)),
        )

        var tileLeft = Int.MAX_VALUE
        var tileTop = Int.MAX_VALUE
        var tileRight = -1
        var tileBottom = -1
        for (y in 0 until 600) {
            for (x in 0 until 500) {
                if (distance(bitmap.getPixel(x, y)) <= 6) {
                    if (x < tileLeft) tileLeft = x
                    if (x > tileRight) tileRight = x
                    if (y < tileTop) tileTop = y
                    if (y > tileBottom) tileBottom = y
                }
            }
        }
        assertTrue("牌面根本没有画出来", tileRight > tileLeft && tileBottom > tileTop)

        val tileW = tileRight - tileLeft + 1
        val tileH = tileBottom - tileTop + 1
        var inkLeft = Int.MAX_VALUE
        var inkRight = -1
        var inkTop = Int.MAX_VALUE
        var inkBottom = -1
        for (y in tileTop until tileBottom) {
            for (x in tileLeft until tileRight) {
                // 只取中段：四角是圆的，描边也不是牌底
                if (x < tileLeft + tileW * 0.18f || x > tileRight - tileW * 0.18f) continue
                if (y < tileTop + tileH * 0.12f || y > tileBottom - tileH * 0.12f) continue
                if (distance(bitmap.getPixel(x, y)) > 40) {
                    if (x < inkLeft) inkLeft = x
                    if (x > inkRight) inkRight = x
                    if (y < inkTop) inkTop = y
                    if (y > inkBottom) inkBottom = y
                }
            }
        }
        assertTrue("牌面上没有字", inkRight > inkLeft && inkBottom > inkTop)

        // 两行字之间那一条完全空白的行为「字间空行」
        var gap = 0
        var run = 0
        for (y in inkTop..inkBottom) {
            var hasInk = false
            for (x in inkLeft..inkRight) {
                if (distance(bitmap.getPixel(x, y)) > 40) {
                    hasInk = true
                    break
                }
            }
            if (!hasInk) {
                run++
                if (run > gap) gap = run
            } else {
                run = 0
            }
        }

        return TileMeasure(
            width = tileW,
            height = tileH,
            topPad = inkTop - tileTop,
            bottomPad = tileBottom - inkBottom,
            leftPad = inkLeft - tileLeft,
            rightPad = tileRight - inkRight,
            lineGap = gap,
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
                opponentHand = 0,
                onNavigateBack = {},
                onSelectSuit = {},
                onMark = {},
                onUnmark = {},
                onClear = {},
                onOpponentChange = {},
            )
        }

        assertTrue(
            "暗色下的牌面点数没有画出来",
            bitmap.countPixelsNear(TileTongDark, tolerance = 32) > 200,
        )
    }
}
