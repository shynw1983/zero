package com.zero.todo.data.repository

import com.zero.todo.domain.model.PlaceTriggerMode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TagPlace
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow

data class TagWithCount(
    val tag: TagItem,
    val activeTaskCount: Int
)

interface TagRepository {
    fun observeActiveTags(): Flow<List<TagItem>>
    fun observeActiveTagsWithCounts(): Flow<List<TagWithCount>>
    fun observeTagsForTask(taskId: String): Flow<List<TagItem>>
    fun observeTasksByTag(tagId: String): Flow<List<TaskItem>>

    suspend fun getOrCreateTag(name: String, colorHex: String? = null, parentId: String? = null): TagItem
    suspend fun createTag(name: String, parentId: String? = null, colorHex: String? = null): TagItem
    suspend fun attachTagToTask(taskId: String, tagId: String)
    suspend fun detachTagFromTask(taskId: String, tagId: String)
    suspend fun renameTag(tagId: String, newName: String)
    suspend fun setTagExpanded(tagId: String, isExpanded: Boolean)
    suspend fun moveTag(tagId: String, newParentId: String?): Boolean
    suspend fun moveTagInOrder(tagId: String, direction: Int)
    suspend fun softDeleteTag(tagId: String)

    suspend fun updateTagPlaceConfig(tagId: String, isPlaceTag: Boolean, triggerMode: PlaceTriggerMode)
    suspend fun addTagPlace(
        tagId: String,
        label: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 150
    ): TagPlace
    suspend fun updateTagPlace(place: TagPlace)
    suspend fun deleteTagPlace(placeId: String)
}
