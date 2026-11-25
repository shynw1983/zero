package com.zero.todo.data.repository

import androidx.room.withTransaction
import com.zero.todo.core.common.FractionalIndex
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val database: ZeroDatabase,
    private val taskDao: TaskDao,
    private val folderDao: FolderDao
) : TaskRepository {

    override fun observeInboxTasks(): Flow<List<TaskItem>> =
        taskDao.observeInboxTasks().map { list -> list.map { it.toDomain() } }

    override fun observeTasksByFolder(
        folderId: String,
        includeSubfolders: Boolean
    ): Flow<List<TaskItem>> {
        val source = if (includeSubfolders) {
            taskDao.observeTasksInFolderSubtree(folderId)
        } else {
            taskDao.observeTasksByFolder(folderId)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeSubtasks(parentTaskId: String): Flow<List<TaskItem>> =
        taskDao.observeSubtasks(parentTaskId).map { list -> list.map { it.toDomain() } }

    override fun observeDescendantSubtasks(rootTaskId: String): Flow<List<TaskItem>> =
        taskDao.observeDescendantSubtasks(rootTaskId).map { list -> list.map { it.toDomain() } }

    override fun observeTasksDueBetween(startMillis: Long, endMillis: Long): Flow<List<TaskItem>> =
        taskDao.observeTasksDueBetween(startMillis, endMillis).map { list -> list.map { it.toDomain() } }

    override fun observeAllActiveTopLevelTasks(): Flow<List<TaskItem>> =
        taskDao.observeAllActiveTopLevelTasks().map { list -> list.map { it.toDomain() } }

    override fun observeTaskWithTags(taskId: String): Flow<TaskItem?> =
        taskDao.observeTaskWithTags(taskId).map { it?.toDomain() }

    override suspend fun getTaskById(taskId: String): TaskItem? =
        taskDao.getTaskWithTags(taskId)?.toDomain()

    override suspend fun createTask(
        title: String,
        folderId: String?,
        parentTaskId: String?,
        description: String,
        priority: TaskPriority,
        dueDate: Long?,
        isAllDay: Boolean,
        reminderAt: Long?,
        recurrenceRule: String?
    ): TaskItem {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "Task title must not be blank" }

        return database.withTransaction {
            if (folderId != null) {
                requireNotNull(folderDao.getFolderById(folderId)) {
                    "Target folder does not exist or is deleted: $folderId"
                }
            }
            if (parentTaskId != null) {
                requireNotNull(taskDao.getTaskById(parentTaskId)) {
                    "Parent task does not exist or is deleted: $parentTaskId"
                }
            }
            val now = System.currentTimeMillis()
            val maxSortOrder = taskDao.getMaxSortOrder(folderId, parentTaskId)
            val sortOrder = if (maxSortOrder == null) {
                FractionalIndex.first()
            } else {
                FractionalIndex.after(maxSortOrder)
            }
            val entity = TaskEntity(
                id = UUID.randomUUID().toString(),
                folderId = folderId,
                parentTaskId = parentTaskId,
                title = trimmedTitle,
                description = description,
                priority = priority.value,
                isCompleted = false,
                completedAt = null,
                dueDate = dueDate,
                isAllDay = isAllDay,
                reminderAt = reminderAt,
                recurrenceRule = recurrenceRule,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
                isDeleted = false
            )
            taskDao.upsertTask(entity)
            entity.toDomain()
        }
    }

    override suspend fun updateTask(task: TaskItem) {
        val trimmedTitle = task.title.trim()
        require(trimmedTitle.isNotEmpty()) { "Task title must not be blank" }
        taskDao.updateTask(
            task.copy(
                title = trimmedTitle,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.updateCompletion(
            taskId = taskId,
            isCompleted = isCompleted,
            completedAt = if (isCompleted) now else null,
            updatedAt = now
        )
    }

    override suspend fun reorderTask(
        taskId: String,
        targetFolderId: String?,
        targetParentTaskId: String?,
        beforeTaskId: String?,
        afterTaskId: String?
    ) {
        database.withTransaction {
            requireNotNull(taskDao.getTaskById(taskId)) { "Task not found: $taskId" }
            if (targetFolderId != null) {
                requireNotNull(folderDao.getFolderById(targetFolderId)) {
                    "Target folder does not exist or is deleted: $targetFolderId"
                }
            }
            val siblings = taskDao.getSiblingTasks(targetFolderId, targetParentTaskId)
                .map { it.id to it.sortOrder }
            val update = FractionalIndex.computeMoveBetween(
                orderedSiblings = siblings,
                movingId = taskId,
                beforeId = beforeTaskId,
                afterId = afterTaskId
            )
            applyTaskOrderUpdate(
                taskId = taskId,
                targetFolderId = targetFolderId,
                update = update
            )
        }
    }

    override suspend fun moveTaskToIndex(
        taskId: String,
        targetFolderId: String?,
        targetParentTaskId: String?,
        targetIndex: Int
    ) {
        database.withTransaction {
            requireNotNull(taskDao.getTaskById(taskId)) { "Task not found: $taskId" }
            if (targetFolderId != null) {
                requireNotNull(folderDao.getFolderById(targetFolderId)) {
                    "Target folder does not exist or is deleted: $targetFolderId"
                }
            }
            val siblings = taskDao.getSiblingTasks(targetFolderId, targetParentTaskId)
                .map { it.id to it.sortOrder }
            val update = FractionalIndex.computeMoveByIndex(
                orderedSiblings = siblings,
                movingId = taskId,
                targetIndex = targetIndex
            )
            applyTaskOrderUpdate(
                taskId = taskId,
                targetFolderId = targetFolderId,
                update = update
            )
        }
    }

    override suspend fun moveTaskToFolder(taskId: String, targetFolderId: String?) {
        database.withTransaction {
            val task = requireNotNull(taskDao.getTaskById(taskId)) { "Task not found: $taskId" }
            if (targetFolderId != null) {
                requireNotNull(folderDao.getFolderById(targetFolderId)) {
                    "Target folder does not exist or is deleted: $targetFolderId"
                }
            }
            val maxOrder = taskDao.getMaxSortOrder(targetFolderId, task.parentTaskId)
            val newOrder = if (maxOrder == null) FractionalIndex.first() else FractionalIndex.after(maxOrder)
            taskDao.moveTaskToFolder(
                taskId = taskId,
                folderId = targetFolderId,
                sortOrder = newOrder,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    override suspend fun promoteSubtaskToTopLevel(subtaskId: String): TaskItem? {
        return database.withTransaction {
            val subtask = taskDao.getTaskById(subtaskId) ?: return@withTransaction null
            if (subtask.parentTaskId == null) return@withTransaction subtask.toDomain()

            // Walk up to root task to inherit its folder placement (or Inbox if null).
            var cursor = subtask
            var inheritedFolderId: String? = subtask.folderId
            val visited = hashSetOf(subtask.id)
            while (cursor.parentTaskId != null) {
                val parent = taskDao.getTaskById(cursor.parentTaskId!!) ?: break
                if (!visited.add(parent.id)) break
                inheritedFolderId = parent.folderId
                cursor = parent
            }

            val maxOrder = taskDao.getMaxSortOrder(inheritedFolderId, parentTaskId = null)
            val newOrder = if (maxOrder == null) FractionalIndex.first() else FractionalIndex.after(maxOrder)
            val now = System.currentTimeMillis()
            taskDao.promoteSubtaskToTopLevel(
                taskId = subtaskId,
                targetFolderId = inheritedFolderId,
                sortOrder = newOrder,
                updatedAt = now
            )
            taskDao.getTaskById(subtaskId)?.toDomain()
        }
    }

    override suspend fun softDeleteTask(taskId: String) {
        database.withTransaction {
            val ids = taskDao.getDescendantTaskIds(taskId)
            if (ids.isEmpty()) return@withTransaction
            taskDao.softDeleteTasks(ids, System.currentTimeMillis())
        }
    }

    override suspend fun restoreTask(taskId: String) {
        database.withTransaction {
            val ids = taskDao.getDescendantTaskIdsIncludingDeleted(taskId)
            if (ids.isEmpty()) return@withTransaction
            taskDao.restoreTasks(ids, System.currentTimeMillis())
        }
    }

    private suspend fun applyTaskOrderUpdate(
        taskId: String,
        targetFolderId: String?,
        update: FractionalIndex.OrderUpdate
    ) {
        val now = System.currentTimeMillis()
        when (update) {
            is FractionalIndex.OrderUpdate.Single -> {
                taskDao.moveTaskToFolder(
                    taskId = taskId,
                    folderId = targetFolderId,
                    sortOrder = update.sortOrder,
                    updatedAt = now
                )
            }
            is FractionalIndex.OrderUpdate.Rebalanced -> {
                for ((id, newSortOrder) in update.sortOrders) {
                    if (id == taskId) {
                        taskDao.moveTaskToFolder(
                            taskId = id,
                            folderId = targetFolderId,
                            sortOrder = newSortOrder,
                            updatedAt = now
                        )
                    } else {
                        taskDao.updateSortOrder(
                            taskId = id,
                            sortOrder = newSortOrder,
                            updatedAt = now
                        )
                    }
                }
            }
        }
    }
}
