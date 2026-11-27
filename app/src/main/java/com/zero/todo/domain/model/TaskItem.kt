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
    val firstStep: String? = null,
    val firstStepCompleted: Boolean = false,
    val estimatedMinutes: Int? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val placeTriggerOverride: PlaceTriggerMode? = null,
    val tags: List<TagItem> = emptyList()
)

enum class PlaceTriggerMode(val storageValue: String) {
    ENTER("ENTER"),
    EXIT("EXIT"),
    BOTH("BOTH");

    companion object {
        fun fromStorage(raw: String?): PlaceTriggerMode =
            entries.firstOrNull { it.storageValue.equals(raw, ignoreCase = true) } ?: ENTER

        fun fromStorageOrNull(raw: String?): PlaceTriggerMode? =
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { clean ->
                entries.firstOrNull { it.storageValue.equals(clean, ignoreCase = true) }
            }
    }
}

data class TagPlace(
    val id: String,
    val tagId: String,
    val label: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 150,
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class TagItem(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val colorHex: String? = null,
    val isExpanded: Boolean = true,
    val isPlaceTag: Boolean = false,
    val placeTriggerMode: PlaceTriggerMode = PlaceTriggerMode.ENTER,
    val places: List<TagPlace> = emptyList(),
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
