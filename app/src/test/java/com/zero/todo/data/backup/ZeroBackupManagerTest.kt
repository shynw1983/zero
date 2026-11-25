package com.zero.todo.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.FolderRepositoryImpl
import com.zero.todo.data.repository.TagRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

        val markdown = backupManager.exportMarkdown(zoneId = ZoneId.of("UTC"))

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
