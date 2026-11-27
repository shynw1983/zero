package com.zero.todo.domain.model

data class RunwayBuildResult(
    val runwayQueue: List<TaskItem>,
    val scheduledTodayMinutes: Int,
    val parentTitleByTaskId: Map<String, String>
)

/**
 * Pure-Kotlin builder for ZERO's NOW Execution Runway.
 *
 * Core execution contract:
 * - Only actionable leaf tasks (no incomplete descendant subtasks) enter the NOW runway or count
 *   toward the daily capacity ruler, eliminating unfinishable container tasks and double counting.
 * - Container tasks with incomplete subtasks remain blocked while their undated (or today/overdue)
 *   predecessor subtasks enter the runway in parallel.
 * - Completing the last incomplete subtask automatically unblocks the parent task back onto NOW.
 */
object RunwayQueueBuilder {

    fun build(
        allActiveTasks: List<TaskItem>,
        tomorrowStartMillis: Long,
        pinnedTaskId: String?,
        skippedIds: List<String>
    ): RunwayBuildResult {
        if (allActiveTasks.isEmpty()) {
            return RunwayBuildResult(
                runwayQueue = emptyList(),
                scheduledTodayMinutes = 0,
                parentTitleByTaskId = emptyMap()
            )
        }

        val allById = allActiveTasks.associateBy { it.id }
        val activeIncomplete = allActiveTasks.filterNot { it.isCompleted || it.isDeleted }
        val incompleteById = activeIncomplete.associateBy { it.id }
        val incompleteChildrenByParent = activeIncomplete
            .filter { it.parentTaskId != null }
            .groupBy { it.parentTaskId!! }

        fun isLeaf(task: TaskItem): Boolean = incompleteChildrenByParent[task.id].isNullOrEmpty()

        fun inheritedDueDate(task: TaskItem): Long? {
            val visited = HashSet<String>()
            var currentId = task.parentTaskId
            while (currentId != null && visited.add(currentId)) {
                val ancestor = incompleteById[currentId] ?: break
                if (ancestor.dueDate != null) return ancestor.dueDate
                currentId = ancestor.parentTaskId
            }
            return null
        }

        fun inheritedPriority(task: TaskItem): TaskPriority {
            if (task.priority != TaskPriority.NONE) return task.priority
            val visited = HashSet<String>()
            var currentId = task.parentTaskId
            while (currentId != null && visited.add(currentId)) {
                val ancestor = incompleteById[currentId] ?: break
                if (ancestor.priority != TaskPriority.NONE) return ancestor.priority
                currentId = ancestor.parentTaskId
            }
            return TaskPriority.NONE
        }

        val todayActionableLeaves = activeIncomplete.filter { task ->
            if (!isLeaf(task)) return@filter false
            val ownDue = task.dueDate
            if (ownDue != null) {
                ownDue < tomorrowStartMillis
            } else {
                val parentDue = inheritedDueDate(task)
                parentDue != null && parentDue < tomorrowStartMillis
            }
        }

        val scheduledTodayMinutes = todayActionableLeaves.sumOf { task ->
            task.estimatedMinutes ?: EffortTier.DEFAULT_UNSET_MINUTES
        }

        val baseCandidates = if (todayActionableLeaves.isNotEmpty()) {
            todayActionableLeaves
        } else {
            activeIncomplete.filter { task ->
                isLeaf(task) &&
                    task.folderId == null &&
                    task.dueDate == null &&
                    inheritedDueDate(task) == null
            }
        }

        val skippedRank = skippedIds.withIndex().associate { (idx, id) -> id to (idx + 1) }
        val runwayQueue = baseCandidates.sortedWith(
            compareBy<TaskItem>(
                { if (it.id == pinnedTaskId) 0 else 1 },
                { skippedRank[it.id] ?: 0 },
                { inheritedPriority(it).value },
                { it.dueDate ?: inheritedDueDate(it) ?: Long.MAX_VALUE },
                { it.sortOrder }
            )
        )

        val parentTitleByTaskId = buildMap {
            for (task in allActiveTasks) {
                val parentId = task.parentTaskId ?: continue
                val parentTitle = allById[parentId]?.title?.takeIf { it.isNotBlank() } ?: continue
                put(task.id, parentTitle)
            }
        }

        return RunwayBuildResult(
            runwayQueue = runwayQueue,
            scheduledTodayMinutes = scheduledTodayMinutes,
            parentTitleByTaskId = parentTitleByTaskId
        )
    }
}
