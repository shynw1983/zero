package com.zero.todo.feature.triage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.hairlineDividerBottom
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.TaskItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ZERO 2.0 Lightning Triage Deck ("30 秒闪电清仓"):
 * Turns overwhelming Inbox & Overdue piles into a calm, 1-card-at-a-time thumb deck:
 * - Swipe UP / Tap "Today" -> Put on Today's Runway
 * - Swipe RIGHT / Tap "Tomorrow" -> Defer cleanly to Tomorrow
 * - Swipe LEFT / Tap "Let Go" -> Guilt-free discard to Trash
 * - Tap any Folder pill below -> File directly into that folder
 */
@Composable
fun TriageDeckOverlay(
    queue: List<TaskItem>,
    quickFolders: List<Folder>,
    onScheduleToday: (TaskItem) -> Unit,
    onDeferTomorrow: (TaskItem) -> Unit,
    onMoveToFolder: (TaskItem, String) -> Unit,
    onDiscardTask: (TaskItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onDismiss)

    val currentTask = queue.firstOrNull()
    val offsetX = remember(currentTask?.id) { Animatable(0f) }
    val offsetY = remember(currentTask?.id) { Animatable(0f) }

    val latestScheduleToday by rememberUpdatedState(onScheduleToday)
    val latestDeferTomorrow by rememberUpdatedState(onDeferTomorrow)
    val latestDiscard by rememberUpdatedState(onDiscardTask)

    LaunchedEffect(currentTask?.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineDividerBottom()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        text = stringResource(R.string.triage_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = queue.size.toString().padStart(2, '0'),
                        style = tabular.counter,
                        color = colors.accent
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ZeroSmallShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroSmallShape)
                        .clickable {
                            haptics.reorderTick()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (currentTask == null) {
                // Zero-Inbox Celebration State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
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
                            color = colors.accent
                        )
                        Text(
                            text = stringResource(R.string.triage_all_clear_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.triage_all_clear_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(ZeroCapsuleShape)
                                .background(colors.accent)
                                .clickable {
                                    haptics.confirm()
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.triage_enter_now),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onAccent
                            )
                        }
                    }
                }
            } else {
                // Live Gesture Hint Banner
                val activeGestureLabel = when {
                    offsetY.value < -110f && abs(offsetY.value) > abs(offsetX.value) ->
                        stringResource(R.string.triage_action_today) to colors.accent
                    offsetX.value > 110f ->
                        stringResource(R.string.triage_action_tomorrow) to colors.priorityHigh
                    offsetX.value < -110f ->
                        stringResource(R.string.triage_action_let_go) to colors.priorityUrgent
                    else -> stringResource(R.string.triage_gesture_hint) to colors.textMuted
                }

                Text(
                    text = activeGestureLabel.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = activeGestureLabel.second,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )

                // Draggable Triage Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                            .graphicsLayer {
                                rotationZ = (offsetX.value / 35f).coerceIn(-8f, 8f)
                            }
                            .clip(ZeroCardShape)
                            .background(colors.surface)
                            .hairlineBorder(
                                shape = ZeroCardShape,
                                color = if (abs(offsetX.value) > 110f || offsetY.value < -110f) {
                                    activeGestureLabel.second
                                } else {
                                    colors.hairlineBorder
                                }
                            )
                            .pointerInput(currentTask.id) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val dx = offsetX.value
                                        val dy = offsetY.value
                                        when {
                                            dy < -130f && abs(dy) > abs(dx) -> {
                                                haptics.confirm()
                                                latestScheduleToday(currentTask)
                                            }
                                            dx > 130f -> {
                                                haptics.reorderTick()
                                                latestDeferTomorrow(currentTask)
                                            }
                                            dx < -130f -> {
                                                haptics.confirm()
                                                latestDiscard(currentTask)
                                            }
                                            else -> {
                                                scope.launch {
                                                    offsetX.animateTo(0f, ZeroMotion.FloatSpring)
                                                }
                                                scope.launch {
                                                    offsetY.animateTo(0f, ZeroMotion.FloatSpring)
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { offsetX.animateTo(0f, ZeroMotion.FloatSpring) }
                                        scope.launch { offsetY.animateTo(0f, ZeroMotion.FloatSpring) }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                            offsetY.snapTo(offsetY.value + dragAmount.y)
                                        }
                                    }
                                )
                            }
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = currentTask.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary
                        )
                        if (currentTask.description.isNotBlank()) {
                            Text(
                                text = currentTask.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (currentTask.tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                currentTask.tags.take(4).forEach { tag ->
                                    Text(
                                        text = "#${tag.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier
                                            .clip(ZeroSmallShape)
                                            .background(colors.surfaceElevated)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Folder Filing Dock (generous 44dp pills)
                if (quickFolders.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.triage_file_to_folder),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickFolders.forEach { folder ->
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(ZeroSmallShape)
                                        .background(colors.surface)
                                        .hairlineBorder(shape = ZeroSmallShape)
                                        .clickable {
                                            haptics.confirm()
                                            onMoveToFolder(currentTask, folder.id)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom 3 Large Thumb Action Buttons (56dp height!)
                Row(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TriageActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        label = stringResource(R.string.triage_action_let_go),
                        accent = colors.priorityUrgent,
                        onClick = {
                            haptics.confirm()
                            onDiscardTask(currentTask)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TriageActionButton(
                        icon = Icons.Rounded.ArrowUpward,
                        label = stringResource(R.string.triage_action_today),
                        accent = colors.accent,
                        filled = true,
                        onClick = {
                            haptics.confirm()
                            onScheduleToday(currentTask)
                        },
                        modifier = Modifier.weight(1.2f)
                    )
                    TriageActionButton(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.triage_action_tomorrow),
                        accent = colors.priorityHigh,
                        onClick = {
                            haptics.reorderTick()
                            onDeferTomorrow(currentTask)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TriageActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(ZeroCapsuleShape)
            .background(if (filled) accent else colors.surface)
            .hairlineBorder(
                shape = ZeroCapsuleShape,
                color = if (filled) accent else accent.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) colors.onAccent else accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) colors.onAccent else colors.textPrimary,
            maxLines = 1
        )
    }
}
