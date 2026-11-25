package com.zero.todo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("taskId"), Index("completedAt"), Index("isDeleted")]
)
data class FocusSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String?,
    val durationSeconds: Int,
    val mode: String, // "POMODORO", "TWO_MIN_STARTER", "STOPWATCH"
    val completedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = completedAt,
    val updatedAt: Long = completedAt,
    val isDeleted: Boolean = false
)
