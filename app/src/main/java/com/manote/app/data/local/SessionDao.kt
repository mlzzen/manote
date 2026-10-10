package com.manote.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
        "UPDATE session SET title = :title, playedAt = :playedAt, note = :note, " +
            "updatedAt = :updatedAt, revision = revision + 1 " +
            "WHERE id = :id AND revision = :expectedRevision",
    )
    suspend fun updateIfRevisionMatches(
        id: String,
        expectedRevision: Long,
        title: String,
        playedAt: Long,
        note: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
