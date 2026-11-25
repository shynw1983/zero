package com.zero.todo.data.repository

import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusRepository {
    fun observeAllSessions(): Flow<List<FocusSession>>
    fun observeSessionsForTask(taskId: String): Flow<List<FocusSession>>
    fun observeTotalFocusSecondsBetween(startMillis: Long, endMillis: Long): Flow<Int>

    suspend fun recordSession(
        taskId: String?,
        durationSeconds: Int,
        mode: FocusMode,
        completedAt: Long = System.currentTimeMillis()
    ): FocusSession

    suspend fun softDeleteSession(sessionId: String)
}
