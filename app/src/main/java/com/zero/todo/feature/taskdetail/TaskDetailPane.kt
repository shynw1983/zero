package com.zero.todo.feature.taskdetail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.core.common.RecurrenceRule
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.hairlineDividerTop
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
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
    subtasks: List<TaskItem>,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onStartFocus: (task: TaskItem, initialMode: FocusMode) -> Unit,
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
                    text = "NO SELECTION",
                    style = tabular.dateCompact,
                    color = colors.textMuted
                )
                Text(
                    text = "选择一项任务查看详情、无限子任务、重复规则与 Markdown 笔记",
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
                            contentDescription = "返回列表",
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
                        text = folderName ?: "收集箱",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        text = "先做2分",
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
                        text = if (task.isCompleted) "已完成" else "完成",
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
                        contentDescription = "删除任务",
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
                    text = "优先级",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.width(46.dp)
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
                                ZeroDateFormatter.formatDueDate(task.dueDate, task.isAllDay)
                            } else {
                                "未设置截止日期"
                            },
                            style = tabular.dateCompact,
                            color = if (task.dueDate != null) colors.textPrimary else colors.textMuted
                        )
                    }

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
                            label = "仅日期",
                            selected = task.isAllDay,
                            onClick = {
                                val date = currentDueDateTime?.toLocalDate() ?: LocalDate.now()
                                updateDueDateTime(date, null, allDay = true)
                            }
                        )
                        ModeSegmentChip(
                            label = "具体时间",
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

                // Quick Date Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val existingTime = currentDueDateTime?.toLocalTime()?.takeUnless { task.isAllDay }
                    val allDay = task.isAllDay || task.dueDate == null
                    QuickChip("今天") {
                        updateDueDateTime(LocalDate.now(), existingTime, allDay)
                    }
                    QuickChip("明天") {
                        updateDueDateTime(LocalDate.now().plusDays(1), existingTime, allDay)
                    }
                    QuickChip("后天") {
                        updateDueDateTime(LocalDate.now().plusDays(2), existingTime, allDay)
                    }
                    QuickChip("下周一") {
                        val nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        updateDueDateTime(nextMon, existingTime, allDay)
                    }
                    if (task.dueDate != null) {
                        QuickChip("-1天") {
                            updateDueDateTime(currentDueDateTime!!.toLocalDate().minusDays(1), existingTime, allDay)
                        }
                        QuickChip("+1天") {
                            updateDueDateTime(currentDueDateTime!!.toLocalDate().plusDays(1), existingTime, allDay)
                        }
                        QuickChip("清除") {
                            onUpdateDueDate(task, null, true)
                        }
                    }
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
                            text = "重复规则",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                    Text(
                        text = RecurrenceRule.formatLabel(task.recurrenceRule),
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
                        "不重复" to null,
                        "每天" to RecurrenceRule.PRESET_DAILY.toRruleString(),
                        "工作日" to RecurrenceRule.PRESET_WEEKDAYS.toRruleString(),
                        "每周" to RecurrenceRule.PRESET_WEEKLY.toRruleString(),
                        "每月" to RecurrenceRule.PRESET_MONTHLY.toRruleString()
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
                        label = "自定义 RRULE",
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
                                    text = "应用",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (parsedCustom != null) colors.onAccent else colors.textMuted
                                )
                            }
                        }
                        Text(
                            text = parsedCustom?.let { "解析结果：${it.toDisplayLabel()}" }
                                ?: "示例：FREQ=DAILY;INTERVAL=2 或 FREQ=WEEKLY;BYDAY=MO,WE,FR",
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
                    text = "标签",
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
                                contentDescription = "移除标签",
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
                                text = "+ 添加标签",
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
                        text = "无限子任务",
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
                                    contentDescription = "添加下级子任务",
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
                                    contentDescription = "提升为主任务",
                                    tint = colors.accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "提升",
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
                                    contentDescription = "删除子任务",
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
                                            text = "在「${sub.title}」下新增子任务…",
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
                                text = "添加子任务…",
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
                        text = "MARKDOWN 笔记",
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
                            label = "编辑",
                            selected = !markdownPreviewMode,
                            onClick = { markdownPreviewMode = false }
                        )
                        ModeSegmentChip(
                            label = "预览",
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
                                text = "支持 Markdown (# 标题 / - [ ] 清单 / **加粗** / `代码` / > 引用)…",
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
