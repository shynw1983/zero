package com.zero.todo.feature.tasklist

import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskSwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskListHierarchyTest {
    @Test
    fun `short drag uses the task directly above and a drop before the first child inherits its parent`() {
        val first = TaskItem(id = "1", title = "Task 1")
        val second = TaskItem(id = "2", title = "Task 2")
        val third = TaskItem(id = "3", title = "Task 3")
        val original = listOf(first, second, third)
        val rows = buildTaskListRows(original, original.associateBy { it.id }, true)
        assertNull(rows.first().indentParentId)
        assertEquals(first.id, rows[1].indentParentId)

        val nested = listOf(first, second.copy(parentTaskId = first.id), third)
        val nestedRows = buildTaskListRows(listOf(first, third), nested.associateBy { it.id }, true)
        assertEquals(listOf("1", "2", "3"), nestedRows.map { it.task.id })
        assertEquals(listOf(0, 1, 0), nestedRows.map { it.depth })
        assertNull(nestedRows[1].indentParentId)
        assertEquals(TaskDropSlot("2", "1", 1), taskDropSlot(nestedRows[1]))
        assertEquals(TaskDropSlot("3", null, 0), taskDropSlot(nestedRows[2]))
    }

    @Test
    fun `right indent and configured swipe are mutually exclusive at release`() {
        assertEquals(TaskCardSwipeStage.BELOW_THRESHOLD, taskCardSwipeStage(15f, 100f, canIndent = true))
        assertEquals(TaskCardSwipeStage.INDENT, taskCardSwipeStage(16f, 100f, canIndent = true))
        assertEquals(TaskCardSwipeStage.INDENT, taskCardSwipeStage(29f, 100f, canIndent = true))
        assertEquals(TaskCardSwipeStage.FULL, taskCardSwipeStage(31f, 100f, canIndent = true))
        assertEquals(TaskCardSwipeStage.BELOW_THRESHOLD, taskCardSwipeStage(22f, 100f, canIndent = false))
        assertEquals(TaskCardSwipeStage.BELOW_THRESHOLD, taskCardSwipeStage(-22f, 100f, canIndent = true))
        assertEquals(TaskCardSwipeStage.FULL, taskCardSwipeStage(-31f, 100f, canIndent = true))
        assertEquals(TaskSwipeAction.NONE, TaskSwipeAction.fromStorage("MAKE_SUBTASK"))
    }
}
