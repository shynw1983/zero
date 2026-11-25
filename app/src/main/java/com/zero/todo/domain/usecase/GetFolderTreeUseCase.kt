package com.zero.todo.domain.usecase

import com.zero.todo.data.repository.FolderRepository
import com.zero.todo.data.repository.TaskRepository
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.FolderTreeBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Combines active folders with active incomplete top-level tasks to emit a live
 * hierarchical [FolderNode] tree complete with per-folder and recursive task counts.
 */
class GetFolderTreeUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val taskRepository: TaskRepository
) {
    operator fun invoke(): Flow<List<FolderNode>> = combine(
        folderRepository.observeActiveFolders(),
        taskRepository.observeAllActiveTopLevelTasks()
    ) { folders, tasks ->
        val incompleteCountsByFolder = tasks
            .asSequence()
            .filter { !it.isCompleted && it.folderId != null }
            .groupingBy { it.folderId!! }
            .eachCount()

        FolderTreeBuilder.build(
            folders = folders,
            directTaskCounts = incompleteCountsByFolder
        )
    }
}
