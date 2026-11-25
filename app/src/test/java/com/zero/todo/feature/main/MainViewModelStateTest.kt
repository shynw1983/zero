package com.zero.todo.feature.main

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.preferences.InMemoryUserPreferencesRepository
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.feature.folder.NavigationDestination
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
        getFolderTreeUseCase = GetFolderTreeUseCase(folderRepository, taskRepository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `SavedStateHandle preserves selected folder, selected task, includeSubfolders, and quick input across recreation`() = runBlocking {
        val folder = folderRepository.createFolder("Work")
        val task = taskRepository.createTask("Draft spec", folderId = folder.id)

        val savedStateHandle = SavedStateHandle()
        val vm1 = MainViewModel(
            savedStateHandle = savedStateHandle,
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = preferencesRepository,
            backupManager = backupManager,
            widgetRefresher = NoOpWidgetRefresher,
            getFolderTreeUseCase = getFolderTreeUseCase
        )
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

        // Simulate Activity/ViewModel recreation across rotation or foldable hinge fold/unfold
        val restoredHandle = SavedStateHandle(
            savedStateHandle.keys().associateWith { key -> savedStateHandle.get<Any?>(key) }
        )
        val vm2 = MainViewModel(
            savedStateHandle = restoredHandle,
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = preferencesRepository,
            backupManager = backupManager,
            widgetRefresher = NoOpWidgetRefresher,
            getFolderTreeUseCase = getFolderTreeUseCase
        )
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
    fun `submitQuickInput routes into nested folder path, attaches tags, and undo reverts completion`() = runBlocking {
        val vm = MainViewModel(
            savedStateHandle = SavedStateHandle(),
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = preferencesRepository,
            backupManager = backupManager,
            widgetRefresher = NoOpWidgetRefresher,
            getFolderTreeUseCase = getFolderTreeUseCase
        )
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }

        vm.updateQuickInputText("明天 15:00 提交报销 /财务/差旅 !1 #紧急 #票据")
        vm.submitQuickInput()

        // Input cleared immediately for continuous entry mode
        assertEquals("", vm.uiState.value.quickInputText)

        // Await Room async nested folder creation: 财务 -> 差旅
        val folders = withTimeout(5_000L) {
            folderRepository.observeActiveFolders().first { list ->
                list.any { it.name == "财务" && it.parentId == null } &&
                    list.any { it.name == "差旅" }
            }
        }
        val finance = folders.first { it.name == "财务" && it.parentId == null }
        val travel = folders.first { it.name == "差旅" && it.parentId == finance.id }

        // Await task creation inside 差旅 and attachment of both tags
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
        assertEquals(setOf("紧急", "票据"), createdTask.tags.map { it.name }.toSet())

        // Toggle complete triggers Undo banner, and performing undo reverts completion
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
    fun `settings preferences update theme, accent, antiMisclickMode, and haptics reactively`() = runBlocking {
        val vm = MainViewModel(
            savedStateHandle = SavedStateHandle(),
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = preferencesRepository,
            backupManager = backupManager,
            widgetRefresher = NoOpWidgetRefresher,
            getFolderTreeUseCase = getFolderTreeUseCase
        )
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
}
