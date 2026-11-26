package com.zero.todo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND folderId IS NULL AND parentTaskId IS NULL
        ORDER BY isCompleted ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeInboxTasks(): Flow<List<TaskWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND folderId = :folderId AND parentTaskId IS NULL
        ORDER BY isCompleted ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeTasksByFolder(folderId: String): Flow<List<TaskWithTags>>

    /**
     * Observes all top-level active tasks inside [rootFolderId] and all of its non-deleted sub-folders.
     */
    @Transaction
    @Query(
        """
        WITH RECURSIVE folder_tree(id) AS (
            SELECT id FROM folders WHERE id = :rootFolderId AND isDeleted = 0
            UNION ALL
            SELECT f.id FROM folders f
            INNER JOIN folder_tree ft ON f.parentId = ft.id
            WHERE f.isDeleted = 0
        )
        SELECT t.* FROM tasks t
        INNER JOIN folder_tree ft ON t.folderId = ft.id
        WHERE t.isDeleted = 0 AND t.parentTaskId IS NULL
        ORDER BY t.isCompleted ASC, t.sortOrder ASC, t.createdAt ASC
        """
    )
    fun observeTasksInFolderSubtree(rootFolderId: String): Flow<List<TaskWithTags>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND parentTaskId = :parentTaskId
        ORDER BY isCompleted ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeSubtasks(parentTaskId: String): Flow<List<TaskEntity>>

    /**
     * Recursively observes all active descendant subtasks under [rootTaskId] (unlimited nesting depth).
     */
    @Query(
        """
        WITH RECURSIVE subtask_tree(id, depth) AS (
            SELECT id, 1 FROM tasks WHERE parentTaskId = :rootTaskId AND isDeleted = 0
            UNION ALL
            SELECT t.id, st.depth + 1 FROM tasks t
            INNER JOIN subtask_tree st ON t.parentTaskId = st.id
            WHERE t.isDeleted = 0
        )
        SELECT t.* FROM tasks t
        INNER JOIN subtask_tree st ON t.id = st.id
        ORDER BY st.depth ASC, t.isCompleted ASC, t.sortOrder ASC, t.createdAt ASC
        """
    )
    fun observeDescendantSubtasks(rootTaskId: String): Flow<List<TaskEntity>>

    @Transaction
    @Query(
        """
        WITH RECURSIVE subtask_tree(id, depth) AS (
            SELECT id, 1 FROM tasks WHERE parentTaskId = :rootTaskId AND isDeleted = 0
            UNION ALL
            SELECT t.id, st.depth + 1 FROM tasks t
            INNER JOIN subtask_tree st ON t.parentTaskId = st.id
            WHERE t.isDeleted = 0
        )
        SELECT t.* FROM tasks t
        INNER JOIN subtask_tree st ON t.id = st.id
        ORDER BY st.depth ASC, t.sortOrder ASC, t.createdAt ASC
        """
    )
    suspend fun getDescendantSubtasksSnapshot(rootTaskId: String): List<TaskWithTags>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND dueDate IS NOT NULL AND dueDate >= :startMillis AND dueDate < :endMillis
        ORDER BY isCompleted ASC, dueDate ASC, priority ASC, sortOrder ASC
        """
    )
    suspend fun getTasksDueBetween(startMillis: Long, endMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND isCompleted = 0
          AND dueDate IS NOT NULL AND dueDate < :endMillis
        ORDER BY dueDate ASC, priority ASC, sortOrder ASC
        """
    )
    suspend fun getActiveTasksDueBefore(endMillis: Long): List<TaskEntity>

    @Query(
        """
        UPDATE tasks
        SET parentTaskId = NULL, folderId = :targetFolderId, sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :taskId AND isDeleted = 0
        """
    )
    suspend fun promoteSubtaskToTopLevel(
        taskId: String,
        targetFolderId: String?,
        sortOrder: Double,
        updatedAt: Long
    )

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND dueDate IS NOT NULL AND dueDate >= :startMillis AND dueDate < :endMillis
        ORDER BY isCompleted ASC, dueDate ASC, priority ASC, sortOrder ASC
        """
    )
    fun observeTasksDueBetween(startMillis: Long, endMillis: Long): Flow<List<TaskWithTags>>

    /**
     * Observes both overdue incomplete tasks (dueDate < [todayStartMillis]) AND all tasks due today.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND dueDate IS NOT NULL
          AND ((dueDate < :todayStartMillis AND isCompleted = 0)
               OR (dueDate >= :todayStartMillis AND dueDate < :tomorrowStartMillis))
        ORDER BY isCompleted ASC, dueDate ASC, priority ASC, sortOrder ASC
        """
    )
    fun observeTodayAndOverdueTasks(
        todayStartMillis: Long,
        tomorrowStartMillis: Long
    ): Flow<List<TaskWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND parentTaskId IS NULL
        ORDER BY isCompleted ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeAllActiveTopLevelTasks(): Flow<List<TaskWithTags>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY isCompleted, sortOrder, createdAt")
    fun observeAllActiveTasks(): Flow<List<TaskWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 1 AND (parentTaskId IS NULL OR parentTaskId IN (SELECT id FROM tasks WHERE isDeleted = 0))
        ORDER BY updatedAt DESC
        """
    )
    fun observeDeletedTasks(): Flow<List<TaskWithTags>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND isCompleted = 0
          AND reminderAt IS NOT NULL AND reminderAt > :nowMillis
        ORDER BY reminderAt ASC
        """
    )
    suspend fun getUpcomingReminderTasks(nowMillis: Long): List<TaskEntity>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId AND isDeleted = 0 LIMIT 1")
    fun observeTaskWithTags(taskId: String): Flow<TaskWithTags?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId AND isDeleted = 0 LIMIT 1")
    suspend fun getTaskWithTags(taskId: String): TaskWithTags?

    @Query("SELECT * FROM tasks WHERE id = :taskId AND isDeleted = 0 LIMIT 1")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND (parentTaskId = :parentTaskId OR (:parentTaskId IS NULL AND parentTaskId IS NULL
              AND ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)))
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    suspend fun getSiblingTasks(folderId: String?, parentTaskId: String?): List<TaskEntity>

    @Query(
        """
        SELECT MAX(sortOrder) FROM tasks
        WHERE isDeleted = 0
          AND (parentTaskId = :parentTaskId OR (:parentTaskId IS NULL AND parentTaskId IS NULL
              AND ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)))
        """
    )
    suspend fun getMaxSortOrder(folderId: String?, parentTaskId: String?): Double?

    @Query(
        """
        WITH RECURSIVE task_tree(id) AS (
            SELECT id FROM tasks WHERE id = :rootTaskId AND isDeleted = 0
            UNION ALL
            SELECT t.id FROM tasks t
            INNER JOIN task_tree tt ON t.parentTaskId = tt.id
            WHERE t.isDeleted = 0
        )
        SELECT id FROM task_tree
        """
    )
    suspend fun getDescendantTaskIds(rootTaskId: String): List<String>

    @Query(
        """
        WITH RECURSIVE task_tree(id) AS (
            SELECT id FROM tasks WHERE id = :rootTaskId
            UNION ALL
            SELECT t.id FROM tasks t
            INNER JOIN task_tree tt ON t.parentTaskId = tt.id
        )
        SELECT id FROM task_tree
        """
    )
    suspend fun getDescendantTaskIdsIncludingDeleted(rootTaskId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query(
        """
        UPDATE tasks
        SET isCompleted = :isCompleted, completedAt = :completedAt, updatedAt = :updatedAt
        WHERE id = :taskId AND isDeleted = 0
        """
    )
    suspend fun updateCompletion(
        taskId: String,
        isCompleted: Boolean,
        completedAt: Long?,
        updatedAt: Long
    )

    @Query("UPDATE tasks SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :taskId AND isDeleted = 0")
    suspend fun updateSortOrder(taskId: String, sortOrder: Double, updatedAt: Long)

    @Query(
        """
        UPDATE tasks
        SET folderId = :folderId, sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :taskId AND isDeleted = 0
        """
    )
    suspend fun moveTaskToFolder(
        taskId: String,
        folderId: String?,
        sortOrder: Double,
        updatedAt: Long
    )

    @Query("""
        UPDATE tasks SET folderId = :folderId, parentTaskId = :parentTaskId, sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :taskId AND isDeleted = 0
    """)
    suspend fun updateTaskPlacement(taskId: String, folderId: String?, parentTaskId: String?, sortOrder: Double, updatedAt: Long)

    @Query("UPDATE tasks SET folderId = NULL, updatedAt = :updatedAt WHERE folderId IN (:folderIds) AND isDeleted = 0")
    suspend fun detachTasksFromFolders(folderIds: List<String>, updatedAt: Long)

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :updatedAt WHERE folderId IN (:folderIds) AND isDeleted = 0")
    suspend fun softDeleteTasksInFolders(folderIds: List<String>, updatedAt: Long)

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :updatedAt WHERE id IN (:taskIds)")
    suspend fun softDeleteTasks(taskIds: List<String>, updatedAt: Long)

    @Query("UPDATE tasks SET isDeleted = 0, updatedAt = :updatedAt WHERE id IN (:taskIds)")
    suspend fun restoreTasks(taskIds: List<String>, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id IN (:taskIds)")
    suspend fun permanentlyDeleteTasks(taskIds: List<String>)

    @Query("DELETE FROM tasks WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC, sortOrder ASC")
    suspend fun getAllTasksSnapshot(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()
}
