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
                val previous = lastOrNull()
                val candidateParentId = if (previous?.task?.parentTaskId != null &&
                    (task.parentTaskId == null || previous.depth > depth)
                ) {
                    previous.task.parentTaskId
                } else {
                    previous?.task?.id
                }
                val indentParentId = candidateParentId?.takeIf { id ->
                    id != task.id && id != task.parentTaskId &&
                        allTasksById[id]?.let { !isTaskDescendantOf(it, task.id, allTasksById) } == true
                }
                add(TaskListRow(task, depth, indentParentId))
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

internal data class TaskDropPosition(val taskId: String, val centerY: Float)

internal class TaskDragTargets(
    rows: List<TaskListRow>,
    movingId: String,
    allTasksById: Map<String, TaskItem>
) {
    private val allowed = rows.filter { it.task.id != movingId && !isTaskDescendantOf(it.task, movingId, allTasksById) }
    private val indexes = allowed.mapIndexed { index, row -> row.task.id to index }.toMap()
    private val originalFollowing = rows.drop(rows.indexOfFirst { it.task.id == movingId } + 1)
        .firstOrNull { it.task.id in indexes }?.task?.id
    val lastId: String? get() = allowed.lastOrNull()?.task?.id

    fun slotAt(centerY: Float, positions: List<TaskDropPosition>): TaskDropSlot? {
        val visible = positions.filter { it.taskId in indexes }.sortedBy { it.centerY }
        val last = visible.lastOrNull() ?: return null
        val next = visible.firstOrNull { centerY < it.centerY }
        val index = if (next != null) indexes.getValue(next.taskId) else indexes.getValue(last.taskId) + 1
        val before = allowed.getOrNull(index)
        return if (before?.task?.id == originalFollowing) null else taskDropSlot(before)
    }
}

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

internal enum class TaskCardSwipeStage { BELOW_THRESHOLD, INDENT, OUTDENT, FULL }

internal fun taskCardSwipeStage(offset: Float, width: Float, canIndent: Boolean, canOutdent: Boolean = false): TaskCardSwipeStage = when {
    width <= 0f -> TaskCardSwipeStage.BELOW_THRESHOLD
    kotlin.math.abs(offset) >= width * FULL_TASK_SWIPE_FRACTION -> TaskCardSwipeStage.FULL
    canIndent && offset >= width * TASK_INDENT_FRACTION -> TaskCardSwipeStage.INDENT
    canOutdent && offset <= -width * TASK_INDENT_FRACTION -> TaskCardSwipeStage.OUTDENT
    else -> TaskCardSwipeStage.BELOW_THRESHOLD
}
