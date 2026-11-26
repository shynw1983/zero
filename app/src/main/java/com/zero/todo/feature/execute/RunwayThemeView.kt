package com.zero.todo.feature.execute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.focus.FocusPhase
import com.zero.todo.feature.focus.FocusUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

@Immutable
private data class RunwayTimelineSlot(
    val task: TaskItem,
    val startMinutes: Int,
    val durationMinutes: Int,
    val endMinutes: Int,
    val isOverCapacity: Boolean,
    val showLimitRailAbove: Boolean,
    val showLimitRailBelow: Boolean
)

/**
 * Execution Theme 2: RUNWAY (Physical Energy Timeline)
 * - Left column: `tnum` monospace cumulative timeline ruler (`0m`, `30m`, `1h`, `2h`...).
 * - Right column: proportional geometric task blocks (`5m` slender bar, `25m` medium, `1h` tall) with `0.5.dp` borders.
 * - Fixed vertical-center NOW Reticle (`#FF4D00` border) with center-snap fling physics & mechanical tick haptics.
 * - Full-width red-orange limit warning line at `dailyCapacityMinutes` (default `4h`), dimming overflow tasks below.
 * - Quick actions (`2m` / `Next` / `Hold Done`) expand ONLY inside the currently snapped NOW block.
 */
@Composable
fun RunwayThemeView(
    state: ExecuteUiState,
    onSelectFocusedTask: (String) -> Unit,
    onCompleteTask: (TaskItem) -> Unit,
    onSkipTask: (TaskItem) -> Unit,
    onOpenTaskDetail: (TaskItem) -> Unit,
    onStartFocus: (TaskItem?, FocusMode) -> Unit,
    onPauseFocus: () -> Unit,
    onStartOrResumeFocus: () -> Unit,
    onContinueFocusToPomodoro: () -> Unit,
    onCompleteFocusedTask: (TaskItem) -> Unit,
    onExitFocus: () -> Unit,
    onOpenTriage: () -> Unit,
    onSwitchToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val haptics = rememberZeroHaptics()

    val slots = remember(state.runwayQueue, state.dailyCapacityMinutes) {
        buildTimelineSlots(state.runwayQueue, state.dailyCapacityMinutes)
    }

    if (slots.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MonolithBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ExecuteMaxContentWidth)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.now_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = stringResource(R.string.now_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.triageCount > 0) {
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(ZeroCapsuleShape)
                                .background(SignalOrange)
                                .clickable {
                                    haptics.confirm()
                                    onOpenTriage()
                                }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.now_start_triage),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(ZeroCapsuleShape)
                            .background(Color(0xFF141417))
                            .hairlineBorder(shape = ZeroCapsuleShape, color = Color(0xFF27272A))
                            .clickable {
                                haptics.reorderTick()
                                onSwitchToVault()
                            }
                            .padding(horizontal = 18.dp),
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
        }
        return
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.focusedTaskIndex.coerceIn(0, slots.lastIndex))
    val snapFling = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )

    val centeredTaskId by remember(slots) {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@derivedStateOf slots.firstOrNull()?.task?.id
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val closest = items.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            } ?: return@derivedStateOf slots.firstOrNull()?.task?.id
            slots.getOrNull(closest.index)?.task?.id
        }
    }

    val currentOnSelect by rememberUpdatedState(onSelectFocusedTask)
    LaunchedEffect(centeredTaskId) {
        val id = centeredTaskId ?: return@LaunchedEffect
        if (id != state.focusedTaskId) {
            haptics.reorderTick()
            currentOnSelect(id)
        }
    }

    val limitLabel = stringResource(
        R.string.runway_limit_label,
        formatAxisMinutes(state.dailyCapacityMinutes)
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MonolithBlack),
        contentAlignment = Alignment.Center
    ) {
        val verticalCenterPad = ((maxHeight - 148.dp) / 2).coerceAtLeast(48.dp)

        Box(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = snapFling,
                contentPadding = PaddingValues(
                    top = verticalCenterPad,
                    bottom = verticalCenterPad,
                    start = 12.dp,
                    end = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = slots,
                    key = { _, slot -> slot.task.id }
                ) { _, slot ->
                    val isSnappedActive = slot.task.id == (centeredTaskId ?: state.focusedTaskId ?: slots.first().task.id)
                    val isTaskFlowing = isSnappedActive &&
                        state.focusState.isActive &&
                        state.focusState.task?.id == slot.task.id

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (slot.showLimitRailAbove) {
                            CapacityLimitRail(label = limitLabel)
                        }

                        RunwayTimelineBlockRow(
                            slot = slot,
                            isActive = isSnappedActive,
                            isFlowActive = isTaskFlowing,
                            focusState = state.focusState,
                            folderName = slot.task.folderId?.let(state.folderNameById::get),
                            onComplete = { onCompleteTask(slot.task) },
                            onSkip = { onSkipTask(slot.task) },
                            onOpenDetail = { onOpenTaskDetail(slot.task) },
                            onStartTwoMin = { onStartFocus(slot.task, FocusMode.TWO_MIN_STARTER) },
                            onPauseFocus = onPauseFocus,
                            onStartOrResumeFocus = onStartOrResumeFocus,
                            onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                            onCompleteFocusedTask = { onCompleteFocusedTask(slot.task) },
                            onExitFocus = onExitFocus
                        )

                        if (slot.showLimitRailBelow) {
                            CapacityLimitRail(label = limitLabel)
                        }
                    }
                }
            }

            // Fixed Center NOW Reticle Overlay (visual targeting frame aligned to the timeline block column)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(start = 70.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .hairlineBorder(
                            shape = RoundedCornerShape(16.dp),
                            color = SignalOrange.copy(alpha = 0.55f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 8.dp)
                            .clip(ZeroCapsuleShape)
                            .background(SignalOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mode_now),
                            style = ZeroTheme.tabularTypography.priorityTag,
                            color = SignalOrange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapacityLimitRail(label: String) {
    val tabular = ZeroTheme.tabularTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(ZeroCapsuleShape)
                .background(SignalOrange.copy(alpha = 0.16f))
                .hairlineBorder(shape = ZeroCapsuleShape, color = SignalOrange)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = tabular.dateCompact,
                color = SignalOrange
            )
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
        ) {
            drawLine(
                color = SignalOrange,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }
    }
}

