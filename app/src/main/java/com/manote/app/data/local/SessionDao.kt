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

    /** 在场场次数（不含回收站）：用于 1,000 场上限与「本机现有 N 场」。 */
    @Query("SELECT COUNT(*) FROM session WHERE deletedAt IS NULL")
    suspend fun count(): Int

    /** 回收站里的场次数。 */
    @Query("SELECT COUNT(*) FROM session WHERE deletedAt IS NOT NULL")
    suspend fun trashCount(): Int

    /** 订阅回收站场次数，供「我的」页上的入口摘要实时刷新。 */
    @Query("SELECT COUNT(*) FROM session WHERE deletedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    /**
     * 仅在库里 revision 仍等于读取值时更新，并原子 +1。
     * 返回受影响行数：0 表示场次已被删除或版本已变，调用方据此拒绝覆盖（规格 2.3）。
     */
    @Query(
        "UPDATE session SET title = :title, playedAt = :playedAt, " +
            "durationMinutes = :durationMinutes, note = :note, " +
            "updatedAt = :updatedAt, revision = revision + 1 " +
            // 回收站里的场次不可编辑：加了这个条件，编辑已删除的场次会得到 0 行，
            // 调用方按 revision 冲突处理，而不是改到一份已经不在列表里的记录上。
            "WHERE id = :id AND revision = :expectedRevision AND deletedAt IS NULL",
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
     * 移入回收站（软删除）：只打上删除时间并 +1 revision，结果行原样保留。
     * 返回受影响行数；0 表示场次不存在或已在回收站里。
     */
    @Query(
        "UPDATE session SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun moveToTrash(id: String, deletedAt: Long, updatedAt: Long): Int

    /** 从回收站还原：清掉删除时间并 +1 revision。返回受影响行数，0 表示不在回收站里。 */
    @Query(
        "UPDATE session SET deletedAt = NULL, updatedAt = :updatedAt, revision = revision + 1 " +
            "WHERE id = :id AND deletedAt IS NOT NULL",
    )
    suspend fun restoreFromTrash(id: String, updatedAt: Long): Int

    /** 永久删除回收站里的某一场；只能删回收站里的（在场场次走 moveToTrash）。 */
    @Query("DELETE FROM session WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteFromTrashById(id: String): Int

    /** 回收站里全部场次的 ID，用于连同结果行一起永久删除。 */
    @Query("SELECT id FROM session WHERE deletedAt IS NOT NULL")
    suspend fun findTrashedIds(): List<String>

    /** 早于 [threshold] 移入回收站的场次 ID，用于到期自动清理。 */
    @Query("SELECT id FROM session WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun findTrashedIdsBefore(threshold: Long): List<String>

    /**
     * 订阅全部**在场**场次及其结果，按结束时间倒序、同时间按 Session ID 稳定排序（规格 2.4）。
     * 首页统计与场次列表共用这一条查询，保证两处数字来自同一份快照。
     * 回收站里的场次不在此列：它们立刻从统计与列表里消失。
     */
    @Transaction
    @Query("SELECT * FROM session WHERE deletedAt IS NULL ORDER BY playedAt DESC, id DESC")
    fun observeAllWithResults(): Flow<List<SessionWithResults>>

    /** 订阅回收站里的全部场次及其结果，按移入时间倒序（最近删的在前）。 */
    @Transaction
    @Query("SELECT * FROM session WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC")
    fun observeTrashWithResults(): Flow<List<SessionWithResults>>

    /** 订阅某个在场场次及其结果，供四人详情使用；已删除的场次发出空列表。 */
    @Transaction
    @Query("SELECT * FROM session WHERE id = :id AND deletedAt IS NULL")
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
            // 回收站里的场次不算参与过：从回收站回来之前，牌友战绩里不该有它
            "WHERE sr.playerId = :playerId AND s.deletedAt IS NULL " +
            "ORDER BY s.playedAt DESC, s.id DESC",
    )
    fun observeByPlayerWithResults(playerId: String): Flow<List<SessionWithResults>>

    /**
     * 一次性读出全部**在场**场次，供 JSON 备份导出；顺序与界面一致（结束时间倒序）。
     *
     * 回收站里的场次不导出：它是「刚刚删掉、还没定」的暂存区，
     * 把它写进备份会让「换台手机导入」后凭空多出几场已经删掉的记录。
     */
    @Query("SELECT * FROM session WHERE deletedAt IS NULL ORDER BY playedAt DESC, id DESC")
    suspend fun findAllOrdered(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<SessionEntity>)

    /** 全量覆盖导入时清空场次表；调用方必须先删 session_result（外键 CASCADE 之外仍按顺序写）。 */
    @Query("DELETE FROM session")
    suspend fun deleteAll()
}
