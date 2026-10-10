package com.manote.app.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.manote.app.R
import com.manote.app.domain.Money
import com.manote.app.domain.SessionRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 界面上的展示文本与格式化。
 *
 * 金额一律经 [Money] 从整数分换算，不在这里做任何浮点运算（规格 2.1）。
 * 时间按设备时区显示，但存储始终是 UTC epoch milliseconds（规格 2.1）。
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
    fun date(epochMillis: Long): String {
        val date = toLocalDate(epochMillis)
        return String.format(
            Locale.US,
            "%04d.%02d.%02d",
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }

    /** `2026.10.05 21:30`。 */
    fun dateTime(epochMillis: Long): String {
        val zone = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return String.format(
            Locale.US,
            "%04d.%02d.%02d %02d:%02d",
            zone.year,
            zone.monthValue,
            zone.dayOfMonth,
            zone.hour,
            zone.minute,
        )
    }

    /** `10.05`，用于紧凑列表。 */
    fun monthDay(epochMillis: Long): String {
        val date = toLocalDate(epochMillis)
        return String.format(Locale.US, "%02d.%02d", date.monthValue, date.dayOfMonth)
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

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

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
