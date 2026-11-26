package com.zero.todo.feature.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.TaskSwipeActions
import com.zero.todo.feature.folder.IncludeSubfoldersPill
import com.zero.todo.feature.folder.NavigationDestination
import com.zero.todo.feature.now.DayCapacityRuler
import java.time.LocalDate

@Composable
fun TaskListPane(
    currentDate: LocalDate,
    title: String,
    breadcrumbs: List<Folder>,
    selectedDestination: NavigationDestination,
    includeSubfolders: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    taskSortMode: TaskSortMode,
    scheduledTodayMinutes: Int,
    dailyCapacityMinutes: Int,
    triageCount: Int,
    tasks: List<TaskItem>,
    allActiveTasks: List<TaskItem>,
    folderNameById: Map<String, String>,
    selectedTaskId: String?,
    antiMisclickMode: AntiMisclickMode,
    taskSwipeActions: TaskSwipeActions,
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
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSetSortMode: (TaskSortMode) -> Unit,
    onRescheduleOverdueToToday: () -> Unit,
    onOpenTriage: () -> Unit,
    onSelectTag: (String) -> Unit,
    onRequestCreateSubfolder: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onIndentTask: (TaskItem, String) -> Unit,
    onOutdentTask: (TaskItem) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onReorderTask: (task: TaskItem, parentTaskId: String?, beforeTaskId: String?) -> Unit,
    onQuickInputEdit: ((String) -> String) -> Unit,
    onQuickInputSubmit: () -> Unit,
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
    val searchFocusRequester = remember { FocusRequester() }

    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var dragOriginCenter by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragScrollDistance by remember { mutableFloatStateOf(0f) }
    var dragTargets by remember { mutableStateOf<TaskDragTargets?>(null) }
    var draggedItemPin by remember { mutableStateOf<PinnableContainer.PinnedHandle?>(null) }
    var showCompletedSection by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val todayStartMillis = remember(currentDate) { ZeroDateFormatter.startOfDayMillis(currentDate) }
    val showOverdueSplit = selectedDestination == NavigationDestination.Today &&
        (!isSearchOpen || searchQuery.isBlank())

    val activeTasks = remember(tasks) { tasks.filterNot { it.isCompleted } }
    val overdueTasks = remember(activeTasks, showOverdueSplit, todayStartMillis) {
        if (showOverdueSplit) {
            activeTasks.filter { it.dueDate != null && it.dueDate < todayStartMillis }
        } else {
            emptyList()
        }
    }
    val regularActiveTasks = remember(activeTasks, showOverdueSplit, todayStartMillis) {
        if (showOverdueSplit) {
            activeTasks.filterNot { it.dueDate != null && it.dueDate < todayStartMillis }
        } else {
            activeTasks
        }
    }
    val completedTasks = remember(tasks) { tasks.filter { it.isCompleted } }
    val incompleteCount = activeTasks.size
    val allById = remember(allActiveTasks) { allActiveTasks.associateBy { it.id } }
    val filtering = isSearchOpen && searchQuery.isNotBlank()
    val hierarchical = !filtering && selectedDestination.showsTaskHierarchy
    val reorderEnabled = hierarchical && taskSortMode == TaskSortMode.MANUAL

    val overdueRows = remember(overdueTasks, allById) { buildTaskListRows(overdueTasks, allById, false) }
    val activeRows = remember(regularActiveTasks, allById, hierarchical) {
        buildTaskListRows(regularActiveTasks, allById, hierarchical)
    }
    val completedRows = remember(completedTasks, allById, hierarchical) {
        buildTaskListRows(completedTasks, allById, hierarchical)
    }

    fun endDrag(taskId: String) {
        if (draggingTaskId != taskId) return
        draggingTaskId = null
        dragTargets = null
        draggedItemPin?.release()
        draggedItemPin = null
    }

    val draggingId = draggingTaskId
    fun currentDropSlot(): TaskDropSlot? {
        val center = dragOriginCenter + dragDistance
        return dragTargets?.slotAt(center, lazyListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            (item.key as? String)?.let { TaskDropPosition(it, item.offset + item.size / 2f) }
        })
    }
    val dropSlot = currentDropSlot()

    val edgePx = with(LocalDensity.current) { 48.dp.toPx() }
    val maxScrollPerFrame = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(draggingId, reorderEnabled) {
        if (draggingId == null) return@LaunchedEffect
        if (!reorderEnabled) {
            endDrag(draggingId)
            return@LaunchedEffect
        }
        while (draggingTaskId == draggingId) {
            withFrameNanos { }
            val layout = lazyListState.layoutInfo
            val center = dragOriginCenter + dragDistance
            val top = layout.viewportStartOffset + edgePx
            val bottom = layout.viewportEndOffset - edgePx
            val factor = when {
                center < top && dragDistance < 0f -> ((center - top) / edgePx).coerceAtLeast(-1f)
                center > bottom && dragDistance > 0f -> ((center - bottom) / edgePx).coerceAtMost(1f)
                else -> 0f
            }
            if (factor != 0f) {
                val scrolled = lazyListState.scrollBy(factor * maxScrollPerFrame)
                if (draggingTaskId == draggingId) dragScrollDistance += scrolled
            }
        }
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (showTreeToggleButton) {
                        IconButton(
                            onClick = onToggleTreePanel,
                            modifier = Modifier.size(36.dp)
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

                    // Group 1: Framed 36.dp Pill Pair (Subfolder Filter + Sort)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedDestination is NavigationDestination.FolderView) {
                            IncludeSubfoldersPill(
                                includeSubfolders = includeSubfolders,
                                onToggle = onToggleIncludeSubfolders
                            )
                        }

                        Box {
                            val automaticSort = taskSortMode != TaskSortMode.MANUAL
                            val sortColor = if (automaticSort) colors.accent else colors.textSecondary
                            Row(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(ZeroSmallShape)
                                    .background(if (automaticSort) colors.accentSubtle else colors.surface)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (automaticSort) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = stringResource(R.string.cd_task_sort),
                                    tint = sortColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(taskSortMode.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sortColor,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                TaskSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes)) },
                                        trailingIcon = {
                                            if (mode == taskSortMode) Icon(Icons.Rounded.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            showSortMenu = false
                                            haptics.reorderTick()
                                            onSetSortMode(mode)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Group 2: Uniform 36.dp Icon Actions (Subfolder + Search + Focus Timer)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedDestination is NavigationDestination.FolderView) {
                            IconButton(
                                onClick = onRequestCreateSubfolder,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = stringResource(R.string.cd_create_subfolder),
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onToggleSearch(!isSearchOpen) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.cd_search_tasks),
                                tint = if (isSearchOpen) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenFocusMode,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = stringResource(R.string.cd_enter_focus),
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = incompleteCount.toString().padStart(2, '0'),
                        style = tabular.counter,
                        color = colors.accent
                    )
                }

                // Inline Global Search Bar
                AnimatedVisibility(visible = isSearchOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (searchQuery.isNotBlank()) colors.accent else colors.hairlineBorder
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onSearchQueryChange("") }
                            )
                        }
                    }
                }

                // Breadcrumb trail for nested folders
                if (breadcrumbs.size > 1 && !isSearchOpen) {
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
                            text = stringResource(
                                if (isSearchOpen && searchQuery.isNotBlank()) R.string.search_empty_hint
                                else R.string.empty_tasks_hint
                            ),
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
                    // Precision Day Capacity Ruler inside Today view
                    if (selectedDestination == NavigationDestination.Today && !isSearchOpen) {
                        item(key = "day_capacity_ruler") {
                            DayCapacityRuler(
                                scheduledMinutes = scheduledTodayMinutes,
                                capacityMinutes = dailyCapacityMinutes,
                                taskCount = activeTasks.size
                            )
                        }
                    }

                    // Lightning Triage Deck Entry inside Inbox / Today when backlog exists
                    if ((selectedDestination == NavigationDestination.Inbox || selectedDestination == NavigationDestination.Today) &&
                        triageCount > 0 && !isSearchOpen
                    ) {
                        item(key = "triage_shortcut_strip") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .clip(ZeroSmallShape)
                                    .background(colors.accentSubtle)
                                    .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                    .clickable {
                                        haptics.confirm()
                                        onOpenTriage()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.triage_banner_prompt, triageCount),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.accent
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.triage_start_short),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent
                                )
                            }
                        }
                    }

                    // Overdue Section (Today smart view only) with 1-Tap Reschedule to Today
                    if (overdueTasks.isNotEmpty()) {
                        item(key = "overdue_section_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ZeroSmallShape)
                                    .background(colors.priorityUrgent.copy(alpha = 0.10f))
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = colors.priorityUrgent.copy(alpha = 0.45f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.section_overdue_tasks),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.priorityUrgent
                                    )
                                    Text(
                                        text = overdueTasks.size.toString().padStart(2, '0'),
                                        style = tabular.dateCompact,
                                        color = colors.priorityUrgent
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(ZeroSmallShape)
                                        .background(colors.surface)
                                        .hairlineBorder(shape = ZeroSmallShape, color = colors.priorityUrgent.copy(alpha = 0.5f))
                                        .clickable {
                                            haptics.confirm()
                                            onRescheduleOverdueToToday()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Update,
                                        contentDescription = null,
                                        tint = colors.priorityUrgent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.action_reschedule_today),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.priorityUrgent
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = overdueRows,
                            key = { _, row -> "overdue_${row.task.id}" }
                        ) { _, row ->
                            val task = row.task
                            AntiMisclickTaskCard(
                                task = task,
                                isSelected = task.id == selectedTaskId,
                                isListScrolling = lazyListState.isScrollInProgress,
                                isDragging = false,
                                dragOffsetY = 0f,
                                folderBadgeName = task.folderId?.let { folderNameById[it] },
                                antiMisclickMode = antiMisclickMode,
                                swipeActions = taskSwipeActions,
                                onToggleComplete = onToggleTaskComplete,
                                onDeleteTask = onDeleteTask,
                                indentParentId = row.indentParentId,
                                onIndent = onIndentTask,
                                onOutdent = onOutdentTask,
                                onSelectTask = onSelectTask,
                                onTagClick = onSelectTag,
                                modifier = Modifier.animateItem(placementSpec = ZeroMotion.IntOffsetSpring)
                                    .padding(start = taskIndent(row.depth))
                            )
                        }

                        if (regularActiveTasks.isNotEmpty()) {
                            item(key = "today_section_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.nav_today),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = regularActiveTasks.size.toString().padStart(2, '0'),
                                        style = tabular.dateCompact,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    }

                    // Active Tasks
                    itemsIndexed(
                        items = activeRows,
                        key = { _, row -> row.task.id }
                    ) { _, row ->
                        val task = row.task
                        val isDragging = draggingTaskId == task.id
                        val pinnable = LocalPinnableContainer.current
                        val showFolderBadge = isSearchOpen ||
                            selectedDestination !is NavigationDestination.FolderView ||
                            task.folderId != selectedDestination.folderId
                        val showBeforeMarker = dropSlot?.beforeTaskId == task.id
                        val showAfterMarker = dropSlot != null && dropSlot.beforeTaskId == null &&
                            dragTargets?.lastId == task.id

                        AntiMisclickTaskCard(
                            task = task,
                            isSelected = task.id == selectedTaskId,
                            isListScrolling = !isDragging && (lazyListState.isScrollInProgress || draggingId != null),
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragDistance + dragScrollDistance else 0f,
                            folderBadgeName = if (showFolderBadge) task.folderId?.let { folderNameById[it] } else null,
                            antiMisclickMode = antiMisclickMode,
                            swipeActions = taskSwipeActions,
                            onToggleComplete = onToggleTaskComplete,
                            onDeleteTask = onDeleteTask,
                            indentParentId = row.indentParentId,
                            onIndent = onIndentTask,
                            onOutdent = onOutdentTask,
                            onSelectTask = onSelectTask,
                            onTagClick = onSelectTag,
                            reorderEnabled = reorderEnabled,
                            onReorderDragStart = {
                                val measured = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == task.id }
                                if (measured != null && draggingTaskId == null) {
                                    draggedItemPin = pinnable?.pin()
                                    dragOriginCenter = measured.offset + measured.size / 2f
                                    dragDistance = 0f
                                    dragScrollDistance = 0f
                                    dragTargets = TaskDragTargets(activeRows, task.id, allById)
                                    draggingTaskId = task.id
                                }
                            },
                            onReorderDragDelta = { deltaY ->
                                if (draggingTaskId == task.id) dragDistance += deltaY
                            },
                            onReorderDragEnd = {
                                if (draggingTaskId == task.id) {
                                    val target = currentDropSlot()
                                    endDrag(task.id)
                                    target?.let { onReorderTask(task, it.parentTaskId, it.beforeTaskId) }
                                }
                            },
                            onReorderDragCancel = { endDrag(task.id) },
                            modifier = Modifier
                                .animateItem(placementSpec = ZeroMotion.IntOffsetSpring)
                                .zIndex(if (isDragging) 10f else 0f)
                                .then(if (showBeforeMarker || showAfterMarker) Modifier.drawWithContent {
                                    drawContent()
                                    val y = if (showBeforeMarker) 1.dp.toPx() else size.height - 1.dp.toPx()
                                    drawLine(colors.accent, Offset(taskIndent(dropSlot?.depth ?: 0).toPx(), y),
                                        Offset(size.width, y), strokeWidth = 2.dp.toPx())
                                } else Modifier)
                                .padding(start = taskIndent(row.depth))
                        )
                    }

                    // Collapsible Completed Tasks Section for calm OCD visual order
                    if (completedTasks.isNotEmpty()) {
                        item(key = "completed_section_header") {
                            val arrowRot by animateFloatAsState(
                                targetValue = if (showCompletedSection) 90f else 0f,
                                animationSpec = ZeroMotion.FloatSpring,
                                label = "completed_chevron"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(ZeroSmallShape)
                                    .background(colors.surface)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .clickable {
                                        haptics.reorderTick()
                                        showCompletedSection = !showCompletedSection
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(arrowRot)
                                    )
                                    Text(
                                        text = stringResource(R.string.section_completed_tasks),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                }
                                Text(
                                    text = completedTasks.size.toString().padStart(2, '0'),
                                    style = tabular.counter,
                                    color = colors.textMuted
                                )
                            }
                        }

                        if (showCompletedSection) {
                            itemsIndexed(
                                items = completedRows,
                                key = { _, row -> row.task.id }
                            ) { _, row ->
                                val task = row.task
                                val showFolderBadge = isSearchOpen ||
                                    selectedDestination !is NavigationDestination.FolderView ||
                                    (includeSubfolders && task.folderId != selectedDestination.folderId)
                                AntiMisclickTaskCard(
                                    task = task,
                                    isSelected = task.id == selectedTaskId,
                                    isListScrolling = lazyListState.isScrollInProgress,
                                    isDragging = false,
                                    dragOffsetY = 0f,
                                    folderBadgeName = if (showFolderBadge) task.folderId?.let { folderNameById[it] } else null,
                                    antiMisclickMode = antiMisclickMode,
                                    swipeActions = taskSwipeActions,
                                    onToggleComplete = onToggleTaskComplete,
                                    onDeleteTask = onDeleteTask,
                                    indentParentId = row.indentParentId,
                                    onIndent = onIndentTask,
                                    onOutdent = onOutdentTask,
                                    onSelectTask = onSelectTask,
                                    onTagClick = onSelectTag,
                                    modifier = Modifier.animateItem(placementSpec = ZeroMotion.IntOffsetSpring)
                                        .padding(start = taskIndent(row.depth))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Undo Capsule + Persistent Quick Input Capsule
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
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
                onEditText = onQuickInputEdit,
                onSubmit = onQuickInputSubmit,
                onToggleContinuousEntry = onToggleContinuousEntry,
                onVoiceInputRecognized = onVoiceInputRecognized,
                currentDate = currentDate
            )
        }
    }
}

private fun taskIndent(depth: Int) = ZeroDimens.TreeIndentPerLevel * depth.coerceAtMost(ZeroDimens.MaxVisibleTreeDepth)
