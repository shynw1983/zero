package com.zero.todo.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZeroBackupManagerTest {

    private lateinit var database: ZeroDatabase
    private lateinit var folderRepository: FolderRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl
    private lateinit var backupManager: ZeroBackupManager

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
        backupManager = ZeroBackupManager(
            database = database,
            folderDao = database.folderDao(),
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            focusSessionDao = database.focusSessionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `full JSON export and restore preserves nested folders, subtasks, tags, and focus sessions`() = runTest {
        val work = folderRepository.createFolder("工作")
        val finance = folderRepository.createFolder("财务", parentId = work.id)
        val reimburse = folderRepository.createFolder("报销", parentId = finance.id)

        val inboxTask = taskRepository.createTask("买咖啡豆", priority = TaskPriority.MEDIUM)
        val nestedTask = taskRepository.createTask(
            title = "提交 Q4 差旅报销",
            folderId = reimburse.id,
            description = "附上高铁与酒店电子发票",
            priority = TaskPriority.URGENT,
            recurrenceRule = "FREQ=MONTHLY"
        )
        val sub1 = taskRepository.createTask("整理发票 PDF", parentTaskId = nestedTask.id)
        val sub2 = taskRepository.createTask("合并成单个文件", parentTaskId = sub1.id)

        val tagUrgent = tagRepository.getOrCreateTag("紧急", "#FF4D00")
        tagRepository.attachTagToTask(nestedTask.id, tagUrgent.id)

        focusRepository.recordSession(
            taskId = nestedTask.id,
            durationSeconds = 120,
            mode = FocusMode.TWO_MIN_STARTER
        )
        focusRepository.recordSession(
            taskId = nestedTask.id,
            durationSeconds = 1500,
            mode = FocusMode.POMODORO
        )

        val jsonBackup = backupManager.exportJson()

        // Reverse folder and task arrays in JSON to prove topological parent-first restore works
        val parsedRoot = JSONObject(jsonBackup)
        parsedRoot.put("folders", reverseJsonArray(parsedRoot.getJSONArray("folders")))
        parsedRoot.put("tasks", reverseJsonArray(parsedRoot.getJSONArray("tasks")))
        val reversedJson = parsedRoot.toString()

        // Wipe database completely and restore from reversed JSON
        val summary = backupManager.importJson(reversedJson, replaceExisting = true)
        assertEquals(3, summary.foldersCount)
        assertEquals(4, summary.tasksCount) // inboxTask + nestedTask + sub1 + sub2
        assertEquals(1, summary.tagsCount)
        assertEquals(2, summary.focusSessionsCount)

        // Verify full folder ancestry restored
        val ancestors = folderRepository.getAncestors(reimburse.id)
        assertEquals(listOf("工作", "财务", "报销"), ancestors.map { it.name })

        // Verify nested task, tags, recurrence, and 2-level subtasks restored
        val restoredTask = taskRepository.getTaskById(nestedTask.id)!!
        assertEquals("提交 Q4 差旅报销", restoredTask.title)
        assertEquals(TaskPriority.URGENT, restoredTask.priority)
        assertEquals("FREQ=MONTHLY", restoredTask.recurrenceRule)
        assertEquals(listOf("紧急"), restoredTask.tags.map { it.name })

        val restoredSubtasks = taskRepository.observeDescendantSubtasks(nestedTask.id).first()
        assertEquals(listOf(sub1.id, sub2.id), restoredSubtasks.map { it.id })

        val restoredInbox = taskRepository.observeInboxTasks().first()
        assertEquals(listOf(inboxTask.id), restoredInbox.map { it.id })

        val restoredSessions = focusRepository.observeSessionsForTask(nestedTask.id).first()
        assertEquals(2, restoredSessions.size)
    }

    @Test
    fun `export keeps folder references consistent while another transaction creates a task`() = runBlocking {
        val folderRead = CompletableDeferred<Unit>()
        val continueExport = CompletableDeferred<Unit>()
        val folderDao = database.folderDao()
        val exportManager = ZeroBackupManager(
            database = database,
            folderDao = object : FolderDao by folderDao {
                override suspend fun getAllFoldersSnapshot() =
                    folderDao.getAllFoldersSnapshot().also {
                        assertTrue(database.inTransaction())
                        folderRead.complete(Unit)
                        continueExport.await()
                    }
            },
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            focusSessionDao = database.focusSessionDao()
        )

        withTimeout(5_000L) {
            val export = async { exportManager.exportJson() }
            folderRead.await()
            val write = async(start = CoroutineStart.UNDISPATCHED) {
                val folder = folderRepository.createFolder("Concurrent folder")
                taskRepository.createTask("Concurrent task", folderId = folder.id)
            }
            continueExport.complete(Unit)

            val snapshot = JSONObject(export.await())
            assertEquals(0, snapshot.getJSONArray("folders").length())
            assertEquals(0, snapshot.getJSONArray("tasks").length())

            val task = write.await()
            val nextSnapshot = JSONObject(backupManager.exportJson())
            assertEquals(task.folderId, nextSnapshot.getJSONArray("folders").getJSONObject(0).getString("id"))
            assertEquals(task.folderId, nextSnapshot.getJSONArray("tasks").getJSONObject(0).getString("folderId"))
        }
    }

    @Test
    fun `malformed backups leave the local database intact`() = runBlocking {
        val folder = folderRepository.createFolder("Local folder")
        val task = taskRepository.createTask("Local task", folderId = folder.id)
        val backup = backupManager.exportJson()
        val requiredTables = listOf("folders", "tasks", "tags", "tagPlaces", "taskTagCrossRefs", "focusSessions")
        val invalidBackups = buildList {
            for (table in requiredTables) {
                add(JSONObject(backup).apply { remove(table) }.toString())
            }
            add(JSONObject(backup).put("tasks", JSONObject.NULL).toString())
            add(JSONObject(backup).put("tasks", JSONObject()).toString())
            add(JSONObject(backup).put("tasks", JSONArray().put(JSONObject())).toString())
            add(JSONObject(backup).put("schemaVersion", 2.5).toString())
            add(JSONObject(backup).put("app", "Other").toString())
            val incompleteV3 = JSONObject(backup).apply {
                put("schemaVersion", 3)
                remove("tagPlaces")
            }
            add(incompleteV3.toString())
            add("{")
        }

        for (invalidBackup in invalidBackups) {
            val failure = runCatching { backupManager.importJson(invalidBackup) }.exceptionOrNull()
            assertTrue("Expected invalid backup, got $failure", failure is InvalidBackupException)
            assertEquals(task, taskRepository.getTaskById(task.id))
            assertEquals(folder, folderRepository.getFolderById(folder.id))
        }
    }

    @Test
    fun `older backups can omit place data and a complete empty backup can replace tasks`() = runBlocking {
        val emptyBackup = backupManager.exportJson()
        val task = taskRepository.createTask("Legacy task")
        val currentBackup = backupManager.exportJson()

        for (version in 1..2) {
            val legacyBackup = JSONObject(currentBackup).apply {
                put("schemaVersion", version)
                remove("tagPlaces")
                getJSONArray("tasks").getJSONObject(0).remove("placeTriggerOverride")
            }.toString()
            assertEquals(1, backupManager.importJson(legacyBackup).tasksCount)
            assertEquals(task, taskRepository.getTaskById(task.id))
        }

        assertEquals(0, backupManager.importJson(emptyBackup).tasksCount)
        assertEquals(null, taskRepository.getTaskById(task.id))
    }

    @Test
    fun `hierarchical Markdown export organizes Inbox, nested folders, subtasks, tags, and notes`() = runTest {
        val work = folderRepository.createFolder("工作")
        val finance = folderRepository.createFolder("财务", parentId = work.id)

        taskRepository.createTask("阅读论文")
        val report = taskRepository.createTask(
            title = "提交月报",
            folderId = finance.id,
            description = "汇总 KPI 数据",
            priority = TaskPriority.URGENT,
            recurrenceRule = "FREQ=MONTHLY"
        )
        val sub = taskRepository.createTask("核对营收", parentTaskId = report.id)
        taskRepository.setTaskCompleted(sub.id, true)
        val tag = tagRepository.getOrCreateTag("重点")
        tagRepository.attachTagToTask(report.id, tag.id)

        val markdown = backupManager.exportMarkdown(
            zoneId = ZoneId.of("UTC"),
            locale = java.util.Locale.SIMPLIFIED_CHINESE
        )

        assertTrue(markdown.contains("# ZERO Export"))
        assertTrue(markdown.contains("## 收集箱 (Inbox)"))
        assertTrue(markdown.contains("- [ ] 阅读论文"))
        assertTrue(markdown.contains("## 工作"))
        assertTrue(markdown.contains("### 财务"))
        assertTrue(markdown.contains("- [ ] 提交月报  [P1] ↻ 每月 #重点"))
        assertTrue(markdown.contains("  > 汇总 KPI 数据"))
        assertTrue(markdown.contains("  - [x] 核对营收"))
    }

    private fun reverseJsonArray(source: JSONArray): JSONArray {
        val reversed = JSONArray()
        for (i in source.length() - 1 downTo 0) {
            reversed.put(source.get(i))
        }
        return reversed
    }
}
