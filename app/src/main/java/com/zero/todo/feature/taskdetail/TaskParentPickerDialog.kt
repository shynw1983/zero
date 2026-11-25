package com.zero.todo.feature.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.domain.model.TaskItem

@Composable
internal fun TaskParentPickerDialog(
    task: TaskItem,
    tasks: List<TaskItem>,
    folderNames: Map<String, String>,
    saving: Boolean,
    failed: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZeroTheme.colors
    var query by rememberSaveable(task.id) { mutableStateOf("") }
    val byId = remember(tasks) { tasks.associateBy { it.id } }
    val eligible = remember(tasks, task.id, task.parentTaskId) {
        val childrenByParent = tasks.groupBy { it.parentTaskId }
        val excluded = mutableSetOf(task.id)
        val pending = ArrayDeque<String>()
        pending.add(task.id)
        while (pending.isNotEmpty()) {
            childrenByParent[pending.removeFirst()].orEmpty().forEach { child ->
                if (excluded.add(child.id)) pending.add(child.id)
            }
        }
        tasks.filter { it.id !in excluded && it.id != task.parentTaskId }
            .sortedWith(compareBy<TaskItem> { it.isCompleted }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    val search = query.trim()
    val filtered = eligible.filter { candidate ->
        candidate.title.contains(search, ignoreCase = true) ||
            folderNames[candidate.folderId]?.contains(search, ignoreCase = true) == true
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroCardShape)
                .background(colors.surfaceElevated)
                .hairlineBorder(shape = ZeroCardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.task_choose_parent), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(task.title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.task_parent_search)) },
                singleLine = true
            )
            if (filtered.isEmpty()) {
                Text(stringResource(R.string.task_parent_empty), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.id }) { candidate ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .clickable(enabled = !saving) { onSelect(candidate.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(candidate.title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = byId[candidate.parentTaskId]?.title ?: folderNames[candidate.folderId] ?: stringResource(R.string.nav_inbox),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (failed) {
                Text(stringResource(R.string.task_parent_failed), style = MaterialTheme.typography.bodySmall, color = colors.priorityUrgent)
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
