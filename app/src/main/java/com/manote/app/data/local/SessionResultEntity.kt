package com.manote.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 一场中某个座位的最终净金额（规格 5.1）。
 *
 * 联合主键 (sessionId, seat)；同时唯一约束 (sessionId, playerId)，
 * 保证同一场不会重复选到同一人。
 * 对 Session 使用 CASCADE（删场次即清结果），对 Player 使用 RESTRICT（档案仍被引用时不可删除）。
 */
@Entity(
    tableName = "session_result",
    primaryKeys = ["sessionId", "seat"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "playerId"], unique = true),
        // 个人历史与跨场统计按 playerId 查询
        Index(value = ["playerId"]),
    ],
)
data class SessionResultEntity(
    val sessionId: String,
    /** 0=自己(SELF)、1=上家、2=对家、3=下家。 */
    val seat: Int,
    val playerId: String,
    /** 保存时的昵称快照；麻友改名后旧场不变。 */
    val nameSnapshot: String,
    /** 净金额，单位分；正数赢、负数输。四人合计必须为 0。 */
    val netMinor: Long,
)
