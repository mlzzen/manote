package com.manote.app.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.manote.app.R
import com.manote.app.domain.Money
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.SessionRules
import com.manote.app.domain.TimeSlot
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 界面上的展示文本与格式化。
 *
 * 金额一律经 [Money] 从整数分换算，不在这里做任何浮点运算（规格 2.1）。
 * 时间按设备时区显示，但存储始终是 UTC epoch milliseconds（规格 2.1）。
 *
 * 所有时间格式化都接受一个可选的 [ZoneId]，默认设备时区——与
 * [com.manote.app.domain.TimeSlot.of]、[com.manote.app.data.repository.SessionCsv] 等
 * 保持同一套约定。界面调用不必传（取默认即可）；
 * 需要钉死时区的场合（测试、导出）则显式传入，
 * 避免出现「按 A 时区构造、按 B 时区读出」这类只有换台机器才暴露的错。
 */
object UiText {

    /** `¥12.30`（不带正号），列表与详情里的金额。 */
    fun yuan(minor: Long): String = "¥${Money.formatMinor(minor)}"

    /**
     * `+¥12.30` / `-¥5.00` / `¥0.00`。
     * 正负号本身也表达输赢，因此颜色只是辅助，不是唯一线索（规格 2.1、3.6）。
     */
    fun signedYuan(minor: Long): String = when {
        minor > 0L -> "+¥${Money.formatMinor(minor)}"
        minor < 0L -> "-¥${Money.formatMinor(-minor)}"
        else -> "¥${Money.formatMinor(0L)}"
    }

    /** `2026.10.05`。 */
    fun date(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = toLocalDate(epochMillis, zoneId)
        return String.format(
            Locale.US,
            "%04d.%02d.%02d",
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }

    /** `2026.10.05 21:30`。 */
    fun dateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return String.format(
            Locale.US,
            "%04d.%02d.%02d %02d:%02d",
            time.year,
            time.monthValue,
            time.dayOfMonth,
            time.hour,
            time.minute,
        )
    }

    /** `10.05`，用于紧凑列表。 */
    fun monthDay(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = toLocalDate(epochMillis, zoneId)
        return String.format(Locale.US, "%02d.%02d", date.monthValue, date.dayOfMonth)
    }

    /** `14:30`，用于只关心一天中哪个时刻的场合（例如推算出的开始时间）。 */
    fun timeOfDay(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return String.format(Locale.US, "%02d:%02d", time.hour, time.minute)
    }

    /**
     * `10.05`，用于已经有 [LocalDate] 的场合（例如最佳 / 最差单日）。
     *
     * 与按时间戳格式化的那个重载同源同格式；已经算出日期的地方不必再折回时间戳，
     * 也就不会因为在两个时区之间来回换算而偏移一天。
     */
    fun monthDay(date: LocalDate): String =
        String.format(Locale.US, "%02d.%02d", date.monthValue, date.dayOfMonth)

    /**
     * 场次列表的月份分组标题（规格 3.3）：中文下是 `2026年10月`，其它语言下是 `October 2026`。
     *
     * [locale] 默认取设备语言，与界面文案同一套语言——界面已经是英文了，
     * 分组标题却还是「2026年10月」，读起来像是漏翻。
     * 中文分支刻意保持原来的字面输出，不改动既有观感。
     */
    fun monthLabel(yearMonth: YearMonth, locale: Locale = Locale.getDefault()): String =
        if (locale.language == Locale.CHINESE.language) {
            String.format(Locale.US, "%d年%d月", yearMonth.year, yearMonth.monthValue)
        } else {
            String.format(
                locale,
                "%s %d",
                yearMonth.month.getDisplayName(TextStyle.FULL, locale),
                yearMonth.year,
            )
        }

