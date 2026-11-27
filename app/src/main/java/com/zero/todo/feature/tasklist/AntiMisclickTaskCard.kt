package com.zero.todo.feature.tasklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroPriorityBadge
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.parseFolderColorHex
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private const val HOLD_TO_COMPLETE_DURATION_MS = 300
private const val SWIPE_POST_THRESHOLD_DAMPING = 0.35f

/**
 * ZERO's Anti-Misclick Task Card:
 * - Strict 3-zone hitbox isolation:
 *   - Left 48.dp: Complete action zone (0.3s short-press with radial fill animation)
 *   - Center: Tap for task details or long-press to reorder in manual lists
 *   - Right 48.dp: Dedicated drag handle for vertical reordering
 * - Scroll Lock: Disables checkbox hold & swipe gestures while `isListScrolling == true`
 * - Configurable swipe actions: Left or right drag >= 30% width with spring resistance
 *   and crisp haptic snap
 */
@Composable
fun AntiMisclickTaskCard(
    task: TaskItem,
    isSelected: Boolean,
    isListScrolling: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    folderBadgeName: String?,
    folderBadgePrefix: String? = null,
    folderBadgeColorHex: String? = null,
    antiMisclickMode: AntiMisclickMode = AntiMisclickMode.BOTH,
    swipeActions: TaskSwipeActions,
    onToggleComplete: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    indentParentId: String? = null,
    onIndent: (TaskItem, String) -> Unit = { _, _ -> },
    onOutdent: ((TaskItem) -> Unit)? = null,
    onSelectTask: (TaskItem) -> Unit,
    onTagClick: ((tagId: String) -> Unit)? = null,
    reorderEnabled: Boolean = false,
    onReorderDragStart: () -> Unit = {},
    onReorderDragDelta: (Float) -> Unit = {},
    onReorderDragEnd: () -> Unit = {},
    onReorderDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()

    val currentTask by rememberUpdatedState(task)
    val scrollingLocked by rememberUpdatedState(isListScrolling)
    val reorderInProgress by rememberUpdatedState(isDragging)
    val currentMode by rememberUpdatedState(antiMisclickMode)
    val currentHaptics by rememberUpdatedState(haptics)
    val toggleCompleteCallback by rememberUpdatedState(onToggleComplete)
    val deleteTaskCallback by rememberUpdatedState(onDeleteTask)
    val indentCallback by rememberUpdatedState(onIndent)
    val outdentCallback by rememberUpdatedState(onOutdent)
    val reorderStart by rememberUpdatedState(onReorderDragStart)
    val reorderDelta by rememberUpdatedState(onReorderDragDelta)
    val reorderEnd by rememberUpdatedState(onReorderDragEnd)
    val reorderCancel by rememberUpdatedState(onReorderDragCancel)

    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var swipeOffsetX by remember(task.id) { mutableFloatStateOf(0f) }
    val holdProgress = remember(task.id) { Animatable(0f) }
    var holdTriggered by remember(task.id) { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> colors.priorityUrgent
        TaskPriority.HIGH -> colors.priorityHigh
        TaskPriority.MEDIUM -> colors.priorityMedium
        TaskPriority.NONE -> colors.textMuted
    }

    val swipeAction = swipeActions.actionForOffset(swipeOffsetX)
    val canIndent = indentParentId != null
    val canOutdent = task.parentTaskId != null && onOutdent != null
    val stage = taskCardSwipeStage(swipeOffsetX, cardWidthPx, canIndent, canOutdent)
    val showIndent = canIndent && swipeOffsetX > 0f && stage != TaskCardSwipeStage.FULL
    val showOutdent = canOutdent && swipeOffsetX < 0f && stage != TaskCardSwipeStage.FULL
    val swipeRatio = if (cardWidthPx > 0f) {
        (abs(swipeOffsetX) / (cardWidthPx * FULL_TASK_SWIPE_FRACTION)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val swipeArmedVisual = stage != TaskCardSwipeStage.BELOW_THRESHOLD
    val swipeModifier = if (antiMisclickMode.allowSwipe && (swipeActions.hasEnabledAction || canIndent || canOutdent)) {
        Modifier.pointerInput(task.id, swipeActions, indentParentId, canOutdent) {
            fun canStart(offset: Float) = swipeActions.actionForOffset(offset) != TaskSwipeAction.NONE ||
                (canIndent && offset > 0f) || (canOutdent && offset < 0f)

            var settleJob: Job? = null
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    holdTriggered = false
                    if (scrollingLocked || reorderInProgress || cardWidthPx <= 0f) return@awaitEachGesture

                    var overSlop = 0f
                    val firstDrag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, distance ->
                        if (!scrollingLocked && !reorderInProgress && !holdTriggered && canStart(distance)) {
                            change.consume()
                            overSlop = distance
                        }
                    } ?: return@awaitEachGesture

                    settleJob?.cancel()
                    swipeOffsetX = 0f
                    var rawDrag = 0f
                    var swipeInterrupted = false

                    fun dragBy(distance: Float) {
                        rawDrag += distance
                        val threshold = cardWidthPx * FULL_TASK_SWIPE_FRACTION
                        val previousStage = taskCardSwipeStage(swipeOffsetX, cardWidthPx, canIndent, canOutdent)
                        val absRaw = abs(rawDrag)
                        swipeOffsetX = when {
                            !canStart(rawDrag) -> 0f
                            absRaw <= threshold -> rawDrag
                            else -> sign(rawDrag) * (threshold + (absRaw - threshold) * SWIPE_POST_THRESHOLD_DAMPING)
                                .coerceAtMost(cardWidthPx * 0.48f)
                        }
                        val nextStage = taskCardSwipeStage(swipeOffsetX, cardWidthPx, canIndent, canOutdent)
                        if (nextStage != previousStage && nextStage != TaskCardSwipeStage.BELOW_THRESHOLD &&
                            (nextStage != TaskCardSwipeStage.FULL || swipeActions.actionForOffset(rawDrag) != TaskSwipeAction.NONE)
                        ) currentHaptics.reorderTick()
                    }

                    dragBy(overSlop)
                    val released = horizontalDrag(firstDrag.id) { change ->
                        val distance = change.positionChange().x
                        change.consume()
                        swipeInterrupted = swipeInterrupted || scrollingLocked || reorderInProgress || holdTriggered
                        if (!swipeInterrupted) dragBy(distance)
                    }

                    if (released && !swipeInterrupted && !scrollingLocked && !reorderInProgress && !holdTriggered) {
                        when (taskCardSwipeStage(swipeOffsetX, cardWidthPx, canIndent, canOutdent)) {
                            TaskCardSwipeStage.INDENT -> indentParentId?.let { indentCallback(currentTask, it) }
                            TaskCardSwipeStage.OUTDENT -> outdentCallback?.invoke(currentTask)
                            TaskCardSwipeStage.FULL -> when (swipeActions.actionForOffset(swipeOffsetX)) {
                                TaskSwipeAction.COMPLETE -> {
                                    currentHaptics.taskComplete()
                                    toggleCompleteCallback(currentTask)
                                }
                                TaskSwipeAction.DELETE -> {
                                    currentHaptics.heavyClick()
                                    deleteTaskCallback(currentTask)
                                }
                                TaskSwipeAction.NONE -> Unit
                            }
                            TaskCardSwipeStage.BELOW_THRESHOLD -> Unit
                        }
                    }

                    settleJob = scope.launch {
                        animate(swipeOffsetX, 0f, animationSpec = ZeroMotion.FloatSpring) { value, _ ->
                            swipeOffsetX = value
                        }
                    }
                }
            } finally {
                settleJob?.cancel()
                swipeOffsetX = 0f
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .clip(ZeroCardShape)
    ) {
        if ((showIndent || showOutdent || swipeAction != TaskSwipeAction.NONE) && abs(swipeOffsetX) > 1f) {
            val deleting = !showIndent && !showOutdent && swipeAction == TaskSwipeAction.DELETE
            val actionColor = if (deleting) colors.priorityUrgent else colors.accent
            val actionBackground = if (deleting) actionColor.copy(alpha = 0.14f) else colors.accentSubtle
            val contentColor = if (swipeArmedVisual || deleting) actionColor else colors.textSecondary
            val revealWidth = with(LocalDensity.current) { abs(swipeOffsetX).toDp() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (swipeArmedVisual) actionBackground else colors.surfaceElevated)
                    .hairlineBorder(
                        shape = ZeroCardShape,
                        color = if (swipeArmedVisual) actionColor else colors.hairlineBorder
                    ),
                contentAlignment = if (swipeOffsetX > 0f) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight
            ) {
                Column(
                    modifier = Modifier.width(revealWidth).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = when {
                            deleting -> Icons.Rounded.DeleteOutline
                            showIndent -> Icons.Rounded.SubdirectoryArrowRight
                            showOutdent -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                            task.isCompleted -> Icons.AutoMirrored.Rounded.Undo
                            else -> Icons.Rounded.Check
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            !swipeArmedVisual -> stringResource(R.string.swipe_progress, (swipeRatio * 100).roundToInt())
                            deleting -> stringResource(R.string.swipe_release_delete)
                            showIndent -> stringResource(R.string.swipe_indent_task)
                            showOutdent -> stringResource(R.string.swipe_outdent_task)
                            task.isCompleted -> stringResource(R.string.swipe_release_restore)
                            else -> stringResource(R.string.swipe_release_complete)
                        },
                        style = tabular.dateCompact,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        // Foreground Card Surface (animated with Spring physics)
        val borderColor by animateColorAsState(
            targetValue = when {
                isDragging -> colors.accent
                isSelected -> colors.accent
                else -> colors.hairlineBorder
            },
            animationSpec = ZeroMotion.ColorSpring,
            label = "task_card_border"
        )
        val surfaceColor by animateColorAsState(
            targetValue = when {
                isDragging -> colors.surfaceElevated
                isSelected -> colors.surfaceElevated
                else -> colors.surface
            },
            animationSpec = ZeroMotion.ColorSpring,
            label = "task_card_surface"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .absoluteOffset { IntOffset(swipeOffsetX.roundToInt(), 0) }
                .clip(ZeroCardShape)
                .background(surfaceColor)
                .hairlineBorder(shape = ZeroCardShape, color = borderColor)
                .then(swipeModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ZONE 1: Left 48.dp Complete Action Zone (0.3s short press with radial fill)
            Box(
                modifier = Modifier
                    .width(ZeroDimens.CompleteHitboxWidth)
                    .height(56.dp)
                    .pointerInput(task.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (scrollingLocked || !currentMode.allowHoldConfirm) {
                                waitForUpOrCancellation()
                                return@awaitEachGesture
                            }

                            var completed = false
                            val fillJob = scope.launch {
                                holdProgress.snapTo(0f)
                                holdProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = HOLD_TO_COMPLETE_DURATION_MS,
                                        easing = LinearEasing
                                    )
                                )
                                if (!scrollingLocked) {
                                    completed = true
                                    holdTriggered = true
                                    currentHaptics.taskComplete()
                                    toggleCompleteCallback(currentTask)
                                }
                            }

                            try {
                                waitForUpOrCancellation()
                            } finally {
                                if (!completed) {
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
                RadialHoldCheckbox(
                    isCompleted = task.isCompleted,
                    holdProgress = holdProgress.value,
                    ringColor = if (task.priority == TaskPriority.NONE) colors.textMuted else priorityColor,
                    accentColor = colors.accent,
                    checkIconColor = colors.onAccent
                )
            }

            // ZONE 2: Center Detail Tap Zone
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (reorderEnabled) Modifier.pointerInput(task.id) {
                        try {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    currentHaptics.reorderTick()
                                    reorderStart()
                                },
                                onDragEnd = { reorderEnd() },
                                onDragCancel = { reorderCancel() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    reorderDelta(amount.y)
                                }
                            )
                        } finally {
                            reorderCancel()
                        }
                    } else Modifier)
                    .clickable(enabled = !isListScrolling) { if (!reorderInProgress) onSelectTask(task) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val hasMetadata = task.dueDate != null ||
                    task.priority != TaskPriority.NONE ||
                    folderBadgeName != null ||
                    task.tags.isNotEmpty()

                if (hasMetadata) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.priority != TaskPriority.NONE) {
                            ZeroPriorityBadge(
                                priority = task.priority,
                                color = priorityColor
                            )
                        }

                        if (task.dueDate != null) {
                            val activeLocale = LocalConfiguration.current.locales[0]
                            val overdue = !task.isCompleted &&
                                ZeroDateFormatter.isOverdue(task.dueDate, task.isAllDay)
                            Text(
                                text = ZeroDateFormatter.formatDueDate(
                                    dueDateMillis = task.dueDate,
                                    isAllDay = task.isAllDay,
                                    locale = activeLocale
                                ),
                                style = tabular.dateCompact,
                                color = if (overdue) colors.priorityUrgent else colors.textSecondary
                            )
                        }

                        if (task.reminderAt != null && !task.isCompleted) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        if (!folderBadgeName.isNullOrBlank()) {
                            val badgeDotColor = parseFolderColorHex(folderBadgeColorHex)
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (badgeDotColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(badgeDotColor)
                                    )
                                }
                                val formattedBadge = buildAnnotatedString {
                                    val dimColor = colors.textMuted.copy(alpha = 0.65f)
                                    if (badgeDotColor == null && folderBadgePrefix.isNullOrBlank()) {
                                        append("/$folderBadgeName")
                                    } else {
                                        if (badgeDotColor == null) {
                                            withStyle(SpanStyle(color = dimColor)) { append("/") }
                                        }
                                        if (!folderBadgePrefix.isNullOrBlank()) {
                                            withStyle(SpanStyle(color = dimColor)) { append(folderBadgePrefix) }
                                        }
                                        withStyle(SpanStyle(color = colors.textMuted)) { append(folderBadgeName) }
                                    }
                                }
                                Text(
                                    text = formattedBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        task.tags.take(2).forEach { tag ->
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .clip(ZeroSmallShape)
                                    .background(colors.surfaceElevated)
                                    .then(
                                        if (onTagClick != null) {
                                            Modifier.clickable { onTagClick(tag.id) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (reorderEnabled) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(56.dp)
                        .pointerInput(task.id) {
                            try {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        currentHaptics.reorderTick()
                                        reorderStart()
                                    },
                                    onDragEnd = { reorderEnd() },
                                    onDragCancel = { reorderCancel() },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        reorderDelta(amount)
                                    }
                                )
                            } finally {
                                reorderCancel()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_reorder),
                        tint = if (isDragging) colors.accent else colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadialHoldCheckbox(
    isCompleted: Boolean,
    holdProgress: Float,
    ringColor: Color,
    accentColor: Color,
    checkIconColor: Color
) {
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokePx = 1.5.dp.toPx()
            val inset = strokePx / 2f
            val diameter = size.minDimension - strokePx

            if (isCompleted) {
                drawCircle(
                    color = accentColor,
                    radius = size.minDimension / 2f
                )
            } else {
                // Base hairline ring
                drawCircle(
                    color = ringColor,
                    radius = diameter / 2f,
                    style = Stroke(width = strokePx)
                )
            }

            // Radial fill arc while holding 0.3s
            if (holdProgress > 0f) {
                if (!isCompleted) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.20f * holdProgress),
                        radius = (diameter / 2f) * holdProgress
                    )
                }
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        if (isCompleted) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.action_completed),
                tint = checkIconColor,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
