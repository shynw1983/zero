package com.zero.todo.widget

import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.data.local.entity.FolderEntity
import com.zero.todo.data.local.entity.TagEntity
import com.zero.todo.data.local.entity.TaskEntity
import com.zero.todo.data.local.entity.TaskWithTags
import com.zero.todo.feature.folder.NavigationDestination
import java.time.LocalDate

object WidgetTaskQueryEngine {

    fun collectDescendantFolderIds(
        rootFolderId: String,
        folders: List<FolderEntity>
    ): Set<String> {
        val activeFolders = folders.filter { !it.isDeleted }
        if (activeFolders.none { it.id == rootFolderId }) return emptySet()

        val childrenByParent = activeFolders.groupBy { it.parentId }
        val result = linkedSetOf(rootFolderId)
        val queue = ArrayDeque<String>()
        queue.addLast(rootFolderId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in childrenByParent[current].orEmpty()) {
                if (result.add(child.id)) {
                    queue.addLast(child.id)
                }
            }
        }
        return result
    }

    fun collectDescendantTagIds(
        rootTagId: String,
        tags: List<TagEntity>
    ): Set<String> {
        val activeTags = tags.filter { !it.isDeleted }
        if (activeTags.none { it.id == rootTagId }) return setOf(rootTagId)

        val childrenByParent = activeTags.groupBy { it.parentId }
        val result = linkedSetOf(rootTagId)
        val queue = ArrayDeque<String>()
        queue.addLast(rootTagId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in childrenByParent[current].orEmpty()) {
                if (result.add(child.id)) {
                    queue.addLast(child.id)
                }
            }
        }
        return result
    }

    fun buildTagPathMap(tags: List<TagEntity>): Map<String, String> {
        val activeById = tags.filter { !it.isDeleted }.associateBy { it.id }
        val result = HashMap<String, String>(activeById.size)
        for ((id, tag) in activeById) {
            val segments = mutableListOf(tag.name)
            val seen = hashSetOf(id)
            var currentParent = tag.parentId
            while (currentParent != null && currentParent !in seen) {
                val parent = activeById[currentParent] ?: break
                seen += currentParent
                segments.add(0, parent.name)
                currentParent = parent.parentId
            }
            result[id] = segments.joinToString("/")
        }
        return result
    }

    fun filterTasks(
        allTasks: List<TaskWithTags>,
        allFolders: List<FolderEntity>,
        config: ZeroWidgetConfig,
        today: LocalDate = LocalDate.now(),
        allTags: List<TagEntity> = emptyList()
    ): List<TaskEntity> {
        val todayStart = ZeroDateFormatter.startOfDayMillis(today)
        val todayEndExclusive = ZeroDateFormatter.endOfDayExclusiveMillis(today)
        val next7DaysEndExclusive = ZeroDateFormatter.endOfDayExclusiveMillis(today.plusDays(6))

        val folderIds = if (config.filterScope == WidgetFilterScope.FOLDER && !config.folderId.isNullOrBlank()) {
            if (config.includeSubfolders) {
                collectDescendantFolderIds(config.folderId, allFolders)
            } else if (allFolders.any { !it.isDeleted && it.id == config.folderId }) {
                setOf(config.folderId)
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }

        val matchingTagIds = if (config.filterScope == WidgetFilterScope.TAG && !config.tagId.isNullOrBlank()) {
            if (allTags.isNotEmpty()) {
                collectDescendantTagIds(config.tagId, allTags)
            } else {
                setOf(config.tagId)
            }
        } else {
            emptySet()
        }

        val filtered = allTasks.asSequence()
            .filter { !it.task.isDeleted }
            .filter { item ->
                val t = item.task
                when (config.filterScope) {
                    WidgetFilterScope.TODAY -> {
                        val due = t.dueDate ?: return@filter false
                        if (due >= todayEndExclusive) return@filter false
                        if (!config.showCompleted) !t.isCompleted
                        else !t.isCompleted || due >= todayStart
                    }

                    WidgetFilterScope.INBOX -> {
                        t.folderId == null && t.parentTaskId == null && (config.showCompleted || !t.isCompleted)
                    }

                    WidgetFilterScope.NEXT_7_DAYS -> {
                        val due = t.dueDate ?: return@filter false
                        if (due >= next7DaysEndExclusive) return@filter false
                        if (!config.showCompleted) !t.isCompleted
                        else !t.isCompleted || due >= todayStart
                    }

                    WidgetFilterScope.HIGH_PRIORITY -> {
                        (t.priority == 1 || t.priority == 2) && (config.showCompleted || !t.isCompleted)
                    }

                    WidgetFilterScope.ALL_ACTIVE -> {
                        t.parentTaskId == null && (config.showCompleted || !t.isCompleted)
                    }

                    WidgetFilterScope.FOLDER -> {
                        t.folderId != null &&
                            t.folderId in folderIds &&
                            t.parentTaskId == null &&
                            (config.showCompleted || !t.isCompleted)
                    }

                    WidgetFilterScope.TAG -> {
                        matchingTagIds.isNotEmpty() &&
                            item.tags.any { !it.isDeleted && it.id in matchingTagIds } &&
                            (config.showCompleted || !t.isCompleted)
                    }
                }
            }
            .map { it.task }
            .toList()

        val comparator = when (config.filterScope) {
            WidgetFilterScope.TODAY,
            WidgetFilterScope.NEXT_7_DAYS -> compareBy<TaskEntity>(
                { it.isCompleted },
                { it.dueDate ?: Long.MAX_VALUE },
                { it.priority },
                { it.sortOrder },
                { it.createdAt }
            )

            WidgetFilterScope.HIGH_PRIORITY -> compareBy<TaskEntity>(
                { it.isCompleted },
                { it.priority },
                { it.dueDate ?: Long.MAX_VALUE },
                { it.sortOrder },
                { it.createdAt }
            )

            else -> compareBy<TaskEntity>(
                { it.isCompleted },
                { it.sortOrder },
                { it.createdAt }
            )
        }
        return filtered.sortedWith(comparator)
    }

    fun destinationStorageKey(config: ZeroWidgetConfig): String = when (config.filterScope) {
        WidgetFilterScope.TODAY -> NavigationDestination.Today.toStorageKey()
        WidgetFilterScope.INBOX -> NavigationDestination.Inbox.toStorageKey()
        WidgetFilterScope.NEXT_7_DAYS -> NavigationDestination.Next7Days.toStorageKey()
        WidgetFilterScope.HIGH_PRIORITY,
        WidgetFilterScope.ALL_ACTIVE -> NavigationDestination.All.toStorageKey()
        WidgetFilterScope.FOLDER -> config.folderId
            ?.let { NavigationDestination.FolderView(it).toStorageKey() }
            ?: NavigationDestination.Inbox.toStorageKey()
        WidgetFilterScope.TAG -> config.tagId
            ?.let { NavigationDestination.TagView(it).toStorageKey() }
            ?: NavigationDestination.All.toStorageKey()
    }
}
