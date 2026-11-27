package com.zero.todo.feature.main

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.core.designsystem.AppIconSwitcher
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.notification.NoOpReminderScheduler
import com.zero.todo.data.backup.WebDavSyncManager
import com.zero.todo.data.backup.ZeroBackupManager
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.preferences.InMemoryUserPreferencesRepository
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.usecase.GetFolderTreeUseCase
import com.zero.todo.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtaskInheritanceTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl
    private lateinit var viewModel: MainViewModel

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
        val prefs = InMemoryUserPreferencesRepository()
        viewModel = MainViewModel(
            savedStateHandle = SavedStateHandle(),
            folderRepository = folderRepository,
            taskRepository = taskRepository,
            tagRepository = tagRepository,
            focusRepository = focusRepository,
            userPreferencesRepository = prefs,
            backupManager = ZeroBackupManager(
                database = database,
                folderDao = database.folderDao(),
                taskDao = database.taskDao(),
                tagDao = database.tagDao(),
                focusSessionDao = database.focusSessionDao()
            ),
            webDavSyncManager = WebDavSyncManager(),
            aiEngine = com.zero.todo.data.ai.ZeroAiEngine(),
            reminderScheduler = NoOpReminderScheduler,
            widgetRefresher = object : WidgetRefresher { override suspend fun refreshAll() = Unit },
            getFolderTreeUseCase = GetFolderTreeUseCase(folderRepository, taskRepository),
            appIconSwitcher = object : AppIconSwitcher { override fun syncAccentIcon(accent: ZeroAccent) = Unit }
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `addSubtask parses NLP tags, inherits parent folder and priority, and leaves dueDate dynamic`() = runBlocking {
        val folderA = folderRepository.createFolder("清水店", null)
        val parent = taskRepository.createTask(
            title = "仕訳資料送付",
            folderId = folderA.id,
            priority = TaskPriority.HIGH
        )
        val collectJob = launch(Dispatchers.Default) { viewModel.uiState.collect {} }

        viewModel.addSubtask(parent.id, "レターパック購入 #外出/コンビニ")

        val state = withTimeout(5_000L) {
            viewModel.uiState.first { ui ->
                ui.allActiveTasks.any { it.parentTaskId == parent.id && it.title == "レターパック購入" && it.tags.isNotEmpty() }
            }
        }
        val child = state.allActiveTasks.first { it.parentTaskId == parent.id }
        assertEquals(folderA.id, child.folderId)
        assertEquals(TaskPriority.HIGH, child.priority)
        assertNull(child.dueDate)
        assertEquals(listOf("コンビニ"), child.tags.map { it.name })
        collectJob.cancel()
    }

    @Test
    fun `moveTaskToFolder cascades target folderId to all descendant subtasks`() = runBlocking {
        val folderA = folderRepository.createFolder("FolderA", null)
        val folderB = folderRepository.createFolder("FolderB", null)
        val parent = taskRepository.createTask(title = "Parent", folderId = folderA.id)
        val child = taskRepository.createTask(title = "Child", folderId = folderA.id, parentTaskId = parent.id)
        val grandchild = taskRepository.createTask(title = "Grandchild", folderId = folderA.id, parentTaskId = child.id)

        taskRepository.moveTaskToFolder(parent.id, folderB.id)

        assertEquals(folderB.id, taskRepository.getTaskById(parent.id)?.folderId)
        assertEquals(folderB.id, taskRepository.getTaskById(child.id)?.folderId)
        assertEquals(folderB.id, taskRepository.getTaskById(grandchild.id)?.folderId)
    }
}
