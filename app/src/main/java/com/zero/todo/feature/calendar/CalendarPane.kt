package com.zero.todo.feature.calendar

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.feature.tasklist.AntiMisclickTaskCard
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.QuickInputCapsule
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoCapsule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class CalendarViewMode(@StringRes val labelRes: Int) {
    MONTH(R.string.cal_mode_month),
    WEEK(R.string.cal_mode_week),
    DAY(R.string.cal_mode_day)
}

private val WEEKDAY_HEADER_RES = listOf(
    R.string.cal_weekday_mon,
    R.string.cal_weekday_tue,
    R.string.cal_weekday_wed,
    R.string.cal_weekday_thu,
    R.string.cal_weekday_fri,
    R.string.cal_weekday_sat,
    R.string.cal_weekday_sun
)

/**
 * Minimalist Near-Future Calendar View supporting Month / Week / Day switching.
 * Every date, month header, task counter, and hourly slot uses tabular monospace numerals (`tnum`)
 * to guarantee pixel-perfect vertical alignment.
 */
@Composable
fun CalendarPane(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    allActiveTasks: List<TaskItem>,
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
    onSelectDate: (LocalDate) -> Unit,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
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
    val zoneId = ZoneId.systemDefault()
    val today = remember { LocalDate.now() }

    val tasksByDate = remember(allActiveTasks) {
        allActiveTasks
            .filter { it.dueDate != null }
            .groupBy { task ->
                Instant.ofEpochMilli(task.dueDate!!).atZone(zoneId).toLocalDate()
            }
    }

    val selectedDayTasks = remember(tasksByDate, selectedDate) {
        tasksByDate[selectedDate].orEmpty()
    }

    val headerLabel = remember(selectedDate, viewMode) {
        when (viewMode) {
            CalendarViewMode.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM"))
            CalendarViewMode.WEEK -> {
                val start = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = start.plusDays(6)
                "${start.format(DateTimeFormatter.ofPattern("MM.dd"))} - ${end.format(DateTimeFormatter.ofPattern("MM.dd"))}"
            }
            CalendarViewMode.DAY -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        }
    }

    fun stepPeriod(direction: Long) {
        val next = when (viewMode) {
            CalendarViewMode.MONTH -> selectedDate.plusMonths(direction)
            CalendarViewMode.WEEK -> selectedDate.plusWeeks(direction)
            CalendarViewMode.DAY -> selectedDate.plusDays(direction)
        }
        onSelectDate(next)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Calendar Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showDrawerButton) {
                        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (showTreeToggleButton) {
                        IconButton(onClick = onToggleTreePanel, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                                contentDescription = stringResource(R.string.cd_toggle_tree_panel),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    IconButton(onClick = { stepPeriod(-1) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_prev_period),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = headerLabel,
                        style = tabular.counter.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(onClick = { stepPeriod(1) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_next_period),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (selectedDate != today) {
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(colors.surface)
                                .hairlineBorder(shape = ZeroSmallShape)
                                .clickable { onSelectDate(today) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cal_today_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent
                            )
                        }
                    }
                }

                // Month / Week / Day Segmented Switcher
                Row(
                    modifier = Modifier
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        val active = viewMode == mode
                        Box(
                            modifier = Modifier
                                .clip(ZeroSmallShape)
                                .background(if (active) colors.surfaceElevated else colors.surface)
                                .clickable { onSelectViewMode(mode) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) colors.accent else colors.textMuted
                            )
                        }
                    }
                }
            }

            // Main Calendar Content
            when (viewMode) {
                CalendarViewMode.MONTH -> {
                    MonthGridSection(
                        selectedDate = selectedDate,
                        today = today,
                        tasksByDate = tasksByDate,
                        onSelectDate = onSelectDate
                    )
                    SelectedDayTaskList(
                        selectedDate = selectedDate,
                        tasks = selectedDayTasks,
                        folderNameById = folderNameById,
                        selectedTaskId = selectedTaskId,
                        antiMisclickMode = antiMisclickMode,
                        onToggleTaskComplete = onToggleTaskComplete,
                        onSelectTask = onSelectTask,
                        modifier = Modifier.weight(1f)
                    )
                }

                CalendarViewMode.WEEK -> {
                    WeekStripSection(
                        selectedDate = selectedDate,
                        today = today,
                        tasksByDate = tasksByDate,
                        onSelectDate = onSelectDate
                    )
                    SelectedDayTaskList(
                        selectedDate = selectedDate,
                        tasks = selectedDayTasks,
                        folderNameById = folderNameById,
                        selectedTaskId = selectedTaskId,
                        antiMisclickMode = antiMisclickMode,
                        onToggleTaskComplete = onToggleTaskComplete,
                        onSelectTask = onSelectTask,
                        modifier = Modifier.weight(1f)
                    )
                }

                CalendarViewMode.DAY -> {
                    DayTimelineSection(
                        selectedDate = selectedDate,
                        tasks = selectedDayTasks,
                        selectedTaskId = selectedTaskId,
                        zoneId = zoneId,
                        onSelectTask = onSelectTask,
                        onToggleTaskComplete = onToggleTaskComplete,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom Floating Undo + Quick Input Capsule
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

@Composable
private fun MonthGridSection(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val yearMonth = YearMonth.from(selectedDate)
    val firstOfMonth = yearMonth.atDay(1)
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val totalDays = 42 // Fixed 6-week grid for rock-solid vertical rhythm
    val days = remember(yearMonth) { List(totalDays) { offset -> gridStart.plusDays(offset.toLong()) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineDividerBottom()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Weekday header
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_HEADER_RES.forEach { dayRes ->
                Text(
                    text = stringResource(dayRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6 rows x 7 columns strictly aligned tabular dates
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                week.forEach { date ->
                    val inMonth = date.month == yearMonth.month
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val incompleteCount = tasksByDate[date]?.count { !it.isCompleted } ?: 0

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(ZeroSmallShape)
                            .background(
                                when {
                                    isSelected -> colors.accentSubtle
                                    isToday -> colors.surface
                                    else -> colors.background
                                }
                            )
                            .then(
                                when {
                                    isSelected -> Modifier.hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                                    isToday -> Modifier.hairlineBorder(shape = ZeroSmallShape, color = colors.hairlineBorder)
                                    else -> Modifier
                                }
                            )
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = date.dayOfMonth.toString().padStart(2, '0'),
                            style = tabular.counter,
                            color = when {
                                isSelected || isToday -> colors.accent
                                inMonth -> colors.textPrimary
                                else -> colors.textMuted.copy(alpha = 0.5f)
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            val dots = incompleteCount.coerceAtMost(3)
                            repeat(dots) {
                                Box(
                                    modifier = Modifier
                                        .size(3.5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.accent else colors.textSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStripSection(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = remember(weekStart) { List(7) { offset -> weekStart.plusDays(offset.toLong()) } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineDividerBottom()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEachIndexed { idx, date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            val incompleteCount = tasksByDate[date]?.count { !it.isCompleted } ?: 0

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(ZeroCardShape)
                    .background(if (isSelected) colors.accentSubtle else colors.surface)
                    .hairlineBorder(
                        shape = ZeroCardShape,
                        color = if (isSelected) colors.accent else colors.hairlineBorder
                    )
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(WEEKDAY_HEADER_RES[idx]),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.accent else colors.textMuted
                )
                Text(
                    text = date.dayOfMonth.toString().padStart(2, '0'),
                    style = tabular.counter.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = when {
                        isSelected || isToday -> colors.accent
                        else -> colors.textPrimary
                    }
                )
                Text(
                    text = if (incompleteCount > 0) incompleteCount.toString().padStart(2, '0') else "—",
                    style = tabular.dateCompact,
                    color = if (incompleteCount > 0) colors.textSecondary else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun SelectedDayTaskList(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    folderNameById: Map<String, String>,
    selectedTaskId: String?,
    antiMisclickMode: AntiMisclickMode,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val listState = rememberLazyListState()
    val dateLabel = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateLabel,
                style = tabular.timestamp,
                color = colors.textSecondary
            )
            Text(
                text = stringResource(
                    R.string.cal_pending_count,
                    tasks.count { !it.isCompleted }.toString().padStart(2, '0')
                ),
                style = tabular.dateCompact,
                color = colors.accent
            )
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.cal_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = tasks, key = { it.id }) { task ->
                    AntiMisclickTaskCard(
                        task = task,
                        isSelected = task.id == selectedTaskId,
                        isListScrolling = listState.isScrollInProgress,
                        isDragging = false,
                        dragOffsetY = 0f,
                        folderBadgeName = task.folderId?.let { folderNameById[it] },
                        antiMisclickMode = antiMisclickMode,
                        onToggleComplete = onToggleTaskComplete,
                        onSelectTask = onSelectTask,
                        onReorderDragStart = {},
                        onReorderDragDelta = {},
                        onReorderDragEnd = {},
                        modifier = Modifier.animateItem(placementSpec = ZeroMotion.IntOffsetSpring)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimelineSection(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    selectedTaskId: String?,
    zoneId: ZoneId,
    onSelectTask: (TaskItem) -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val allDayTasks = remember(tasks) { tasks.filter { it.isAllDay } }
    val timedTasksByHour = remember(tasks) {
        tasks.filter { !it.isAllDay && it.dueDate != null }.groupBy { task ->
            Instant.ofEpochMilli(task.dueDate!!).atZone(zoneId).hour
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Lightweight All-Day tray
        item(key = "all_day_tray") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZeroCardShape)
                    .background(colors.surface)
                    .hairlineBorder(shape = ZeroCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.cal_all_day_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = allDayTasks.size.toString().padStart(2, '0'),
                        style = tabular.counter,
                        color = colors.accent
                    )
                }
                if (allDayTasks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cal_all_day_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                } else {
                    allDayTasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroSmallShape)
                                .background(if (task.id == selectedTaskId) colors.accentSubtle else colors.surfaceElevated)
                                .hairlineBorder(
                                    shape = ZeroSmallShape,
                                    color = if (task.id == selectedTaskId) colors.accent else colors.hairlineBorder
                                )
                                .clickable { onSelectTask(task) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (task.isCompleted) stringResource(R.string.action_completed) else "P${task.priority.value}",
                                style = tabular.dateCompact,
                                color = if (task.isCompleted) colors.textMuted else colors.accent,
                                modifier = Modifier.clickable { onToggleTaskComplete(task) }
                            )
                        }
                    }
                }
            }
        }

        // 24-hour tabular timeline
        items((0..23).toList(), key = { hour -> "hour_$hour" }) { hour ->
            val hourTasks = timedTasksByHour[hour].orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerTop()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${hour.toString().padStart(2, '0')}:00",
                    style = tabular.timestamp,
                    color = if (hourTasks.isNotEmpty()) colors.accent else colors.textMuted,
                    modifier = Modifier.width(52.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hourTasks.isEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                    } else {
                        hourTasks.forEach { task ->
                            val timeText = Instant.ofEpochMilli(task.dueDate!!)
                                .atZone(zoneId)
                                .toLocalTime()
                                .format(timeFormatter)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ZeroSmallShape)
                                    .background(if (task.id == selectedTaskId) colors.accentSubtle else colors.surface)
                                    .hairlineBorder(
                                        shape = ZeroSmallShape,
                                        color = if (task.id == selectedTaskId) colors.accent else colors.hairlineBorder
                                    )
                                    .clickable { onSelectTask(task) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = timeText,
                                    style = tabular.dateCompact,
                                    color = colors.accent
                                )
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
