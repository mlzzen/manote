package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 赢/输切换 → 带符号净额（规格 2.1）。
 *
 * 零必须不受切换影响，否则界面上会出现「输 0.00 元」这种自相矛盾的结果。
 */
class SeatEntryTest {

    @Test
    fun winKeepsPositive() {
        assertEquals(3_000L, SeatEntry.signedMinor(3_000L, isWin = true))
        assertEquals(1L, SeatEntry.signedMinor(1L, isWin = true))
    }

    @Test
    fun lossBecomesNegative() {
        assertEquals(-3_000L, SeatEntry.signedMinor(3_000L, isWin = false))
        assertEquals(-1L, SeatEntry.signedMinor(1L, isWin = false))
    }

    @Test
    fun zeroIsUnaffectedByToggle() {
        assertEquals(0L, SeatEntry.signedMinor(0L, isWin = true))
        assertEquals(0L, SeatEntry.signedMinor(0L, isWin = false))
    }

    @Test
    fun maximumMagnitudeRoundTrips() {
        assertEquals(Money.MAX_NET_MINOR, SeatEntry.signedMinor(Money.MAX_NET_MINOR, isWin = true))
        assertEquals(Money.MIN_NET_MINOR, SeatEntry.signedMinor(Money.MAX_NET_MINOR, isWin = false))
    }
}
