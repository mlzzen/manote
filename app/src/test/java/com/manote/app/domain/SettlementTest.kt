package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 结清建议（规格 2.5、验收用例 T14）。 */
class SettlementTest {

    @Test
    fun matchesDebtorsToCreditorsInDescendingOrder() {
        // T14：A +50、B +20、C -40、D -30 → C 给 A 40、D 给 A 10、D 给 B 20
        val transfers = Settlement.suggest(
            listOf(
                SeatNet(0, 5000L), // A 自己
                SeatNet(1, 2000L), // B 上家
                SeatNet(2, -4000L), // C 对家
                SeatNet(3, -3000L), // D 下家
            ),
        )

        assertEquals(3, transfers.size)
        assertEquals(Transfer(fromSeat = 2, toSeat = 0, amountMinor = 4000L), transfers[0])
        assertEquals(Transfer(fromSeat = 3, toSeat = 0, amountMinor = 1000L), transfers[1])
        assertEquals(Transfer(fromSeat = 3, toSeat = 1, amountMinor = 2000L), transfers[2])
    }

    @Test
    fun allZeroNeedsNoSettlement() {
        // T15 前半：全零显示「无需结清」
        val transfers = Settlement.suggest(
            listOf(SeatNet(0, 0L), SeatNet(1, 0L), SeatNet(2, 0L), SeatNet(3, 0L)),
        )

        assertTrue(transfers.isEmpty())
    }

    @Test
    fun neverExceedsThreeTransfers() {
        val cases = listOf(
            listOf(5000L, 2000L, -4000L, -3000L),
            listOf(1000L, 1000L, -2000L, 0L),
            listOf(3000L, -1000L, -1000L, -1000L),
            listOf(1000L, 1000L, -1000L, -1000L),
            listOf(2500L, -100L, -1200L, -1200L),
            listOf(700L, 700L, 700L, -2100L),
        )
        for (nets in cases) {
            val transfers = Settlement.suggest(nets.mapIndexed { seat, net -> SeatNet(seat, net) })
            assertTrue("笔数不得超过三笔：$nets -> $transfers", transfers.size <= 3)
        }
    }

    @Test
    fun everyTransferClearsTheWholeBalance() {
        val cases = listOf(
            listOf(5000L, 2000L, -4000L, -3000L),
            listOf(3000L, -1000L, -1000L, -1000L),
            listOf(700L, 700L, 700L, -2100L),
            listOf(1L, 0L, 0L, -1L),
        )
        for (nets in cases) {
            val transfers = Settlement.suggest(nets.mapIndexed { seat, net -> SeatNet(seat, net) })

            // 每个人收付相抵后余额必须归零
            val settled = MutableList(nets.size) { 0L }
            for (transfer in transfers) {
                settled[transfer.fromSeat] -= transfer.amountMinor
                settled[transfer.toSeat] += transfer.amountMinor
                assertTrue("单笔金额必须为正：$transfer", transfer.amountMinor > 0L)
            }
            assertEquals("结清后每人收付净额应等于其本场净金额：$nets", nets, settled)
        }
    }

    @Test
    fun tiesAreBrokenBySeatOrder() {
        // 待付同额时按 seat 升序，先与余额最大的待收方撮合
        val transfers = Settlement.suggest(
            listOf(
                SeatNet(0, 2000L),
                SeatNet(1, -1000L),
                SeatNet(2, -1000L),
                SeatNet(3, 0L),
            ),
        )

        assertEquals(2, transfers.size)
        assertEquals(Transfer(fromSeat = 1, toSeat = 0, amountMinor = 1000L), transfers[0])
        assertEquals(Transfer(fromSeat = 2, toSeat = 0, amountMinor = 1000L), transfers[1])
    }
}
