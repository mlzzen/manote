package com.manote.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionResultDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(results: List<SessionResultEntity>)

    @Query("SELECT * FROM session_result WHERE sessionId = :sessionId ORDER BY seat ASC")
    suspend fun findBySession(sessionId: String): List<SessionResultEntity>

    @Query("SELECT * FROM session_result WHERE playerId = :playerId ORDER BY seat ASC")
    suspend fun findByPlayer(playerId: String): List<SessionResultEntity>

    /**
     * 跨场统计的唯一数据来源：该玩家各场净金额（分）。
     *
     * JOIN session 而不是直接扫 session_result：回收站里的场次（`deletedAt IS NOT NULL`）
     * 不该算进战绩，否则「删掉一场」之后统计还挂着它的金额。
     */
    @Query(
        "SELECT sr.netMinor FROM session_result sr " +
            "INNER JOIN session s ON s.id = sr.sessionId " +
            "WHERE sr.playerId = :playerId AND s.deletedAt IS NULL",
    )
    suspend fun findNetMinorsByPlayer(playerId: String): List<Long>

    /**
     * 订阅某位玩家的各场净金额，用于统计的实时刷新（规格 2.4：
     * 修改、删除、导入后本人和相关麻友统计通过数据库订阅立即刷新）。
     * 同样只统计在场场次。
     */
    @Query(
        "SELECT sr.netMinor FROM session_result sr " +
            "INNER JOIN session s ON s.id = sr.sessionId " +
            "WHERE sr.playerId = :playerId AND s.deletedAt IS NULL",
    )
    fun observeNetMinorsByPlayer(playerId: String): Flow<List<Long>>

    /**
     * 订阅全部玩家的场数与净输赢（规格 3.4：牌友列表每行显示场数、净输赢）。
     *
     * 一次聚合查出全部玩家，避免列表里对每位麻友各查一次造成 N+1。
     * 结果是纯派生数据，与 [observeNetMinorsByPlayer] 同源，不会出现两处对不上的情况；
     * 只统计在场场次，回收站里的不算。
     */
    @Query(
        "SELECT sr.playerId AS playerId, COUNT(*) AS sessionCount, " +
            "COALESCE(SUM(sr.netMinor), 0) AS netMinor " +
            "FROM session_result sr " +
            "INNER JOIN session s ON s.id = sr.sessionId " +
            "WHERE s.deletedAt IS NULL " +
            "GROUP BY sr.playerId",
    )
    fun observeAggregates(): Flow<List<PlayerAggregate>>

    @Query("SELECT COUNT(*) FROM session_result WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    /**
     * 一次性读出全部结果，供 JSON 备份导出。
     *
     * 与 [SessionDao.findAllOrdered] 一样排除回收站里的场次：否则备份里会出现
     * 指向不存在场次的结果行，连自己的校验都过不了。
     */
    @Query(
        "SELECT sr.* FROM session_result sr " +
            "INNER JOIN session s ON s.id = sr.sessionId " +
            "WHERE s.deletedAt IS NULL " +
            "ORDER BY sr.sessionId ASC, sr.seat ASC",
    )
    suspend fun findAllOrdered(): List<SessionResultEntity>

    /** 全量覆盖导入时清空结果表；必须先于 session 与 player 清空。 */
    @Query("DELETE FROM session_result")
    suspend fun deleteAll()

    @Query("DELETE FROM session_result WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int
}
