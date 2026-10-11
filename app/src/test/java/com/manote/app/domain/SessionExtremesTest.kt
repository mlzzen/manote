package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单场极值：赢得最多的那一场、输得最多的那一场。
 *
 * 要点是**两边都可以为 null**：「从没赢过」与「单场赢最多 ¥0.00」是两回事，
 * 后者根本不成立——净额 0 的一场既不是赢也不是输，不该被当成极值占位。
 * 另一处是并列时的取舍：同一份输入必须永远得到同一个日期，
 * 否则界面会在两次刷新之间换一场，用户会以为数据变了。
 */
class SessionExtremesTest {

    private fun net(playedAt: Long, netMinor: Long) = DatedNet(playedAt = playedAt, netMinor = netMinor)

    @Test
    fun emptyHistoryHasNoExtremes() {
        val extremes = SessionExtremes.of(emptyList())

        assertNull(extremes.bestWin)
        assertNull(extremes.worstLoss)
        assertFalse("一场都没打过时整块没有可说的内容", extremes.hasAny)
    }

    @Test
    fun picksTheHighestAndLowestSession() {
        val extremes = SessionExtremes.of(
            listOf(
                net(1_000L, 2_600L),
                net(2_000L, -400L),
                net(3_000L, 900L),
                net(4_000L, -4_100L),
            ),
        )

        assertEquals(2_600L, requireNotNull(extremes.bestWin).netMinor)
        assertEquals(1_000L, requireNotNull(extremes.bestWin).playedAt)
        assertEquals(-4_100L, requireNotNull(extremes.worstLoss).netMinor)
        assertEquals(4_000L, requireNotNull(extremes.worstLoss).playedAt)
    }

    @Test
    fun neverWinningLeavesBestWinNull() {
        // 全是输：没有「赢最多」这一场，而不是「赢最多 ¥0.00」
        val extremes = SessionExtremes.of(listOf(net(1_000L, -300L), net(2_000L, -800L)))

        assertNull(extremes.bestWin)
        assertEquals(-800L, requireNotNull(extremes.worstLoss).netMinor)
        assertTrue(extremes.hasAny)
    }

    @Test
    fun neverLosingLeavesWorstLossNull() {
        val extremes = SessionExtremes.of(listOf(net(1_000L, 300L), net(2_000L, 800L)))

        assertEquals(800L, requireNotNull(extremes.bestWin).netMinor)
        assertNull(extremes.worstLoss)
        assertTrue(extremes.hasAny)
    }

    @Test
    fun allDrawsHasNoExtremes() {
        // 全程持平：净额 0 的一场既不是赢最多也不是输最多，两边都不该有值
        val extremes = SessionExtremes.of(listOf(net(1_000L, 0L), net(2_000L, 0L)))

        assertNull(extremes.bestWin)
        assertNull(extremes.worstLoss)
        assertFalse(extremes.hasAny)
    }

    @Test
    fun singleSessionCanBeBothBestWinAndWorstLossOfItsKind() {
        // 只有一场且为赢：它是「赢最多」，但没有任何一场是「输最多」
        val extremes = SessionExtremes.of(listOf(net(1_000L, 500L)))

        assertEquals(500L, requireNotNull(extremes.bestWin).netMinor)
        assertNull(extremes.worstLoss)
    }

    @Test
    fun tiesGoToTheEarliestSession() {
        // 两场都是 +2_600：取更早的那一场，与「最佳 / 最差单日」的取舍一致。
        // 不定下来一个，界面会在两次刷新之间换日期，看起来像数据变了。
        val extremes = SessionExtremes.of(listOf(net(9_000L, 2_600L), net(3_000L, 2_600L)))

        assertEquals(3_000L, requireNotNull(extremes.bestWin).playedAt)
    }

    @Test
    fun tieBreakingDoesNotDependOnInputOrder() {
        // 同一份数据换个传入顺序，结果必须一模一样：
        // 数据库给首页是倒序、给牌友详情也是倒序，但谁也保证不了以后不变。
        val ascending = SessionExtremes.of(listOf(net(3_000L, -1_200L), net(9_000L, -1_200L)))
        val descending = SessionExtremes.of(listOf(net(9_000L, -1_200L), net(3_000L, -1_200L)))

        assertEquals(ascending, descending)
        assertEquals(3_000L, requireNotNull(ascending.worstLoss).playedAt)
    }

    @Test
    fun ignoresDrawsWhenPickingTheExtremes() {
        // 持平场夹在中间，不能把真正的极值挤掉
        val extremes = SessionExtremes.of(
            listOf(net(1_000L, 0L), net(2_000L, 700L), net(3_000L, -900L), net(4_000L, 0L)),
        )

        assertEquals(2_000L, requireNotNull(extremes.bestWin).playedAt)
        assertEquals(3_000L, requireNotNull(extremes.worstLoss).playedAt)
    }
}
