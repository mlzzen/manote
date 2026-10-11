package com.manote.app.domain

/**
 * 一场对局的时长。
 *
 * 表格里只存**结束时间**与时长——打完这一刻是确定的，用户不必回忆几点开局；
 * 这一场的「中点」由两者算出，早场 / 下午场 / 晚场按中点落在哪一段判定。
 *
 * 时长本身不参与任何金额与统计计算，它只影响时段归类。
 */
object SessionDuration {

    /** 1 小时 = 60 分钟。 */
    const val MINUTES_PER_HOUR = 60

    /**
     * 输入粒度：0.1 小时 = 6 分钟。
     *
     * 时长一律是它的整数倍——解析入口只接受一位小数，反写回输入框时才能精确还原，
     * 不会出现「填 4.2、存 252 分钟、再打开显示 4.2」之外的四舍五入痕迹。
     */
    const val MINUTES_PER_TENTH_HOUR = 6

    /** 用户没填时按 4 小时算。 */
    const val DEFAULT_MINUTES = 4 * MINUTES_PER_HOUR

    /** 下限 0.5 小时：再短不像一场麻将，多半是填错了。 */
    const val MIN_MINUTES = 30

    /** 上限 24 小时：超过一天的「一局」不可能存在。 */
    const val MAX_MINUTES = 24 * MINUTES_PER_HOUR

    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * 时长是否合法：落在 0.5～24 小时之间，且是输入粒度（6 分钟）的整数倍。
     *
     * 粒度这一条同时守住了 [formatHours] 的精确性——接受一个 31 分钟的值，
     * 反写回输入框就只能显示成 0.5 小时，用户下次打开会看到一个自己没填过的数字。
     */
    fun isValid(minutes: Int): Boolean =
        minutes in MIN_MINUTES..MAX_MINUTES && minutes % MINUTES_PER_TENTH_HOUR == 0

    /**
     * 由结束时间与时长倒推开始时间。
     *
     * 只做减法，不碰时区：存储全程是 UTC epoch 毫秒，
     * 「属于哪个时段」这一步才在显示时按设备时区换算。
     */
    fun startAt(playedAt: Long, durationMinutes: Int): Long =
        playedAt - durationMinutes * MILLIS_PER_MINUTE

    /**
     * 这一场的中点。时段是按它判定的（见 [com.manote.app.domain.TimeSlot]）。
     *
     * 用中点而不是开始时间，是因为一场横跨 17:00 的牌局（17:30 打到 21:30）
     * 既不是纯粹的下午场也不是纯粹的晚场，按开始时间会被判成下午场，
     * 与「打了大半晚」的直觉相反。中点落在哪一段，就是「这一场主要发生在哪一段」。
     *
     * 时长永远是 6 分钟的整数倍，1 分钟 = 60 000 毫秒，因此折半始终是整数，不会有取整误差。
     */
    fun midpointAt(playedAt: Long, durationMinutes: Int): Long =
        playedAt - durationMinutes * MILLIS_PER_MINUTE / 2

    /**
     * 分钟 → 「小时」文本：240 → `4`、270 → `4.5`、30 → `0.5`。
     *
     * 不四舍五入。合法值一定是 6 分钟的整数倍，余数只可能是 0 或 6 的倍数，
     * 因此这里写出的数字与原值严格相等。
     */
    fun formatHours(minutes: Int): String {
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        if (rest == 0) return hours.toString()
        return "$hours.${rest / MINUTES_PER_TENTH_HOUR}"
    }
}
