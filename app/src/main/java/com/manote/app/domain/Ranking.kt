package com.manote.app.domain

/** 某座位在本场的名次。 */
data class SeatRank(val seat: Int, val rank: Int)

/**
 * 本场排名（规格 2.5）。
 *
 * 按净金额从高到低排；同额并列，采用竞赛排名（1、1、3、4）。
 * 结果按名次升序、同额按 seat 升序返回，可直接用于 2×2 展示。
 */
object Ranking {

    fun rank(nets: List<SeatNet>): List<SeatRank> {
        val ordered = nets.sortedWith(
            compareByDescending<SeatNet> { it.netMinor }.thenBy { it.seat },
        )
        return ordered.map { seatNet ->
            val betterCount = nets.count { it.netMinor > seatNet.netMinor }
            SeatRank(seat = seatNet.seat, rank = betterCount + 1)
        }
    }
}
