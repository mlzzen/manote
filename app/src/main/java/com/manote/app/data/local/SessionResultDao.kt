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

    /** 跨场统计的唯一数据来源：该玩家各场净金额（分）。 */
    @Query("SELECT netMinor FROM session_result WHERE playerId = :playerId")
    suspend fun findNetMinorsByPlayer(playerId: String): List<Long>

    /**
     * 订阅某位玩家的各场净金额，用于统计的实时刷新（规格 2.4：
     * 修改、删除、导入后本人和相关麻友统计通过数据库订阅立即刷新）。
     */
    @Query("SELECT netMinor FROM session_result WHERE playerId = :playerId")
    fun observeNetMinorsByPlayer(playerId: String): Flow<List<Long>>

    /**
     * 订阅全部玩家的场数与净输赢（规格 3.4：牌友列表每行显示场数、净输赢）。
     *
     * 一次聚合查出全部玩家，避免列表里对每位麻友各查一次造成 N+1。
     * 结果是纯派生数据，与 [observeNetMinorsByPlayer] 同源，不会出现两处对不上的情况。
     */
    @Query(
        "SELECT playerId AS playerId, COUNT(*) AS sessionCount, " +
            "COALESCE(SUM(netMinor), 0) AS netMinor " +
            "FROM session_result GROUP BY playerId",
    )
    fun observeAggregates(): Flow<List<PlayerAggregate>>

    @Query("SELECT COUNT(*) FROM session_result WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query("DELETE FROM session_result WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int
}
