package com.zero.todo.data.repository

import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPlacementChange
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TaskOccurrenceChange(val before: List<TaskItem>, val after: List<TaskItem>)

interface TaskRepository {
    fun observeInboxTasks(): Flow<List<TaskItem>>
    fun observeTasksByFolder(folderId: String, includeSubfolders: Boolean = false): Flow<List<TaskItem>>
    fun observeSubtasks(parentTaskId: String): Flow<List<TaskItem>>
    fun observeDescendantSubtasks(rootTaskId: String): Flow<List<TaskItem>>
    fun observeTasksDueBetween(startMillis: Long, endMillis: Long): Flow<List<TaskItem>>
    fun observeTodayAndOverdueTasks(todayStartMillis: Long, tomorrowStartMillis: Long): Flow<List<TaskItem>>
    fun observeAllActiveTopLevelTasks(): Flow<List<TaskItem>>
    fun observeAllActiveTasks(): Flow<List<TaskItem>>
    fun observeDeletedTasks(): Flow<List<TaskItem>>
    fun observeTaskWithTags(taskId: String): Flow<TaskItem?>

    suspend fun getTaskById(taskId: String): TaskItem?
    suspend fun getDescendantSubtasksSnapshot(rootTaskId: String): List<TaskItem>
    suspend fun getUpcomingReminderTasks(nowMillis: Long): List<TaskItem>

    suspend fun createTask(
        title: String,
        folderId: String? = null,
        parentTaskId: String? = null,
        description: String = "",
        priority: TaskPriority = TaskPriority.NONE,
        dueDate: Long? = null,
        isAllDay: Boolean = true,
        reminderAt: Long? = null,
        recurrenceRule: String? = null,
        firstStep: String? = null,
        estimatedMinutes: Int? = null,
        placeTriggerOverride: PlaceTriggerMode? = null
    ): TaskItem

    suspend fun updateTask(taskId: String, edit: (TaskItem) -> TaskItem): TaskItem?
    suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean)
    suspend fun completeTask(taskId: String, today: LocalDate = LocalDate.now()): List<TaskItem>?
    suspend fun skipTaskOccurrence(expected: TaskItem, today: LocalDate): TaskOccurrenceChange?
    suspend fun undoSkipTaskOccurrence(change: TaskOccurrenceChange): Boolean

    suspend fun reorderTask(
        taskId: String,
        targetFolderId: String?,
        targetParentTaskId: String? = null,
        beforeTaskId: String? = null,
        afterTaskId: String? = null
    )

    suspend fun placeTaskInList(
        taskId: String,
        parentTaskId: String?,
        beforeTaskId: String? = null,
        rootFolderId: String? = null,
        acrossFolders: Boolean = false
    ): TaskPlacementChange?
    suspend fun outdentTask(taskId: String): TaskPlacementChange?
    suspend fun undoTaskPlacement(change: TaskPlacementChange): Boolean

    suspend fun moveTaskToFolder(taskId: String, targetFolderId: String?)
    suspend fun moveTaskUnderParent(taskId: String, parentTaskId: String): Boolean

    /**
     * Reschedules a list of tasks to [targetDate], preserving specific clock times on timed tasks.
     */
    suspend fun rescheduleTasksToDate(taskIds: List<String>, targetDate: LocalDate)

    /**
     * Promotes a nested subtask (at any depth) to an independent top-level task,
     * preserving the root ancestor task's folder placement.
     */
    suspend fun promoteSubtaskToTopLevel(subtaskId: String): TaskItem?
    suspend fun softDeleteTask(taskId: String): List<String>
    suspend fun restoreTask(taskId: String)
    suspend fun permanentlyDeleteTask(taskId: String)
    suspend fun emptyTrash()
}
