package com.manote.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 统一表示本人和麻友的档案（规格 5.1）。
 * 关联一律使用 UUID 字符串主键，禁止按昵称关联。
 */
@Entity(
    tableName = "player",
    indices = [
        // 按 kind 统计/查找 SELF 与 FRIEND（如全库唯一 SELF 校验、麻友数量上限）
        Index(value = ["kind"]),
    ],
)
data class PlayerEntity(
    @PrimaryKey val id: String,
    val kind: PlayerKind,
    val nickname: String,
    val note: String,
    /** 归档时间；未归档为 null。SELF 永远为 null。 */
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
