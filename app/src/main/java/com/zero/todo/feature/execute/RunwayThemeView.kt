package com.zero.todo.feature.execute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.parseFolderColorHex
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.EffortTier
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.FolderBadgeDisplay
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority
import com.zero.todo.feature.focus.FocusPhase
import com.zero.todo.feature.focus.FocusUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

@Immutable
internal data class RunwayTimelineSlot(
    val task: TaskItem,
    val startMinutes: Int,
    val durationMinutes: Int,
    val endMinutes: Int,
    val isOverCapacity: Boolean
)

@Immutable
internal sealed interface RunwayListEntry {
    data class TaskBlock(val slot: RunwayTimelineSlot) : RunwayListEntry
    data class CapacityLimitLine(val limitMinutes: Int) : RunwayListEntry
    data class CapacityFooterSummary(
        val allocatedMinutes: Int,
        val capacityMinutes: Int
    ) : RunwayListEntry
}

/**
 * Execution Theme 2: RUNWAY (Physical Energy Timeline)
 * - Left column: continuous vertical rail + `tnum` monospace cumulative timeline ticks (`0m`, `30m`, `1h`, `2h`...).
 * - Right column: proportional geometric task blocks (`5m` slender bar, `25m` medium, `1h` tall) with `0.5.dp` hairline borders.
 * - Precision horizontal targeting reticle pointers at vertical center + single highlighted active block (`#FF4D00` border) with center-snap fling physics.
 * - Full-width red-orange limit warning line inserted only when cumulative time reaches/exceeds `dailyCapacityMinutes` (default `4h`); otherwise an understated capacity footer.
 * - Quick actions (`2m` / `Next` / `Hold Done`) expand ONLY inside the currently snapped active block.
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
    val scope = rememberCoroutineScope()

    val entries = remember(state.runwayQueue, state.dailyCapacityMinutes) {
        buildRunwayEntries(state.runwayQueue, state.dailyCapacityMinutes)
    }

    if (entries.isEmpty()) {
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

    val initialEntryIndex = remember(entries, state.focusedTaskId) {
        val targetId = state.focusedTaskId
        val matched = entries.indexOfFirst { (it as? RunwayListEntry.TaskBlock)?.slot?.task?.id == targetId }
        if (matched >= 0) matched else entries.indexOfFirst { it is RunwayListEntry.TaskBlock }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialEntryIndex)
    val snapFling = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )

    val centeredTaskId by remember(entries) {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val firstTaskId = (entries.firstOrNull { it is RunwayListEntry.TaskBlock } as? RunwayListEntry.TaskBlock)
                ?.slot?.task?.id
            if (items.isEmpty()) return@derivedStateOf firstTaskId
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val closestTask = items
                .mapNotNull { item ->
                    val block = entries.getOrNull(item.index) as? RunwayListEntry.TaskBlock ?: return@mapNotNull null
                    item to block
                }
                .minByOrNull { (item, _) ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
            closestTask?.second?.slot?.task?.id ?: firstTaskId
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
        val verticalCenterPad = ((maxHeight - 120.dp) / 2).coerceAtLeast(48.dp)

        Box(
            modifier = Modifier
                .widthIn(max = ExecuteMaxContentWidth)
                .fillMaxSize()
        ) {
            // Continuous full-height vertical time ruler rail (x = 12.dp start padding + 54.dp ruler - 4.dp inset)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val railX = 62.dp.toPx()
                drawLine(
                    color = Color(0xFF27272A),
                    start = Offset(railX, 0f),
                    end = Offset(railX, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
            }

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
                    items = entries,
                    key = { _, entry ->
                        when (entry) {
                            is RunwayListEntry.TaskBlock -> "task_${entry.slot.task.id}"
                            is RunwayListEntry.CapacityLimitLine -> "limit_${entry.limitMinutes}"
                            is RunwayListEntry.CapacityFooterSummary -> "summary_${entry.allocatedMinutes}_${entry.capacityMinutes}"
                        }
                    }
                ) { entryIndex, entry ->
                    when (entry) {
                        is RunwayListEntry.CapacityLimitLine -> {
                            CapacityLimitRail(label = limitLabel)
                        }

                        is RunwayListEntry.CapacityFooterSummary -> {
                            CapacityFooterSummaryRow(
                                allocatedMinutes = entry.allocatedMinutes,
                                capacityMinutes = entry.capacityMinutes
                            )
                        }

                        is RunwayListEntry.TaskBlock -> {
                            val slot = entry.slot
                            val activeId = centeredTaskId ?: state.focusedTaskId
                                ?: (entries.firstOrNull { it is RunwayListEntry.TaskBlock } as? RunwayListEntry.TaskBlock)?.slot?.task?.id
                            val isSnappedActive = slot.task.id == activeId
                            val isTaskFlowing = isSnappedActive &&
                                state.focusState.isActive &&
                                state.focusState.task?.id == slot.task.id

                            RunwayTimelineBlockRow(
                                slot = slot,
                                isActive = isSnappedActive,
                                isFlowActive = isTaskFlowing,
                                focusState = state.focusState,
                                folderName = slot.task.folderId?.let(state.folderNameById::get),
                                folderBadge = slot.task.folderId?.let(state.folderBadgeById::get),
                                parentTitle = state.parentTaskTitleById[slot.task.id],
                                onComplete = { onCompleteTask(slot.task) },
                                onSkip = { onSkipTask(slot.task) },
                                onBlockTap = {
                                    if (isSnappedActive) {
                                        onOpenTaskDetail(slot.task)
                                    } else {
                                        val info = listState.layoutInfo
                                        val tappedItem = info.visibleItemsInfo.firstOrNull { it.index == entryIndex }
                                        if (tappedItem != null) {
                                            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                                            val delta = (tappedItem.offset + tappedItem.size / 2) - viewportCenter
                                            scope.launch {
                                                listState.animateScrollBy(delta.toFloat())
                                            }
                                        } else {
                                            scope.launch {
                                                listState.animateScrollToItem(entryIndex)
                                            }
                                        }
                                    }
                                },
                                onStartTwoMin = { onStartFocus(slot.task, FocusMode.TWO_MIN_STARTER) },
                                onPauseFocus = onPauseFocus,
                                onStartOrResumeFocus = onStartOrResumeFocus,
                                onContinueFocusToPomodoro = onContinueFocusToPomodoro,
                                onCompleteFocusedTask = { onCompleteFocusedTask(slot.task) },
                                onExitFocus = onExitFocus
                            )
                        }
                    }
                }
            }

            // Center Targeting Reticle Pointers (left pointer from rail + right edge sight tick; zero overlapping card box)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height * 0.5f
                val railX = 62.dp.toPx()
                val pointer = Path().apply {
                    moveTo(railX - 1.dp.toPx(), cy - 5.dp.toPx())
                    lineTo(railX + 6.dp.toPx(), cy)
                    lineTo(railX - 1.dp.toPx(), cy + 5.dp.toPx())
                    close()
                }
                drawPath(path = pointer, color = SignalOrange)

                val rightTickX = size.width - 5.dp.toPx()
                drawLine(
                    color = SignalOrange,
                    start = Offset(rightTickX, cy - 9.dp.toPx()),
                    end = Offset(rightTickX, cy + 9.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun CapacityFooterSummaryRow(
    allocatedMinutes: Int,
    capacityMinutes: Int
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val remaining = (capacityMinutes - allocatedMinutes).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(24.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val railX = size.width - 4.dp.toPx()
                drawCircle(
                    color = Color(0xFF52525B),
                    radius = 2.5.dp.toPx(),
                    center = Offset(railX, size.height / 2f)
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
            ) {
                drawLine(
                    color = Color(0xFF3F3F46),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
            Text(
                text = stringResource(
                    R.string.runway_capacity_summary,
                    formatAxisMinutes(allocatedMinutes),
                    formatAxisMinutes(capacityMinutes),
                    formatAxisMinutes(remaining)
                ),
                style = tabular.dateCompact,
                color = colors.textMuted
            )
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
    folderBadge: FolderBadgeDisplay? = null,
    parentTitle: String? = null,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onBlockTap: () -> Unit,
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
        slot.durationMinutes <= 5 -> 48.dp
        slot.durationMinutes <= 15 -> 60.dp
        slot.durationMinutes <= 30 -> 82.dp
        else -> 124.dp
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

    val blockShape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(blockAlpha),
        verticalAlignment = Alignment.Top
    ) {
        // Left Time Ruler Column (horizontal tnum tick anchored to the continuous background rail)
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(baseHeight)
                .padding(end = 6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val railX = size.width - 4.dp.toPx()
                // Major tick at start of slot
                drawLine(
                    color = if (isActive) SignalOrange else Color(0xFF52525B),
                    start = Offset(railX - 7.dp.toPx(), 14.dp.toPx()),
                    end = Offset(railX, 14.dp.toPx()),
                    strokeWidth = if (isActive) 1.5.dp.toPx() else 1.dp.toPx()
                )
                // Intermediate minor tick for larger blocks
                if (slot.durationMinutes >= 25) {
                    drawLine(
                        color = Color(0xFF3F3F46),
                        start = Offset(railX - 4.dp.toPx(), size.height * 0.56f),
                        end = Offset(railX, size.height * 0.56f),
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
                    .padding(top = 6.dp)
            )
        }

        // Right Proportional Geometric Block
        Column(
            modifier = Modifier
                .weight(1f)
                .animateContentSize()
                .heightIn(min = if (isActive) maxOf(baseHeight.value, 112f).dp else baseHeight)
                .clip(blockShape)
                .background(if (isActive) Color(0xFF17171D) else Color(0xFF111115))
                .then(
                    if (isActive) {
                        Modifier.border(width = 1.dp, color = SignalOrange, shape = blockShape)
                    } else {
                        Modifier.hairlineBorder(shape = blockShape, color = Color(0xFF27272A))
                    }
                )
                .clickable {
                    haptics.reorderTick()
                    onBlockTap()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = if (isActive && baseHeight <= 82.dp) {
                Arrangement.spacedBy(12.dp)
            } else {
                Arrangement.SpaceBetween
            }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.task.title,
                        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = if (isActive) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive && !parentTitle.isNullOrBlank()) {
                        Text(
                            text = "↳ $parentTitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if ((folderBadge != null || !folderName.isNullOrBlank()) && isActive) {
                    val dotColor = parseFolderColorHex(folderBadge?.colorHex)
                    Row(
                        modifier = Modifier.widthIn(max = 140.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (dotColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(5.5.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        val label = if (folderBadge != null) {
                            buildAnnotatedString {
                                if (!folderBadge.prefix.isNullOrBlank()) {
                                    withStyle(SpanStyle(color = colors.textMuted.copy(alpha = 0.65f))) {
                                        append(folderBadge.prefix)
                                    }
                                }
                                withStyle(SpanStyle(color = colors.textSecondary)) {
                                    append(folderBadge.leaf)
                                }
                            }
                        } else {
                            buildAnnotatedString {
                                append(folderName.orEmpty())
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ZeroCapsuleShape)
                        .background(
                            if (isActive) SignalOrange.copy(alpha = 0.14f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    if (isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(SignalOrange)
                            )
                            Text(
                                text = "${stringResource(R.string.mode_now)} · ${formatAxisMinutes(slot.durationMinutes)}",
                                style = tabular.dateCompact,
                                color = SignalOrange
                            )
                        }
                    } else {
                        Text(
                            text = formatAxisMinutes(slot.durationMinutes),
                            style = tabular.dateCompact,
                            color = colors.textSecondary
                        )
                    }
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

internal fun buildRunwayEntries(
    queue: List<TaskItem>,
    dailyCapacityMinutes: Int
): List<RunwayListEntry> {
    if (queue.isEmpty()) return emptyList()
    var cursor = 0
    var limitPlaced = false
    val entries = mutableListOf<RunwayListEntry>()

    for (task in queue) {
        val dur = task.estimatedMinutes ?: EffortTier.DEFAULT_UNSET_MINUTES
        val start = cursor
        val end = cursor + dur
        cursor = end
        val over = end > dailyCapacityMinutes
        if (over && !limitPlaced) {
            entries += RunwayListEntry.CapacityLimitLine(dailyCapacityMinutes)
            limitPlaced = true
        }
        entries += RunwayListEntry.TaskBlock(
            RunwayTimelineSlot(
                task = task,
                startMinutes = start,
                durationMinutes = dur,
                endMinutes = end,
                isOverCapacity = over
            )
        )
    }

    if (!limitPlaced) {
        if (cursor >= dailyCapacityMinutes) {
            entries += RunwayListEntry.CapacityLimitLine(dailyCapacityMinutes)
        } else {
            entries += RunwayListEntry.CapacityFooterSummary(
                allocatedMinutes = cursor,
                capacityMinutes = dailyCapacityMinutes
            )
        }
    }
    return entries
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
