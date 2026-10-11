package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分时段统计（早场 / 下午场 / 晚场的胜率与场均）。
 *
 * 这些数字直接上首页，因此口径必须钉死：
 * 胜率分母包含持平场；场均用净额 ÷ 场数；缺失的时段补 0 场而不是省略。
 */
class TimeSlotStatisticsTest {

    @Test
    fun alwaysReturnsThreeSlotsInFixedOrder() {
        val slots = TimeSlotStatistics.of(emptyMap())

        assertEquals(3, slots.size)
        assertEquals(
            listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON, TimeSlot.EVENING),
            slots.map { it.slot },
        )
        assertTrue(slots.all { it.isEmpty })
    }

    @Test
    fun missingSlotIsFilledWithEmptyStatistics() {
        val slots = TimeSlotStatistics.of(mapOf(TimeSlot.EVENING to listOf(1000L, -500L)))
        val morning = slots.first { it.slot == TimeSlot.MORNING }
        val evening = slots.first { it.slot == TimeSlot.EVENING }

        assertEquals(0, morning.sessionCount)
        assertEquals(0L, morning.averageNetMinor)
        assertEquals(0, morning.winRatePercent)
        // 领域层只给 0，显示成「—」是界面的职责

        assertEquals(2, evening.sessionCount)
        assertEquals(250L, evening.averageNetMinor)
        assertEquals(50, evening.winRatePercent)
    }

    @Test
    fun winRateCountsDrawsInTheDenominator() {
        // 一场赢、一场输、一场持平 → 1/3 ≈ 33%，而不是 1/2 = 50%
        val slots = TimeSlotStatistics.of(mapOf(TimeSlot.MORNING to listOf(100L, -100L, 0L)))
        val morning = slots.first { it.slot == TimeSlot.MORNING }

        assertEquals(3, morning.sessionCount)
        assertEquals(33, morning.winRatePercent)
        assertEquals(0L, morning.averageNetMinor)
    }

    @Test
    fun averageRoundsHalfAwayFromZeroAndStaysIntegral() {
        // 105 分 ÷ 2 场 = 52.5 分 → 进位到 53；负数向远离零的方向进位
        val slots = TimeSlotStatistics.of(
            mapOf(
                TimeSlot.AFTERNOON to listOf(105L, 0L),
                TimeSlot.EVENING to listOf(-105L, 0L),
            ),
        )

        assertEquals(53L, slots.first { it.slot == TimeSlot.AFTERNOON }.averageNetMinor)
        assertEquals(-53L, slots.first { it.slot == TimeSlot.EVENING }.averageNetMinor)
    }

    @Test
    fun winRateIsRoundedToNearestWholePercent() {
        // 2/3 → 66.67% → 67%
        val slots = TimeSlotStatistics.of(
            mapOf(TimeSlot.EVENING to listOf(100L, 100L, -50L)),
        )

        assertEquals(67, slots.first { it.slot == TimeSlot.EVENING }.winRatePercent)
    }

    @Test
    fun slotIsNotEmptyOnlyWhenItHasSessions() {
        val slots = TimeSlotStatistics.of(mapOf(TimeSlot.MORNING to listOf(0L)))

        val morning = slots.first { it.slot == TimeSlot.MORNING }
        assertFalse(morning.isEmpty)
        assertEquals(1, morning.sessionCount)
        assertEquals(0, morning.winRatePercent)
    }
}
