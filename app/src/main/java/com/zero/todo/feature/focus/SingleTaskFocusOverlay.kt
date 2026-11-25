package com.zero.todo.feature.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.todo.core.designsystem.ZeroCapsuleShape
import com.zero.todo.core.designsystem.ZeroCardShape
import com.zero.todo.core.designsystem.ZeroMotion
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.core.designsystem.rememberZeroHaptics
import com.zero.todo.domain.model.FocusMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskPriority

/**
 * Full-screen distraction-free Single-Task Focus Mode.
 * Hides all navigation chrome and lists, centering purely on 1 task, the tabular countdown ring,
 * the "Just 2 Mins" starter, and seamless transition to a 25-minute Pomodoro.
 */
@Composable
fun SingleTaskFocusOverlay(
    state: FocusUiState,
    onSelectMode: (FocusMode) -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onContinueToPomodoro: () -> Unit,
    onStopStopwatch: () -> Unit,
    onCompleteTask: (TaskItem) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val haptics = rememberZeroHaptics()

    BackHandler(enabled = state.isActive) {
        onExit()
    }

    LaunchedEffect(state.hapticSignalId) {
        if (state.hapticSignalId > 0L) {
            when (state.hapticKind) {
                FocusHapticKind.GENTLE_TWO_MIN_DONE -> haptics.gentlePrompt()
                FocusHapticKind.HEAVY_POMODORO_DONE -> haptics.heavyClick()
                FocusHapticKind.NONE -> Unit
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = ZeroMotion.FloatSpring,
        label = "focus_ring_progress"
    )

    val todayMinutes = state.todayFocusSeconds / 60

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Minimal Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Text(
                        text = "SINGLE FOCUS",
                        style = tabular.dateCompact,
                        color = colors.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "今日 ${todayMinutes.toString().padStart(2, '0')}m",
                        style = tabular.dateCompact,
                        color = colors.textMuted
                    )
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "退出专注模式",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Centerpiece: Mode Switcher + Single Task + Tabular Progress Ring
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Mode Selector
                Row(
                    modifier = Modifier
                        .clip(ZeroCapsuleShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroCapsuleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FocusModeTab(
                        label = "先做 2 分钟",
                        selected = state.mode == FocusMode.TWO_MIN_STARTER,
                        onClick = {
                            haptics.reorderTick()
                            onSelectMode(FocusMode.TWO_MIN_STARTER)
                        }
                    )
                    FocusModeTab(
                        label = "25m 番茄",
                        selected = state.mode == FocusMode.POMODORO,
                        onClick = {
                            haptics.reorderTick()
                            onSelectMode(FocusMode.POMODORO)
                        }
                    )
                    FocusModeTab(
                        label = "正计时",
                        selected = state.mode == FocusMode.STOPWATCH,
                        onClick = {
                            haptics.reorderTick()
                            onSelectMode(FocusMode.STOPWATCH)
                        }
                    )
                }

                // Single Focused Task Card (Zero distractions)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZeroCardShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroCardShape)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeTask = state.task
                    if (activeTask != null && activeTask.priority != TaskPriority.NONE) {
                        Text(
                            text = "P${activeTask.priority.value}",
                            style = tabular.priorityTag,
                            color = colors.accent
                        )
                    }
                    Text(
                        text = activeTask?.title ?: "自由专注时段",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Precision Circular Countdown Ring + Tabular Hero Digits
                Box(
                    modifier = Modifier.size(236.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 3.dp.toPx()
                        val trackStrokePx = 0.75.dp.toPx()
                        val inset = strokeWidthPx
                        val diameter = size.minDimension - inset * 2

                        drawCircle(
                            color = colors.hairlineBorder,
                            radius = diameter / 2f,
                            style = Stroke(width = trackStrokePx)
                        )

                        drawArc(
                            color = colors.accent,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.formattedTimer,
                            style = tabular.timerHero,
                            color = colors.textPrimary
                        )
                        Text(
                            text = when (state.phase) {
                                FocusPhase.READY -> when (state.mode) {
                                    FocusMode.TWO_MIN_STARTER -> "打破启动阻力，只需 2 分钟"
                                    FocusMode.POMODORO -> "25 分钟深度专注"
                                    FocusMode.STOPWATCH -> "无压正计时"
                                }
                                FocusPhase.RUNNING -> "专注进行中"
                                FocusPhase.PAUSED -> "已暂停"
                                FocusPhase.TWO_MIN_FINISHED_PROMPT -> "2 分钟起步完成"
                                FocusPhase.POMODORO_COMPLETED -> "番茄钟完成"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT ||
                                state.phase == FocusPhase.POMODORO_COMPLETED
                            ) colors.accent else colors.textMuted
                        )
                    }
                }

                // Seamless 2-Minute -> 25-Minute Pomodoro Continuation Card
                AnimatedVisibility(visible = state.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZeroCardShape)
                            .background(colors.surfaceElevated)
                            .hairlineBorder(shape = ZeroCardShape, color = colors.accent)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "状态已启动，是否乘势进入完整专注？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ZeroCapsuleShape)
                                .background(colors.accent)
                                .clickable {
                                    haptics.heavyClick()
                                    onContinueToPomodoro()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "无缝续接 25 分钟番茄钟",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onAccent
                            )
                        }
                    }
                }
            }

            // Bottom Minimalist Controls
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset / Stop button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(ZeroCapsuleShape)
                        .background(colors.surface)
                        .hairlineBorder(shape = ZeroCapsuleShape)
                        .clickable {
                            haptics.reorderTick()
                            if (state.mode == FocusMode.STOPWATCH && state.elapsedSeconds > 0) {
                                onStopStopwatch()
                            } else {
                                onReset()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.mode == FocusMode.STOPWATCH && state.elapsedSeconds > 0) {
                            Icons.Rounded.Stop
                        } else {
                            Icons.Rounded.Refresh
                        },
                        contentDescription = "重置计时",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Primary Play / Pause button
                val isRunning = state.phase == FocusPhase.RUNNING
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(ZeroCapsuleShape)
                        .background(if (isRunning) colors.surfaceElevated else colors.accent)
                        .hairlineBorder(
                            shape = ZeroCapsuleShape,
                            color = if (isRunning) colors.hairlineBorder else colors.accent
                        )
                        .clickable {
                            haptics.confirm()
                            if (isRunning) onPause() else onStartOrResume()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = if (isRunning) colors.textPrimary else colors.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = when {
                            isRunning -> "暂停"
                            state.phase == FocusPhase.PAUSED -> "继续专注"
                            state.mode == FocusMode.TWO_MIN_STARTER -> "先做 2 分钟"
                            state.mode == FocusMode.POMODORO -> "开始番茄钟"
                            else -> "开始计时"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isRunning) colors.textPrimary else colors.onAccent
                    )
                }

                // Complete Task button
                if (state.task != null && !state.task.isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(ZeroCapsuleShape)
                            .background(colors.surface)
                            .hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent)
                            .clickable {
                                haptics.taskComplete()
                                onCompleteTask(state.task)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "完成任务",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroTheme.colors
    Box(
        modifier = Modifier
            .clip(ZeroCapsuleShape)
            .background(if (selected) colors.surfaceElevated else colors.surface)
            .then(if (selected) Modifier.hairlineBorder(shape = ZeroCapsuleShape, color = colors.accent) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}
