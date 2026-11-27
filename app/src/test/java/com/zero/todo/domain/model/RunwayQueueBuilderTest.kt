package com.zero.todo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RunwayQueueBuilderTest {

    private val todayMillis = LocalDate.of(2026, 11, 27).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private val tomorrowMillis = LocalDate.of(2026, 11, 28).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private val nextWeekMillis = LocalDate.of(2026, 12, 4).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun containerWithIncompleteSubtasksIsBlockedWhileUndatedChildrenEnterInParallel() {
        val parent = TaskItem(
            id = "p1",
            title = "仕訳資料送付",
            folderId = "f_keiri",
            priority = TaskPriority.URGENT,
            dueDate = todayMillis,
            estimatedMinutes = 25
        )
        val subBuy = TaskItem(
            id = "s1",
            parentTaskId = "p1",
            title = "レターパック購入",
            folderId = "f_keiri",
            estimatedMinutes = 15
        )
        val subSort = TaskItem(
            id = "s2",
            parentTaskId = "p1",
            title = "領収書整理",
            folderId = "f_keiri",
            estimatedMinutes = 25
        )

        val result = RunwayQueueBuilder.build(
            allActiveTasks = listOf(parent, subBuy, subSort),
            tomorrowStartMillis = tomorrowMillis,
            pinnedTaskId = null,
            skippedIds = emptyList()
        )

        assertEquals(listOf("s1", "s2"), result.runwayQueue.map { it.id })
        // Only leaves (15 + 25 = 40), parent container's 25m is not double-counted
        assertEquals(40, result.scheduledTodayMinutes)
        assertEquals("仕訳資料送付", result.parentTitleByTaskId["s1"])
        assertEquals("仕訳資料送付", result.parentTitleByTaskId["s2"])
    }

    @Test
    fun subtaskScheduledInFutureStaysOutWhileSiblingEnters() {
        val parent = TaskItem(
            id = "p1",
            title = "Send documents",
            dueDate = todayMillis
        )
        val subToday = TaskItem(
            id = "s_today",
            parentTaskId = "p1",
            title = "Buy envelope"
        )
        val subFuture = TaskItem(
            id = "s_future",
            parentTaskId = "p1",
            title = "Double check",
            dueDate = tomorrowMillis
        )

        val result = RunwayQueueBuilder.build(
            allActiveTasks = listOf(parent, subToday, subFuture),
            tomorrowStartMillis = tomorrowMillis,
            pinnedTaskId = null,
            skippedIds = emptyList()
        )

        assertEquals(listOf("s_today"), result.runwayQueue.map { it.id })
    }

    @Test
    fun completingAllSubtasksUnlocksParentIntoRunway() {
        val parent = TaskItem(
            id = "p1",
            title = "仕訳資料送付",
            dueDate = todayMillis,
            estimatedMinutes = 25
        )
        val subDone1 = TaskItem(
            id = "s1",
            parentTaskId = "p1",
            title = "レターパック購入",
            isCompleted = true
        )
        val subDone2 = TaskItem(
            id = "s2",
            parentTaskId = "p1",
            title = "領収書整理",
            isCompleted = true
        )

        val result = RunwayQueueBuilder.build(
            allActiveTasks = listOf(parent, subDone1, subDone2),
            tomorrowStartMillis = tomorrowMillis,
            pinnedTaskId = null,
            skippedIds = emptyList()
        )

        assertEquals(listOf("p1"), result.runwayQueue.map { it.id })
        assertEquals(25, result.scheduledTodayMinutes)
    }

    @Test
    fun movingParentToFutureDateRemovesUndatedSubtasksFromTodayRunway() {
        val parent = TaskItem(
            id = "p1",
            title = "Send tax pack",
            dueDate = nextWeekMillis
        )
        val subUndated = TaskItem(
            id = "s1",
            parentTaskId = "p1",
            title = "Buy envelope"
        )
        val standalone = TaskItem(
            id = "standalone",
            title = "Drink water",
            dueDate = todayMillis
        )

        val result = RunwayQueueBuilder.build(
            allActiveTasks = listOf(parent, subUndated, standalone),
            tomorrowStartMillis = tomorrowMillis,
            pinnedTaskId = null,
            skippedIds = emptyList()
        )

        assertEquals(listOf("standalone"), result.runwayQueue.map { it.id })
    }

    @Test
    fun subtaskInheritsParentPriorityWhenOwnIsNoneAndCyclesTerminateSafely() {
        val urgentParent = TaskItem(
            id = "p_urgent",
            title = "Urgent Project",
            priority = TaskPriority.URGENT,
            dueDate = todayMillis
        )
        val undatedSub = TaskItem(
            id = "s_inherited",
            parentTaskId = "p_urgent",
            title = "Predecessor",
            priority = TaskPriority.NONE
        )
        val lowTask = TaskItem(
            id = "t_low",
            title = "Low Priority",
            priority = TaskPriority.MEDIUM,
            dueDate = todayMillis
        )
        val cycA = TaskItem(id = "ca", parentTaskId = "cb", title = "Cycle A")
        val cycB = TaskItem(id = "cb", parentTaskId = "ca", title = "Cycle B")

        val result = RunwayQueueBuilder.build(
            allActiveTasks = listOf(lowTask, urgentParent, undatedSub, cycA, cycB),
            tomorrowStartMillis = tomorrowMillis,
            pinnedTaskId = null,
            skippedIds = emptyList()
        )

        assertEquals("s_inherited", result.runwayQueue.first().id)
        assertFalse(result.runwayQueue.any { it.id == "p_urgent" })
        assertTrue(result.runwayQueue.any { it.id == "t_low" })
    }
}
