package com.zero.todo.feature.tasklist

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.feature.folder.IncludeSubfoldersPill
import com.zero.todo.feature.folder.NavigationDestination

@Composable
fun TaskListPane(
    title: String,
    breadcrumbs: List<Folder>,
    selectedDestination: NavigationDestination,
    includeSubfolders: Boolean,
    tasks: List<TaskItem>,
    folderNameById: Map<String, String>,
    selectedTaskId: String?,
    antiMisclickMode: AntiMisclickMode,
    quickInputText: String,
    parsedQuickInput: ParsedQuickInput,
    folderSuggestions: List<FolderPathSuggestion>,
    continuousEntryEnabled: Boolean,
    undoBanner: UndoBannerState?,
    quickInputFocusRequester: FocusRequester,
    showDrawerButton: Boolean,
    showTreeToggleButton: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleTreePanel: () -> Unit,
    onSelectBreadcrumb: (String) -> Unit,
    onToggleIncludeSubfolders: () -> Unit,
    onRequestCreateSubfolder: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onReorderTask: (taskId: String, targetIndex: Int) -> Unit,
    onQuickInputChange: (String) -> Unit,
    onQuickInputSubmit: () -> Unit,
    onSelectFolderSuggestion: (FolderPathSuggestion) -> Unit,
    onToggleContinuousEntry: () -> Unit,
    onVoiceInputRecognized: (String) -> Unit,
    onUndo: (UndoBannerState) -> Unit,
    onUndoTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val lazyListState = rememberLazyListState()

    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }

    val incompleteCount = remember(tasks) { tasks.count { !it.isCompleted } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimalist Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (showTreeToggleButton) {
                        IconButton(
                            onClick = onToggleTreePanel,
                            modifier = Modifier.size(34.dp)
                        ) {
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
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (selectedDestination is NavigationDestination.FolderView) {
                        IncludeSubfoldersPill(
                            includeSubfolders = includeSubfolders,
                            onToggle = onToggleIncludeSubfolders
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onRequestCreateSubfolder,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = stringResource(R.string.cd_create_subfolder),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onOpenFocusMode,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.cd_enter_focus),
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = incompleteCount.toString().padStart(2, '0'),
                        style = tabular.counter,
                        color = colors.accent
                    )
                }

                // Breadcrumb trail for nested folders
                if (breadcrumbs.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.padding(start = if (showDrawerButton || showTreeToggleButton) 38.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { idx, folder ->
                            if (idx > 0) {
                                Text(
                                    text = "/",
                                    style = tabular.dateCompact,
                                    color = colors.textMuted
                                )
                            }
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx == breadcrumbs.lastIndex) colors.textSecondary else colors.textMuted,
                                modifier = Modifier.clickable { onSelectBreadcrumb(folder.id) }
                            )
                        }
                    }
                }
            }

            // Task List
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 96.dp),
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
                            text = stringResource(R.string.empty_tasks_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = tasks,
                        key = { _, task -> task.id }
                    ) { index, task ->
                        val isDragging = draggingTaskId == task.id
                        val showFolderBadge = selectedDestination !is NavigationDestination.FolderView ||
                            (includeSubfolders && task.folderId != selectedDestination.folderId)

                        AntiMisclickTaskCard(
                            task = task,
                            isSelected = task.id == selectedTaskId,
                            isListScrolling = lazyListState.isScrollInProgress,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) draggingOffsetY else 0f,
                            folderBadgeName = if (showFolderBadge) task.folderId?.let { folderNameById[it] } else null,
                            antiMisclickMode = antiMisclickMode,
                            onToggleComplete = onToggleTaskComplete,
                            onSelectTask = onSelectTask,
                            onReorderDragStart = {
                                draggingTaskId = task.id
                                draggingOffsetY = 0f
                            },
                            onReorderDragDelta = { deltaY ->
                                if (draggingTaskId == task.id) {
                                    draggingOffsetY += deltaY
                                    val stepPx = 140f
                                    if (draggingOffsetY > stepPx && index < tasks.lastIndex) {
                                        draggingOffsetY -= stepPx
                                        haptics.reorderTick()
                                        onReorderTask(task.id, index + 1)
                                    } else if (draggingOffsetY < -stepPx && index > 0) {
                                        draggingOffsetY += stepPx
                                        haptics.reorderTick()
                                        onReorderTask(task.id, index - 1)
                                    }
                                }
                            },
                            onReorderDragEnd = {
                                draggingTaskId = null
                                draggingOffsetY = 0f
                            },
                            modifier = Modifier
                                .animateItem(placementSpec = ZeroMotion.IntOffsetSpring)
                                .zIndex(if (isDragging) 10f else 0f)
                        )
                    }
                }
            }
        }

        // Bottom Floating Undo Capsule + Persistent Quick Input Capsule
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UndoCapsule(
                banner = undoBanner,
                onUndo = onUndo,
                onTimeout = onUndoTimeout
            )
            QuickInputCapsule(
                text = quickInputText,
                parsed = parsedQuickInput,
                folderSuggestions = folderSuggestions,
                continuousEntryEnabled = continuousEntryEnabled,
                focusRequester = quickInputFocusRequester,
                onTextChange = onQuickInputChange,
                onSubmit = onQuickInputSubmit,
                onSelectFolderSuggestion = onSelectFolderSuggestion,
                onToggleContinuousEntry = onToggleContinuousEntry,
                onVoiceInputRecognized = onVoiceInputRecognized
            )
        }
    }
}
