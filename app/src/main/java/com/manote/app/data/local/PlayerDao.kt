package com.manote.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayerDao {

    /** 取唯一的 SELF；ORDER BY 只为结果稳定，正常库里最多一行。 */
    @Query("SELECT * FROM player WHERE kind = :kind ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun findFirstByKind(kind: PlayerKind): PlayerEntity?

    @Query("SELECT * FROM player WHERE kind = :kind ORDER BY createdAt ASC, id ASC")
    suspend fun findAllByKind(kind: PlayerKind): List<PlayerEntity>

    @Query("SELECT COUNT(*) FROM player WHERE kind = :kind")
    suspend fun countByKind(kind: PlayerKind): Int

    @Query("SELECT * FROM player WHERE id = :id")
    suspend fun findById(id: String): PlayerEntity?

    @Query("SELECT * FROM player WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(player: PlayerEntity)

    /** 只允许改 FRIEND，SELF 的昵称单独走 [updateSelfNickname]。 */
    @Query(
        "UPDATE player SET nickname = :nickname, note = :note, updatedAt = :updatedAt " +
            "WHERE id = :id AND kind = 'FRIEND'",
    )
    suspend fun updateFriendProfile(
        id: String,
        nickname: String,
        note: String,
        updatedAt: Long,
    ): Int

    /** 本人只能改昵称，不能改备注、不能归档。 */
    @Query(
        "UPDATE player SET nickname = :nickname, updatedAt = :updatedAt " +
            "WHERE id = :id AND kind = 'SELF'",
    )
    suspend fun updateSelfNickname(id: String, nickname: String, updatedAt: Long): Int

    /** 归档/取消归档只作用于 FRIEND 且永不触碰 SELF。 */
    @Query(
        "UPDATE player SET archivedAt = :archivedAt, updatedAt = :updatedAt " +
            "WHERE id = :id AND kind = 'FRIEND'",
    )
    suspend fun setFriendArchivedAt(
        id: String,
        archivedAt: Long?,
        updatedAt: Long,
    ): Int
}
