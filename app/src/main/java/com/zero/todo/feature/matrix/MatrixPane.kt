package com.zero.todo.feature.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroPriorityBadge
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority

/**
 * Minimalist 2x2 Eisenhower Priority Matrix view aligned with TickTick's Matrix.
 * Organizes all active incomplete tasks across P1 (Urgent), P2 (High), P3 (Medium), P4 (Later).
 */
@Composable
fun MatrixPane(
    allActiveTasks: List<TaskItem>,
    selectedTaskId: String?,
    showDrawerButton: Boolean,
    showTreeToggleButton: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleTreePanel: () -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onOpenTaskActions: ((TaskItem) -> Unit)? = null,
    onQuickAddWithPriority: (TaskPriority) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    val incomplete = remember(allActiveTasks) { allActiveTasks.filterNot { it.isCompleted } }
    val p1 = remember(incomplete) { incomplete.filter { it.priority == TaskPriority.URGENT } }
    val p2 = remember(incomplete) { incomplete.filter { it.priority == TaskPriority.HIGH } }
    val p3 = remember(incomplete) { incomplete.filter { it.priority == TaskPriority.MEDIUM } }
    val p4 = remember(incomplete) { incomplete.filter { it.priority == TaskPriority.NONE } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineDividerBottom()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDrawerButton) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.cd_open_drawer),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else if (showTreeToggleButton) {
                IconButton(onClick = onToggleTreePanel, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                        contentDescription = stringResource(R.string.cd_toggle_tree_panel),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = stringResource(R.string.nav_matrix),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = incomplete.size.toString().padStart(2, '0'),
                style = tabular.counter,
                color = colors.accent
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuadrantCard(
                    priority = TaskPriority.URGENT,
                    title = stringResource(R.string.matrix_q1_title),
                    accent = colors.priorityUrgent,
                    tasks = p1,
                    selectedTaskId = selectedTaskId,
                    onSelectTask = onSelectTask,
                    onOpenTaskActions = onOpenTaskActions,
                    onQuickAdd = { onQuickAddWithPriority(TaskPriority.URGENT) },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    priority = TaskPriority.HIGH,
                    title = stringResource(R.string.matrix_q2_title),
                    accent = colors.priorityHigh,
                    tasks = p2,
                    selectedTaskId = selectedTaskId,
                    onSelectTask = onSelectTask,
                    onOpenTaskActions = onOpenTaskActions,
                    onQuickAdd = { onQuickAddWithPriority(TaskPriority.HIGH) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuadrantCard(
                    priority = TaskPriority.MEDIUM,
                    title = stringResource(R.string.matrix_q3_title),
                    accent = colors.priorityMedium,
                    tasks = p3,
                    selectedTaskId = selectedTaskId,
                    onSelectTask = onSelectTask,
                    onOpenTaskActions = onOpenTaskActions,
                    onQuickAdd = { onQuickAddWithPriority(TaskPriority.MEDIUM) },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    priority = TaskPriority.NONE,
                    title = stringResource(R.string.matrix_q4_title),
                    accent = colors.textMuted,
                    tasks = p4,
                    selectedTaskId = selectedTaskId,
                    onSelectTask = onSelectTask,
                    onOpenTaskActions = onOpenTaskActions,
                    onQuickAdd = { onQuickAddWithPriority(TaskPriority.NONE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuadrantCard(
    priority: TaskPriority,
    title: String,
    accent: Color,
    tasks: List<TaskItem>,
    selectedTaskId: String?,
    onSelectTask: (TaskItem) -> Unit,
    onOpenTaskActions: ((TaskItem) -> Unit)? = null,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(ZeroCardShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroCardShape)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                ZeroPriorityBadge(
                    priority = priority,
                    color = accent
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tasks.size.toString().padStart(2, '0'),
                    style = tabular.dateCompact,
                    color = colors.textMuted
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.surfaceElevated)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable(onClick = onQuickAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "—",
                    style = tabular.dateCompact,
                    color = colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = tasks, key = { it.id }) { task ->
                    val selected = task.id == selectedTaskId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroSmallShape)
                            .background(if (selected) colors.accentSubtle else colors.background)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) colors.accent else colors.hairlineBorder
                            )
                            .combinedClickable(
                                onClick = { onSelectTask(task) },
                                onLongClick = onOpenTaskActions?.let { open -> { open(task) } },
                                onLongClickLabel = stringResource(R.string.cd_task_actions)
                            )
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}
