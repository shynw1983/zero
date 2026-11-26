package com.zero.todo.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef
import com.zero.todo.data.local.entity.TaskWithTags
import kotlinx.coroutines.flow.Flow

data class TagWithTaskCountRow(
    @Embedded val tag: TagEntity,
    val activeTaskCount: Int
)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getActiveTagsSnapshot(): List<TagEntity>

    @Query(
        """
        SELECT tg.*, COUNT(t.id) AS activeTaskCount
        FROM tags tg
        LEFT JOIN task_tag_cross_ref ref ON tg.id = ref.tagId
        LEFT JOIN tasks t ON t.id = ref.taskId
            AND t.isDeleted = 0 AND t.isCompleted = 0
        WHERE tg.isDeleted = 0
        GROUP BY tg.id
        ORDER BY tg.name COLLATE NOCASE ASC
        """
    )
    fun observeActiveTagsWithCounts(): Flow<List<TagWithTaskCountRow>>

    @Query("SELECT * FROM tags WHERE isDeleted = 0 AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getTagById(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Query("UPDATE tags SET name = :newName, updatedAt = :updatedAt WHERE id = :tagId AND isDeleted = 0")
    suspend fun renameTag(tagId: String, newName: String, updatedAt: Long)

    @Query("UPDATE tags SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :tagId")
    suspend fun softDeleteTag(tagId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTagToTask(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun detachTagFromTask(taskId: String, tagId: String)

    @Query(
        """
        SELECT tg.* FROM tags tg
        INNER JOIN task_tag_cross_ref ref ON tg.id = ref.tagId
        WHERE ref.taskId = :taskId AND tg.isDeleted = 0
        ORDER BY tg.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForTask(taskId: String): Flow<List<TagEntity>>

    @Transaction
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN task_tag_cross_ref ref ON t.id = ref.taskId
        INNER JOIN tags tg ON tg.id = ref.tagId
        WHERE ref.tagId = :tagId AND t.isDeleted = 0 AND tg.isDeleted = 0
        ORDER BY t.isCompleted ASC, t.sortOrder ASC, t.createdAt ASC
        """
    )
    fun observeTasksByTag(tagId: String): Flow<List<TaskWithTags>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun getAllTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM task_tag_cross_ref")
    suspend fun getAllCrossRefsSnapshot(): List<TaskTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref")
    suspend fun clearAllCrossRefs()

    @Query("DELETE FROM tags")
    suspend fun clearAllTags()
}