@Composable
private fun RunwayTimelineBlockRow(
    slot: RunwayTimelineSlot,
    isActive: Boolean,
    isFlowActive: Boolean,
    focusState: FocusUiState,
    folderName: String?,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetail: () -> Unit,
    onStartTwoMin: () -> Unit,
    onPauseFocus: () -> Unit,
    onStartOrResumeFocus: () -> Unit,
    onContinueFocusToPomodoro: () -> Unit,
    onCompleteFocusedTask: () -> Unit,
    onExitFocus: () -> Unit
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()
    val holdProgress = remember(slot.task.id) { Animatable(0f) }

    val baseHeight: Dp = when {
        slot.durationMinutes <= 5 -> 72.dp
        slot.durationMinutes <= 15 -> 92.dp
        slot.durationMinutes <= 30 -> 118.dp
        else -> 156.dp
    }
    val blockAlpha = when {
        slot.isOverCapacity && isActive -> 0.86f
        slot.isOverCapacity -> 0.42f
        isActive -> 1f
        else -> 0.78f
    }

    val priorityColor = when (slot.task.priority) {
        TaskPriority.URGENT -> colors.priorityUrgent
        TaskPriority.HIGH -> colors.priorityHigh
        TaskPriority.MEDIUM -> colors.priorityMedium
        TaskPriority.NONE -> colors.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(blockAlpha),
        verticalAlignment = Alignment.Top
    ) {
        // Left Time Ruler Column (tnum ticks)
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(baseHeight)
                .padding(end = 6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val railX = size.width - 4.dp.toPx()
                drawLine(
                    color = Color(0xFF27272A),
                    start = Offset(railX, 0f),
                    end = Offset(railX, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
                // Major tick at start of slot
                drawLine(
                    color = if (isActive) SignalOrange else Color(0xFF52525B),
                    start = Offset(railX - 7.dp.toPx(), 10.dp.toPx()),
                    end = Offset(railX, 10.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                // Intermediate minor tick for larger blocks
                if (slot.durationMinutes >= 25) {
                    drawLine(
                        color = Color(0xFF3F3F46),
                        start = Offset(railX - 4.dp.toPx(), size.height * 0.52f),
                        end = Offset(railX, size.height * 0.52f),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
            }
            Text(
                text = formatAxisMinutes(slot.startMinutes),
                style = tabular.dateCompact,
                color = if (isActive) SignalOrange else colors.textMuted,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp)
            )
        }

        // Right Proportional Geometric Block
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (isActive) maxOf(baseHeight.value, 140f).dp else baseHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) Color(0xFF17171C) else Color(0xFF111115))
                .hairlineBorder(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) SignalOrange else Color(0xFF27272A)
                )
                .clickable {
                    haptics.reorderTick()
                    onOpenDetail()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(ZeroCapsuleShape)
                        .background(if (slot.task.priority != TaskPriority.NONE) priorityColor else colors.textMuted.copy(alpha = 0.4f))
                )
                Text(
                    text = slot.task.title,
                    style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = if (isActive) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!folderName.isNullOrBlank() && isActive) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(ZeroCapsuleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formatAxisMinutes(slot.durationMinutes),
                        style = tabular.dateCompact,
                        color = if (isActive) SignalOrange else colors.textSecondary
                    )
                }
            }

            // ONLY the snapped active block inside NOW Reticle reveals quick actions or active 2-min countdown
            AnimatedVisibility(visible = isActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isFlowActive) {
                        val isRunning = focusState.phase == FocusPhase.RUNNING
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = focusState.formattedTimer,
                                style = tabular.timerMedium,
                                color = SignalOrange,
                                modifier = Modifier.weight(1f)
                            )
                            if (focusState.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT) {
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(ZeroCapsuleShape)
                                        .background(SignalOrange)
                                        .clickable {
                                            haptics.confirm()
                                            onContinueFocusToPomodoro()
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.focus_btn_continue_pomodoro),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SignalOrange.copy(alpha = 0.16f))
                                        .hairlineBorder(shape = CircleShape, color = SignalOrange)
                                        .clickable {
                                            haptics.confirm()
                                            if (isRunning) onPauseFocus() else onStartOrResumeFocus()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = SignalOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SignalOrange)
                                    .clickable {
                                        haptics.taskComplete()
                                        onCompleteFocusedTask()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.cd_complete_task),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        haptics.reorderTick()
                                        onExitFocus()
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.now_exit_flow),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .hairlineBorder(shape = ZeroCapsuleShape, color = SignalOrange.copy(alpha = 0.55f))
                                    .clickable {
                                        haptics.confirm()
                                        onStartTwoMin()
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = SignalOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.detail_start_2min),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SignalOrange
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .hairlineBorder(shape = ZeroCapsuleShape, color = Color(0xFF27272A))
                                    .clickable {
                                        haptics.reorderTick()
                                        onSkip()
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.now_skip_card),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            val currentComplete by rememberUpdatedState(onComplete)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(ZeroCapsuleShape)
                                    .background(MonolithBlack)
                                    .hairlineBorder(shape = ZeroCapsuleShape, color = SignalOrange)
                                    .pointerInput(slot.task.id) {
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
                                                    holdProgress.animateTo(0f, ZeroMotion.FloatSpring)
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
                                            .fillMaxHeight()
                                            .background(SignalOrange)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = if (holdProgress.value > 0.55f) Color.White else SignalOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.now_hold_complete),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (holdProgress.value > 0.55f) Color.White else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTimelineSlots(
    queue: List<TaskItem>,
    dailyCapacityMinutes: Int
): List<RunwayTimelineSlot> {
    if (queue.isEmpty()) return emptyList()
    var cursor = 0
    var limitMarkerPlaced = false
    val slots = queue.mapIndexed { index, task ->
        val dur = task.estimatedMinutes ?: EffortTier.DEFAULT_UNSET_MINUTES
        val start = cursor
        val end = cursor + dur
        cursor = end
        val over = end > dailyCapacityMinutes
        val showAbove = over && !limitMarkerPlaced
        if (showAbove) {
            limitMarkerPlaced = true
        }
        val showBelow = !limitMarkerPlaced && index == queue.lastIndex
        RunwayTimelineSlot(
            task = task,
            startMinutes = start,
            durationMinutes = dur,
            endMinutes = end,
            isOverCapacity = over,
            showLimitRailAbove = showAbove,
            showLimitRailBelow = showBelow
        )
    }
    return slots
}

private fun formatAxisMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
