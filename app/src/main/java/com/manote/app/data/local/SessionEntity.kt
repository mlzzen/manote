package com.manote.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一整场对局的最终结果（规格 5.1）。
 * playedAt 为打完时间，使用 UTC epoch milliseconds 存储。
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
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 乐观并发控制：创建为 1，每次成功编辑 +1。 */
    val revision: Long,
)
