package com.zero.todo.feature.tasklist

import com.zero.todo.domain.model.TaskItem

internal data class TaskListRow(
    val task: TaskItem,
    val depth: Int,
    val indentParentId: String?
)

internal fun buildTaskListRows(
    tasks: List<TaskItem>,
    allTasksById: Map<String, TaskItem>,
    includeDescendants: Boolean
): List<TaskListRow> {
    val taskIds = tasks.mapTo(hashSetOf()) { it.id }
    val roots = if (includeDescendants) tasks else tasks.filter { it.parentTaskId !in taskIds }
    val visibleChildren = (if (includeDescendants) allTasksById.values else tasks).groupBy { it.parentTaskId }
    val visited = hashSetOf<String>()
    val pending = ArrayDeque<Pair<TaskItem, Int>>()

    return buildList<TaskListRow> {
        for (root in roots + tasks) {
            if (root.id in visited) continue
            pending.addLast(root to 0)
            while (pending.isNotEmpty()) {
                val (task, depth) = pending.removeLast()
                if (!visited.add(task.id)) continue
                val previous = lastOrNull()?.task
                val indentParent = previous?.takeIf {
                    it.id != task.parentTaskId && !isTaskDescendantOf(it, task.id, allTasksById)
                }
                add(TaskListRow(task, depth, indentParent?.id))
                visibleChildren[task.id].orEmpty().asReversed().forEach { child ->
                    pending.addLast(child to depth + 1)
                }
            }
        }
    }
}

/** A slot immediately before a visible child belongs to that child's parent. */
internal data class TaskDropSlot(val beforeTaskId: String?, val parentTaskId: String?, val depth: Int)

internal fun taskDropSlot(before: TaskListRow?): TaskDropSlot = TaskDropSlot(
    beforeTaskId = before?.task?.id,
    parentTaskId = before?.task?.parentTaskId,
    depth = before?.depth ?: 0
)

internal fun isTaskDescendantOf(task: TaskItem, ancestorId: String, allTasksById: Map<String, TaskItem>): Boolean {
    val visited = hashSetOf<String>()
    var parent = task.parentTaskId
    while (parent != null && visited.add(parent)) {
        if (parent == ancestorId) return true
        parent = allTasksById[parent]?.parentTaskId
    }
    return false
}

internal const val FULL_TASK_SWIPE_FRACTION = 0.30f
private const val TASK_INDENT_FRACTION = 0.16f

internal enum class TaskCardSwipeStage { BELOW_THRESHOLD, INDENT, FULL }

internal fun taskCardSwipeStage(offset: Float, width: Float, canIndent: Boolean): TaskCardSwipeStage = when {
    width <= 0f -> TaskCardSwipeStage.BELOW_THRESHOLD
    kotlin.math.abs(offset) >= width * FULL_TASK_SWIPE_FRACTION -> TaskCardSwipeStage.FULL
    canIndent && offset >= width * TASK_INDENT_FRACTION -> TaskCardSwipeStage.INDENT
    else -> TaskCardSwipeStage.BELOW_THRESHOLD
}
