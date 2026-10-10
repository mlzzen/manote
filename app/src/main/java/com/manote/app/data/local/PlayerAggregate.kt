package com.manote.app.data.local

import androidx.room.ColumnInfo

/**
 * 一位玩家在 `session_result` 上的聚合结果（规格 2.4）。
 *
 * `COALESCE` 保证没有任何结果时也返回 0 而不是 null，调用方不必再做可空判断。
 * 只用于列表展示；统计仍以 [com.manote.app.domain.PlayerStatistics] 的计算口径为准。
 */
data class PlayerAggregate(
    @ColumnInfo(name = "playerId") val playerId: String,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
    @ColumnInfo(name = "netMinor") val netMinor: Long,
)
