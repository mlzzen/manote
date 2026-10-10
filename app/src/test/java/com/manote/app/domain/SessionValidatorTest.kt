package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 输入校验（规格 2.1、2.2、5.1）。 */
class SessionValidatorTest {

    private fun <T> successOrNull(result: ManoteResult<T>): T? =
        (result as? ManoteResult.Success)?.value

    private fun <T> errorOrNull(result: ManoteResult<T>): ManoteError? =
        (result as? ManoteResult.Failure)?.error

    @Test
    fun blankTitleFallsBackToDefault() {
        assertEquals(SessionRules.DEFAULT_TITLE, successOrNull(SessionValidator.normalizeTitle("")))
        assertEquals(SessionRules.DEFAULT_TITLE, successOrNull(SessionValidator.normalizeTitle("   ")))
    }

    @Test
    fun titleIsTrimmedAndLimitedToThirtyCharacters() {
        assertEquals("周五晚局", successOrNull(SessionValidator.normalizeTitle("  周五晚局  ")))

        val exactlyThirty = "甲".repeat(30)
        assertEquals(exactlyThirty, successOrNull(SessionValidator.normalizeTitle(exactlyThirty)))

        assertEquals(
            ManoteError.INVALID_TITLE,
            errorOrNull(SessionValidator.normalizeTitle("甲".repeat(31))),
        )
    }

    @Test
    fun titleLengthCountsCodePointsNotSurrogates() {
        // 15 个 emoji 是 15 字，不应因 UTF-16 代理对被算成 30 字以上而误拒
        val emojiTitle = "\uD83C\uDC04".repeat(15)
        assertTrue(successOrNull(SessionValidator.normalizeTitle(emojiTitle)) != null)
        assertEquals(
            ManoteError.INVALID_TITLE,
            errorOrNull(SessionValidator.normalizeTitle("\uD83C\uDC04".repeat(31))),
        )
    }

    @Test
    fun noteIsOptionalAndLimitedToOneHundredCharacters() {
        assertEquals("", successOrNull(SessionValidator.normalizeNote("")))
        assertEquals("三缺一改四人", successOrNull(SessionValidator.normalizeNote("  三缺一改四人 ")))
        assertEquals("乙".repeat(100), successOrNull(SessionValidator.normalizeNote("乙".repeat(100))))
        assertEquals(
            ManoteError.INVALID_NOTE,
            errorOrNull(SessionValidator.normalizeNote("乙".repeat(101))),
        )
    }

    @Test
    fun nicknameMustBeOneToTwelveCharactersAfterTrim() {
        assertEquals(ManoteError.INVALID_NICKNAME, errorOrNull(SessionValidator.normalizeNickname("")))
        assertEquals(ManoteError.INVALID_NICKNAME, errorOrNull(SessionValidator.normalizeNickname("   ")))
        assertEquals("阿明", successOrNull(SessionValidator.normalizeNickname("  阿明 ")))
        assertEquals("丙".repeat(12), successOrNull(SessionValidator.normalizeNickname("丙".repeat(12))))
        assertEquals(
            ManoteError.INVALID_NICKNAME,
            errorOrNull(SessionValidator.normalizeNickname("丙".repeat(13))),
        )
    }

    @Test
    fun playedAtRejectsFutureButAllowsPast() {
        val now = 1_700_000_000_000L

        assertNull(SessionValidator.validatePlayedAt(now, now))
        assertNull(SessionValidator.validatePlayedAt(now - 86_400_000L, now))
        assertEquals(
            ManoteError.PLAYED_AT_IN_FUTURE,
            SessionValidator.validatePlayedAt(now + 1L, now),
        )
    }

    @Test
    fun seatNetsRequireAllFourSeats() {
        assertEquals(
            ManoteError.INVALID_SEAT_SET,
            SessionValidator.validateSeatNets(mapOf(0 to 0L, 1 to 0L, 2 to 0L)),
        )
        assertEquals(
            ManoteError.INVALID_SEAT_SET,
            SessionValidator.validateSeatNets(mapOf(0 to 0L, 1 to 0L, 2 to 0L, 4 to 0L)),
        )
        assertNull(SessionValidator.validateSeatNets(mapOf(0 to 0L, 1 to 0L, 2 to 0L, 3 to 0L)))
    }

    @Test
    fun seatNetsRejectUnbalancedTotalsWithoutAutoFixing() {
        // T02：+30、-10、-10、0 合计 +10，必须拒绝
        assertEquals(
            ManoteError.SUM_NOT_ZERO,
            SessionValidator.validateSeatNets(mapOf(0 to 3000L, 1 to -1000L, 2 to -1000L, 3 to 0L)),
        )
        // T01/T03：合计为零才通过
        assertNull(SessionValidator.validateSeatNets(mapOf(0 to 3000L, 1 to -1000L, 2 to -1000L, 3 to -1000L)))
        assertNull(SessionValidator.validateSeatNets(mapOf(0 to 30L, 1 to -10L, 2 to -10L, 3 to -10L)))
    }

    @Test
    fun seatNetsRejectOutOfRangeBeforeCheckingBalance() {
        assertEquals(
            ManoteError.AMOUNT_OUT_OF_RANGE,
            SessionValidator.validateSeatNets(
                mapOf(
                    0 to Money.MAX_NET_MINOR + 1,
                    1 to -(Money.MAX_NET_MINOR + 1),
                    2 to 0L,
                    3 to 0L,
                ),
            ),
        )
    }

    @Test
    fun totalMinorReportsTheRawSum() {
        assertEquals(1000L, SessionValidator.totalMinor(mapOf(0 to 3000L, 1 to -1000L, 2 to -1000L, 3 to 0L)))
        assertEquals(0L, SessionValidator.totalMinor(mapOf(0 to 0L, 1 to 0L, 2 to 0L, 3 to 0L)))
    }
}
