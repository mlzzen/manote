package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 对手账与「最苦主」（牌友列表标签）。
 *
 * 「他赢我多少」在四人对局里没有直接数据，这里用同桌各场的净额之差近似；
 * 因此门槛（同桌 ≥5 场）与「必须先整体赢我」这两个条件都必须被测试钉住，
 * 否则偶尔一次同桌输得多就会被标成最苦主。
 */
class RivalryTest {

    /** 造 [count] 场同桌记录，我每场输 [myPerSession]，他每场赢 [theirPerSession]。 */
    private fun matches(
        playerId: String,
        count: Int,
        myPerSession: Long,
        theirPerSession: Long,
    ): List<OpponentMatch> = List(count) {
        OpponentMatch(playerId = playerId, myNetMinor = myPerSession, theirNetMinor = theirPerSession)
    }

    @Test
    fun aggregatesByPlayerId() {
        val records = Rivalry.of(
            matches("f1", 3, myPerSession = -100L, theirPerSession = 200L),
        )

        assertEquals(1, records.size)
        val record = records.single()
        assertEquals("f1", record.playerId)
        assertEquals(3, record.commonSessionCount)
        assertEquals(-300L, record.myNetMinor)
        assertEquals(600L, record.theirNetMinor)
        assertEquals("同桌净额差 = 他多赢的部分", 900L, record.marginMinor)
    }

    @Test
    fun requiresAtLeastFiveCommonSessions() {
        // 只同桌 4 场、且他赢得更多，仍然不入榜
        val short = matches("f1", Rivalry.MIN_COMMON_SESSIONS - 1, -500L, 500L)
        assertNull(Rivalry.worstRival(short))

        val enough = matches("f1", Rivalry.MIN_COMMON_SESSIONS, -500L, 500L)
        assertEquals("f1", Rivalry.worstRival(enough)?.playerId)
    }

    @Test
    fun ignoresOpponentsWhoDidNotOutperformMe() {
        // 同桌够多，但这些场次里他没有比我多赢：不算苦主
        val even = matches("f1", 6, myPerSession = 0L, theirPerSession = 0L)
        assertNull(Rivalry.worstRival(even))

        val iWonMore = matches("f1", 6, myPerSession = 300L, theirPerSession = 100L)
        assertNull(Rivalry.worstRival(iWonMore))
    }

    @Test
    fun picksTheLargestMargin() {
        val result = Rivalry.worstRival(
            matches("small", 5, -100L, 200L) +   // margin ×5 = 1_500
            matches("big", 5, -300L, 500L),      // margin ×5 = 4_000
        )

        assertEquals("big", result?.playerId)
    }

    @Test
    fun tiesAreBrokenByPlayerIdSoTheBadgeDoesNotFlicker() {
        val result = Rivalry.worstRival(
            matches("bbb", 5, -100L, 100L) + matches("aaa", 5, -100L, 100L),
        )

        assertEquals("并列时取 ID 较小的一个，标签不会在两次刷新之间跳来跳去", "aaa", result?.playerId)
    }

    @Test
    fun worstRivalIsNullWithoutAnyMatch() {
        assertNull(Rivalry.worstRival(emptyList()))
    }

    @Test
    fun countsSessionsPerOpponentIndependently() {
        val result = Rivalry.worstRival(
            // 同桌 3 场（不够）但每场输得很惨
            matches("rare", 3, -5_000L, 5_000L) +
                // 同桌 5 场（刚好够），每场小输
                matches("often", 5, -100L, 100L),
        )

        assertEquals("often", result?.playerId)
    }
}
