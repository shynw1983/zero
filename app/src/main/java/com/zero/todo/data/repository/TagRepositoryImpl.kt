package com.zero.todo.data.repository

import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagRepository {

    override fun observeActiveTags(): Flow<List<TagItem>> =
        tagDao.observeActiveTags().map { list -> list.map { it.toDomain() } }

    override fun observeActiveTagsWithCounts(): Flow<List<TagWithCount>> =
        tagDao.observeActiveTagsWithCounts().map { rows ->
            rows.map { row -> TagWithCount(tag = row.tag.toDomain(), activeTaskCount = row.activeTaskCount) }
        }

    override fun observeTagsForTask(taskId: String): Flow<List<TagItem>> =
        tagDao.observeTagsForTask(taskId).map { list -> list.map { it.toDomain() } }

    override fun observeTasksByTag(tagId: String): Flow<List<TaskItem>> =
        tagDao.observeTasksByTag(tagId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateTag(name: String, colorHex: String?): TagItem {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Tag name must not be blank" }

        val existing = tagDao.getTagByName(trimmedName)
        if (existing != null) return existing.toDomain()

        val now = System.currentTimeMillis()
        val entity = TagEntity(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            colorHex = colorHex,
            createdAt = now,
            updatedAt = now,
            isDeleted = false
        )
        tagDao.upsertTag(entity)
        return entity.toDomain()
    }

    override suspend fun attachTagToTask(taskId: String, tagId: String) {
        tagDao.attachTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
    }

    override suspend fun detachTagFromTask(taskId: String, tagId: String) {
        tagDao.detachTagFromTask(taskId = taskId, tagId = tagId)
    }

    override suspend fun renameTag(tagId: String, newName: String) {
        val clean = newName.trim().removePrefix("#")
        if (clean.isEmpty()) return
        tagDao.renameTag(tagId = tagId, newName = clean, updatedAt = System.currentTimeMillis())
    }

    override suspend fun softDeleteTag(tagId: String) {
        tagDao.softDeleteTag(tagId = tagId, updatedAt = System.currentTimeMillis())
    }
}
