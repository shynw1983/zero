package com.zero.todo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zero.todo.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query(
        """
        SELECT * FROM folders
        WHERE isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeActiveFolders(): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT * FROM folders
        WHERE isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    suspend fun getActiveFolders(): List<FolderEntity>

    @Query(
        """
        SELECT * FROM folders
        WHERE isDeleted = 0
          AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeChildFolders(parentId: String?): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT * FROM folders
        WHERE isDeleted = 0
          AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    suspend fun getChildFolders(parentId: String?): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderByIdIncludingDeleted(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun observeFolderById(id: String): Flow<FolderEntity?>

    @Query(
        """
        SELECT MAX(sortOrder) FROM folders
        WHERE isDeleted = 0
          AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        """
    )
    suspend fun getMaxSortOrder(parentId: String?): Double?

    /**
     * Recursively returns [rootFolderId] and all active (non-deleted) descendant folder IDs.
     */
    @Query(
        """
        WITH RECURSIVE folder_tree(id) AS (
            SELECT id FROM folders WHERE id = :rootFolderId AND isDeleted = 0
            UNION ALL
            SELECT f.id FROM folders f
            INNER JOIN folder_tree ft ON f.parentId = ft.id
            WHERE f.isDeleted = 0
        )
        SELECT id FROM folder_tree
        """
    )
    suspend fun getDescendantFolderIds(rootFolderId: String): List<String>

    @Query(
        """
        WITH RECURSIVE folder_tree(id) AS (
            SELECT id FROM folders WHERE id = :rootFolderId
            UNION ALL
            SELECT f.id FROM folders f
            INNER JOIN folder_tree ft ON f.parentId = ft.id
        )
        SELECT id FROM folder_tree
        """
    )
    suspend fun getDescendantFolderIdsIncludingDeleted(rootFolderId: String): List<String>

    /**
     * Recursively returns [rootFolderId] and all active descendants ordered by tree depth and sortOrder.
     */
    @Query(
        """
        WITH RECURSIVE folder_tree(id, depth) AS (
            SELECT id, 0 FROM folders WHERE id = :rootFolderId AND isDeleted = 0
            UNION ALL
            SELECT f.id, ft.depth + 1 FROM folders f
            INNER JOIN folder_tree ft ON f.parentId = ft.id
            WHERE f.isDeleted = 0
        )
        SELECT f.* FROM folders f
        INNER JOIN folder_tree ft ON f.id = ft.id
        ORDER BY ft.depth ASC, f.sortOrder ASC, f.createdAt ASC
        """
    )
    suspend fun getSubtreeFolders(rootFolderId: String): List<FolderEntity>

    /**
     * Recursively walks up parent links to build a root-to-leaf breadcrumb trail.
     */
    @Query(
        """
        WITH RECURSIVE ancestors(id, parentId, depth) AS (
            SELECT id, parentId, 0 FROM folders WHERE id = :folderId AND isDeleted = 0
            UNION ALL
            SELECT f.id, f.parentId, a.depth + 1 FROM folders f
            INNER JOIN ancestors a ON f.id = a.parentId
            WHERE f.isDeleted = 0
        )
        SELECT f.* FROM folders f
        INNER JOIN ancestors a ON f.id = a.id
        ORDER BY a.depth DESC
        """
    )
    suspend fun getAncestorFolders(folderId: String): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :folderId AND isDeleted = 0")
    suspend fun renameFolder(folderId: String, name: String, updatedAt: Long)

    @Query("UPDATE folders SET icon = :icon, colorHex = :colorHex, updatedAt = :updatedAt WHERE id = :folderId AND isDeleted = 0")
    suspend fun updateAppearance(folderId: String, icon: String?, colorHex: String?, updatedAt: Long)

    @Query("UPDATE folders SET isExpanded = :isExpanded, updatedAt = :updatedAt WHERE id = :folderId AND isDeleted = 0")
    suspend fun updateExpanded(folderId: String, isExpanded: Boolean, updatedAt: Long)

    @Query("UPDATE folders SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :folderId AND isDeleted = 0")
    suspend fun updateSortOrder(folderId: String, sortOrder: Double, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :folderId AND isDeleted = 0")
    suspend fun updateParentAndSortOrder(
        folderId: String,
        parentId: String?,
        sortOrder: Double,
        updatedAt: Long
    )

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :updatedAt WHERE id IN (:folderIds)")
    suspend fun softDeleteFolders(folderIds: List<String>, updatedAt: Long)

    @Query("UPDATE folders SET isDeleted = 0, updatedAt = :updatedAt WHERE id IN (:folderIds)")
    suspend fun restoreFolders(folderIds: List<String>, updatedAt: Long)

    @Query("SELECT * FROM folders ORDER BY createdAt ASC, sortOrder ASC")
    suspend fun getAllFoldersSnapshot(): List<FolderEntity>

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()
}
