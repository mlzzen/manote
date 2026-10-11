package com.manote.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * 打完时间落在一天中的哪个时段：早场 / 下午场 / 晚场。
 *
 * 判定只看「打完时间的钟点」，与日期无关。时区必须与列表显示时间一致（设备时区），
 * 否则会出现「列表显示 21:30、却被算成下午场」这种自相矛盾的结果。
 *
 * 麻将局经常打到后半夜，因此 00:00–04:59 归入晚场，
 * 而不是切出一个几乎没人用的「凌晨场」。
 */
enum class TimeSlot {
    /** 05:00–11:59。 */
    MORNING,

    /** 12:00–17:59。 */
    AFTERNOON,

    /** 18:00–23:59，以及跨过零点的 00:00–04:59。 */
    EVENING,
    ;

    companion object {

        /** 早场起点（含）。 */
        const val MORNING_START_HOUR = 5

        /** 下午场起点（含）。 */
        const val AFTERNOON_START_HOUR = 12

        /** 晚场起点（含），一直持续到次日早场起点。 */
        const val EVENING_START_HOUR = 18

        /**
         * 展示与统计的固定顺序：早场 → 下午场 → 晚场。
         *
         * 各处都遍历这个列表，而不是依赖 enum 的 ordinal，
         * 将来插入新时段时不会悄悄改变界面顺序。
         */
        val displayOrder: List<TimeSlot> = listOf(MORNING, AFTERNOON, EVENING)

        fun of(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): TimeSlot =
            ofHour(Instant.ofEpochMilli(epochMillis).atZone(zoneId).hour)

        /** 按 0–23 的整点判定，便于直接钉死边界。 */
        fun ofHour(hour: Int): TimeSlot = when {
            hour >= EVENING_START_HOUR || hour < MORNING_START_HOUR -> EVENING
            hour >= AFTERNOON_START_HOUR -> AFTERNOON
            else -> MORNING
        }
    }
}
