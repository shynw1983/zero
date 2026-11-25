package com.zero.todo.feature.taskdetail

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FolderNode
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
    folderName: String?,
    folderTree: List<FolderNode>,
    subtasks: List<TaskItem>,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onStartFocus: (task: TaskItem, initialMode: FocusMode) -> Unit,
    onMoveTaskToFolder: (taskId: String, targetFolderId: String?) -> Unit,
    onUpdateTitleAndDescription: (task: TaskItem, title: String, description: String) -> Unit,
    onUpdatePriority: (task: TaskItem, priority: TaskPriority) -> Unit,
    onUpdateDueDate: (task: TaskItem, dueDateMillis: Long?, isAllDay: Boolean) -> Unit,
    onUpdateRecurrenceRule: (task: TaskItem, rrule: String?) -> Unit,
    onAddSubtask: (parentTaskId: String, title: String) -> Unit,
    onToggleSubtask: (TaskItem) -> Unit,
    onPromoteSubtask: (subtaskId: String) -> Unit,
    onDeleteSubtask: (TaskItem) -> Unit,
    onAddTag: (taskId: String, tagName: String) -> Unit,
    onRemoveTag: (taskId: String, tagId: String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val activeLocale = LocalConfiguration.current.locales[0]

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
    var markdownPreviewMode by rememberSaveable(task.id) { mutableStateOf(false) }
    var newSubtaskDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var activeChildSubtaskParentId by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    var nestedSubtaskDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var newTagDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var showCustomRruleEditor by rememberSaveable(task.id) { mutableStateOf(false) }
    var customRruleDraft by rememberSaveable(task.id) { mutableStateOf(task.recurrenceRule ?: "FREQ=DAILY;INTERVAL=2") }
    var showMiniCalendar by rememberSaveable(task.id) { mutableStateOf(false) }
    var showFolderPicker by rememberSaveable(task.id) { mutableStateOf(false) }

    LaunchedEffect(task.id, task.title, task.description) {
        if (titleDraft != task.title) titleDraft = task.title
        if (descriptionDraft != task.description) descriptionDraft = task.description
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                IconButton(
                    onClick = {
                        haptics.confirm()
                        onDeleteTask(task)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_task),
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
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

            // Priority Selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "P${level.value}",
                            style = tabular.priorityTag,
                            color = if (selected) accent else colors.textMuted
                        )
                    }
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
                val todayDate = remember { LocalDate.now() }
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
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_remove_tag),
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onRemoveTag(task.id, tag.id) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(120.dp)
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
            }

            // Infinite Nested Subtasks Section with One-Click Promote to Main Task
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
                    val indentDp = (entry.depth.coerceAtMost(ZeroDimens.MaxVisibleTreeDepth) * 14).dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                            // Subtask complete checkbox
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(ZeroSmallShape)
                                    .background(if (sub.isCompleted) colors.accent else colors.background)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (sub.isCompleted) colors.accent else colors.textMuted
                                    )
                                    .clickable {
                                        haptics.taskComplete()
                                        onToggleSubtask(sub)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (sub.isCompleted) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = colors.onAccent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            if (entry.depth > ZeroDimens.MaxVisibleTreeDepth) {
                                Text(
                                    text = "L${entry.depth + 1}",
                                    style = tabular.dateCompact,
                                    color = colors.accent
                                )
                            }

                            Text(
                                text = sub.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (sub.isCompleted) colors.textMuted else colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Add nested child subtask under this subtask
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(ZeroSmallShape)
                                    .clickable {
                                        activeChildSubtaskParentId =
                                            if (activeChildSubtaskParentId == sub.id) null else sub.id
                                        nestedSubtaskDraft = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SubdirectoryArrowRight,
                                    contentDescription = stringResource(R.string.cd_add_nested_subtask),
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // One-click promote subtask to top-level main task
                            Row(
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(colors.surfaceElevated)
                                    .hairlineBorder(shape = ZeroSmallShape)
                                    .clickable {
                                        haptics.confirm()
                                        onPromoteSubtask(sub.id)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NorthWest,
                                    contentDescription = stringResource(R.string.cd_promote_subtask),
                                    tint = colors.accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.detail_promote_subtask),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(ZeroSmallShape)
                                    .clickable { onDeleteSubtask(sub) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_delete_subtask),
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Inline child subtask creator for infinite nesting
                        if (activeChildSubtaskParentId == sub.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .clip(ZeroSmallShape)
                                    .background(colors.surfaceElevated)
                                    .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (nestedSubtaskDraft.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.detail_add_child_subtask_placeholder, sub.title),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = nestedSubtaskDraft,
                                        onValueChange = { nestedSubtaskDraft = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                        cursorBrush = SolidColor(colors.accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                val clean = nestedSubtaskDraft.trim()
                                                if (clean.isNotEmpty()) {
                                                    haptics.confirm()
                                                    onAddSubtask(sub.id, clean)
                                                    nestedSubtaskDraft = ""
                                                    activeChildSubtaskParentId = null
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
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
                        ZeroMarkdownPreview(markdown = descriptionDraft)
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
                            onValueChange = { updated ->
                                descriptionDraft = updated
                                onUpdateTitleAndDescription(task, titleDraft.ifBlank { task.title }, updated)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            minLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
