package com.zero.todo.data.repository

import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeActiveFolders(): Flow<List<Folder>>
    fun observeFolderTree(): Flow<List<FolderNode>>
    fun observeChildFolders(parentId: String?): Flow<List<Folder>>
    fun observeFolderById(folderId: String): Flow<Folder?>

    suspend fun getFolderById(folderId: String): Folder?
    suspend fun getSubtreeFolders(rootFolderId: String): List<Folder>
    suspend fun getDescendantIds(rootFolderId: String): List<String>
    suspend fun getAncestors(folderId: String): List<Folder>

    suspend fun createFolder(
        name: String,
        parentId: String? = null,
        icon: String? = null,
        colorHex: String? = null
    ): Folder

    suspend fun renameFolder(folderId: String, newName: String)
    suspend fun updateFolderAppearance(folderId: String, icon: String?, colorHex: String?)
    suspend fun setFolderExpanded(folderId: String, isExpanded: Boolean)

    /**
     * Reorders or re-parents [folderId] under [targetParentId] at [targetIndex] among target siblings.
     * Prevents moving a folder into itself or any of its descendants.
     */
    suspend fun moveFolderToIndex(
        folderId: String,
        targetParentId: String?,
        targetIndex: Int
    )

    /**
     * Reorders or re-parents [folderId] under [targetParentId] between [beforeFolderId] and [afterFolderId].
     * Prevents moving a folder into itself or any of its descendants.
     */
    suspend fun reorderFolderBetween(
        folderId: String,
        targetParentId: String?,
        beforeFolderId: String? = null,
        afterFolderId: String? = null
    )

    /**
     * Soft-deletes [folderId] and all of its active descendants.
     * By default, tasks inside deleted folders are safely moved to Inbox (`folderId = null`);
     * pass [deleteContainedTasks] = true to soft-delete those tasks as well.
     */
    suspend fun softDeleteFolder(folderId: String, deleteContainedTasks: Boolean = false)

    /**
     * Restores a soft-deleted folder and its subtree.
     */
    suspend fun restoreFolder(folderId: String)
}
