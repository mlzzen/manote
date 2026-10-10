package com.manote.app.domain

/** 一笔结清建议：fromSeat 应付给 toSeat 多少分。 */
data class Transfer(val fromSeat: Int, val toSeat: Int, val amountMinor: Long)

/**
 * 结清建议（规格 2.5）。
 *
 * 只做「输家向赢家补齐金额」的算术建议，不表示付款已完成，也不接支付。
 * 待付与待收双方各按余额从大到小、同额按 seat 升序排列，
 * 依次取 min(待付额, 待收额) 撮合；四人场景下笔数天然不超过三笔。
 */
object Settlement {

    fun suggest(nets: List<SeatNet>): List<Transfer> {
        val debtors = nets
            .filter { it.netMinor < 0L }
            .map { it.seat to -it.netMinor }
            .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })
            .toMutableList()

        val creditors = nets
            .filter { it.netMinor > 0L }
            .map { it.seat to it.netMinor }
            .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })
            .toMutableList()

        val transfers = mutableListOf<Transfer>()
        var debtorIndex = 0
        var creditorIndex = 0

        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val (debtorSeat, owed) = debtors[debtorIndex]
            val (creditorSeat, receivable) = creditors[creditorIndex]
            val amount = minOf(owed, receivable)

            if (amount > 0L) {
                transfers += Transfer(fromSeat = debtorSeat, toSeat = creditorSeat, amountMinor = amount)
            }

            val remainingOwed = owed - amount
            val remainingReceivable = receivable - amount
            debtors[debtorIndex] = debtorSeat to remainingOwed
            creditors[creditorIndex] = creditorSeat to remainingReceivable

            if (remainingOwed == 0L) debtorIndex++
            if (remainingReceivable == 0L) creditorIndex++
        }

        return transfers
    }
}
