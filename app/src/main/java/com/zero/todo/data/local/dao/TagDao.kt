package com.zero.todo.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TagPlaceEntity
import com.zero.todo.data.local.entity.TaskEntity
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

    @Query(
        """
        SELECT * FROM tags
        WHERE isDeleted = 0
          AND LOWER(name) = LOWER(:name)
          AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        LIMIT 1
        """
    )
    suspend fun getTagByNameInParent(name: String, parentId: String?): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getTagById(id: String): TagEntity?

    @Query(
        """
        WITH RECURSIVE tag_tree(id) AS (
            SELECT id FROM tags WHERE id = :rootTagId AND isDeleted = 0
            UNION ALL
            SELECT tg.id FROM tags tg
            INNER JOIN tag_tree tt ON tg.parentId = tt.id
            WHERE tg.isDeleted = 0
        )
        SELECT id FROM tag_tree
        """
    )
    suspend fun getDescendantTagIds(rootTagId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Query("UPDATE tags SET name = :newName, updatedAt = :updatedAt WHERE id = :tagId AND isDeleted = 0")
    suspend fun renameTag(tagId: String, newName: String, updatedAt: Long)

    @Query("UPDATE tags SET isExpanded = :isExpanded, updatedAt = :updatedAt WHERE id = :tagId AND isDeleted = 0")
    suspend fun updateTagExpanded(tagId: String, isExpanded: Boolean, updatedAt: Long)

    @Query("UPDATE tags SET parentId = :newParentId, updatedAt = :updatedAt WHERE id = :tagId AND isDeleted = 0")
    suspend fun moveTag(tagId: String, newParentId: String?, updatedAt: Long)

    @Query("UPDATE tags SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :tagId")
    suspend fun softDeleteTag(tagId: String, updatedAt: Long)

    @Query("UPDATE tags SET isDeleted = 1, updatedAt = :updatedAt WHERE id IN (:tagIds)")
    suspend fun softDeleteTags(tagIds: List<String>, updatedAt: Long)

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
        WITH RECURSIVE tag_tree(id) AS (
            SELECT id FROM tags WHERE id = :tagId AND isDeleted = 0
            UNION ALL
            SELECT tg.id FROM tags tg
            INNER JOIN tag_tree tt ON tg.parentId = tt.id
            WHERE tg.isDeleted = 0
        )
        SELECT DISTINCT t.* FROM tasks t
        INNER JOIN task_tag_cross_ref ref ON t.id = ref.taskId
        INNER JOIN tag_tree tt ON ref.tagId = tt.id
        WHERE t.isDeleted = 0
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

    @Query("UPDATE tags SET isPlaceTag = :isPlaceTag, placeTriggerMode = :placeTriggerMode, updatedAt = :updatedAt WHERE id = :tagId AND isDeleted = 0")
    suspend fun updateTagPlaceConfig(tagId: String, isPlaceTag: Boolean, placeTriggerMode: String, updatedAt: Long)

    @Query("SELECT * FROM tag_places ORDER BY createdAt ASC")
    fun observeAllTagPlaces(): Flow<List<TagPlaceEntity>>

    @Query("SELECT * FROM tag_places ORDER BY createdAt ASC")
    suspend fun getAllTagPlacesSnapshot(): List<TagPlaceEntity>

    @Query(
        """
        SELECT tp.* FROM tag_places tp
        INNER JOIN tags tg ON tp.tagId = tg.id
        WHERE tg.isDeleted = 0 AND tg.isPlaceTag = 1 AND tp.isEnabled = 1
        ORDER BY tp.createdAt ASC
        """
    )
    suspend fun getActiveGeofencePlaces(): List<TagPlaceEntity>

    @Query("SELECT * FROM tag_places WHERE id = :placeId LIMIT 1")
    suspend fun getTagPlaceById(placeId: String): TagPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTagPlace(place: TagPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTagPlaces(places: List<TagPlaceEntity>)

    @Query("DELETE FROM tag_places WHERE id = :placeId")
    suspend fun deleteTagPlace(placeId: String)

    @Query("DELETE FROM tag_places")
    suspend fun clearAllTagPlaces()

    @Query(
        """
        WITH RECURSIVE tag_tree(id) AS (
            SELECT id FROM tags WHERE id = :tagId AND isDeleted = 0
            UNION ALL
            SELECT tg.id FROM tags tg
            INNER JOIN tag_tree tt ON tg.parentId = tt.id
            WHERE tg.isDeleted = 0
        )
        SELECT DISTINCT t.* FROM tasks t
        INNER JOIN task_tag_cross_ref ref ON t.id = ref.taskId
        INNER JOIN tag_tree tt ON ref.tagId = tt.id
        WHERE t.isDeleted = 0 AND t.isCompleted = 0
        ORDER BY t.priority ASC, t.sortOrder ASC, t.createdAt ASC
        """
    )
    suspend fun getActiveIncompleteTasksForTagTree(tagId: String): List<TaskEntity>
}
