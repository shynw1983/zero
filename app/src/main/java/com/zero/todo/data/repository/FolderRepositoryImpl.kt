package com.zero.todo.data.repository

import androidx.room.withTransaction
import com.zero.todo.core.common.FractionalIndex
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.FolderTreeBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val database: ZeroDatabase,
    private val folderDao: FolderDao,
    private val taskDao: TaskDao
) : FolderRepository {

    override fun observeActiveFolders(): Flow<List<Folder>> =
        folderDao.observeActiveFolders().map { entities -> entities.map { it.toDomain() } }

    override fun observeFolderTree(): Flow<List<FolderNode>> =
        observeActiveFolders().map { folders -> FolderTreeBuilder.build(folders) }

    override fun observeChildFolders(parentId: String?): Flow<List<Folder>> =
        folderDao.observeChildFolders(parentId).map { entities -> entities.map { it.toDomain() } }

    override fun observeFolderById(folderId: String): Flow<Folder?> =
        folderDao.observeFolderById(folderId).map { it?.toDomain() }

    override suspend fun getFolderById(folderId: String): Folder? =
        folderDao.getFolderById(folderId)?.toDomain()

    override suspend fun getSubtreeFolders(rootFolderId: String): List<Folder> =
        folderDao.getSubtreeFolders(rootFolderId).map { it.toDomain() }

    override suspend fun getDescendantIds(rootFolderId: String): List<String> =
        folderDao.getDescendantFolderIds(rootFolderId)

    override suspend fun getAncestors(folderId: String): List<Folder> =
        folderDao.getAncestorFolders(folderId).map { it.toDomain() }

    override suspend fun createFolder(
        name: String,
        parentId: String?,
        icon: String?,
        colorHex: String?
    ): Folder {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Folder name must not be blank" }

        return database.withTransaction {
            if (parentId != null) {
                requireNotNull(folderDao.getFolderById(parentId)) {
                    "Parent folder does not exist or is deleted: $parentId"
                }
            }
            val now = System.currentTimeMillis()
            val maxSortOrder = folderDao.getMaxSortOrder(parentId)
            val sortOrder = if (maxSortOrder == null) {
                FractionalIndex.first()
            } else {
                FractionalIndex.after(maxSortOrder)
            }
            val entity = FolderEntity(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                name = trimmedName,
                icon = icon,
                colorHex = colorHex,
                sortOrder = sortOrder,
                isExpanded = true,
                createdAt = now,
                updatedAt = now,
                isDeleted = false
            )
            folderDao.upsertFolder(entity)
            entity.toDomain()
        }
    }

    override suspend fun renameFolder(folderId: String, newName: String) {
        val trimmedName = newName.trim()
        require(trimmedName.isNotEmpty()) { "Folder name must not be blank" }
        folderDao.renameFolder(
            folderId = folderId,
            name = trimmedName,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateFolderAppearance(folderId: String, icon: String?, colorHex: String?) {
        folderDao.updateAppearance(
            folderId = folderId,
            icon = icon,
            colorHex = colorHex,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun setFolderExpanded(folderId: String, isExpanded: Boolean) {
        folderDao.updateExpanded(
            folderId = folderId,
            isExpanded = isExpanded,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun moveFolderToIndex(
        folderId: String,
        targetParentId: String?,
        targetIndex: Int
    ) {
        database.withTransaction {
            validateFolderMove(folderId = folderId, targetParentId = targetParentId)
            val siblings = folderDao.getChildFolders(targetParentId)
                .map { it.id to it.sortOrder }
            val update = FractionalIndex.computeMoveByIndex(
                orderedSiblings = siblings,
                movingId = folderId,
                targetIndex = targetIndex
            )
            applyFolderOrderUpdate(
                folderId = folderId,
                targetParentId = targetParentId,
                update = update
            )
        }
    }

    override suspend fun reorderFolderBetween(
        folderId: String,
        targetParentId: String?,
        beforeFolderId: String?,
        afterFolderId: String?
    ) {
        database.withTransaction {
            val folder = validateFolderMove(folderId = folderId, targetParentId = targetParentId)
            val siblings = folderDao.getChildFolders(targetParentId)
                .map { it.id to it.sortOrder }
            if (beforeFolderId != null && siblings.none { it.first == beforeFolderId && it.first != folderId }) {
                return@withTransaction
            }
            if (afterFolderId != null && siblings.none { it.first == afterFolderId && it.first != folderId }) {
                return@withTransaction
            }
            val update = FractionalIndex.computeMoveBetween(
                orderedSiblings = siblings,
                movingId = folderId,
                beforeId = beforeFolderId,
                afterId = afterFolderId
            )
            applyFolderOrderUpdate(
                folderId = folderId,
                targetParentId = targetParentId,
                update = update
            )
            if (folder.parentId != targetParentId && targetParentId != null &&
                folderDao.getFolderById(targetParentId)?.isExpanded == false
            ) {
                folderDao.updateExpanded(targetParentId, true, System.currentTimeMillis())
            }
        }
    }

    override suspend fun softDeleteFolder(folderId: String, deleteContainedTasks: Boolean) {
        database.withTransaction {
            val descendantIds = folderDao.getDescendantFolderIds(folderId)
            if (descendantIds.isEmpty()) return@withTransaction

            val now = System.currentTimeMillis()
            if (deleteContainedTasks) {
                taskDao.softDeleteTasksInFolders(descendantIds, now)
            } else {
                taskDao.detachTasksFromFolders(descendantIds, now)
            }
            folderDao.softDeleteFolders(descendantIds, now)
        }
    }

    override suspend fun restoreFolder(folderId: String) {
        database.withTransaction {
            val target = folderDao.getFolderByIdIncludingDeleted(folderId) ?: return@withTransaction
            val descendantIds = folderDao.getDescendantFolderIdsIncludingDeleted(folderId)
            if (descendantIds.isEmpty()) return@withTransaction

            val now = System.currentTimeMillis()
            folderDao.restoreFolders(descendantIds, now)

            // If original parent is still soft-deleted, promote restored root folder to top-level.
            val parentId = target.parentId
            if (parentId != null && folderDao.getFolderById(parentId) == null) {
                val maxRootOrder = folderDao.getMaxSortOrder(null)
                val newOrder = if (maxRootOrder == null) {
                    FractionalIndex.first()
                } else {
                    FractionalIndex.after(maxRootOrder)
                }
                folderDao.updateParentAndSortOrder(
                    folderId = folderId,
                    parentId = null,
                    sortOrder = newOrder,
                    updatedAt = now
                )
            }
        }
    }

    private suspend fun validateFolderMove(folderId: String, targetParentId: String?): FolderEntity {
        val folder = requireNotNull(folderDao.getFolderById(folderId)) {
            "Folder does not exist or is deleted: $folderId"
        }
        if (targetParentId != null) {
            require(targetParentId != folderId) {
                "Cannot move folder $folderId into itself"
            }
            requireNotNull(folderDao.getFolderById(targetParentId)) {
                "Target parent folder does not exist or is deleted: $targetParentId"
            }
            val descendants = folderDao.getDescendantFolderIds(folderId)
            require(targetParentId !in descendants) {
                "Cannot move folder $folderId into its own descendant $targetParentId"
            }
        }
        return folder
    }

    private suspend fun applyFolderOrderUpdate(
        folderId: String,
        targetParentId: String?,
        update: FractionalIndex.OrderUpdate
    ) {
        val now = System.currentTimeMillis()
        when (update) {
            is FractionalIndex.OrderUpdate.Single -> {
                folderDao.updateParentAndSortOrder(
                    folderId = folderId,
                    parentId = targetParentId,
                    sortOrder = update.sortOrder,
                    updatedAt = now
                )
            }
            is FractionalIndex.OrderUpdate.Rebalanced -> {
                for ((id, newSortOrder) in update.sortOrders) {
                    if (id == folderId) {
                        folderDao.updateParentAndSortOrder(
                            folderId = id,
                            parentId = targetParentId,
                            sortOrder = newSortOrder,
                            updatedAt = now
                        )
                    } else {
                        folderDao.updateSortOrder(
                            folderId = id,
                            sortOrder = newSortOrder,
                            updatedAt = now
                        )
                    }
                }
            }
        }
    }
}
