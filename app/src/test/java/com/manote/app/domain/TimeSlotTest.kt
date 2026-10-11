package com.manote.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 结束时间 → 早场 / 下午场 / 晚场。
 *
 * 边界与跨零点是最容易写错的地方，这里逐个钉死；
 * 时区必须与列表显示时间一致，因此也要有一条跨时区的用例。
 */
class TimeSlotTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun hourBoundariesSplitIntoThreeSlots() {
        // 早场 05:00 起；04:59 仍算晚场（后半夜的局）
        assertEquals(TimeSlot.EVENING, TimeSlot.ofHour(4))
        assertEquals(TimeSlot.MORNING, TimeSlot.ofHour(5))
        assertEquals(TimeSlot.MORNING, TimeSlot.ofHour(11))

        assertEquals(TimeSlot.AFTERNOON, TimeSlot.ofHour(12))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.ofHour(17))

        assertEquals(TimeSlot.EVENING, TimeSlot.ofHour(18))
        assertEquals(TimeSlot.EVENING, TimeSlot.ofHour(23))
        assertEquals(TimeSlot.EVENING, TimeSlot.ofHour(0))
    }

    @Test
    fun everyHourOfTheDayMapsToExactlyOneSlot() {
        val count = (0..23).groupingBy { TimeSlot.ofHour(it) }.eachCount()
        assertEquals(24, count.values.sum())
        // 早场 05–11 共 7 小时，下午场 12–17 共 6 小时，晚场 18–04 共 11 小时
        assertEquals(7, count[TimeSlot.MORNING])
        assertEquals(6, count[TimeSlot.AFTERNOON])
        assertEquals(11, count[TimeSlot.EVENING])
    }

    @Test
    fun timestampIsJudgedInTheGivenZone() {
        // 2026-10-05 21:30（上海）→ 晚场
        val eveningInShanghai = at(2026, 10, 5, 21, 30)
        assertEquals(TimeSlot.EVENING, TimeSlot.of(eveningInShanghai, zone))

        // 同一时刻在 UTC 是 13:30，按 UTC 判定会落到下午场——说明时区确实被用上了
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.of(eveningInShanghai, ZoneId.of("UTC")))
    }

    @Test
    fun afterMidnightSessionStillCountsAsEvening() {
        // 打通宵到凌晨 2 点，按晚场统计，而不是切成一个新时段
        val afterMidnight = at(2026, 10, 6, 2, 15)
        assertEquals(TimeSlot.EVENING, TimeSlot.of(afterMidnight, zone))
    }

    @Test
    fun displayOrderIsMorningAfternoonEvening() {
        assertEquals(
            listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON, TimeSlot.EVENING),
            TimeSlot.displayOrder,
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
