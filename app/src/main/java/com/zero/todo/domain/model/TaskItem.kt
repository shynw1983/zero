package com.zero.todo.domain.model

enum class TaskPriority(val value: Int) {
    URGENT(1),
    HIGH(2),
    MEDIUM(3),
    NONE(4);

    companion object {
        fun fromInt(value: Int): TaskPriority =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}

data class TaskItem(
    val id: String,
    val folderId: String? = null,
    val parentTaskId: String? = null,
    val title: String,
    val description: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: Long? = null,
    val isAllDay: Boolean = true,
    val reminderAt: Long? = null,
    val recurrenceRule: String? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val tags: List<TagItem> = emptyList()
)

data class TagItem(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

enum class FocusMode(val storageValue: String) {
    POMODORO("POMODORO"),
    TWO_MIN_STARTER("TWO_MIN_STARTER"),
    STOPWATCH("STOPWATCH");

    companion object {
        fun fromStorage(value: String): FocusMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: POMODORO
    }
}

data class FocusSession(
    val id: String,
    val taskId: String?,
    val durationSeconds: Int,
    val mode: FocusMode,
    val completedAt: Long,
    val createdAt: Long = completedAt,
    val updatedAt: Long = completedAt,
    val isDeleted: Boolean = false
)
