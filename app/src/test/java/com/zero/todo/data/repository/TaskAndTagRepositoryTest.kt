package com.zero.todo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskAndTagRepositoryTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderRepository = FolderRepositoryImpl(database, database.folderDao(), database.taskDao())
        taskRepository = TaskRepositoryImpl(database, database.taskDao(), database.folderDao())
        tagRepository = TagRepositoryImpl(database.tagDao())
        focusRepository = FocusRepositoryImpl(database.focusSessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `task ordering, completion undo, and subtask cascading soft delete work cleanly`() = runTest {
        val t1 = taskRepository.createTask("First", priority = TaskPriority.URGENT)
        val t2 = taskRepository.createTask("Second", priority = TaskPriority.HIGH)
        val t3 = taskRepository.createTask("Third")
        val sub = taskRepository.createTask("Subtask of First", parentTaskId = t1.id)

        // Reorder Third before First
        taskRepository.reorderTask(
            taskId = t3.id,
            targetFolderId = null,
            beforeTaskId = null,
            afterTaskId = t1.id
        )
        assertEquals(
            listOf("Third", "First", "Second"),
            taskRepository.observeInboxTasks().first().map { it.title }
        )

        // Complete First -> moves after incomplete tasks & records completedAt
        taskRepository.setTaskCompleted(t1.id, isCompleted = true)
        val completed = taskRepository.getTaskById(t1.id)!!
        assertTrue(completed.isCompleted)
        assertNotNull(completed.completedAt)
        assertEquals(
            listOf("Third", "Second", "First"),
            taskRepository.observeInboxTasks().first().map { it.title }
        )

        // Undo completion -> restores original order
        taskRepository.setTaskCompleted(t1.id, isCompleted = false)
        assertFalse(taskRepository.getTaskById(t1.id)!!.isCompleted)
        assertNull(taskRepository.getTaskById(t1.id)!!.completedAt)
        assertEquals(
            listOf("Third", "First", "Second"),
            taskRepository.observeInboxTasks().first().map { it.title }
        )

        // Soft delete First cascades to its subtask
        taskRepository.softDeleteTask(t1.id)
        assertNull(taskRepository.getTaskById(t1.id))
        assertNull(taskRepository.getTaskById(sub.id))
        assertTrue(taskRepository.observeSubtasks(t1.id).first().isEmpty())

        // Restore First also restores its subtask
        taskRepository.restoreTask(t1.id)
        assertNotNull(taskRepository.getTaskById(t1.id))
        assertEquals(listOf(sub.id), taskRepository.observeSubtasks(t1.id).first().map { it.id })
    }

    @Test
    fun `tags deduplicate by case-insensitive name, attach to tasks, and filter soft-deleted tags`() = runTest {
        val work = folderRepository.createFolder("Work")
        val task = taskRepository.createTask("Prepare Q4 deck", folderId = work.id)

        val tag1 = tagRepository.getOrCreateTag("Urgent", "#FF4D00")
        val tag1Duplicate = tagRepository.getOrCreateTag("urgent")
        val tag2 = tagRepository.getOrCreateTag("Design")

        assertEquals(tag1.id, tag1Duplicate.id)

        tagRepository.attachTagToTask(task.id, tag1.id)
        tagRepository.attachTagToTask(task.id, tag2.id)

        val loadedWithTags = taskRepository.observeTaskWithTags(task.id).first()!!
        assertEquals(setOf("Urgent", "Design"), loadedWithTags.tags.map { it.name }.toSet())

        val tasksWithUrgent = tagRepository.observeTasksByTag(tag1.id).first()
        assertEquals(listOf(task.id), tasksWithUrgent.map { it.id })

        // Soft deleting tag hides it from active tag queries and task-with-tags mapping
        tagRepository.softDeleteTag(tag2.id)
        val afterDelete = taskRepository.observeTaskWithTags(task.id).first()!!
        assertEquals(listOf("Urgent"), afterDelete.tags.map { it.name })
    }

    @Test
    fun `focus sessions record Pomodoro and 2-Minute Starter runs and aggregate totals`() = runTest {
        val task = taskRepository.createTask("Write architecture doc")
        val baseTime = 1_700_000_000_000L

        focusRepository.recordSession(
            taskId = task.id,
            durationSeconds = 120,
            mode = FocusMode.TWO_MIN_STARTER,
            completedAt = baseTime + 1_000L
        )
        focusRepository.recordSession(
            taskId = task.id,
            durationSeconds = 1500,
            mode = FocusMode.POMODORO,
            completedAt = baseTime + 2_000L
        )

        val sessions = focusRepository.observeSessionsForTask(task.id).first()
        assertEquals(2, sessions.size)
        assertEquals(FocusMode.POMODORO, sessions.first().mode)

        val totalSeconds = focusRepository.observeTotalFocusSecondsBetween(
            startMillis = baseTime,
            endMillis = baseTime + 10_000L
        ).first()
        assertEquals(1620, totalSeconds)
    }

    @Test
    fun `moving a task under a parent preserves its attributes and prevents cycles`() = runTest {
        val folder = folderRepository.createFolder("Personal")
        val parent = taskRepository.createTask("Holiday")
        val task = taskRepository.createTask(
            title = "Book hotel", folderId = folder.id, description = "Compare prices", priority = TaskPriority.HIGH,
            dueDate = 2_000_000L, isAllDay = false, reminderAt = 1_900_000L, recurrenceRule = "FREQ=WEEKLY",
            firstStep = "Check dates", estimatedMinutes = 25
        )
        val child = taskRepository.createTask("Check availability", parentTaskId = task.id)
        val tag = tagRepository.getOrCreateTag("Travel")
        tagRepository.attachTagToTask(task.id, tag.id)
        val before = taskRepository.getTaskById(task.id)!!

        assertFalse(taskRepository.moveTaskUnderParent(task.id, task.id))
        assertFalse(taskRepository.moveTaskUnderParent(task.id, child.id))
        assertTrue(taskRepository.moveTaskUnderParent(task.id, parent.id))
        assertFalse(taskRepository.moveTaskUnderParent(parent.id, child.id))
        val nested = taskRepository.getTaskById(task.id)!!

        assertEquals(before.copy(parentTaskId = parent.id, sortOrder = nested.sortOrder, updatedAt = nested.updatedAt), nested)
        assertEquals(task.id, taskRepository.getTaskById(child.id)?.parentTaskId)
        assertEquals(folder.id, taskRepository.promoteSubtaskToTopLevel(task.id)?.folderId)
        assertEquals(task.id, taskRepository.getTaskById(child.id)?.parentTaskId)
    }

    @Test
    fun `infinite nested subtasks and one-click promoteSubtaskToTopLevel preserve parent folder`() = runTest {
        val work = folderRepository.createFolder("Work")
        val rootTask = taskRepository.createTask("Launch ZERO v1", folderId = work.id)
        val subL1 = taskRepository.createTask("Build Phase 3", parentTaskId = rootTask.id)
        val subL2 = taskRepository.createTask("Add Glance widgets", parentTaskId = subL1.id)
        val subL3 = taskRepository.createTask("Verify 1x1 capsule", parentTaskId = subL2.id)

        val descendants = taskRepository.observeDescendantSubtasks(rootTask.id).first()
        assertEquals(listOf(subL1.id, subL2.id, subL3.id), descendants.map { it.id })

        // Promote deep L3 subtask to top-level task
        val promoted = taskRepository.promoteSubtaskToTopLevel(subL3.id)
        assertNotNull(promoted)
        assertNull(promoted!!.parentTaskId)
        assertEquals(work.id, promoted.folderId)

        // Verify promoted task now appears in Work folder top-level tasks and is no longer a descendant
        val workTasks = taskRepository.observeTasksByFolder(work.id, includeSubfolders = false).first()
        assertEquals(setOf(rootTask.id, subL3.id), workTasks.map { it.id }.toSet())
        val remainingDescendants = taskRepository.observeDescendantSubtasks(rootTask.id).first()
        assertEquals(listOf(subL1.id, subL2.id), remainingDescendants.map { it.id })
    }
}
