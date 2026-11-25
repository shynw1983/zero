package com.zero.todo.feature.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.TaskItem

@Composable
fun TrashPane(
    deletedTasks: List<TaskItem>,
    showDrawerButton: Boolean,
    showTreeToggleButton: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleTreePanel: () -> Unit,
    onRestoreTask: (String) -> Unit,
    onPermanentlyDeleteTask: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    var showEmptyConfirm by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.nav_trash),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            if (deletedTasks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.priorityUrgent.copy(alpha = 0.6f))
                        .clickable { showEmptyConfirm = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.trash_empty_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.priorityUrgent
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = deletedTasks.size.toString().padStart(2, '0'),
                style = tabular.counter,
                color = colors.textMuted
            )
        }

        if (deletedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "00",
                        style = tabular.timerMedium,
                        color = colors.textMuted
                    )
                    Text(
                        text = stringResource(R.string.trash_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = deletedTasks, key = { it.id }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroCardShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroCardShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(colors.accentSubtle)
                                .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                .clickable {
                                    haptics.confirm()
                                    onRestoreTask(task.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestoreFromTrash,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.action_restore),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptics.confirm()
                                onPermanentlyDeleteTask(task.id)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = stringResource(R.string.action_delete_forever),
                                tint = colors.textMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        Dialog(onDismissRequest = { showEmptyConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surfaceElevated)
                    .hairlineBorder(shape = ZeroCardShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.trash_empty_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = stringResource(R.string.trash_empty_confirm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .clickable { showEmptyConfirm = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.priorityUrgent)
                            .clickable {
                                showEmptyConfirm = false
                                haptics.confirm()
                                onEmptyTrash()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.trash_empty_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onAccent
                        )
                    }
                }
            }
        }
    }
}
