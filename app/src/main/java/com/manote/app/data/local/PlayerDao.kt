package com.manote.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    /**
     * 同名麻友查询（规格 2.2：创建同名麻友时提示已有档案）。
     * 仅作提示，不阻止创建；统计仍按 ID 分开。
     */
    @Query("SELECT * FROM player WHERE kind = 'FRIEND' AND nickname = :nickname ORDER BY createdAt ASC, id ASC")
    suspend fun findFriendsByNickname(nickname: String): List<PlayerEntity>

    /** 订阅唯一 SELF，供首页与「我的」显示本人昵称。 */
    @Query("SELECT * FROM player WHERE kind = 'SELF' ORDER BY createdAt ASC, id ASC LIMIT 1")
    fun observeSelf(): Flow<PlayerEntity?>

    /** 订阅未归档麻友，新建场次只能从这些档案中选择（规格 2.2）。 */
    @Query("SELECT * FROM player WHERE kind = 'FRIEND' AND archivedAt IS NULL ORDER BY updatedAt DESC, id ASC")
    fun observeActiveFriends(): Flow<List<PlayerEntity>>

    /**
     * 订阅全部麻友（含已归档），供牌友页的「包含已归档」开关使用（规格 3.4）。
     *
     * 排序与 [observeActiveFriends] 一致：updatedAt 倒序、同时间按 ID 稳定排序。
     * 不混入 SELF。是否显示已归档由界面在内存里过滤，避免为开关切换反复重建查询。
     */
    @Query("SELECT * FROM player WHERE kind = 'FRIEND' ORDER BY updatedAt DESC, id ASC")
    fun observeAllFriends(): Flow<List<PlayerEntity>>

    /** 订阅单个档案，供麻友详情实时刷新（改名、归档后立即反映）。 */
    @Query("SELECT * FROM player WHERE id = :id")
    fun observeById(id: String): Flow<PlayerEntity?>

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
