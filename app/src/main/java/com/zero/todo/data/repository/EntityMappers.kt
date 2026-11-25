package com.zero.todo.data.repository

import com.zero.todo.data.local.entity.FocusSessionEntity
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskWithTags
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FocusSession
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    parentId = parentId,
    name = name,
    icon = icon,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isExpanded = isExpanded,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    parentId = parentId,
    name = name,
    icon = icon,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isExpanded = isExpanded,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun TagEntity.toDomain(): TagItem = TagItem(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun TaskEntity.toDomain(tags: List<TagItem> = emptyList()): TaskItem = TaskItem(
    id = id,
    folderId = folderId,
    parentTaskId = parentTaskId,
    title = title,
    description = description,
    priority = TaskPriority.fromInt(priority),
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueDate = dueDate,
    isAllDay = isAllDay,
    reminderAt = reminderAt,
    recurrenceRule = recurrenceRule,
    firstStep = firstStep,
    firstStepCompleted = firstStepCompleted,
    estimatedMinutes = estimatedMinutes,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    tags = tags
)

fun TaskWithTags.toDomain(): TaskItem = task.toDomain(
    tags = tags.filterNot { it.isDeleted }.map { it.toDomain() }
)

fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    folderId = folderId,
    parentTaskId = parentTaskId,
    title = title,
    description = description,
    priority = priority.value,
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueDate = dueDate,
    isAllDay = isAllDay,
    reminderAt = reminderAt,
    recurrenceRule = recurrenceRule,
    firstStep = firstStep,
    firstStepCompleted = firstStepCompleted,
    estimatedMinutes = estimatedMinutes,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id = id,
    taskId = taskId,
    durationSeconds = durationSeconds,
    mode = FocusMode.fromStorage(mode),
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)
