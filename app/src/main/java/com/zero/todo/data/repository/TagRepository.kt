package com.zero.todo.data.repository

import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeActiveTags(): Flow<List<TagItem>>
    fun observeTagsForTask(taskId: String): Flow<List<TagItem>>
    fun observeTasksByTag(tagId: String): Flow<List<TaskItem>>

    suspend fun getOrCreateTag(name: String, colorHex: String? = null): TagItem
    suspend fun attachTagToTask(taskId: String, tagId: String)
    suspend fun detachTagFromTask(taskId: String, tagId: String)
    suspend fun softDeleteTag(tagId: String)
}
