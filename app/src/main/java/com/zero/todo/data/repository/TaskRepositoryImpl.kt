package com.zero.todo.data.repository

import androidx.room.withTransaction
import com.zero.todo.core.common.FractionalIndex
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPlacement
import com.zero.todo.domain.model.TaskPlacementChange
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    override fun observeTodayAndOverdueTasks(
        todayStartMillis: Long,
        tomorrowStartMillis: Long
    ): Flow<List<TaskItem>> =
        taskDao.observeTodayAndOverdueTasks(todayStartMillis, tomorrowStartMillis)
            .map { list -> list.map { it.toDomain() } }

    override fun observeAllActiveTopLevelTasks(): Flow<List<TaskItem>> =
        taskDao.observeAllActiveTopLevelTasks().map { list -> list.map { it.toDomain() } }

    override fun observeAllActiveTasks(): Flow<List<TaskItem>> =
        taskDao.observeAllActiveTasks().map { list -> list.map { it.toDomain() } }

    override fun observeDeletedTasks(): Flow<List<TaskItem>> =
        taskDao.observeDeletedTasks().map { list -> list.map { it.toDomain() } }

    override fun observeTaskWithTags(taskId: String): Flow<TaskItem?> =
        taskDao.observeTaskWithTags(taskId).map { it?.toDomain() }

    override suspend fun getTaskById(taskId: String): TaskItem? =
        taskDao.getTaskWithTags(taskId)?.toDomain()

    override suspend fun getDescendantSubtasksSnapshot(rootTaskId: String): List<TaskItem> =
        taskDao.getDescendantSubtasksSnapshot(rootTaskId).map { it.toDomain() }

    override suspend fun getUpcomingReminderTasks(nowMillis: Long): List<TaskItem> =
        taskDao.getUpcomingReminderTasks(nowMillis).map { it.toDomain() }

    override suspend fun createTask(
        title: String,
        folderId: String?,
        parentTaskId: String?,
        description: String,
        priority: TaskPriority,
        dueDate: Long?,
        isAllDay: Boolean,
        reminderAt: Long?,
        recurrenceRule: String?,
        firstStep: String?,
        estimatedMinutes: Int?
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
                firstStep = firstStep,
                estimatedMinutes = estimatedMinutes,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
                isDeleted = false
            )
            taskDao.upsertTask(entity)
            entity.toDomain()
        }
    }

    override suspend fun updateTask(taskId: String, edit: (TaskItem) -> TaskItem): TaskItem? =
        database.withTransaction {
            val current = taskDao.getTaskById(taskId)?.toDomain() ?: return@withTransaction null
            val edited = edit(current)
            val trimmedTitle = edited.title.trim()
            require(trimmedTitle.isNotEmpty()) { "Task title must not be blank" }
            val updated = edited.copy(
                title = trimmedTitle,
                updatedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(updated.toEntity())
            updated
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

    override suspend fun completeTask(taskId: String, today: LocalDate): List<TaskItem>? =
        database.withTransaction {
            val task = taskDao.getTaskWithTags(taskId)?.toDomain() ?: return@withTransaction null
            if (task.isCompleted) return@withTransaction null
            setTaskCompleted(taskId, true)
            val rule = RecurrenceRule.parse(task.recurrenceRule) ?: return@withTransaction emptyList()
            val anchorMillis = task.dueDate ?: ZeroDateFormatter.startOfDayMillis(today)
            val nextDueMillis = rule.nextOccurrenceMillis(anchorMillis)

            val zone = ZoneId.systemDefault()
            fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(dateOf(anchorMillis), dateOf(nextDueMillis))
            fun shiftDate(millis: Long?): Long? = millis?.let {
                Instant.ofEpochMilli(it).atZone(zone).plusDays(days).toInstant().toEpochMilli()
            }

            val sources = listOf(task) + getDescendantSubtasksSnapshot(taskId)
            val clonedIds = mutableMapOf<String, String>()
            val tagDao = database.tagDao()
            sources.map { source ->
                val root = source.id == taskId
                val due = if (root) nextDueMillis else shiftDate(source.dueDate)
                val reminder = when {
                    source.reminderAt == null -> null
                    source.dueDate != null && due != null -> due - (source.dueDate - source.reminderAt)
                    else -> shiftDate(source.reminderAt)
                }
                val copy = createTask(
                    title = source.title,
                    folderId = source.folderId,
                    parentTaskId = if (root) source.parentTaskId else clonedIds.getValue(source.parentTaskId!!),
                    description = source.description,
                    priority = source.priority,
                    dueDate = due,
                    isAllDay = source.isAllDay,
                    reminderAt = reminder,
                    recurrenceRule = source.recurrenceRule,
                    firstStep = source.firstStep,
                    estimatedMinutes = source.estimatedMinutes
                )
                source.tags.forEach { tag -> tagDao.attachTagToTask(TaskTagCrossRef(copy.id, tag.id)) }
                clonedIds[source.id] = copy.id
                copy
            }
        }

    override suspend fun reorderTask(
        taskId: String,
        targetFolderId: String?,
        targetParentTaskId: String?,
        beforeTaskId: String?,
        afterTaskId: String?
    ) {
        database.withTransaction {
            val task = requireNotNull(taskDao.getTaskById(taskId)) { "Task not found: $taskId" }
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
                targetParentTaskId = task.parentTaskId,
                update = update
            )
        }
    }

    override suspend fun placeTaskInList(
        taskId: String,
        parentTaskId: String?,
        beforeTaskId: String?,
        rootFolderId: String?
    ): TaskPlacementChange? = database.withTransaction {
        val task = taskDao.getTaskById(taskId) ?: return@withTransaction null
        val folder = if (parentTaskId == null) rootFolderId else task.folderId
        if (folder != null && folderDao.getFolderById(folder) == null) return@withTransaction null
        if (!canAssignParent(taskId, parentTaskId)) return@withTransaction null

        val siblings = taskDao.getSiblingTasks(folder, parentTaskId)
        if (beforeTaskId != null && (beforeTaskId == taskId || siblings.none { it.id == beforeTaskId })) {
            return@withTransaction null
        }
        val currentIndex = siblings.indexOfFirst { it.id == taskId }
        if (currentIndex >= 0 && siblings.getOrNull(currentIndex + 1)?.id == beforeTaskId) return@withTransaction null

        val update = FractionalIndex.computeMoveBetween(siblings.map { it.id to it.sortOrder }, taskId, null, beforeTaskId)
        val newOrder = applyTaskOrderUpdate(taskId, folder, parentTaskId, update)
        TaskPlacementChange(taskId, task.placement(), TaskPlacement(folder, parentTaskId, newOrder))
    }

    override suspend fun undoTaskPlacement(change: TaskPlacementChange): Boolean = database.withTransaction {
        val task = taskDao.getTaskById(change.taskId) ?: return@withTransaction false
        if (task.placement() != change.after) return@withTransaction false
        val previous = change.before
        if (previous.folderId != null && folderDao.getFolderById(previous.folderId) == null) return@withTransaction false
        if (!canAssignParent(task.id, previous.parentTaskId)) return@withTransaction false
        taskDao.updateTaskPlacement(task.id, previous.folderId, previous.parentTaskId, previous.sortOrder, System.currentTimeMillis())
        true
    }

    private fun TaskEntity.placement() = TaskPlacement(folderId, parentTaskId, sortOrder)

    private suspend fun canAssignParent(taskId: String, parentTaskId: String?): Boolean {
        val visited = hashSetOf(taskId)
        var ancestorId = parentTaskId
        while (ancestorId != null) {
            if (!visited.add(ancestorId)) return false
            val ancestor = taskDao.getTaskById(ancestorId) ?: return false
            ancestorId = ancestor.parentTaskId
        }
        return true
    }

    override suspend fun moveTaskToFolder(taskId: String, targetFolderId: String?) {
        database.withTransaction {
            val task = requireNotNull(taskDao.getTaskById(taskId)) { "Task not found: $taskId" }
            if (targetFolderId != null) {
                requireNotNull(folderDao.getFolderById(targetFolderId)) {
                    "Target folder does not exist or is deleted: $targetFolderId"
                }
            }
            val newOrder = if (task.parentTaskId == null) {
                nextSortOrder(targetFolderId, parentTaskId = null)
            } else {
                task.sortOrder
            }
            taskDao.moveTaskToFolder(
                taskId = taskId,
                folderId = targetFolderId,
                sortOrder = newOrder,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    override suspend fun moveTaskUnderParent(taskId: String, parentTaskId: String): Boolean =
        database.withTransaction {
            val task = taskDao.getTaskById(taskId) ?: return@withTransaction false
            if (!canAssignParent(taskId, parentTaskId)) return@withTransaction false
            if (task.parentTaskId != parentTaskId) {
                taskDao.updateTaskPlacement(
                    taskId, task.folderId, parentTaskId, nextSortOrder(task.folderId, parentTaskId), System.currentTimeMillis()
                )
            }
            true
        }

    override suspend fun rescheduleTasksToDate(taskIds: List<String>, targetDate: LocalDate) {
        if (taskIds.isEmpty()) return
        val zoneId = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        database.withTransaction {
            for (taskId in taskIds) {
                val entity = taskDao.getTaskById(taskId) ?: continue
                val newDueMillis = if (entity.isAllDay || entity.dueDate == null) {
                    ZeroDateFormatter.startOfDayMillis(targetDate, zoneId)
                } else {
                    val existingTime = Instant.ofEpochMilli(entity.dueDate).atZone(zoneId).toLocalTime()
                    LocalDateTime.of(targetDate, existingTime).atZone(zoneId).toInstant().toEpochMilli()
                }
                val newReminder = if (entity.reminderAt != null && entity.dueDate != null) {
                    val offset = entity.dueDate - entity.reminderAt
                    newDueMillis - offset
                } else {
                    entity.reminderAt
                }
                taskDao.updateTask(
                    entity.copy(
                        dueDate = newDueMillis,
                        reminderAt = newReminder,
                        updatedAt = now
                    )
                )
            }
        }
    }

    override suspend fun promoteSubtaskToTopLevel(subtaskId: String): TaskItem? {
        return database.withTransaction {
            val subtask = taskDao.getTaskById(subtaskId) ?: return@withTransaction null
            if (subtask.parentTaskId == null) return@withTransaction subtask.toDomain()

            var cursor = subtask
            var inheritedFolderId: String? = subtask.folderId
            val visited = hashSetOf(subtask.id)
            while (cursor.parentTaskId != null) {
                val parent = taskDao.getTaskById(cursor.parentTaskId!!) ?: break
                if (!visited.add(parent.id)) break
                inheritedFolderId = parent.folderId
                cursor = parent
            }

            val targetFolder = subtask.folderId ?: inheritedFolderId
            val newOrder = nextSortOrder(targetFolder, parentTaskId = null)
            val now = System.currentTimeMillis()
            taskDao.promoteSubtaskToTopLevel(
                taskId = subtaskId,
                targetFolderId = targetFolder,
                sortOrder = newOrder,
                updatedAt = now
            )
            taskDao.getTaskById(subtaskId)?.toDomain()
        }
    }

    override suspend fun softDeleteTask(taskId: String): List<String> =
        database.withTransaction {
            val ids = taskDao.getDescendantTaskIds(taskId)
            if (ids.isNotEmpty()) taskDao.softDeleteTasks(ids, System.currentTimeMillis())
            ids
        }

    override suspend fun restoreTask(taskId: String) {
        database.withTransaction {
            val ids = taskDao.getDescendantTaskIdsIncludingDeleted(taskId)
            if (ids.isEmpty()) return@withTransaction
            taskDao.restoreTasks(ids, System.currentTimeMillis())
        }
    }

    override suspend fun permanentlyDeleteTask(taskId: String) {
        database.withTransaction {
            val ids = taskDao.getDescendantTaskIdsIncludingDeleted(taskId)
            if (ids.isEmpty()) return@withTransaction
            taskDao.permanentlyDeleteTasks(ids)
        }
    }

    override suspend fun emptyTrash() {
        taskDao.emptyTrash()
    }

    private suspend fun nextSortOrder(folderId: String?, parentTaskId: String?): Double =
        taskDao.getMaxSortOrder(folderId, parentTaskId)?.let(FractionalIndex::after) ?: FractionalIndex.first()

    private suspend fun applyTaskOrderUpdate(
        taskId: String,
        targetFolderId: String?,
        targetParentTaskId: String?,
        update: FractionalIndex.OrderUpdate
    ): Double {
        val now = System.currentTimeMillis()
        return when (update) {
            is FractionalIndex.OrderUpdate.Single -> {
                taskDao.updateTaskPlacement(
                    taskId = taskId,
                    folderId = targetFolderId,
                    parentTaskId = targetParentTaskId,
                    sortOrder = update.sortOrder,
                    updatedAt = now
                )
                update.sortOrder
            }
            is FractionalIndex.OrderUpdate.Rebalanced -> {
                for ((id, newSortOrder) in update.sortOrders) {
                    if (id == taskId) {
                        taskDao.updateTaskPlacement(
                            taskId = id,
                            folderId = targetFolderId,
                            parentTaskId = targetParentTaskId,
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
                update.sortOrders.getValue(taskId)
            }
        }
    }
}
