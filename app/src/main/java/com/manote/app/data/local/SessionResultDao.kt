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

    @Query("SELECT COUNT(*) FROM session_result WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query("DELETE FROM session_result WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int
}
