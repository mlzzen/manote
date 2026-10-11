package com.manote.app.domain

/**
 * 单场极值：赢得最多的那一场、输得最多的那一场。
 *
 * 全部由已保存场次实时算出，不另存任何累计值（规格 2.4）。
 *
 * 两边都可能是 null，而且这**不是错误**：「从没赢过」与「单场赢最多 ¥0.00」
 * 是两回事，后者根本不成立——净额 0 的一场既不是赢也不是输。
 * 界面据此显示占位符，而不是摆一个假的 0 出来。
 */
data class SessionExtremes(
    /** 净额最高且为正的那一场；从没赢过时为 null。 */
    val bestWin: DatedNet?,
    /** 净额最低且为负的那一场；从没输过时为 null。 */
    val worstLoss: DatedNet?,
) {
    /** 是否至少有一边的记录可展示。两边都是 null 时整块没有可说的内容。 */
    val hasAny: Boolean get() = bestWin != null || worstLoss != null

    companion object {

        val EMPTY = SessionExtremes(bestWin = null, worstLoss = null)

        /**
         * 由各场的（结束时间，净额）算出两个极值。
         *
         * **持平不参与**：净额 0 的一场既不是「赢最多」也不是「输最多」，
         * 全程持平、或一场都没打过时，两边都是 null。
         *
         * 同额时命中**最早**的一场：先按结束时间升序排一遍，`maxByOrNull` /
         * `minByOrNull` 返回遇到的第一个极值，与 [Streaks] 里「最佳 / 最差单日」的取舍一致，
         * 因此同一份输入永远得到同一个日期，界面不会在两次刷新之间换一场。
         *
         * 不需要时区：这里只比金额大小，不分组到「哪一天」；
         * 日期是界面拿到 [DatedNet.playedAt] 之后按设备时区自己格式化的。
         */
        fun of(items: List<DatedNet>): SessionExtremes {
            val ordered = items.sortedBy { it.playedAt }
            return SessionExtremes(
                bestWin = ordered.maxByOrNull { it.netMinor }?.takeIf { it.netMinor > 0L },
                worstLoss = ordered.minByOrNull { it.netMinor }?.takeIf { it.netMinor < 0L },
            )
        }
    }
}
