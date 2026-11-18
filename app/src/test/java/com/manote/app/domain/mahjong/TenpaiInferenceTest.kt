package com.manote.app.domain.mahjong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「对手可能听什么」的推算。
 *
 * 这一块最容易出错的不是算法，而是**漏掉摆法**：手动分析时人往往只想得到
 * 「剩下的牌能拼成哪几副面子」，而同一池牌常常还有好几种分配都能听牌——
 * `223456679` 就有九种（用户手算时先看到三种）。所以这里断的是**全部九种**。
 */
class TenpaiInferenceTest {

    @Test
    fun infer_listsEveryTenpaiWayToSplitThePool() {
        val combos = TenpaiInference.infer(tilesOf("${POOL}m"), opponentHand = 7)

        assertEquals(
            listOf(
                "2234566", "2234567", "2234579",
                "2245667", "2245679",
                "2345667", "2345679", "2346679",
                "3456679",
            ),
            combos.map { specOf(it.hand) },
        )
    }

    @Test
    fun infer_carriesWaitsAndWhatStaysInTheWall() {
        val combos = TenpaiInference.infer(tilesOf("${POOL}m"), opponentHand = 7)
        val table = combos.associate {
            specOf(it.hand) to (specOf(it.waits) to specOf(it.leftovers))
        }

        assertEquals(
            mapOf(
                "2234566" to ("26" to "79"),
                "2234567" to ("258" to "69"),
                "2234579" to ("8" to "66"),
                "2245667" to ("58" to "39"),
                "2245679" to ("8" to "36"),
                "2345667" to ("6" to "29"),
                "2345679" to ("9" to "26"),
                "2346679" to ("8" to "25"),
                "3456679" to ("8" to "22"),
            ),
            table,
        )

        // 每一条的「手牌 + 牌山」都得正好还原这一池牌，既不能丢也不能多
        combos.forEach { combo ->
            assertEquals(7, combo.hand.size)
            assertEquals(2, combo.leftovers.size)
            assertEquals(POOL, specOf(combo.hand + combo.leftovers))
            assertTrue("听牌的手不该没得听：${specOf(combo.hand)}", combo.waits.isNotEmpty())
        }
    }

    @Test
    fun infer_acceptsAPoolThatIsExactlyTheWholeHand() {
        // 未知七张、对手七张：牌山一张不剩，只有一种可能，照样要算
        val combos = TenpaiInference.infer(tilesOf("1234567m"), opponentHand = 7)

        assertEquals(1, combos.size)
        assertEquals("1234567", specOf(combos.single().hand))
        // 1、4、7 都能和：11 + 234 + 567、123 + 44 + 567、123 + 456 + 77
        assertEquals("147", specOf(combos.single().waits))
        assertTrue(combos.single().leftovers.isEmpty())
    }

    @Test
    fun infer_stopsOnceTheWallGetsTooWide() {
        // 未知十一张、对手七张：牌山剩四张，摆法一下多到没法看
        val pool = tilesOf("12345678911m")

        assertFalse(TenpaiInference.canInfer(pool, opponentHand = 7))
        assertTrue(
            "超过上限就不推，而不是推一半",
            TenpaiInference.infer(pool, opponentHand = 7).isEmpty(),
        )
    }

    @Test
    fun infer_needsAHandSizeThatCanActuallyBeWaiting() {
        // 十四张是刚摸完该打的那一步，谈「听什么」没有意义；
        // 零张、负数、比未知牌还多，也都不是真实状态。
        listOf(0, -1, 14, 11, 8).forEach { size ->
            assertTrue(
                "对手手牌 $size 张不该推",
                TenpaiInference.infer(tilesOf("${POOL}m"), opponentHand = size).isEmpty(),
            )
        }
        assertTrue(
            "未知牌比手牌还少，装不下",
            TenpaiInference.infer(tilesOf("123m"), opponentHand = 7).isEmpty(),
        )
    }

    @Test
    fun infer_hasNothingToSayWhenTheHandCannotListen() {
        // 1 4 7 2 5 8 3：七张互不相干（唯一的一副顺子还被拆散），一向听，
        // 所以这一池算出来是空的——「没有能听的摆法」也是要报的一种结论。
        assertTrue(TenpaiInference.infer(tilesOf("1472583m"), opponentHand = 7).isEmpty())
    }

    private companion object {
        /** 用户报的那一池：九张万子，对手七张。 */
        const val POOL = "223456679"
    }
}
