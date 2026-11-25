package com.zero.todo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zero.todo.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE isDeleted = 0 ORDER BY completedAt DESC")
    fun observeAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId AND isDeleted = 0 ORDER BY completedAt DESC")
    fun observeSessionsForTask(taskId: String): Flow<List<FocusSessionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions
        WHERE isDeleted = 0 AND completedAt >= :startMillis AND completedAt < :endMillis
        """
    )
    fun observeTotalFocusSecondsBetween(startMillis: Long, endMillis: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Query("UPDATE focus_sessions SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteSession(id: String, updatedAt: Long)

    @Query("SELECT * FROM focus_sessions ORDER BY completedAt ASC")
    suspend fun getAllSessionsSnapshot(): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSessionEntity>)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAllSessions()
}
