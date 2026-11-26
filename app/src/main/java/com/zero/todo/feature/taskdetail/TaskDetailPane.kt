package com.zero.todo.feature.taskdetail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.notification.ZeroNotificationHelper
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FolderNode
import com.zero.todo.domain.model.TagItem
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.calendar.ZeroMiniCalendar
import com.zero.todo.feature.folder.FolderPickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private data class FlattenedSubtask(
    val task: TaskItem,
    val depth: Int
)

private fun buildFlattenedSubtasks(rootTaskId: String, descendants: List<TaskItem>): List<FlattenedSubtask> {
    if (descendants.isEmpty()) return emptyList()
    val byParent = descendants.groupBy { it.parentTaskId }
    val visited = HashSet<String>(descendants.size)
    val result = mutableListOf<FlattenedSubtask>()

    fun walk(parentId: String, depth: Int) {
        val children = byParent[parentId]
            ?.sortedWith(compareBy<TaskItem>({ it.isCompleted }, { it.sortOrder }, { it.createdAt }))
            ?: return
        for (child in children) {
            if (!visited.add(child.id)) continue
            result.add(FlattenedSubtask(child, depth))
            walk(child.id, depth + 1)
        }
    }

    walk(rootTaskId, 0)
    return result
}

@Composable
fun TaskDetailPane(
    task: TaskItem?,
    currentDate: LocalDate,
    folderName: String?,
    folderTree: List<FolderNode>,
    subtasks: List<TaskItem>,
    parentTask: TaskItem?,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onStartFocus: (task: TaskItem, initialMode: FocusMode) -> Unit,
    onMoveTaskToFolder: (taskId: String, targetFolderId: String?) -> Unit,
    onUpdateTitleAndDescription: (task: TaskItem, title: String, description: String) -> Unit,
    onUpdatePriority: (task: TaskItem, priority: TaskPriority) -> Unit,
    onUpdateEffort: (task: TaskItem, estimatedMinutes: Int?) -> Unit,
    onUpdateFirstStep: (task: TaskItem, firstStep: String?) -> Unit,
    onUpdateDueDate: (task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) -> Unit,
    onUpdateReminder: (task: TaskItem, reminderAtMillis: Long?) -> Unit,
    onUpdateRecurrenceRule: (task: TaskItem, rrule: String?) -> Unit,
    onAddSubtask: (parentTaskId: String, title: String) -> Unit,
    onPromoteSubtask: (subtaskId: String) -> Unit,
    onOpenTaskParentPicker: (TaskItem) -> Unit,
    onOpenRelatedTask: (String) -> Unit,
    onAddTag: (taskId: String, tagName: String) -> Unit,
    onRemoveTag: (taskId: String, tagId: String) -> Unit,
    onSelectTag: (tagId: String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    tagPathById: Map<String, String> = emptyMap(),
    allTags: List<TagItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val context = LocalContext.current
    val activeLocale = LocalConfiguration.current.locales[0]

    var pendingReminderMillis by remember { mutableStateOf<Long?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val pending = pendingReminderMillis
        if (task != null && pending != null) {
            onUpdateReminder(task, pending)
        }
        pendingReminderMillis = null
    }

    fun applyReminderWithPermission(targetTask: TaskItem, reminderMillis: Long?) {
        if (reminderMillis == null) {
            onUpdateReminder(targetTask, null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ZeroNotificationHelper.canPostNotifications(context)
        ) {
            pendingReminderMillis = reminderMillis
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onUpdateReminder(targetTask, reminderMillis)
        }
    }

    if (task == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_no_selection_title),
                    style = tabular.dateCompact,
                    color = colors.textMuted
                )
                Text(
                    text = stringResource(R.string.detail_no_selection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }
        return
    }

    var titleDraft by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var descriptionDraft by rememberSaveable(task.id) { mutableStateOf(task.description) }
    var pendingDescription by remember(task.id) { mutableStateOf<String?>(null) }
    var markdownPreviewMode by rememberSaveable(task.id) { mutableStateOf(false) }
    var checklistItemDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var newSubtaskDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var showTaskActions by rememberSaveable(task.id) { mutableStateOf(false) }
    var newTagDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var showCustomRruleEditor by rememberSaveable(task.id) { mutableStateOf(false) }
    var customRruleDraft by rememberSaveable(task.id) { mutableStateOf(task.recurrenceRule ?: "FREQ=DAILY;INTERVAL=2") }
    var showMiniCalendar by rememberSaveable(task.id) { mutableStateOf(false) }
    var showFolderPicker by rememberSaveable(task.id) { mutableStateOf(false) }

    LaunchedEffect(task.id, task.title, task.description) {
        if (titleDraft != task.title) titleDraft = task.title
        if (pendingDescription == null || pendingDescription == task.description) {
            pendingDescription = null
            descriptionDraft = task.description
        }
    }

    val flattenedSubtasks = remember(task.id, subtasks) {
        buildFlattenedSubtasks(task.id, subtasks)
    }

    val zoneId = ZoneId.systemDefault()
    val currentDueDateTime = remember(task.dueDate) {
        task.dueDate?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDateTime() }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    fun updateDueDateTime(date: LocalDate, time: LocalTime?, allDay: Boolean) {
        val millis = if (allDay || time == null) {
            ZeroDateFormatter.startOfDayMillis(date, zoneId)
        } else {
            LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
        }
        onUpdateDueDate(task, millis, allDay)
    }

    fun updateDescription(updated: String) {
        descriptionDraft = updated
        pendingDescription = updated
        onUpdateTitleAndDescription(task, titleDraft.ifBlank { task.title }, updated)
    }

    fun addChecklistItem() {
        val label = checklistItemDraft.lines().joinToString(" ").trim()
        if (label.isEmpty()) return
        val separator = if (descriptionDraft.isEmpty() || descriptionDraft.endsWith('\n')) "" else "\n"
        checklistItemDraft = ""
        updateDescription("$descriptionDraft$separator- [ ] $label")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Detail Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineDividerBottom()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (showBackButton) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_to_list),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable {
                            haptics.reorderTick()
                            showFolderPicker = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = folderName ?: stringResource(R.string.nav_inbox),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.dialog_move_to_folder_title),
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Just 2 Mins" anti-procrastination direct starter
                Row(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.accentSubtle)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                        .clickable {
                            haptics.heavyClick()
                            onStartFocus(task, FocusMode.TWO_MIN_STARTER)
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.detail_start_2min),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(if (task.isCompleted) colors.accentSubtle else colors.surface)
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = if (task.isCompleted) colors.accent else colors.hairlineBorder
                        )
                        .clickable {
                            haptics.taskComplete()
                            onToggleComplete(task)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (task.isCompleted) R.string.action_completed else R.string.action_done
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.isCompleted) colors.accent else colors.textPrimary
                    )
                }

                Box {
                    IconButton(onClick = { showTaskActions = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.cd_task_actions),
                            tint = colors.textSecondary
                        )
                    }
                    DropdownMenu(expanded = showTaskActions, onDismissRequest = { showTaskActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.task_choose_parent)) },
                            onClick = {
                                showTaskActions = false
                                onOpenTaskParentPicker(task)
                            }
                        )
                        if (task.parentTaskId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_promote_subtask)) },
                                onClick = {
                                    showTaskActions = false
                                    onPromoteSubtask(task.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = colors.priorityUrgent) },
                            onClick = {
                                showTaskActions = false
                                haptics.confirm()
                                onDeleteTask(task)
                            }
                        )
                    }
                }
            }
        }

        // Detail Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (parentTask != null) {
                TextButton(onClick = { onOpenRelatedTask(parentTask.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.task_parent_link, parentTask.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Editable Title
            BasicTextField(
                value = titleDraft,
                onValueChange = { updated ->
                    titleDraft = updated
                    if (updated.isNotBlank()) {
                        onUpdateTitleAndDescription(task, updated, descriptionDraft)
                    }
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )

            // Priority Selector (comfortable 40dp touch height)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.detail_priority),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.width(54.dp)
                )
                TaskPriority.entries.forEach { level ->
                    val selected = task.priority == level
                    val accent = when (level) {
                        TaskPriority.URGENT -> colors.priorityUrgent
                        TaskPriority.HIGH -> colors.priorityHigh
                        TaskPriority.MEDIUM -> colors.priorityMedium
                        TaskPriority.NONE -> colors.textSecondary
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(ZeroSmallShape)
                            .background(if (selected) accent.copy(alpha = 0.16f) else colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                onUpdatePriority(task, level)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P${level.value}",
                            style = tabular.priorityTag,
                            color = if (selected) accent else colors.textMuted
                        )
                    }
                }
            }

            // Effort Tier Selector for Day Capacity Ruler (5m / 25m / 1h)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.detail_effort),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.width(54.dp)
                )
                EffortTier.entries.forEach { tier ->
                    val selected = task.estimatedMinutes == tier.minutes
                    Box(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(ZeroSmallShape)
                            .background(if (selected) colors.accentSubtle else colors.surface)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (selected) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                onUpdateEffort(task, if (selected) null else tier.minutes)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tier.shortCode} · ${stringResource(tier.labelRes)}",
                            style = tabular.dateCompact,
                            color = if (selected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            // Lightweight "First Tiny Step" Icebreaker Field
            var firstStepDraft by rememberSaveable(task.id, task.firstStep) {
                mutableStateOf(task.firstStep.orEmpty())
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surface)
                    .hairlineBorder(shape = ZeroCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.now_first_step_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.background)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (firstStepDraft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.now_first_step_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    BasicTextField(
                        value = firstStepDraft,
                        onValueChange = { updated ->
                            firstStepDraft = updated.take(36)
                            onUpdateFirstStep(task, firstStepDraft.trim().ifBlank { null })
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Due Date Section: Date-Only Lightweight Mode vs Specific Time
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surface)
                    .hairlineBorder(shape = ZeroCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ZeroSmallShape)
                            .clickable {
                                haptics.reorderTick()
                                showMiniCalendar = !showMiniCalendar
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (task.dueDate != null) colors.accent else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (task.dueDate != null) {
                                ZeroDateFormatter.formatDueDateWithContext(
                                    dueDateMillis = task.dueDate,
                                    isAllDay = task.isAllDay,
                                    locale = activeLocale
                                )
                            } else {
                                stringResource(R.string.detail_no_due_date)
                            },
                            style = tabular.dateCompact,
                            color = if (task.dueDate != null) colors.textPrimary else colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Date-Only vs Specific Time toggle
                    Row(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.background)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModeSegmentChip(
                            label = stringResource(R.string.detail_date_only),
                            selected = task.isAllDay,
                            onClick = {
                                val date = currentDueDateTime?.toLocalDate() ?: LocalDate.now()
                                updateDueDateTime(date, null, allDay = true)
                            }
                        )
                        ModeSegmentChip(
                            label = stringResource(R.string.detail_specific_time),
                            selected = !task.isAllDay && task.dueDate != null,
                            onClick = {
                                val date = currentDueDateTime?.toLocalDate() ?: LocalDate.now()
                                val time = currentDueDateTime?.toLocalTime()
                                    ?.takeUnless { task.isAllDay }
                                    ?: LocalTime.of(9, 0)
                                updateDueDateTime(date, time, allDay = false)
                            }
                        )
                    }
                }

                // Quick Date Chips — each displays BOTH intuitive label AND exact MM/dd + weekday
                val todayDate = currentDate
                val tomorrowDate = remember(todayDate) { todayDate.plusDays(1) }
                val dayAfterDate = remember(todayDate) { todayDate.plusDays(2) }
                val nextMonDate = remember(todayDate) { todayDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
                val existingTime = currentDueDateTime?.toLocalTime()?.takeUnless { task.isAllDay }
                val allDay = task.isAllDay || task.dueDate == null
                val selectedLocalDate = currentDueDateTime?.toLocalDate()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickDateOptionTile(
                        title = stringResource(R.string.quick_today),
                        dateHint = ZeroDateFormatter.formatMonthDayWeekday(todayDate, activeLocale),
                        selected = selectedLocalDate == todayDate
                    ) {
                        haptics.reorderTick()
                        updateDueDateTime(todayDate, existingTime, allDay)
                    }
                    QuickDateOptionTile(
                        title = stringResource(R.string.quick_tomorrow),
                        dateHint = ZeroDateFormatter.formatMonthDayWeekday(tomorrowDate, activeLocale),
                        selected = selectedLocalDate == tomorrowDate
                    ) {
                        haptics.reorderTick()
                        updateDueDateTime(tomorrowDate, existingTime, allDay)
                    }
                    QuickDateOptionTile(
                        title = stringResource(R.string.quick_day_after),
                        dateHint = ZeroDateFormatter.formatMonthDayWeekday(dayAfterDate, activeLocale),
                        selected = selectedLocalDate == dayAfterDate
                    ) {
                        haptics.reorderTick()
                        updateDueDateTime(dayAfterDate, existingTime, allDay)
                    }
                    QuickDateOptionTile(
                        title = stringResource(R.string.quick_next_monday),
                        dateHint = ZeroDateFormatter.formatMonthDayWeekday(nextMonDate, activeLocale),
                        selected = selectedLocalDate == nextMonDate
                    ) {
                        haptics.reorderTick()
                        updateDueDateTime(nextMonDate, existingTime, allDay)
                    }

                    // Mini Calendar Toggle Tile
                    Column(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(if (showMiniCalendar) colors.accentSubtle else colors.background)
                            .hairlineBorder(
                                shape = ZeroSmallShape,
                                color = if (showMiniCalendar) colors.accent else colors.hairlineBorder
                            )
                            .clickable {
                                haptics.reorderTick()
                                showMiniCalendar = !showMiniCalendar
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = stringResource(R.string.picker_select_date_title),
                            tint = if (showMiniCalendar) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_pick_calendar),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showMiniCalendar) colors.accent else colors.textMuted
                        )
                    }

                    if (task.dueDate != null) {
                        QuickChip(stringResource(R.string.action_clear)) {
                            haptics.reorderTick()
                            onUpdateDueDate(task, null, true)
                        }
                    }
                }

                // Inline Expandable Mini Calendar for picking any exact date & seeing weekdays
                AnimatedVisibility(
                    visible = showMiniCalendar,
                    enter = expandVertically(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeIn(animationSpec = ZeroMotion.FloatSpring),
                    exit = shrinkVertically(animationSpec = ZeroMotion.IntSizeSpring) +
                        fadeOut(animationSpec = ZeroMotion.FloatSpring)
                ) {
                    ZeroMiniCalendar(
                        selectedDate = selectedLocalDate,
                        onSelectDate = { pickedDate ->
                            updateDueDateTime(pickedDate, existingTime, allDay)
                        }
                    )
                }

                // Specific Time Controls when not All-Day
                AnimatedVisibility(visible = !task.isAllDay && currentDueDateTime != null) {
                    val activeDt = currentDueDateTime ?: LocalDateTime.now().withHour(9).withMinute(0)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeDt.toLocalTime().format(timeFmt),
                                style = tabular.timerMedium,
                                color = colors.accent
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QuickChip("-30m") {
                                    val shifted = activeDt.minusMinutes(30)
                                    updateDueDateTime(shifted.toLocalDate(), shifted.toLocalTime(), false)
                                }
                                QuickChip("+30m") {
                                    val shifted = activeDt.plusMinutes(30)
                                    updateDueDateTime(shifted.toLocalDate(), shifted.toLocalTime(), false)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "09:00" to LocalTime.of(9, 0),
                                "12:00" to LocalTime.of(12, 0),
                                "15:00" to LocalTime.of(15, 0),
                                "18:00" to LocalTime.of(18, 0),
                                "21:00" to LocalTime.of(21, 0)
                            ).forEach { (label, slot) ->
                                QuickChip(label = label, useTabular = true) {
                                    updateDueDateTime(activeDt.toLocalDate(), slot, false)
                                }
                            }
                        }
                    }
                }

                // System Notification Reminder Row
                if (task.dueDate != null) {
                    val baseTrigger = if (task.isAllDay) {
                        val date = currentDueDateTime?.toLocalDate() ?: currentDate
                        LocalDateTime.of(date, LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
                    } else {
                        task.dueDate
                    }
                    val activeOffset = task.reminderAt?.let { baseTrigger - it }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hairlineDividerTop()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = if (task.reminderAt != null) colors.accent else colors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_reminder),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickChip(
                                label = stringResource(R.string.reminder_off),
                                selected = task.reminderAt == null
                            ) {
                                haptics.reorderTick()
                                applyReminderWithPermission(task, null)
                            }
                            QuickChip(
                                label = stringResource(R.string.reminder_on_time),
                                selected = activeOffset == 0L
                            ) {
                                haptics.reorderTick()
                                applyReminderWithPermission(task, baseTrigger)
                            }
                            QuickChip(
                                label = stringResource(R.string.reminder_15m_before),
                                selected = activeOffset == 15L * 60_000L
                            ) {
                                haptics.reorderTick()
                                applyReminderWithPermission(task, baseTrigger - 15L * 60_000L)
                            }
                            QuickChip(
                                label = stringResource(R.string.reminder_1h_before),
                                selected = activeOffset == 60L * 60_000L
                            ) {
                                haptics.reorderTick()
                                applyReminderWithPermission(task, baseTrigger - 60L * 60_000L)
                            }
                        }
                    }
                }
            }

            // Recurrence Rule (RRULE) Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surface)
                    .hairlineBorder(shape = ZeroCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = null,
                            tint = if (task.recurrenceRule != null) colors.accent else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_recurrence),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                    Text(
                        text = RecurrenceRule.formatLabel(task.recurrenceRule, activeLocale),
                        style = tabular.dateCompact,
                        color = if (task.recurrenceRule != null) colors.accent else colors.textMuted
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        stringResource(R.string.recur_none) to null,
                        stringResource(R.string.recur_daily) to RecurrenceRule.PRESET_DAILY.toRruleString(),
                        stringResource(R.string.recur_weekdays) to RecurrenceRule.PRESET_WEEKDAYS.toRruleString(),
                        stringResource(R.string.recur_weekly) to RecurrenceRule.PRESET_WEEKLY.toRruleString(),
                        stringResource(R.string.recur_monthly) to RecurrenceRule.PRESET_MONTHLY.toRruleString()
                    )
                    presets.forEach { (label, rrule) ->
                        val active = task.recurrenceRule == rrule
                        QuickChip(
                            label = label,
                            selected = active
                        ) {
                            haptics.reorderTick()
                            showCustomRruleEditor = false
                            onUpdateRecurrenceRule(task, rrule)
                        }
                    }
                    QuickChip(
                        label = stringResource(R.string.recur_custom),
                        selected = showCustomRruleEditor
                    ) {
                        showCustomRruleEditor = !showCustomRruleEditor
                    }
                }

                AnimatedVisibility(visible = showCustomRruleEditor) {
                    val parsedCustom = remember(customRruleDraft) { RecurrenceRule.parse(customRruleDraft) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ZeroSmallShape)
                                    .background(colors.background)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (parsedCustom != null) colors.accent else colors.hairlineBorder
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = customRruleDraft,
                                    onValueChange = { customRruleDraft = it },
                                    singleLine = true,
                                    textStyle = tabular.timestamp.copy(color = colors.textPrimary),
                                    cursorBrush = SolidColor(colors.accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(if (parsedCustom != null) colors.accent else colors.surfaceElevated)
                                    .clickable(enabled = parsedCustom != null) {
                                        val normalized = parsedCustom?.toRruleString() ?: return@clickable
                                        haptics.confirm()
                                        onUpdateRecurrenceRule(task, normalized)
                                        showCustomRruleEditor = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_apply),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (parsedCustom != null) colors.onAccent else colors.textMuted
                                )
                            }
                        }
                        Text(
                            text = parsedCustom?.let {
                                stringResource(R.string.recur_parsed_preview, it.toDisplayLabel(activeLocale))
                            } ?: stringResource(R.string.recur_rrule_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (parsedCustom != null) colors.accent else colors.textMuted
                        )
                    }
                }
            }

            // Tags Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerTop()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_tags),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    task.tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .padding(start = 10.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "#${tagPathById[tag.id] ?: tag.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .clickable {
                                        haptics.reorderTick()
                                        onSelectTag(tag.id)
                                    }
                                    .padding(vertical = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(ZeroSmallShape)
                                    .clickable { onRemoveTag(task.id, tag.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_remove_tag),
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        if (newTagDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_add_tag_placeholder),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                        BasicTextField(
                            value = newTagDraft,
                            onValueChange = { newTagDraft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val clean = newTagDraft.trim().removePrefix("#")
                                    if (clean.isNotEmpty()) {
                                        onAddTag(task.id, clean)
                                        newTagDraft = ""
                                    }
                                }
                            )
                        )
                    }
                }

                val attachedIds = remember(task.tags) { task.tags.map { it.id }.toSet() }
                val tagSuggestions = remember(allTags, attachedIds, newTagDraft, tagPathById) {
                    val q = newTagDraft.trim().removePrefix("#").lowercase()
                    allTags.asSequence()
                        .filter { it.id !in attachedIds }
                        .map { it to (tagPathById[it.id] ?: it.name) }
                        .filter { (_, path) -> q.isEmpty() || path.lowercase().contains(q) }
                        .sortedBy { (_, path) -> path.lowercase() }
                        .take(12)
                        .toList()
                }
                if (tagSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tagSuggestions.forEach { (candidate, path) ->
                            Box(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(colors.background)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .clickable {
                                        haptics.reorderTick()
                                        onAddTag(task.id, path)
                                        newTagDraft = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "+ #$path",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Related tasks open the same detail editor at every depth.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerTop()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.detail_subtasks),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                    val doneSub = flattenedSubtasks.count { it.task.isCompleted }
                    Text(
                        text = "$doneSub/${flattenedSubtasks.size}",
                        style = tabular.counter,
                        color = colors.textSecondary
                    )
                }

                flattenedSubtasks.forEach { entry ->
                    val sub = entry.task
                    val indent = (entry.depth.coerceAtMost(ZeroDimens.MaxVisibleTreeDepth) * 12).dp
                    val completionLabel = stringResource(if (sub.isCompleted) R.string.cd_reopen_task else R.string.cd_complete_task)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent)
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptics.taskComplete()
                                onToggleComplete(sub)
                            },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = completionLabel }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(ZeroSmallShape)
                                    .background(if (sub.isCompleted) colors.accent else colors.background)
                                    .hairlineBorder(shape = ZeroSmallShape, color = if (sub.isCompleted) colors.accent else colors.textMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sub.isCompleted) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable { onOpenRelatedTask(sub.id) }
                                .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (sub.isCompleted) colors.textMuted else colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                sub.dueDate?.let { dueDate ->
                                    Text(
                                        text = ZeroDateFormatter.formatDueDate(dueDate, sub.isAllDay, zoneId = zoneId, locale = activeLocale),
                                        style = tabular.dateCompact,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Add root subtask row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (newSubtaskDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_add_subtask_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        BasicTextField(
                            value = newSubtaskDraft,
                            onValueChange = { newSubtaskDraft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val clean = newSubtaskDraft.trim()
                                    if (clean.isNotEmpty()) {
                                        haptics.confirm()
                                        onAddSubtask(task.id, clean)
                                        newSubtaskDraft = ""
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Markdown Notes Editor + Preview Switcher
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerTop()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.detail_markdown_notes),
                        style = tabular.dateCompact,
                        color = colors.textMuted
                    )
                    Row(
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroSmallShape)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModeSegmentChip(
                            label = stringResource(R.string.detail_mode_edit),
                            selected = !markdownPreviewMode,
                            onClick = { markdownPreviewMode = false }
                        )
                        ModeSegmentChip(
                            label = stringResource(R.string.detail_mode_preview),
                            selected = markdownPreviewMode,
                            onClick = { markdownPreviewMode = true }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZeroCardShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroCardShape)
                        .padding(12.dp)
                ) {
                    if (markdownPreviewMode) {
                        ZeroMarkdownPreview(markdown = descriptionDraft, onToggleChecklistItem = { lineIndex ->
                            val updated = toggleMarkdownChecklistItem(descriptionDraft, lineIndex)
                            if (updated != descriptionDraft) {
                                updateDescription(updated)
                            }
                        })
                    } else {
                        if (descriptionDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_markdown_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }
                        BasicTextField(
                            value = descriptionDraft,
                            onValueChange = ::updateDescription,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            minLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (markdownPreviewMode) {
                    OutlinedTextField(
                        value = checklistItemDraft,
                        onValueChange = { checklistItemDraft = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.detail_add_checklist_item)) },
                        trailingIcon = {
                            IconButton(onClick = ::addChecklistItem, enabled = checklistItemDraft.isNotBlank()) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.detail_add_checklist_item))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addChecklistItem() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folderTree = folderTree,
            currentFolderId = task.folderId,
            onSelectFolder = { targetFolderId ->
                haptics.confirm()
                onMoveTaskToFolder(task.id, targetFolderId)
            },
            onDismiss = { showFolderPicker = false }
        )
    }
}

@Composable
private fun QuickDateOptionTile(
    title: String,
    dateHint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Column(
        modifier = Modifier
            .clip(ZeroSmallShape)
            .background(if (selected) colors.accentSubtle else colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textPrimary
        )
        Text(
            text = dateHint,
            style = tabular.dateCompact,
            color = if (selected) colors.accent else colors.textMuted
        )
    }
}

@Composable
private fun ModeSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Box(
        modifier = Modifier
            .clip(ZeroSmallShape)
            .background(if (selected) colors.surfaceElevated else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textMuted
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean = false,
    useTabular: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    Box(
        modifier = Modifier
            .clip(ZeroSmallShape)
            .background(if (selected) colors.accentSubtle else colors.background)
            .hairlineBorder(
                shape = ZeroSmallShape,
                color = if (selected) colors.accent else colors.hairlineBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = if (useTabular) tabular.dateCompact else MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}
