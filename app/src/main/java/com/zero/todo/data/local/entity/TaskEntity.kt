package com.zero.todo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("folderId"), Index("parentTaskId"), Index("dueDate"), Index("isDeleted")]
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val folderId: String? = null, // null = Inbox
    val parentTaskId: String? = null, // non-null = Subtask
    val title: String,
    val description: String = "", // Markdown supported
    val priority: Int = 4, // 1=Urgent, 2=High, 3=Medium, 4=None
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: Long? = null, // Epoch millis
    val isAllDay: Boolean = true, // Lightweight "date only" mode
    val reminderAt: Long? = null,
    val recurrenceRule: String? = null, // RFC 5545 RRULE string
    val firstStep: String? = null, // Ultra-light <=20 char icebreaker action
    val firstStepCompleted: Boolean = false,
    val estimatedMinutes: Int? = null, // Effort tier (5m / 25m / 60m) for Day Budget ruler
    val sortOrder: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
