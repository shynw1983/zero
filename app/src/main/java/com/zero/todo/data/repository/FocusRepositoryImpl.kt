package com.zero.todo.data.repository

import com.zero.todo.data.local.dao.FocusSessionDao
import com.zero.todo.data.local.entity.FocusSessionEntity
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRepositoryImpl @Inject constructor(
    private val focusSessionDao: FocusSessionDao
) : FocusRepository {

    override fun observeAllSessions(): Flow<List<FocusSession>> =
        focusSessionDao.observeAllSessions().map { list -> list.map { it.toDomain() } }

    override fun observeSessionsForTask(taskId: String): Flow<List<FocusSession>> =
        focusSessionDao.observeSessionsForTask(taskId).map { list -> list.map { it.toDomain() } }

    override fun observeTotalFocusSecondsBetween(startMillis: Long, endMillis: Long): Flow<Int> =
        focusSessionDao.observeTotalFocusSecondsBetween(startMillis, endMillis)

    override suspend fun recordSession(
        taskId: String?,
        durationSeconds: Int,
        mode: FocusMode,
        completedAt: Long
    ): FocusSession {
        require(durationSeconds > 0) { "Focus duration must be positive, was $durationSeconds" }
        val entity = FocusSessionEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            durationSeconds = durationSeconds,
            mode = mode.storageValue,
            completedAt = completedAt,
            createdAt = completedAt,
            updatedAt = completedAt,
            isDeleted = false
        )
        focusSessionDao.insertSession(entity)
        return entity.toDomain()
    }

    override suspend fun softDeleteSession(sessionId: String) {
        focusSessionDao.softDeleteSession(sessionId, System.currentTimeMillis())
    }
}
