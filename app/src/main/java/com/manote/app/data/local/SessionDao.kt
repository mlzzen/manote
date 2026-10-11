package com.manote.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun findById(id: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM session")
    suspend fun count(): Int

    /**
     * 仅在库里 revision 仍等于读取值时更新，并原子 +1。
     * 返回受影响行数：0 表示场次已被删除或版本已变，调用方据此拒绝覆盖（规格 2.3）。
     */
    @Query(
        "UPDATE session SET title = :title, playedAt = :playedAt, " +
            "durationMinutes = :durationMinutes, note = :note, " +
            "updatedAt = :updatedAt, revision = revision + 1 " +
            "WHERE id = :id AND revision = :expectedRevision",
    )
    suspend fun updateIfRevisionMatches(
        id: String,
        expectedRevision: Long,
        title: String,
        playedAt: Long,
        durationMinutes: Int,
        note: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * 订阅全部场次及其四条结果，按结束时间倒序、同时间按 Session ID 稳定排序（规格 2.4）。
     * 首页统计与场次列表共用这一条查询，保证两处数字来自同一份快照。
     */
    @Transaction
    @Query("SELECT * FROM session ORDER BY playedAt DESC, id DESC")
    fun observeAllWithResults(): Flow<List<SessionWithResults>>

    /** 订阅某个场次及其结果，供四人详情使用。 */
    @Transaction
    @Query("SELECT * FROM session WHERE id = :id")
    fun observeByIdWithResults(id: String): Flow<List<SessionWithResults>>

    /**
     * 订阅某位玩家参与过的全部场次及其四人结果，按结束时间倒序（规格 3.4 逐场统计）。
     *
     * 连同该场全部四条结果一起取出，界面据此同时显示「麻友本场结果」与「本人本场结果」，
     * 不需要为每一场再查一次。
     */
    @Transaction
    @Query(
        "SELECT s.* FROM session s " +
            "INNER JOIN session_result sr ON sr.sessionId = s.id " +
            "WHERE sr.playerId = :playerId " +
            "ORDER BY s.playedAt DESC, s.id DESC",
    )
    fun observeByPlayerWithResults(playerId: String): Flow<List<SessionWithResults>>

    /** 一次性读出全部场次，供 JSON 备份导出；顺序与界面一致（结束时间倒序）。 */
    @Query("SELECT * FROM session ORDER BY playedAt DESC, id DESC")
    suspend fun findAllOrdered(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<SessionEntity>)

    /** 全量覆盖导入时清空场次表；调用方必须先删 session_result（外键 CASCADE 之外仍按顺序写）。 */
    @Query("DELETE FROM session")
    suspend fun deleteAll()
}
