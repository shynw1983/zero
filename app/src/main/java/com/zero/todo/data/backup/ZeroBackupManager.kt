package com.zero.todo.data.backup

import androidx.room.withTransaction
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.local.dao.FocusSessionDao
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.dao.TaskDao
import com.zero.todo.data.local.entity.FocusSessionEntity
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskTagCrossRef
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(
    val foldersCount: Int,
    val tasksCount: Int,
    val tagsCount: Int,
    val focusSessionsCount: Int
)

/**
 * Local-first full backup & export manager for ZERO.
 * - Uses zero-reflection `org.json` serialization (100% R8/ProGuard safe).
 * - Exports & restores all 5 Room tables losslessly inside a single atomic transaction
 *   with topological parent-before-child ordering to satisfy SQLite foreign keys.
 * - Exports active folders & nested tasks/subtasks into clean hierarchical Markdown.
 */
@Singleton
class ZeroBackupManager @Inject constructor(
    private val database: ZeroDatabase,
    private val folderDao: FolderDao,
    private val taskDao: TaskDao,
    private val tagDao: TagDao,
    private val focusSessionDao: FocusSessionDao
) {

    suspend fun getLocalStats(): BackupSummary {
        val folders = folderDao.getActiveFolders()
        val tasks = taskDao.getAllTasksSnapshot().count { !it.isDeleted }
        val tags = tagDao.getAllTagsSnapshot().count { !it.isDeleted }
        val sessions = focusSessionDao.getAllSessionsSnapshot().count { !it.isDeleted }
        return BackupSummary(
            foldersCount = folders.size,
            tasksCount = tasks,
            tagsCount = tags,
            focusSessionsCount = sessions
        )
    }

    /**
     * Exports the complete local database (folders, tasks, tags, cross-refs, focus sessions)
     * as a versioned pretty-printed JSON backup.
     */
    suspend fun exportJson(): String {
        val folders = folderDao.getAllFoldersSnapshot()
        val tasks = taskDao.getAllTasksSnapshot()
        val tags = tagDao.getAllTagsSnapshot()
        val crossRefs = tagDao.getAllCrossRefsSnapshot()
        val sessions = focusSessionDao.getAllSessionsSnapshot()

        val root = JSONObject().apply {
            put("app", "ZERO")
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("folders", JSONArray().apply {
                folders.forEach { put(it.toJson()) }
            })
            put("tasks", JSONArray().apply {
                tasks.forEach { put(it.toJson()) }
            })
            put("tags", JSONArray().apply {
                tags.forEach { put(it.toJson()) }
            })
            put("taskTagCrossRefs", JSONArray().apply {
                crossRefs.forEach { put(it.toJson()) }
            })
            put("focusSessions", JSONArray().apply {
                sessions.forEach { put(it.toJson()) }
            })
        }
        return root.toString(2)
    }

    /**
     * Validates and restores a JSON backup atomically inside a Room transaction.
     * Topologically sorts folders and tasks so foreign-key references are always valid.
     */
    suspend fun importJson(json: String, replaceExisting: Boolean = true): BackupSummary {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", -1)
        require(version in 1..CURRENT_SCHEMA_VERSION) {
            "不支持的备份格式版本: $version"
        }

        val rawFolders = parseArray(root.optJSONArray("folders")) { it.toFolderEntity() }
        val rawTasks = parseArray(root.optJSONArray("tasks")) { it.toTaskEntity() }
        val rawTags = parseArray(root.optJSONArray("tags")) { it.toTagEntity() }
        val rawCrossRefs = parseArray(root.optJSONArray("taskTagCrossRefs")) { it.toCrossRef() }
        val rawSessions = parseArray(root.optJSONArray("focusSessions")) { it.toFocusSessionEntity() }

        val folderIds = rawFolders.mapTo(HashSet()) { it.id }
        val sanitizedFolders = topologicallyOrder(
            items = rawFolders.map { folder ->
                if (folder.parentId != null && folder.parentId !in folderIds) {
                    folder.copy(parentId = null)
                } else {
                    folder
                }
            },
            idSelector = { it.id },
            parentSelector = { it.parentId }
        )

        val taskIds = rawTasks.mapTo(HashSet()) { it.id }
        val sanitizedTasks = topologicallyOrder(
            items = rawTasks.map { task ->
                val validFolderId = task.folderId?.takeIf { it in folderIds }
                val validParentTaskId = task.parentTaskId?.takeIf { it in taskIds && it != task.id }
                task.copy(folderId = validFolderId, parentTaskId = validParentTaskId)
            },
            idSelector = { it.id },
            parentSelector = { it.parentTaskId }
        )

        val tagIds = rawTags.mapTo(HashSet()) { it.id }
        val validCrossRefs = rawCrossRefs.filter { it.taskId in taskIds && it.tagId in tagIds }
        val validSessions = rawSessions.map { session ->
            val validTaskId = session.taskId?.takeIf { it in taskIds }
            session.copy(taskId = validTaskId)
        }

        database.withTransaction {
            if (replaceExisting) {
                tagDao.clearAllCrossRefs()
                focusSessionDao.clearAllSessions()
                taskDao.clearAllTasks()
                tagDao.clearAllTags()
                folderDao.clearAllFolders()
            }

            // Insert in strict FK order: folders -> tags -> tasks -> crossRefs -> focusSessions
            for (folder in sanitizedFolders) {
                folderDao.upsertFolder(folder)
            }
            if (rawTags.isNotEmpty()) {
                tagDao.upsertTags(rawTags)
            }
            for (task in sanitizedTasks) {
                taskDao.upsertTask(task)
            }
            if (validCrossRefs.isNotEmpty()) {
                tagDao.insertCrossRefs(validCrossRefs)
            }
            if (validSessions.isNotEmpty()) {
                focusSessionDao.insertSessions(validSessions)
            }
        }

        return BackupSummary(
            foldersCount = sanitizedFolders.count { !it.isDeleted },
            tasksCount = sanitizedTasks.count { !it.isDeleted },
            tagsCount = rawTags.count { !it.isDeleted },
            focusSessionsCount = validSessions.count { !it.isDeleted }
        )
    }

    /**
     * Exports all active folders, tasks, nested subtasks, tags, and notes as clean
     * hierarchical Markdown organized by the infinite folder tree.
     */
    suspend fun exportMarkdown(
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: java.util.Locale = java.util.Locale.getDefault()
    ): String {
        val activeFolders = folderDao.getActiveFolders()
        val allActiveTasks = taskDao.getAllTasksSnapshot().filterNot { it.isDeleted }
        val activeTagsById = tagDao.getAllTagsSnapshot()
            .filterNot { it.isDeleted }
            .associateBy { it.id }
        val crossRefs = tagDao.getAllCrossRefsSnapshot()

        val tagsByTaskId = HashMap<String, MutableList<String>>()
        for (ref in crossRefs) {
            val tag = activeTagsById[ref.tagId] ?: continue
            tagsByTaskId.getOrPut(ref.taskId) { mutableListOf() }.add(tag.name)
        }

        val subtasksByParent = allActiveTasks
            .filter { it.parentTaskId != null }
            .groupBy { it.parentTaskId!! }

        val topLevelTasksByFolder = allActiveTasks
            .filter { it.parentTaskId == null }
            .groupBy { it.folderId }

        val foldersByParent = activeFolders.groupBy { it.parentId }

        val sb = StringBuilder()
        val exportedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.now().atZone(zoneId))
        sb.appendLine("# ZERO Export")
        sb.appendLine()
        sb.appendLine("> Exported at `$exportedTime`")
        sb.appendLine()

        fun appendTaskRecursive(task: TaskEntity, indentLevel: Int) {
            val indent = "  ".repeat(indentLevel)
            val check = if (task.isCompleted) "x" else " "
            val metaParts = mutableListOf<String>()
            if (task.priority in 1..3) {
                metaParts.add("[P${task.priority}]")
            }
            if (task.dueDate != null) {
                metaParts.add(
                    "📅 ${ZeroDateFormatter.formatDueDate(task.dueDate, task.isAllDay, zoneId = zoneId, locale = locale)}"
                )
            }
            if (!task.recurrenceRule.isNullOrBlank()) {
                metaParts.add("↻ ${RecurrenceRule.formatLabel(task.recurrenceRule, locale)}")
            }
            tagsByTaskId[task.id]?.sorted()?.forEach { tagName ->
                metaParts.add("#$tagName")
            }

            sb.append(indent).append("- [").append(check).append("] ").append(task.title)
            if (metaParts.isNotEmpty()) {
                sb.append("  ").append(metaParts.joinToString(" "))
            }
            sb.appendLine()

            if (task.description.isNotBlank()) {
                task.description.lines().forEach { line ->
                    sb.append(indent).append("  > ").append(line).appendLine()
                }
            }

            val children = subtasksByParent[task.id]
                ?.sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { it.sortOrder }, { it.createdAt }))
                .orEmpty()
            for (child in children) {
                appendTaskRecursive(child, indentLevel + 1)
            }
        }

        // 1. Inbox Section
        val inboxTasks = topLevelTasksByFolder[null]
            ?.sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { it.sortOrder }, { it.createdAt }))
            .orEmpty()
        sb.appendLine("## 收集箱 (Inbox)")
        sb.appendLine()
        if (inboxTasks.isEmpty()) {
            sb.appendLine("_空_")
            sb.appendLine()
        } else {
            inboxTasks.forEach { appendTaskRecursive(it, 0) }
            sb.appendLine()
        }

        // 2. Recursive Folder Hierarchy
        val visitedFolders = HashSet<String>()
        fun appendFolderRecursive(folder: FolderEntity, depth: Int, pathPrefix: String) {
            if (!visitedFolders.add(folder.id)) return
            val currentPath = if (pathPrefix.isEmpty()) folder.name else "$pathPrefix / ${folder.name}"
            val headingHashes = "#".repeat((depth + 2).coerceAtMost(6))
            val headingText = if (depth >= 4) "$headingHashes $currentPath" else "$headingHashes ${folder.name}"

            sb.appendLine(headingText)
            sb.appendLine()

            val tasks = topLevelTasksByFolder[folder.id]
                ?.sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { it.sortOrder }, { it.createdAt }))
                .orEmpty()
            if (tasks.isNotEmpty()) {
                tasks.forEach { appendTaskRecursive(it, 0) }
                sb.appendLine()
            }

            val childFolders = foldersByParent[folder.id]
                ?.sortedWith(compareBy<FolderEntity>({ it.sortOrder }, { it.createdAt }, { it.id }))
                .orEmpty()
            for (child in childFolders) {
                appendFolderRecursive(child, depth + 1, currentPath)
            }
        }

        val rootFolders = foldersByParent[null]
            ?.sortedWith(compareBy<FolderEntity>({ it.sortOrder }, { it.createdAt }, { it.id }))
            .orEmpty()
        for (rootFolder in rootFolders) {
            appendFolderRecursive(rootFolder, depth = 0, pathPrefix = "")
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun <T> topologicallyOrder(
        items: List<T>,
        idSelector: (T) -> String,
        parentSelector: (T) -> String?
    ): List<T> {
        val byId = items.associateBy(idSelector)
        val emitted = HashSet<String>(items.size)
        val visiting = HashSet<String>()
        val ordered = ArrayList<T>(items.size)

        fun visit(item: T) {
            val id = idSelector(item)
            if (id in emitted || !visiting.add(id)) return
            val parentId = parentSelector(item)
            if (parentId != null) {
                byId[parentId]?.let { visit(it) }
            }
            visiting.remove(id)
            if (emitted.add(id)) {
                ordered.add(item)
            }
        }

        for (item in items) {
            visit(item)
        }
        return ordered
    }

    private fun <T> parseArray(array: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(mapper(array.getJSONObject(i)))
            }
        }
    }

    private fun FolderEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("parentId", parentId ?: JSONObject.NULL)
        put("name", name)
        put("icon", icon ?: JSONObject.NULL)
        put("colorHex", colorHex ?: JSONObject.NULL)
        put("sortOrder", sortOrder)
        put("isExpanded", isExpanded)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDeleted", isDeleted)
    }

    private fun JSONObject.toFolderEntity(): FolderEntity = FolderEntity(
        id = getString("id"),
        parentId = optNullableString("parentId"),
        name = getString("name"),
        icon = optNullableString("icon"),
        colorHex = optNullableString("colorHex"),
        sortOrder = optDouble("sortOrder", 0.0),
        isExpanded = optBoolean("isExpanded", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        isDeleted = optBoolean("isDeleted", false)
    )

    private fun TaskEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("folderId", folderId ?: JSONObject.NULL)
        put("parentTaskId", parentTaskId ?: JSONObject.NULL)
        put("title", title)
        put("description", description)
        put("priority", priority)
        put("isCompleted", isCompleted)
        put("completedAt", completedAt ?: JSONObject.NULL)
        put("dueDate", dueDate ?: JSONObject.NULL)
        put("isAllDay", isAllDay)
        put("reminderAt", reminderAt ?: JSONObject.NULL)
        put("recurrenceRule", recurrenceRule ?: JSONObject.NULL)
        put("firstStep", firstStep ?: JSONObject.NULL)
        put("firstStepCompleted", firstStepCompleted)
        put("estimatedMinutes", estimatedMinutes ?: JSONObject.NULL)
        put("sortOrder", sortOrder)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDeleted", isDeleted)
    }

    private fun JSONObject.toTaskEntity(): TaskEntity = TaskEntity(
        id = getString("id"),
        folderId = optNullableString("folderId"),
        parentTaskId = optNullableString("parentTaskId"),
        title = getString("title"),
        description = optString("description", ""),
        priority = optInt("priority", 4),
        isCompleted = optBoolean("isCompleted", false),
        completedAt = optNullableLong("completedAt"),
        dueDate = optNullableLong("dueDate"),
        isAllDay = optBoolean("isAllDay", true),
        reminderAt = optNullableLong("reminderAt"),
        recurrenceRule = optNullableString("recurrenceRule"),
        firstStep = optNullableString("firstStep"),
        firstStepCompleted = optBoolean("firstStepCompleted", false),
        estimatedMinutes = optNullableInt("estimatedMinutes"),
        sortOrder = optDouble("sortOrder", 0.0),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        isDeleted = optBoolean("isDeleted", false)
    )

    private fun TagEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("colorHex", colorHex ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDeleted", isDeleted)
    }

    private fun JSONObject.toTagEntity(): TagEntity = TagEntity(
        id = getString("id"),
        name = getString("name"),
        colorHex = optNullableString("colorHex"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        isDeleted = optBoolean("isDeleted", false)
    )

    private fun TaskTagCrossRef.toJson(): JSONObject = JSONObject().apply {
        put("taskId", taskId)
        put("tagId", tagId)
    }

    private fun JSONObject.toCrossRef(): TaskTagCrossRef = TaskTagCrossRef(
        taskId = getString("taskId"),
        tagId = getString("tagId")
    )

    private fun FocusSessionEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("taskId", taskId ?: JSONObject.NULL)
        put("durationSeconds", durationSeconds)
        put("mode", mode)
        put("completedAt", completedAt)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDeleted", isDeleted)
    }

    private fun JSONObject.toFocusSessionEntity(): FocusSessionEntity {
        val completed = optLong("completedAt", System.currentTimeMillis())
        return FocusSessionEntity(
            id = getString("id"),
            taskId = optNullableString("taskId"),
            durationSeconds = optInt("durationSeconds", 0),
            mode = optString("mode", "POMODORO"),
            completedAt = completed,
            createdAt = optLong("createdAt", completed),
            updatedAt = optLong("updatedAt", completed),
            isDeleted = optBoolean("isDeleted", false)
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
