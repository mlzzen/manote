package com.manote.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 一场对局连同它的座位结果（规格 5.1：一场记录 1～4 个位置，必含 seat 0）。
 *
 * 用于首页「最近场次」、场次列表与四人详情，一次查询取齐，避免在 UI 层做 N+1 查询。
 */
data class SessionWithResults(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val results: List<SessionResultEntity>,
)
