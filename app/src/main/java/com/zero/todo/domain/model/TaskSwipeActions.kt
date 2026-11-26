package com.zero.todo.domain.model

import androidx.annotation.StringRes
import com.zero.todo.R

enum class TaskSwipeDirection(@StringRes val labelRes: Int) {
    LEFT(R.string.settings_swipe_left),
    RIGHT(R.string.settings_swipe_right)
}

enum class TaskSwipeAction(@StringRes val labelRes: Int) {
    COMPLETE(R.string.task_swipe_complete),
    DELETE(R.string.action_delete),
    NONE(R.string.task_swipe_none);

    companion object {
        fun fromStorage(raw: String?): TaskSwipeAction =
            when (raw) {
                "MAKE_SUBTASK" -> NONE
                else -> entries.firstOrNull { it.name == raw } ?: COMPLETE
            }
    }
}

data class TaskSwipeActions(
    val left: TaskSwipeAction = TaskSwipeAction.COMPLETE,
    val right: TaskSwipeAction = TaskSwipeAction.COMPLETE
) {
    val hasEnabledAction: Boolean
        get() = left != TaskSwipeAction.NONE || right != TaskSwipeAction.NONE

    fun actionFor(direction: TaskSwipeDirection): TaskSwipeAction = when (direction) {
        TaskSwipeDirection.LEFT -> left
        TaskSwipeDirection.RIGHT -> right
    }

    fun withAction(direction: TaskSwipeDirection, action: TaskSwipeAction): TaskSwipeActions = when (direction) {
        TaskSwipeDirection.LEFT -> copy(left = action)
        TaskSwipeDirection.RIGHT -> copy(right = action)
    }

    fun actionForOffset(offset: Float): TaskSwipeAction = when {
        offset < 0f -> left
        offset > 0f -> right
        else -> TaskSwipeAction.NONE
    }
}
