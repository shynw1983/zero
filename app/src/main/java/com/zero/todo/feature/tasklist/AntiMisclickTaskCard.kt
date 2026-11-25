package com.zero.todo.feature.tasklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.common.ZeroDateFormatter
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroDimens
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private const val HOLD_TO_COMPLETE_DURATION_MS = 300
private const val SWIPE_COMPLETE_THRESHOLD_FRACTION = 0.30f
private const val SWIPE_POST_THRESHOLD_DAMPING = 0.35f

/**
 * ZERO's Anti-Misclick Task Card:
 * - Strict 3-zone hitbox isolation:
 *   - Left 48.dp: Complete action zone (0.3s short-press with radial fill animation)
 *   - Center: Opens Task Detail pane
 *   - Right 40.dp: Dedicated drag handle for vertical reordering
 * - Scroll Lock: Disables checkbox hold & swipe gestures while `isListScrolling == true`
 * - Bidirectional Swipe-to-Complete: Left or right drag >= 30% width with spring resistance
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
    antiMisclickMode: AntiMisclickMode = AntiMisclickMode.BOTH,
    onToggleComplete: (TaskItem) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onTagClick: ((tagId: String) -> Unit)? = null,
    onReorderDragStart: () -> Unit,
    onReorderDragDelta: (Float) -> Unit,
    onReorderDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()

    val currentTask by rememberUpdatedState(task)
    val scrollingLocked by rememberUpdatedState(isListScrolling)
    val currentMode by rememberUpdatedState(antiMisclickMode)
    val toggleCompleteCallback by rememberUpdatedState(onToggleComplete)

    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    val swipeOffsetX = remember(task.id) { Animatable(0f) }
    val holdProgress = remember(task.id) { Animatable(0f) }
    var swipeThresholdArmed by remember(task.id) { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> colors.priorityUrgent
        TaskPriority.HIGH -> colors.priorityHigh
        TaskPriority.MEDIUM -> colors.priorityMedium
        TaskPriority.NONE -> colors.textMuted
    }

    val swipeRatio = if (cardWidthPx > 0f) {
        (abs(swipeOffsetX.value) / (cardWidthPx * SWIPE_COMPLETE_THRESHOLD_FRACTION)).coerceIn(0f, 1.25f)
    } else {
        0f
    }
    val swipeArmedVisual = swipeRatio >= 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .clip(ZeroCardShape)
    ) {
        // Underlying Swipe-to-Complete reveal track (Left & Right)
        if (abs(swipeOffsetX.value) > 1f) {
            val isSwipingRight = swipeOffsetX.value > 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (swipeArmedVisual) colors.accentSubtle else colors.surfaceElevated)
                    .hairlineBorder(
                        shape = ZeroCardShape,
                        color = if (swipeArmedVisual) colors.accent else colors.hairlineBorder
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (swipeArmedVisual) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = when {
                            swipeArmedVisual && task.isCompleted -> stringResource(R.string.swipe_release_restore)
                            swipeArmedVisual -> stringResource(R.string.swipe_release_complete)
                            else -> "${(swipeRatio.coerceAtMost(1f) * 100).roundToInt()}%"
                        },
                        style = tabular.dateCompact,
                        color = if (swipeArmedVisual) colors.accent else colors.textSecondary
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
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .clip(ZeroCardShape)
                .background(surfaceColor)
                .hairlineBorder(shape = ZeroCardShape, color = borderColor)
                .pointerInput(task.id) {
                    var rawDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            rawDrag = 0f
                            swipeThresholdArmed = false
                        },
                        onDragEnd = {
                            if (!currentMode.allowSwipeComplete) {
                                swipeThresholdArmed = false
                                return@detectHorizontalDragGestures
                            }
                            val threshold = cardWidthPx * SWIPE_COMPLETE_THRESHOLD_FRACTION
                            val triggered = threshold > 0f && abs(swipeOffsetX.value) >= threshold
                            if (triggered && !scrollingLocked) {
                                haptics.taskComplete()
                                toggleCompleteCallback(currentTask)
                            }
                            swipeThresholdArmed = false
                            scope.launch {
                                swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                            }
                        },
                        onDragCancel = {
                            swipeThresholdArmed = false
                            scope.launch {
                                swipeOffsetX.animateTo(0f, animationSpec = ZeroMotion.FloatSpring)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!currentMode.allowSwipeComplete || scrollingLocked || cardWidthPx <= 0f) {
                                return@detectHorizontalDragGestures
                            }
                            change.consume()
                            rawDrag += dragAmount
                            val threshold = cardWidthPx * SWIPE_COMPLETE_THRESHOLD_FRACTION
                            val absRaw = abs(rawDrag)
                            val damped = if (absRaw <= threshold) {
                                rawDrag
                            } else {
                                sign(rawDrag) * (threshold + (absRaw - threshold) * SWIPE_POST_THRESHOLD_DAMPING)
                            }
                            val maxOffset = cardWidthPx * 0.48f
                            val clamped = damped.coerceIn(-maxOffset, maxOffset)

                            val nowArmed = abs(clamped) >= threshold
                            if (nowArmed && !swipeThresholdArmed) {
                                haptics.reorderTick()
                            }
                            swipeThresholdArmed = nowArmed

                            scope.launch {
                                swipeOffsetX.snapTo(clamped)
                            }
                        }
                    )
                },
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
                                    haptics.taskComplete()
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
                    .clickable(enabled = !isListScrolling) { onSelectTask(task) }
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
                            Text(
                                text = "P${task.priority.value}",
                                style = tabular.priorityTag,
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
                            Text(
                                text = "/$folderBadgeName",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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

            // ZONE 3: Right 40.dp Dedicated Drag Handle for Reordering
            Box(
                modifier = Modifier
                    .width(ZeroDimens.DragHandleWidth)
                    .height(56.dp)
                    .pointerInput(task.id) {
                        detectDragGestures(
                            onDragStart = {
                                haptics.reorderTick()
                                onReorderDragStart()
                            },
                            onDragEnd = { onReorderDragEnd() },
                            onDragCancel = { onReorderDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onReorderDragDelta(dragAmount.y)
                            }
                        )
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
