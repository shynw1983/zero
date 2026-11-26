package com.zero.todo.domain.model

data class TaskPlacement(val folderId: String?, val parentTaskId: String?, val sortOrder: Double)

data class TaskPlacementChange(
    val taskId: String,
    val before: TaskPlacement,
    val after: TaskPlacement,
    val rebalancedSiblings: Map<String, Pair<TaskPlacement, TaskPlacement>> = emptyMap()
)
