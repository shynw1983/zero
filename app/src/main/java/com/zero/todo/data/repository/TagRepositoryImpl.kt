package com.zero.todo.data.repository

import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TagPlaceEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef
import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import com.zero.todo.domain.model.TagTreeBuilder
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagRepository {

    override fun observeActiveTags(): Flow<List<TagItem>> =
        combine(
            tagDao.observeActiveTags(),
            tagDao.observeAllTagPlaces()
        ) { list, places ->
            val placesByTag = places.groupBy({ it.tagId }, { it.toDomain() })
            list.map { it.toDomain(placesByTag[it.id].orEmpty()) }
        }

    override fun observeActiveTagsWithCounts(): Flow<List<TagWithCount>> =
        combine(
            tagDao.observeActiveTagsWithCounts(),
            tagDao.observeAllTagPlaces()
        ) { rows, places ->
            val placesByTag = places.groupBy({ it.tagId }, { it.toDomain() })
            rows.map { row ->
                TagWithCount(
                    tag = row.tag.toDomain(placesByTag[row.tag.id].orEmpty()),
                    activeTaskCount = row.activeTaskCount
                )
            }
        }

    override fun observeTagsForTask(taskId: String): Flow<List<TagItem>> =
        tagDao.observeTagsForTask(taskId).map { list -> list.map { it.toDomain() } }

    override fun observeTasksByTag(tagId: String): Flow<List<TaskItem>> =
        tagDao.observeTasksByTag(tagId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateTag(name: String, colorHex: String?, parentId: String?): TagItem {
        val segments = TagTreeBuilder.parseTagPathSegments(name)
        require(segments.isNotEmpty()) { "Tag name must not be blank" }

        var currentParentId = parentId
        var leafTag: TagItem? = null
        val now = System.currentTimeMillis()

        for (segment in segments) {
            val existing = tagDao.getTagByNameInParent(segment, currentParentId)
            val resolved = if (existing != null) {
                existing.toDomain()
            } else {
                val entity = TagEntity(
                    id = UUID.randomUUID().toString(),
                    parentId = currentParentId,
                    name = segment,
                    colorHex = colorHex,
                    isExpanded = true,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false
                )
                tagDao.upsertTag(entity)
                entity.toDomain()
            }
            currentParentId = resolved.id
            leafTag = resolved
        }
        return requireNotNull(leafTag)
    }

    override suspend fun createTag(name: String, parentId: String?, colorHex: String?): TagItem =
        getOrCreateTag(name = name, colorHex = colorHex, parentId = parentId)

    override suspend fun attachTagToTask(taskId: String, tagId: String) {
        tagDao.attachTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
    }

    override suspend fun detachTagFromTask(taskId: String, tagId: String) {
        tagDao.detachTagFromTask(taskId = taskId, tagId = tagId)
    }

    override suspend fun renameTag(tagId: String, newName: String) {
        val clean = newName.trim().removePrefix("#").replace('/', ' ')
            .trim()
        if (clean.isEmpty()) return
        tagDao.renameTag(tagId = tagId, newName = clean, updatedAt = System.currentTimeMillis())
    }

    override suspend fun setTagExpanded(tagId: String, isExpanded: Boolean) {
        tagDao.updateTagExpanded(tagId = tagId, isExpanded = isExpanded, updatedAt = System.currentTimeMillis())
    }

    override suspend fun moveTag(tagId: String, newParentId: String?): Boolean {
        if (newParentId == tagId) return false
        if (newParentId != null) {
            val targetExists = tagDao.getTagById(newParentId) != null
            if (!targetExists) return false
            val descendants = tagDao.getDescendantTagIds(tagId)
            if (newParentId in descendants) return false
        }
        tagDao.moveTag(tagId = tagId, newParentId = newParentId, updatedAt = System.currentTimeMillis())
        return true
    }

    override suspend fun softDeleteTag(tagId: String) {
        val subtreeIds = tagDao.getDescendantTagIds(tagId)
        val now = System.currentTimeMillis()
        if (subtreeIds.isEmpty()) {
            tagDao.softDeleteTag(tagId = tagId, updatedAt = now)
        } else {
            tagDao.softDeleteTags(tagIds = subtreeIds, updatedAt = now)
        }
    }

    override suspend fun updateTagPlaceConfig(
        tagId: String,
        isPlaceTag: Boolean,
        triggerMode: PlaceTriggerMode
    ) {
        tagDao.updateTagPlaceConfig(
            tagId = tagId,
            isPlaceTag = isPlaceTag,
            placeTriggerMode = triggerMode.storageValue,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun addTagPlace(
        tagId: String,
        label: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): TagPlace {
        val now = System.currentTimeMillis()
        val entity = TagPlaceEntity(
            id = UUID.randomUUID().toString(),
            tagId = tagId,
            label = label.trim().ifBlank { address.trim().take(24) },
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters.coerceIn(50, 2_000),
            isEnabled = true,
            createdAt = now,
            updatedAt = now
        )
        tagDao.upsertTagPlace(entity)
        return entity.toDomain()
    }

    override suspend fun updateTagPlace(place: TagPlace) {
        tagDao.upsertTagPlace(
            place.copy(
                radiusMeters = place.radiusMeters.coerceIn(50, 2_000),
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override suspend fun deleteTagPlace(placeId: String) {
        tagDao.deleteTagPlace(placeId)
    }
}