    /**
     * 把 Material 日期选择器给出的 UTC 零点毫秒 + 时/分，合成为设备时区下的真实时间戳。
     *
     * 日期选择器的语义是「哪一天」，与具体时区无关，因此必须按 UTC 取出年月日，
     * 再按设备时区组装时刻；否则东八区会出现整体偏移一天的问题。
     */
    fun combineDateAndTime(
        dateUtcMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val date = Instant.ofEpochMilli(dateUtcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return date.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun toLocalDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    /**
     * UUID 短串（前 8 位），用于区分同名麻友（规格 3.4）。
     * 只做展示与辨识，不作为标识使用——关联始终用完整 ID。
     */
    fun shortId(id: String): String = id.take(SHORT_ID_LENGTH)

    private const val SHORT_ID_LENGTH = 8

    /**
     * 百分比取整（四舍五入），例如 1/10 → 10。
     * 用整数运算而非 Double，避免不必要的浮点中转。
     */
    fun percent(part: Int, total: Int): Int =
        if (total <= 0) 0 else (part * 100 + total / 2) / total
}

/** 座位名称：自己 / 上家 / 对家 / 下家。 */
@Composable
fun seatLabel(seat: Int): String = stringResource(
    when (seat) {
        SessionRules.SEAT_SELF -> R.string.seat_self
        1 -> R.string.seat_upper
        2 -> R.string.seat_opposite
        else -> R.string.seat_lower
    },
)

/** 时段名称：早场 / 下午场 / 晚场。 */
@Composable
fun timeSlotLabel(slot: TimeSlot): String = stringResource(
    when (slot) {
        TimeSlot.MORNING -> R.string.time_slot_morning
        TimeSlot.AFTERNOON -> R.string.time_slot_afternoon
        TimeSlot.EVENING -> R.string.time_slot_evening
    },
)

/**
 * 顶栏上的「今天」，例如中文下 `10.12 · 周一`、英文下 `10.12 · Mon`。
 *
 * 取设备时区的当天，与全应用的时间显示同源。星期名跟随设备语言：
 * 界面文案已经按语言分了两套，星期名也该跟着走——否则英文界面上会出现
 * `10.12 · 周一` 这样半中半英的一行。
 *
 * [today] 可显式传入，便于预览与测试钉死日期，与本文件其它格式化函数的
 * `zoneId` 参数是同一个用意：默认取环境，需要确定性时由调用方给死。
 */
@Composable
fun todayLabel(today: LocalDate = LocalDate.now()): String = stringResource(
    R.string.common_today_format,
    UiText.monthDay(today),
    today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
)

/**
 * 场次的时间区间，例如 `14:00 – 18:30`。
 *
 * 跨零点时把日期补上（`10.05 20:00 – 10.06 01:00`）——只写钟点的话，
 * 「20:00 – 01:00」会被读成一段倒退的时间。同一天里则只给钟点，保持紧凑。
 */
@Composable
fun sessionClockRange(startedAt: Long, playedAt: Long): String {
    val sameDay = UiText.toLocalDate(startedAt) == UiText.toLocalDate(playedAt)
    val start = if (sameDay) {
        UiText.timeOfDay(startedAt)
    } else {
        UiText.monthDay(startedAt) + " " + UiText.timeOfDay(startedAt)
    }
    val end = if (sameDay) {
        UiText.timeOfDay(playedAt)
    } else {
        UiText.monthDay(playedAt) + " " + UiText.timeOfDay(playedAt)
    }
    return "$start – $end"
}

/**
 * 时间区间加时长，例如 `14:00 – 18:30 · 4 小时`。
 *
 * 详情页与记一场的确认框共用：时段是按这一场的中点判定的，光看结束时间会显得自相矛盾，
 * 两处都把区间写出来，用户才看得出「为什么这一场算下午场」。
 */
@Composable
fun sessionTimeRange(startedAt: Long, playedAt: Long, durationMinutes: Int): String {
    val duration = stringResource(R.string.detail_duration_format, SessionDuration.formatHours(durationMinutes))
    return sessionClockRange(startedAt, playedAt) + " · " + duration
}
