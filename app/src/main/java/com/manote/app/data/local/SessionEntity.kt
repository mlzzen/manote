package com.manote.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.manote.app.domain.SessionDuration

/**
 * 一整场对局的最终结果（规格 5.1）。
 * playedAt 为结束时间，使用 UTC epoch milliseconds 存储。
 */
@Entity(
    tableName = "session",
    indices = [
        // 历史按 playedAt 倒序
        Index(value = ["playedAt"]),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val playedAt: Long,
    /**
     * 本场时长（分钟），用来倒推开始时间判定早场 / 下午场 / 晚场。
     *
     * 用户不填时按 [SessionDuration.DEFAULT_MINUTES] 入库——「没填」与「填了 4 小时」
     * 对时段判定毫无区别，与其到处判空，不如落库时就把默认值落实。
     */
    val durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 乐观并发控制：创建为 1，每次成功编辑 +1。 */
    val revision: Long,
    /**
     * 移入回收站的时间（UTC epoch ms）；`null` 表示这一场正常存在于场次列表里。
     *
     * 删除做成软删除：任何普通查询都带上 `deletedAt IS NULL`，于是这一场立刻
     * 从列表、统计与牌友战绩里消失，但结果行还在，可以随时还原（规格 3.3）。
     * 回收站里的行不带进 JSON 备份，也不会占用「1,000 场」的名额。
     */
    val deletedAt: Long? = null,
)
