package com.zero.todo.feature.now

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.tasklist.FolderPathSuggestion
import com.zero.todo.feature.tasklist.QuickInputCapsule
import com.zero.todo.feature.tasklist.UndoBannerState
import com.zero.todo.feature.tasklist.UndoCapsule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * ZERO 2.0 "NOW" Execution Runway — breaks the traditional overwhelming checklist paradigm.
 * Shows ONLY the single active task card with generous 48dp+ thumb ergonomics, a lightweight
 * "First Step" icebreaker that launches forward-momentum flow when checked, guilt-free skip,
 * hold-to-complete, Day Capacity Ruler, and 1-tap Lightning Triage entry.
 */
@Composable
fun NowRunwayScreen(
    currentDate: LocalDate,
    runwayQueue: List<TaskItem>,
    scheduledTodayMinutes: Int,
    dailyCapacityMinutes: Int,
    triageCount: Int,
    folderNameById: Map<String, String>,
    quickInputText: String,
    parsedQuickInput: ParsedQuickInput,
    folderSuggestions: List<FolderPathSuggestion>,
    continuousEntryEnabled: Boolean,
    undoBanner: UndoBannerState?,
    quickInputFocusRequester: FocusRequester,
    onCompleteTask: (TaskItem) -> Unit,
    onSkipTask: (TaskItem) -> Unit,
    onOpenTaskDetail: (TaskItem) -> Unit,
    onStartFocus: (TaskItem, FocusMode) -> Unit,
    onUpdateFirstStep: (TaskItem, String?) -> Unit,
    onCompleteFirstStepAndStartFlow: (TaskItem) -> Unit,
    onUpdateEffort: (TaskItem, Int?) -> Unit,
    onOpenTriage: () -> Unit,
    onSwitchToVault: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val activeTask = runwayQueue.firstOrNull()
    val nextTask = runwayQueue.getOrNull(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // Minimalist Tactile Top Bar (all touch targets >= 44dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineDividerBottom()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Text(
                    text = stringResource(R.string.now_header_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                if (runwayQueue.isNotEmpty()) {
                    Text(
                        text = "01 / ${runwayQueue.size.toString().padStart(2, '0')}",
                        style = tabular.dateCompact,
                        color = colors.textMuted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (triageCount > 0) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.accentSubtle)
                            .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                            .clickable {
                                haptics.confirm()
                                onOpenTriage()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.now_triage_pill, triageCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable {
                            haptics.reorderTick()
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Scrollable Center Stage
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Precision Day Capacity Ruler
            DayCapacityRuler(
                scheduledMinutes = scheduledTodayMinutes,
                capacityMinutes = dailyCapacityMinutes,
                taskCount = runwayQueue.size,
                modifier = Modifier.widthIn(max = 560.dp)
            )

            if (activeTask == null) {
                // Calm Clear Runway State
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(vertical = 36.dp)
                        .clip(ZeroCardShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroCardShape)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "00",
                        style = tabular.timerHero,
                        color = colors.textMuted
                    )
                    Text(
                        text = stringResource(R.string.now_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.now_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (triageCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(colors.accent)
                                    .clickable {
                                        haptics.confirm()
                                        onOpenTriage()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.now_start_triage),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onAccent
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(ZeroCapsuleShape)
                                .background(colors.surfaceElevated)
                                .hairlineBorder(shape = ZeroCapsuleShape)
                                .clickable {
                                    haptics.reorderTick()
                                    onSwitchToVault()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.now_open_vault),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            } else {
                // Single Active Execution Card
                ActiveRunwayCard(
                    task = activeTask,
                    folderName = activeTask.folderId?.let { folderNameById[it] },
                    onComplete = { onCompleteTask(activeTask) },
                    onSkip = { onSkipTask(activeTask) },
                    onOpenDetail = { onOpenTaskDetail(activeTask) },
                    onStartTwoMin = { onStartFocus(activeTask, FocusMode.TWO_MIN_STARTER) },
                    onUpdateFirstStep = { step -> onUpdateFirstStep(activeTask, step) },
                    onCompleteFirstStep = { onCompleteFirstStepAndStartFlow(activeTask) },
                    onSelectEffort = { mins -> onUpdateEffort(activeTask, mins) },
                    modifier = Modifier.widthIn(max = 560.dp)
                )

                // Subtle Peeking Next-Up Index Card Edge (gives calm continuity without list overwhelm)
                if (nextTask != null) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 532.dp)
                            .fillMaxWidth()
                            .alpha(0.72f)
                            .clip(ZeroCardShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroCardShape)
                            .clickable {
                                haptics.reorderTick()
                                onSkipTask(activeTask)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.now_up_next),
                                style = tabular.dateCompact,
                                color = colors.textMuted
                            )
                            Text(
                                text = nextTask.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (nextTask.estimatedMinutes != null) {
                            Text(
                                text = "${nextTask.estimatedMinutes}m",
                                style = tabular.dateCompact,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }

        // Bottom Undo + Persistent Quick Capture
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
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

@Composable
private fun ActiveRunwayCard(
    task: TaskItem,
    folderName: String?,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetail: () -> Unit,
    onStartTwoMin: () -> Unit,
    onUpdateFirstStep: (String?) -> Unit,
    onCompleteFirstStep: () -> Unit,
    onSelectEffort: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()
    val activeLocale = LocalConfiguration.current.locales[0]

    val currentComplete by rememberUpdatedState(onComplete)
    val currentSkip by rememberUpdatedState(onSkip)

    var firstStepDraft by remember(task.id, task.firstStep) { mutableStateOf(task.firstStep.orEmpty()) }
    val swipeOffsetX = remember(task.id) { Animatable(0f) }
    val holdProgress = remember(task.id) { Animatable(0f) }

    LaunchedEffect(task.id) {
        swipeOffsetX.snapTo(0f)
        holdProgress.snapTo(0f)
    }

    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> colors.priorityUrgent
        TaskPriority.HIGH -> colors.priorityHigh
        TaskPriority.MEDIUM -> colors.priorityMedium
        TaskPriority.NONE -> colors.textMuted
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            .clip(ZeroCardShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroCardShape, color = colors.hairlineBorder)
            .pointerInput(task.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffsetX.value < -150f) {
                            haptics.reorderTick()
                            currentSkip()
                        }
                        scope.launch {
                            swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val next = (swipeOffsetX.value + dragAmount).coerceIn(-260f, 80f)
                        scope.launch { swipeOffsetX.snapTo(next) }
                    }
                )
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card Top Metadata Row + Expand Details Button (44dp target)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (task.priority != TaskPriority.NONE) {
                    Text(
                        text = "P${task.priority.value}",
                        style = tabular.priorityTag,
                        color = priorityColor,
                        modifier = Modifier
                            .clip(ZeroSmallShape)
                            .background(priorityColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (task.dueDate != null) {
                    val overdue = ZeroDateFormatter.isOverdue(task.dueDate, task.isAllDay)
                    Text(
                        text = ZeroDateFormatter.formatDueDate(task.dueDate, task.isAllDay, locale = activeLocale),
                        style = tabular.dateCompact,
                        color = if (overdue) colors.priorityUrgent else colors.textSecondary
                    )
                }
                if (!folderName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ZeroSmallShape)
                    .background(colors.background)
                    .hairlineBorder(shape = ZeroSmallShape)
                    .clickable {
                        haptics.reorderTick()
                        onOpenDetail()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.cd_open_task_detail),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Hero Task Title
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary
        )

        // Effort Tier Quick Selector (5m / 25m / 1h — generous 42dp touch height)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EffortTier.entries.forEach { tier ->
                val selected = task.estimatedMinutes == tier.minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(ZeroSmallShape)
                        .background(if (selected) colors.accentSubtle else colors.background)
                        .hairlineBorder(
                            shape = ZeroSmallShape,
                            color = if (selected) colors.accent else colors.hairlineBorder
                        )
                        .clickable {
                            haptics.reorderTick()
                            onSelectEffort(if (selected) null else tier.minutes)
                        },
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

        // "First Tiny Step" Icebreaker Section — checking it launches Forward Momentum Flow!
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZeroSmallShape)
                .background(colors.background)
                .hairlineBorder(
                    shape = ZeroSmallShape,
                    color = if (!task.firstStep.isNullOrBlank()) colors.accent.copy(alpha = 0.5f) else colors.hairlineBorder
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.now_first_step_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }

            if (!task.firstStep.isNullOrBlank() && !task.firstStepCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.accentSubtle)
                        .hairlineBorder(shape = ZeroSmallShape, color = colors.accent)
                        .clickable {
                            haptics.taskComplete()
                            onCompleteFirstStep()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .hairlineBorder(shape = CircleShape, color = colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.firstStep,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.now_first_step_flow_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (firstStepDraft.isEmpty()) {
                            Text(
                                text = stringResource(
                                    if (task.firstStepCompleted) R.string.now_first_step_done_hint
                                    else R.string.now_first_step_placeholder
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        BasicTextField(
                            value = firstStepDraft,
                            onValueChange = { firstStepDraft = it.take(36) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary,
                                textDecoration = if (task.firstStepCompleted && firstStepDraft == task.firstStep) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val clean = firstStepDraft.trim()
                                    if (clean.isNotEmpty()) {
                                        haptics.confirm()
                                        onUpdateFirstStep(clean)
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(visible = firstStepDraft.trim() != task.firstStep.orEmpty() && firstStepDraft.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(ZeroSmallShape)
                                .background(colors.accent)
                                .clickable {
                                    haptics.confirm()
                                    onUpdateFirstStep(firstStepDraft.trim())
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_save),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onAccent
                            )
                        }
                    }
                }
            }
        }

        // Primary Tactile Thumb Controls (52dp height, zero squinting)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Guilt-free Skip / Next Card
            Box(
                modifier = Modifier
                    .height(52.dp)
                    .clip(ZeroCapsuleShape)
                    .background(colors.surfaceElevated)
                    .hairlineBorder(shape = ZeroCapsuleShape)
                    .clickable {
                        haptics.reorderTick()
                        onSkip()
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.now_skip_card),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.now_skip_card),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                }
            }

            // 2-Min Starter Focus Button
            Box(
                modifier = Modifier
                    .height(52.dp)
                    .clip(ZeroCapsuleShape)
                    .background(colors.accentSubtle)
                    .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                    .clickable {
                        haptics.heavyClick()
                        onStartTwoMin()
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.detail_start_2min),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent
                    )
                }
            }

            // Hold-to-Complete Anti-Misclick Primary Button (0.3s fill progress)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(ZeroCapsuleShape)
                    .background(colors.surfaceElevated)
                    .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                    .pointerInput(task.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var triggered = false
                            val fillJob = scope.launch {
                                holdProgress.snapTo(0f)
                                holdProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 300, easing = LinearEasing)
                                )
                                triggered = true
                                haptics.taskComplete()
                                currentComplete()
                            }
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                if (!triggered) {
                                    fillJob.cancel(CancellationException("Released before 300ms"))
                                }
                                scope.launch {
                                    holdProgress.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (holdProgress.value > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(holdProgress.value)
                            .height(52.dp)
                            .background(colors.accent)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (holdProgress.value > 0.55f) colors.onAccent else colors.accent,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.now_hold_complete),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (holdProgress.value > 0.55f) colors.onAccent else colors.textPrimary
                    )
                }
            }
        }
    }
}
