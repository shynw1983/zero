package com.zero.todo.feature.main

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.nlp.QuickInputParser
import com.zero.todo.core.notification.NoOpReminderScheduler
import com.zero.todo.data.backup.WebDavSyncManager
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.preferences.InMemoryUserPreferencesRepository
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.WorkspaceMode
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.tasklist.UndoType
import com.zero.todo.widget.NoOpWidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelStateTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl
    private lateinit var preferencesRepository: InMemoryUserPreferencesRepository
    private lateinit var backupManager: ZeroBackupManager
    private lateinit var webDavSyncManager: WebDavSyncManager
    private lateinit var getFolderTreeUseCase: GetFolderTreeUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderRepository = FolderRepositoryImpl(database, database.folderDao(), database.taskDao())
        taskRepository = TaskRepositoryImpl(database, database.taskDao(), database.folderDao())
        tagRepository = TagRepositoryImpl(database.tagDao())
        focusRepository = FocusRepositoryImpl(database.focusSessionDao())
        preferencesRepository = InMemoryUserPreferencesRepository()
        backupManager = ZeroBackupManager(
            database = database,
            folderDao = database.folderDao(),
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            focusSessionDao = database.focusSessionDao()
        )
        webDavSyncManager = WebDavSyncManager()
        getFolderTreeUseCase = GetFolderTreeUseCase(folderRepository, taskRepository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): MainViewModel =
        MainViewModel(
            savedStateHandle = savedStateHandle,
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = preferencesRepository,
            backupManager = backupManager,
            webDavSyncManager = webDavSyncManager,
            reminderScheduler = NoOpReminderScheduler,
            widgetRefresher = NoOpWidgetRefresher,
            getFolderTreeUseCase = getFolderTreeUseCase
        )

    @Test
    fun `SavedStateHandle preserves selected folder, selected task, includeSubfolders, and quick input across recreation`() = runBlocking {
        val folder = folderRepository.createFolder("Work")
        val task = taskRepository.createTask("Draft spec", folderId = folder.id)

        val savedStateHandle = SavedStateHandle()
        val vm1 = newViewModel(savedStateHandle)
        val collectJob1 = launch(Dispatchers.Default) { vm1.uiState.collect {} }

        vm1.selectDestination(NavigationDestination.FolderView(folder.id))
        vm1.selectTask(task.id)
        vm1.toggleIncludeSubfolders()
        vm1.updateQuickInputText("明天下午3点 /Work 审核原型 !1 #设计")
        withTimeout(5_000L) {
            vm1.uiState.first {
                it.selectedTask?.id == task.id &&
                    it.includeSubfolders &&
                    it.quickInputText.isNotEmpty()
            }
        }
        collectJob1.cancel()

        val restoredHandle = SavedStateHandle(
            savedStateHandle.keys().associateWith { key -> savedStateHandle.get<Any?>(key) }
        )
        val vm2 = newViewModel(restoredHandle)
        val collectJob2 = launch(Dispatchers.Default) { vm2.uiState.collect {} }
        val state = withTimeout(5_000L) {
            vm2.uiState.first { it.selectedTask?.id == task.id }
        }
        assertEquals(NavigationDestination.FolderView(folder.id), state.selectedDestination)
        assertEquals(task.id, state.selectedTaskId)
        assertEquals("Draft spec", state.selectedTask?.title)
        assertTrue(state.includeSubfolders)
        assertEquals("明天下午3点 /Work 审核原型 !1 #设计", state.quickInputText)
        assertEquals(TaskPriority.URGENT, state.parsedQuickInput.priority)
        collectJob2.cancel()
    }

    @Test
    fun `rapid quick input shortcuts edit the latest draft and submit a clean task`() = runBlocking {
        val archive = folderRepository.createFolder("Archive")
        val work = folderRepository.createFolder("Work")
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }
        withTimeout(5_000L) {
            vm.uiState.first { work.id in it.folderNameById }
        }

        vm.updateQuickInputText("Review draft /${archive.name} !2")
        repeat(3) {
            vm.editQuickInputText(QuickInputParser::startFolderQuery)
            vm.editQuickInputText { QuickInputParser.setPriorityShortcut(it, TaskPriority.URGENT) }
        }
        repeat(2) {
            vm.editQuickInputText { QuickInputParser.selectFolderSuggestion(it, work.name) }
        }
        vm.submitQuickInput()

        val task = withTimeout(5_000L) {
            taskRepository.observeTasksByFolder(work.id, includeSubfolders = false)
                .first { it.isNotEmpty() }.single()
        }
        assertEquals("Review draft", task.title)
        assertEquals(TaskPriority.URGENT, task.priority)
        collectJob.cancel()
    }

    @Test
    fun `submitQuickInput routes into nested folder path, attaches tags, and undo reverts completion`() = runBlocking {
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.updateQuickInputText("明天 15:00 提交报销 /财务/差旅 !1 #紧急 #票据")
        vm.submitQuickInput()

        assertEquals("", vm.uiState.value.quickInputText)

        val folders = withTimeout(5_000L) {
            folderRepository.observeActiveFolders().first { list ->
                list.any { it.name == "财务" && it.parentId == null } &&
                    list.any { it.name == "差旅" }
            }
        }
        val finance = folders.first { it.name == "财务" && it.parentId == null }
        val travel = folders.first { it.name == "差旅" && it.parentId == finance.id }

        val tasksInTravel = withTimeout(5_000L) {
            taskRepository.observeTasksByFolder(travel.id, includeSubfolders = false)
                .first { it.size == 1 }
        }
        val createdTask = withTimeout(5_000L) {
            taskRepository.observeTaskWithTags(tasksInTravel.single().id)
                .filterNotNull()
                .first { it.tags.size == 2 }
        }
        assertEquals("提交报销", createdTask.title)
        assertEquals(TaskPriority.URGENT, createdTask.priority)
        assertNotNull(createdTask.dueDate)
        assertFalse(createdTask.isAllDay)
        assertNotNull(createdTask.reminderAt)
        assertEquals(setOf("紧急", "票据"), createdTask.tags.map { it.name }.toSet())

        vm.toggleTaskCompletion(createdTask)
        val banner = withTimeout(5_000L) {
            vm.uiState.filter { it.undoBanner != null }.first().undoBanner!!
        }
        withTimeout(5_000L) {
            taskRepository.observeTaskWithTags(createdTask.id)
                .filterNotNull()
                .first { it.isCompleted }
        }

        vm.performUndo(banner)
        val reverted = withTimeout(5_000L) {
            taskRepository.observeTaskWithTags(createdTask.id)
                .filterNotNull()
                .first { !it.isCompleted }
        }
        assertFalse(reverted.isCompleted)
        collectJob.cancel()
    }

    @Test
    fun `Today view surfaces overdue tasks and rescheduleOverdueToToday moves them to today`() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now()
        val overdue = taskRepository.createTask(
            title = "Pay rent",
            dueDate = ZeroDateFormatter.startOfDayMillis(yesterday),
            isAllDay = true
        )
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.selectDestination(NavigationDestination.Today)
        val stateBefore = withTimeout(5_000L) {
            vm.uiState.first {
                it.selectedDestination == NavigationDestination.Today &&
                    it.tasks.any { t -> t.id == overdue.id } &&
                    it.smartViewCounts.overdue == 1
            }
        }
        assertEquals(1, stateBefore.smartViewCounts.overdue)

        vm.rescheduleOverdueToToday()
        val stateAfter = withTimeout(5_000L) {
            vm.uiState.first {
                it.smartViewCounts.overdue == 0 &&
                    it.tasks.any { t -> t.id == overdue.id && t.dueDate == ZeroDateFormatter.startOfDayMillis(today) }
            }
        }
        assertEquals(0, stateAfter.smartViewCounts.overdue)
        collectJob.cancel()
    }

    @Test
    fun `task moved with the picker keeps full details and appears on its own due date`() = runBlocking {
        val parent = taskRepository.createTask("Trip")
        val original = taskRepository.createTask("Book hotel")
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.openTaskParentPicker(original)
        vm.chooseTaskParent(parent.id)
        withTimeout(5_000L) { vm.taskParentPicker.first { it == null } }
        vm.selectTask(original.id)
        vm.selectDestination(NavigationDestination.Today)
        val due = ZeroDateFormatter.startOfDayMillis(LocalDate.now())
        vm.updateTaskDueDate(original, due, isAllDay = true)

        withTimeout(5_000L) {
            vm.uiState.first {
                it.selectedTask?.parentTaskId == parent.id && it.selectedTask.dueDate == due &&
                    it.selectedDestination == NavigationDestination.Today && it.tasks.any { task -> task.id == original.id } &&
                    it.runwayQueue.any { task -> task.id == original.id } &&
                    it.allActiveTasks.any { task -> task.id == original.id && task.dueDate == due }
            }
        }
        collectJob.cancel()
    }

    @Test
    fun `completing recurring task copies tags and subtasks, and Undo rolls back spawned occurrence`() = runBlocking {
        val parent = taskRepository.createTask("Household")
        val today = LocalDate.now()
        val original = taskRepository.createTask(
            title = "Weekly review",
            parentTaskId = parent.id,
            dueDate = ZeroDateFormatter.startOfDayMillis(today),
            recurrenceRule = RecurrenceRule.PRESET_WEEKLY.toRruleString()
        )
        val tag = tagRepository.getOrCreateTag("Ritual")
        tagRepository.attachTagToTask(original.id, tag.id)
        val child = taskRepository.createTask(
            title = "Clean Inbox", parentTaskId = original.id,
            dueDate = ZeroDateFormatter.startOfDayMillis(today.plusDays(1)), firstStep = "Open Inbox"
        )
        tagRepository.attachTagToTask(child.id, tag.id)

        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        val hydrated = taskRepository.getTaskById(original.id)!!
        vm.toggleTaskCompletion(hydrated)

        val banner = withTimeout(5_000L) {
            vm.uiState.filter { it.undoBanner?.spawnedTaskId != null }.first().undoBanner!!
        }
        val spawnedId = banner.spawnedTaskId!!
        val spawned = withTimeout(5_000L) {
            taskRepository.observeTaskWithTags(spawnedId).filterNotNull().first { it.tags.size == 1 }
        }
        assertEquals("Ritual", spawned.tags.single().name)
        assertEquals(parent.id, spawned.parentTaskId)
        val spawnedSubtasks = withTimeout(5_000L) {
            taskRepository.observeDescendantSubtasks(spawnedId).first { it.size == 1 }
        }
        val copiedChild = taskRepository.getTaskById(spawnedSubtasks.single().id)!!
        assertEquals("Clean Inbox", copiedChild.title)
        assertEquals("Open Inbox", copiedChild.firstStep)
        assertEquals(listOf("Ritual"), copiedChild.tags.map { it.name })
        assertEquals(
            ZeroDateFormatter.startOfDayMillis(today.plusDays(8)),
            copiedChild.dueDate
        )

        // Performing Undo reverts original AND deletes spawned duplicate cleanly
        vm.performUndo(banner)
        withTimeout(5_000L) {
            taskRepository.observeAllActiveTasks().first { list ->
                list.any { it.id == original.id && !it.isCompleted } && list.none { it.id == spawnedId }
            }
        }
        collectJob.cancel()
    }

    @Test
    fun `deleting a subtask offers undo and restores it under the same parent`() = runBlocking {
        val parent = taskRepository.createTask("Publish draft")
        val task = taskRepository.createTask("Review draft", parentTaskId = parent.id)
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.deleteTask(task)
        val banner = withTimeout(5_000L) {
            vm.uiState.first { state ->
                state.deletedTasks.any { it.id == task.id } && state.undoBanner?.taskId == task.id
            }.undoBanner!!
        }

        vm.performUndo(banner)
        withTimeout(5_000L) {
            vm.uiState.first { state ->
                state.undoBanner == null && state.deletedTasks.none { it.id == task.id } &&
                    state.allActiveTasks.any { it.id == task.id && it.parentTaskId == parent.id }
            }
        }
        collectJob.cancel()
    }

    @Test
    fun `indenting a task offers undo and restores its previous position`() = runBlocking {
        val parent = taskRepository.createTask("Plan trip")
        val task = taskRepository.createTask("Book ticket")
        val vm = newViewModel()

        vm.indentTask(task, parent.id)
        val banner = withTimeout(5_000L) {
            vm.uiState.first { state ->
                state.undoBanner?.taskId == task.id && state.allActiveTasks.any {
                    it.id == task.id && it.parentTaskId == parent.id
                }
            }.undoBanner!!
        }
        assertEquals(UndoType.RESTORE_TASK_PLACEMENT, banner.undoType)

        vm.performUndo(banner)
        val restored = withTimeout(5_000L) {
            vm.uiState.first { state ->
                state.undoBanner == null && state.allActiveTasks.any {
                    it.id == task.id && it.parentTaskId == null && it.sortOrder == task.sortOrder
                }
            }.allActiveTasks.first { it.id == task.id }
        }
        assertEquals(task.sortOrder, restored.sortOrder, 0.0)
    }

    @Test
    fun `global search, sort mode, and trash restore work end-to-end`() = runBlocking {
        val tLow = taskRepository.createTask("Alpha note", priority = TaskPriority.NONE)
        val tUrgent = taskRepository.createTask("Beta urgent", priority = TaskPriority.URGENT)
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        // Sort by priority puts Urgent first
        vm.setTaskSortMode(TaskSortMode.PRIORITY)
        val sorted = withTimeout(5_000L) {
            vm.uiState.first {
                it.taskSortMode == TaskSortMode.PRIORITY &&
                    it.tasks.firstOrNull()?.id == tUrgent.id
            }
        }
        assertEquals(tUrgent.id, sorted.tasks.first().id)

        // Global search filters accurately
        vm.setSearchOpen(true)
        vm.updateSearchQuery("Alpha")
        val searched = withTimeout(5_000L) {
            vm.uiState.first { it.tasks.size == 1 && it.tasks.single().id == tLow.id }
        }
        assertEquals(tLow.id, searched.tasks.single().id)
        vm.setSearchOpen(false)

        // Delete moves to Trash and restore brings it back
        vm.deleteTask(tLow)
        withTimeout(5_000L) {
            vm.uiState.first { state ->
                state.smartViewCounts.trash == 1 && state.deletedTasks.any { it.id == tLow.id }
            }
        }

        vm.restoreDeletedTask(tLow.id)
        withTimeout(5_000L) {
            vm.uiState.first { it.deletedTasks.isEmpty() && it.allActiveTasks.any { t -> t.id == tLow.id } }
        }
        collectJob.cancel()
    }

    @Test
    fun `settings preferences update theme, accent, antiMisclickMode, and haptics reactively`() = runBlocking {
        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.setDarkTheme(false)
        vm.setAccent(ZeroAccent.Sage)
        vm.setAntiMisclickMode(AntiMisclickMode.SWIPE_DAMPED)
        vm.setHapticsEnabled(false)

        val updated = withTimeout(5_000L) {
            vm.uiState.first {
                !it.isDarkTheme &&
                    it.activeAccent == ZeroAccent.Sage &&
                    it.antiMisclickMode == AntiMisclickMode.SWIPE_DAMPED &&
                    !it.hapticsEnabled
            }
        }
        assertFalse(updated.isDarkTheme)
        assertEquals(ZeroAccent.Sage, updated.activeAccent)
        assertEquals(AntiMisclickMode.SWIPE_DAMPED, updated.antiMisclickMode)
        assertFalse(updated.hapticsEnabled)
        collectJob.cancel()
    }

    @Test
    fun `ZERO 2_0 NOW runway skip, first-step flow momentum, day budget, triage, and global tag delete work end-to-end`() = runBlocking {
        val todayMillis = ZeroDateFormatter.startOfDayMillis(LocalDate.now())
        val t1 = taskRepository.createTask("Write Q4 review", priority = TaskPriority.URGENT, dueDate = todayMillis)
        val t2 = taskRepository.createTask("Reply client email", priority = TaskPriority.HIGH, dueDate = todayMillis)
        val unfiled = taskRepository.createTask("Random inbox thought")
        val tag = tagRepository.getOrCreateTag("DeepWork")
        tagRepository.attachTagToTask(t1.id, tag.id)

        val vm = newViewModel()
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        // Default workspaceMode is NOW, runway contains today tasks ordered by priority
        val initial = withTimeout(5_000L) {
            vm.uiState.first {
                it.workspaceMode == WorkspaceMode.NOW &&
                    it.runwayQueue.size == 2 &&
                    it.triageQueue.any { item -> item.id == unfiled.id }
            }
        }
        assertEquals(t1.id, initial.runwayQueue.first().id)

        // Guilt-free skip moves t1 behind t2
        vm.skipTaskInRunway(t1)
        val afterSkip = withTimeout(5_000L) {
            vm.uiState.first { it.runwayQueue.firstOrNull()?.id == t2.id }
        }
        assertEquals(t2.id, afterSkip.runwayQueue.first().id)

        // ADHD Blind Draw / Shuffle moves a different queued task to the top spotlight
        vm.shuffleRunway()
        val afterShuffle = withTimeout(5_000L) {
            vm.uiState.first { it.runwayQueue.firstOrNull()?.id == t1.id }
        }
        assertEquals(t1.id, afterShuffle.runwayQueue.first().id)

        // Completing a runway task increments todayCompletedCount for Today Rhythm Dots
        vm.toggleTaskCompletion(t2)
        val afterComplete = withTimeout(5_000L) {
            vm.uiState.first { it.todayCompletedCount == 1 }
        }
        assertEquals(1, afterComplete.todayCompletedCount)
        val completedT2 = afterComplete.allActiveTasks.first { it.id == t2.id }
        vm.toggleTaskCompletion(completedT2)
        withTimeout(5_000L) {
            vm.uiState.first { it.todayCompletedCount == 0 && it.runwayQueue.size == 2 }
        }

        // Setting effort updates Day Budget scheduledTodayMinutes
        vm.updateTaskEffort(t1, 60)
        vm.updateTaskEffort(t2, 25)
        val budgeted = withTimeout(5_000L) {
            vm.uiState.first { it.scheduledTodayMinutes == 85 }
        }
        assertEquals(85, budgeted.scheduledTodayMinutes)

        // Setting First Step and completing it starts Forward Momentum Stopwatch focus
        vm.updateTaskFirstStep(t1, "Open doc and type title")
        val withStep = withTimeout(5_000L) {
            taskRepository.observeTaskWithTags(t1.id).filterNotNull().first { it.firstStep == "Open doc and type title" }
        }
        vm.completeFirstStepAndStartFlow(withStep)
        val inFlow = withTimeout(5_000L) {
            vm.uiState.first { it.focusState.isActive && it.focusState.mode == FocusMode.STOPWATCH }
        }
        assertEquals(t1.id, inFlow.focusState.task?.id)
        vm.exitFocus()

        // Triage schedules unfiled inbox item to today -> clears triageQueue
        vm.triageScheduleToday(unfiled)
        withTimeout(5_000L) {
            vm.uiState.first { it.triageQueue.none { item -> item.id == unfiled.id } }
        }

        // Global Tag rename & delete
        vm.renameTag(tag.id, "DeepFocus")
        withTimeout(5_000L) {
            vm.uiState.first { it.tagsWithCounts.any { tc -> tc.tag.id == tag.id && tc.tag.name == "DeepFocus" } }
        }
        vm.deleteTag(tag.id)
        withTimeout(5_000L) {
            vm.uiState.first { it.tagsWithCounts.none { tc -> tc.tag.id == tag.id } }
        }

        collectJob.cancel()
    }
}
